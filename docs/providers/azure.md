# Microsoft Foundry (Azure)

For Qwen, Gemma, and other catalog models deployed to a managed endpoint with an **OpenAI-compatible** `/v1/chat/completions` URL.

```properties
llm.provider=azure
llm.azure.base-url=https://YOUR-ENDPOINT/v1
llm.azure.api-key=${AZURE_ML_API_KEY}
llm.azure.model=Qwen/Qwen3.5-9B
# llm.azure.deployment-name=   # optional azureml-model-deployment header
```

1. Deploy the model in [Microsoft Foundry](https://ai.azure.com).
2. From the deployment **Code** tab, copy the base URL ending in `/v1` (not `/generate`), the API key, and the catalog **model** id.
3. Use templates `application-azure-qwen.properties` or `application-azure-gemma.properties`, or set env vars for [Docker + cloud LLM](../run/docker-cloud-llm.md).

One endpoint per model is simplest; use separate profiles or property files for Qwen vs Gemma.

Chat Completions only (not the Responses API). Prefer models that work in the Foundry playground with chat completions.
