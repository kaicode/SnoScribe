package org.snomed.annotate.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.annotate.exception.ServiceException;
import org.snomed.annotate.model.Annotation;
import org.snomed.annotate.model.Context;
import org.snomed.annotate.model.Laterality;
import org.snomed.annotate.model.Subject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class OllamaProcessorService {

	private static final Pattern JSON_CODE_BLOCK = Pattern.compile("^\\s*```(?:json)?\\s*\\n?(.*)\\n?```\\s*$", Pattern.DOTALL);

	private final String apiUrl = "http://localhost:11434/api/chat";
	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final String systemPrompt;
	private final String model;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public OllamaProcessorService(RestTemplateBuilder builder,
			@Value("${ollama.model}") String model) throws IOException {
		this.restTemplate = builder.build();
		this.model = model;
		systemPrompt = StreamUtils.copyToString(getClass().getClassLoader().getResourceAsStream("annotate-prompt.txt"), StandardCharsets.UTF_8);
	}

	public List<Annotation> processDocument(String document) throws ServiceException {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		ConversationMessage[] conversation = new ConversationMessage[2];
		conversation[0] = new ConversationMessage("user", systemPrompt);
		conversation[1] = new ConversationMessage("user", document);
		System.out.println("--Request Body Start --");
		System.out.println(systemPrompt);
		System.out.println(document);
		System.out.println("--Request Body End --");

		RequestBody requestBody = new RequestBody(model, false, conversation);

		HttpEntity<RequestBody> entity = new HttpEntity<>(requestBody, headers);

		try {
			ResponseEntity<OllamaChatResponse> responseEntity = restTemplate.postForEntity(apiUrl, entity, OllamaChatResponse.class);
			OllamaChatResponse ollamaResponse = responseEntity.getBody();
			if (ollamaResponse == null || ollamaResponse.message == null) {
				throw new RuntimeException("Empty response from Ollama API");
			}
			String content = ollamaResponse.message.content;
			if (content == null || content.isBlank()) {
				throw new RuntimeException("No content in Ollama API response");
			}
			String json = stripJsonCodeBlock(content);
			List<Map<String, Object>> rawList = objectMapper.readValue(json, new TypeReference<>() {});
			List<Map<String, String>> response = toStringMapList(rawList);
			return extractContentFromResponse(response);
		} catch (Exception e) {
			throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing document", e);
		}
	}

	/**
	 * Strips optional markdown code block wrapper (e.g. ```json ... ```) from the model output.
	 */
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
				if (entity.containsKey("n")) {
					annotation.setNormalisedText(entity.get("n"));
				}
				if (entity.containsKey("neg")) {
					annotation.setNegated("1".equals(entity.get("neg")));
				}
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
				annotations.add(annotation);
			}
		}
		return annotations;
	}

	// Inner classes for JSON structure
	static class ConversationMessage {
		public String role;
		public String content;

		public ConversationMessage(String role, String content) {
			this.role = role;
			this.content = content;
		}
	}

	static class RequestBody {
		public String model;
		public boolean stream;
		public ConversationMessage[] messages;

		public RequestBody(String model, boolean stream, ConversationMessage[] messages) {
			this.model = model;
			this.stream = stream;
			this.messages = messages;
		}
	}

	/** Ollama /api/chat response: message.content holds the assistant text (e.g. JSON array). */
	static class OllamaChatResponse {
		public String model;
		public OllamaMessage message;
		public Boolean done;
	}

	static class OllamaMessage {
		public String role;
		public String content;
	}
}
