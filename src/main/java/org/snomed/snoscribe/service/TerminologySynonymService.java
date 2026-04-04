package org.snomed.snoscribe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snoscribe.model.AnnotationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Asks the LLM for 1–2 synonym or spelling-variant phrases when FHIR $expand
 * returns no rows for the original and fuzzy filters.
 */
@Service
public class TerminologySynonymService {

	private static final Pattern JSON_CODE_BLOCK = Pattern.compile("^\\s*```(?:json)?\\s*\\n?(.*)\\n?```\\s*$", Pattern.DOTALL);

	private final ChatModel chatModel;
	private final ObjectMapper objectMapper;
	private final String systemPrompt;
	private final boolean enabled;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public TerminologySynonymService(ChatModel chatModel, ObjectMapper objectMapper,
			@Value("${terminology.synonym-llm.enabled:true}") boolean enabled) throws IOException {
		this.chatModel = chatModel;
		this.objectMapper = objectMapper;
		this.enabled = enabled;
		this.systemPrompt = StreamUtils.copyToString(
				getClass().getClassLoader().getResourceAsStream("terminology-synonym-prompt.txt"),
				StandardCharsets.UTF_8);
	}

	/**
	 * Returns up to two distinct non-blank suggestions, excluding any that equal
	 * {@code originalTerm} ignoring case. Empty if disabled or the LLM call fails.
	 */
	public List<String> suggestSynonyms(String originalTerm, AnnotationType type) {
		if (!enabled || originalTerm == null || originalTerm.isBlank()) {
			return List.of();
		}
		try {
			String user = "Annotation type: " + type.name() + "\nTerm: " + originalTerm.trim();
			List<ChatMessage> messages = List.of(
					SystemMessage.from(systemPrompt),
					UserMessage.from(user)
			);
			String content = chatModel.chat(messages).aiMessage().text();
			if (content == null || content.isBlank()) {
				return List.of();
			}
			String json = stripJsonCodeBlock(content.trim());
			List<String> raw = objectMapper.readValue(json, new TypeReference<>() {});
			if (raw == null) {
				return List.of();
			}
			LinkedHashSet<String> out = new LinkedHashSet<>();
			for (String s : raw) {
				if (s == null) {
					continue;
				}
				String t = s.trim();
				if (t.isEmpty() || t.equalsIgnoreCase(originalTerm.trim())) {
					continue;
				}
				out.add(t);
				if (out.size() >= 2) {
					break;
				}
			}
			return new ArrayList<>(out);
		} catch (Exception e) {
			logger.warn("LLM synonym suggestion failed for '{}': {}", originalTerm, e.getMessage());
			return List.of();
		}
	}

	private static String stripJsonCodeBlock(String content) {
		var matcher = JSON_CODE_BLOCK.matcher(content);
		if (matcher.matches()) {
			return matcher.group(1).trim();
		}
		return content;
	}
}
