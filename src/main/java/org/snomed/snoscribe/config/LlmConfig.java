package org.snomed.snoscribe.config;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

	@Value("${llm.provider}")
	private String provider;

	@Value("${llm.ollama.base-url}")
	private String ollamaBaseUrl;

	@Value("${llm.ollama.model}")
	private String ollamaModel;

	@Value("${llm.ollama.think}")
	private boolean ollamaThink;

	@Value("${llm.openai.api-key:}")
	private String openAiApiKey;

	@Value("${llm.openai.model:}")
	private String openAiModel;

	@Value("${llm.anthropic.api-key:}")
	private String anthropicApiKey;

	@Value("${llm.anthropic.model:}")
	private String anthropicModel;

	@Value("${llm.google.api-key:}")
	private String googleApiKey;

	@Value("${llm.google.model:}")
	private String googleModel;

	@Bean
	public ChatModel chatModel() {
		return buildModel(defaultModelName());
	}

	/**
	 * Builds a {@link ChatModel} for the configured provider using the given model name.
	 * Used by {@link org.snomed.snoscribe.service.LlmProcessorService} to support
	 * per-call model overrides (e.g. during evaluation runs).
	 */
	public ChatModel buildModel(String modelName) {
		return switch (provider.toLowerCase()) {
			case "openai" -> OpenAiChatModel.builder()
					.apiKey(openAiApiKey)
					.modelName(modelName)
					.build();
			case "anthropic" -> AnthropicChatModel.builder()
					.apiKey(anthropicApiKey)
					.modelName(modelName)
					.build();
			case "google" -> GoogleAiGeminiChatModel.builder()
					.apiKey(googleApiKey)
					.modelName(modelName)
					.build();
			default -> OllamaChatModel.builder()
					.baseUrl(ollamaBaseUrl)
					.modelName(modelName)
					.think(ollamaThink)
					.build();
		};
	}

	public String defaultModelName() {
		return switch (provider.toLowerCase()) {
			case "openai" -> openAiModel;
			case "anthropic" -> anthropicModel;
			case "google" -> googleModel;
			default -> ollamaModel;
		};
	}
}
