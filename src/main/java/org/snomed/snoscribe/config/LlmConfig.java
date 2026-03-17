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

	@Value("${llm.ollama.base-url:http://localhost:11434}")
	private String ollamaBaseUrl;

	@Value("${llm.ollama.model:gemma3:4b}")
	private String ollamaModel;

	@Value("${llm.openai.api-key:}")
	private String openAiApiKey;

	@Value("${llm.openai.model:gpt-4o}")
	private String openAiModel;

	@Value("${llm.anthropic.api-key:}")
	private String anthropicApiKey;

	@Value("${llm.anthropic.model:claude-opus-4-5}")
	private String anthropicModel;

	@Value("${llm.google.api-key:}")
	private String googleApiKey;

	@Value("${llm.google.model:gemini-1.5-pro}")
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
