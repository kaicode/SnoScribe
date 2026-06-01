# Run: Docker + cloud LLM

SnoScribe and Infinity run in Docker; the chat model is a **cloud** endpoint (Microsoft Foundry, OpenAI, Anthropic, or Google). No Ollama container — expect about **6 GB RAM** for Infinity + the JVM.

## Prerequisites

- Docker Compose v2
- A deployed cloud model and API credentials
- Network from the app container to your LLM endpoint and `FHIR_TX_URL`

## Steps

1. Copy and edit environment variables:

   ```bash
   cp .env.example .env
   ```

2. Set `LLM_PROVIDER` and the matching keys in `.env` (see provider docs below).

3. Start the stack:

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.cloud-llm.yml up --build
   ```

4. Open **http://localhost:8080**.

## Provider setup

| Provider | Doc |
|----------|-----|
| Microsoft Foundry (Qwen, Gemma) | [azure.md](../providers/azure.md) |
| OpenAI | [openai.md](../providers/openai.md) |
| Anthropic | [anthropic.md](../providers/anthropic.md) |
| Google Gemini | [google.md](../providers/google.md) |

Example `.env` for Foundry:

```bash
LLM_PROVIDER=azure
LLM_AZURE_BASE_URL=https://YOUR-ENDPOINT/v1
LLM_AZURE_API_KEY=your-key
LLM_AZURE_MODEL=Qwen/Qwen3.5-9B
```

## Verify

```bash
curl -s http://localhost:7997/health
curl -sf http://localhost:8080/ >/dev/null && echo OK
```

## See also

- [Configuration reference](../configuration.md)
- [Local full Docker stack](docker-ollama.md)
