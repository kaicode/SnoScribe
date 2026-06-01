package org.snomed.snoscribe.config;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;

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

	/**
	 * Max tokens the model may emit. Langchain4j Anthropic defaults to 1024, which truncates
	 * large annotation JSON and breaks parsing — raise this for long clinical notes.
	 */
	@Value("${llm.anthropic.max-output-tokens:16384}")
	private int anthropicMaxOutputTokens;

	@Value("${llm.google.api-key:}")
	private String googleApiKey;

	@Value("${llm.google.model:}")
	private String googleModel;

	@Value("${llm.azure.base-url:}")
	private String azureBaseUrl;

	@Value("${llm.azure.api-key:}")
	private String azureApiKey;

	@Value("${llm.azure.model:}")
	private String azureModel;

	/**
	 * Optional {@code azureml-model-deployment} header when multiple deployments share one scoring URL.
	 */
	@Value("${llm.azure.deployment-name:}")
	private String azureDeploymentName;

	@Value("${llm.azure.max-output-tokens:16384}")
	private int azureMaxOutputTokens;

	@PostConstruct
	void validateProviderConfig() {
		if ("azure".equalsIgnoreCase(provider)) {
			validateAzureConfig();
		}
	}

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
					.maxTokens(anthropicMaxOutputTokens)
					.build();
			case "google" -> GoogleAiGeminiChatModel.builder()
					.apiKey(googleApiKey)
					.modelName(modelName)
					.build();
			case "azure" -> {
				validateAzureConfig();
				OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
						.baseUrl(stripTrailingSlash(azureBaseUrl))
						.apiKey(azureApiKey)
						.modelName(modelName)
						.maxTokens(azureMaxOutputTokens);
				Map<String, String> headers = azureCustomHeaders();
				if (!headers.isEmpty()) {
					builder.customHeaders(headers);
				}
				yield builder.build();
			}
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
			case "azure" -> azureModel;
			default -> ollamaModel;
		};
	}

	private void validateAzureConfig() {
		if (!StringUtils.hasText(azureBaseUrl)) {
			throw new IllegalStateException(
					"llm.provider=azure requires llm.azure.base-url (Foundry endpoint including /v1, e.g. https://<host>/v1)");
		}
		if (!StringUtils.hasText(azureApiKey)) {
			throw new IllegalStateException("llm.provider=azure requires llm.azure.api-key (or AZURE_ML_API_KEY env var)");
		}
		if (!StringUtils.hasText(azureModel)) {
			throw new IllegalStateException(
					"llm.provider=azure requires llm.azure.model (catalog model id from the Foundry deployment Code tab)");
		}
	}

	private Map<String, String> azureCustomHeaders() {
		if (!StringUtils.hasText(azureDeploymentName)) {
			return Collections.emptyMap();
		}
		return Map.of("azureml-model-deployment", azureDeploymentName.trim());
	}

	private static String stripTrailingSlash(String url) {
		if (url == null || url.isBlank()) {
			return url;
		}
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
