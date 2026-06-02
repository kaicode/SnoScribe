package org.snomed.snoscribe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snoscribe.config.LlmConfig;
import org.snomed.snoscribe.exception.ServiceException;
import org.snomed.snoscribe.model.Annotation;
import org.snomed.snoscribe.model.AnnotationType;
import org.snomed.snoscribe.model.LlmProcessResult;
import org.snomed.snoscribe.model.Context;
import org.snomed.snoscribe.model.Laterality;
import org.snomed.snoscribe.model.Subject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class LlmProcessorService {

	private static final Pattern JSON_CODE_BLOCK = Pattern.compile("^\\s*```(?:json)?\\s*\\n?(.*)\\n?```\\s*$", Pattern.DOTALL);

	private static final String LLM_CONNECTION_FAILURE_MESSAGE =
			"Failed to connect to the LLM service. Check that the LLM service is running and reachable.";

	private final ChatModel defaultModel;
	private final LlmConfig llmConfig;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final String systemPrompt;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public LlmProcessorService(ChatModel defaultModel, LlmConfig llmConfig) throws IOException {
		this.defaultModel = defaultModel;
		this.llmConfig = llmConfig;
		systemPrompt = StreamUtils.copyToString(getClass().getClassLoader().getResourceAsStream("annotate-prompt.txt"), StandardCharsets.UTF_8);
	}

	public LlmProcessResult processDocument(String document) throws ServiceException {
		return processWithModel(defaultModel, document);
	}

	public LlmProcessResult processDocument(String document, String modelName) throws ServiceException {
		return processWithModel(llmConfig.buildModel(modelName), document);
	}

	private LlmProcessResult processWithModel(ChatModel model, String document) throws ServiceException {
		logger.debug("--Request Body Start --");
		logger.debug(systemPrompt);
		logger.debug(document);
		logger.debug("--Request Body End --");

		List<ChatMessage> messages = List.of(
				SystemMessage.from(systemPrompt),
				UserMessage.from(document)
		);

		try {
			long llmStart = System.currentTimeMillis();
			String content = model.chat(messages).aiMessage().text();
			double llmSeconds = round1dp((System.currentTimeMillis() - llmStart) / 1000.0);
			if (content == null || content.isBlank()) {
				throw new RuntimeException("No content in LLM response");
			}
			String json = stripJsonCodeBlock(content);
			List<Map<String, Object>> rawList = objectMapper.readValue(json, new TypeReference<>() {});
			List<Map<String, String>> response = toStringMapList(rawList);
			return new LlmProcessResult(extractContentFromResponse(response), llmSeconds);
		} catch (Exception e) {
			if (isConnectFailure(e)) {
				logger.error(LLM_CONNECTION_FAILURE_MESSAGE, e);
				throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, LLM_CONNECTION_FAILURE_MESSAGE, e);
			}
			throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing document", e);
		}
	}

	private static boolean isConnectFailure(Throwable throwable) {
		for (Throwable t = throwable; t != null; t = t.getCause()) {
			if (t instanceof ConnectException) {
				return true;
			}
		}
		return false;
	}

	private String stripJsonCodeBlock(String content) {
		content = content.trim();
		var matcher = JSON_CODE_BLOCK.matcher(content);
		if (matcher.matches()) {
			return matcher.group(1).trim();
		}
		return content;
	}

	private List<Map<String, String>> toStringMapList(List<Map<String, Object>> rawList) {
		List<Map<String, String>> result = new ArrayList<>();
		for (Map<String, Object> raw : rawList) {
			Map<String, String> row = new LinkedHashMap<>();
			for (Map.Entry<String, Object> e : raw.entrySet()) {
				row.put(e.getKey(), e.getValue() == null ? null : String.valueOf(e.getValue()));
			}
			result.add(row);
		}
		return result;
	}

	private List<Annotation> extractContentFromResponse(List<Map<String, String>> response) {
		List<Annotation> annotations = new ArrayList<>();
		for (Map<String, String> entity : response) {
			if (entity.containsKey("t")) {
				Annotation annotation = new Annotation();
				annotation.setText(entity.get("t"));
				if (entity.containsKey("type")) {
					String type = entity.get("type");
					if ("MEDICATION".equals(type)) {
						annotation.setType(AnnotationType.MEDICATION);
					} else if ("PROCEDURE".equals(type)) {
						annotation.setType(AnnotationType.PROCEDURE);
					} else {
						annotation.setType(AnnotationType.CONDITION);
					}
				}
				if (entity.containsKey("n")) {
					annotation.setNormalisedText(entity.get("n"));
				}
				if (entity.containsKey("dose")) {
					annotation.setDose(entity.get("dose"));
				}
				if (entity.containsKey("freq")) {
					annotation.setFrequency(entity.get("freq"));
				}
				if (entity.containsKey("route")) {
					annotation.setRoute(entity.get("route"));
				}
				if (entity.containsKey("doseForm")) {
					annotation.setDoseForm(entity.get("doseForm"));
				}
				if (entity.containsKey("neg")) {
					annotation.setNegated("1".equals(entity.get("neg")));
				}
				extractSubject(entity, annotation);
				extractLaterality(entity, annotation);
				extractContext(entity, annotation);
				annotations.add(annotation);
			}
		}
		return annotations;
	}

	private void extractSubject(Map<String, String> entity, Annotation annotation) {
		if (entity.containsKey("s")) {
			String subject = entity.get("s");
			if ("PATIENT".equals(subject)) {
				annotation.setSubject(Subject.PATIENT);
			} else if ("FAMILY".equals(subject)) {
				annotation.setSubject(Subject.FAMILY);
			} else if ("OTHER".equals(subject)) {
				annotation.setSubject(Subject.OTHER);
			} else {
				logger.warn("Unrecognized subject: {}", subject);
			}
		}
	}

	private void extractLaterality(Map<String, String> entity, Annotation annotation) {
		if (entity.containsKey("l")) {
			String laterality = entity.get("l");
			if ("LEFT".equals(laterality)) {
				annotation.setLaterality(Laterality.LEFT);
			} else if ("RIGHT".equals(laterality)) {
				annotation.setLaterality(Laterality.RIGHT);
			} else if ("LEFT_AND_RIGHT".equals(laterality)) {
				annotation.setLaterality(Laterality.LEFT_AND_RIGHT);
			} else {
				logger.warn("Unrecognized laterality: {}", laterality);
			}
		}
	}

	private static double round1dp(double value) {
		return Math.round(value * 10.0) / 10.0;
	}

	private void extractContext(Map<String, String> entity, Annotation annotation) {
		if (entity.containsKey("c")) {
			String context = entity.get("c");
			if ("CURRENT".equals(context)) {
				annotation.setContext(Context.CURRENT);
			} else if ("HISTORICAL".equals(context)) {
				annotation.setContext(Context.HISTORICAL);
			} else if ("SUSPECTED".equals(context)) {
				annotation.setContext(Context.SUSPECTED);
			} else {
				logger.warn("Unrecognized context: {}", context);
			}
		}
	}
}
