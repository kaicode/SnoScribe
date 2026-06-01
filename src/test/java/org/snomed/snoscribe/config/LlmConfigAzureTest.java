package org.snomed.snoscribe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmConfigAzureTest {

	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void buildModel_callsFoundryChatCompletionsWithBearerAndModel() throws Exception {
		AtomicReference<String> authorization = new AtomicReference<>();
		AtomicReference<Map<String, List<String>>> requestHeaders = new AtomicReference<>();
		AtomicReference<String> requestBody = new AtomicReference<>();

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		int port = server.getAddress().getPort();
		server.createContext("/v1/chat/completions", exchange -> {
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			requestHeaders.set(exchange.getRequestHeaders());
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] response = new ObjectMapper().writeValueAsBytes(Map.of(
					"choices", List.of(Map.of(
							"message", Map.of(
									"role", "assistant",
									"content", "ok")))));
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, response.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(response);
			}
		});
		server.start();

		LlmConfig config = azureConfig("http://127.0.0.1:" + port + "/v1", "test-key", "", "Qwen/Qwen3.5-9B");
		ChatModel model = config.buildModel("Qwen/Qwen3.5-9B");
		String reply = model.chat(UserMessage.from("hi")).aiMessage().text();

		assertThat(reply).isEqualTo("ok");
		assertThat(authorization.get()).isEqualTo("Bearer test-key");
		assertThat(requestBody.get()).contains("Qwen/Qwen3.5-9B");
		assertThat(requestHeaders.get().get("azureml-model-deployment")).isNull();
	}

	@Test
	void buildModel_addsAzureMlDeploymentHeaderWhenConfigured() throws Exception {
		AtomicReference<String> deploymentHeader = new AtomicReference<>();

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		int port = server.getAddress().getPort();
		server.createContext("/v1/chat/completions", exchange -> {
			deploymentHeader.set(exchange.getRequestHeaders().getFirst("azureml-model-deployment"));
			byte[] response = new ObjectMapper().writeValueAsBytes(Map.of(
					"choices", List.of(Map.of(
							"message", Map.of(
									"role", "assistant",
									"content", "[]")))));
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, response.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(response);
			}
		});
		server.start();

		LlmConfig config = azureConfig(
				"http://127.0.0.1:" + port + "/v1/",
				"key",
				"my-deployment",
				"Gemma/Test");
		config.buildModel("Gemma/Test").chat(UserMessage.from("note"));

		assertThat(deploymentHeader.get()).isEqualTo("my-deployment");
	}

	@Test
	void validateAzureConfig_requiresBaseUrlApiKeyAndModel() {
		LlmConfig config = new LlmConfig();
		ReflectionTestUtils.setField(config, "provider", "azure");
		ReflectionTestUtils.setField(config, "azureBaseUrl", "");
		ReflectionTestUtils.setField(config, "azureApiKey", "key");
		ReflectionTestUtils.setField(config, "azureModel", "Qwen/X");

		assertThatThrownBy(config::validateProviderConfig)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("llm.azure.base-url");
	}

	private static LlmConfig azureConfig(String baseUrl, String apiKey, String deploymentName, String defaultModel) {
		LlmConfig config = new LlmConfig();
		ReflectionTestUtils.setField(config, "provider", "azure");
		ReflectionTestUtils.setField(config, "azureBaseUrl", baseUrl);
		ReflectionTestUtils.setField(config, "azureApiKey", apiKey);
		ReflectionTestUtils.setField(config, "azureModel", defaultModel);
		ReflectionTestUtils.setField(config, "azureDeploymentName", deploymentName);
		ReflectionTestUtils.setField(config, "azureMaxOutputTokens", 16384);
		return config;
	}
}
