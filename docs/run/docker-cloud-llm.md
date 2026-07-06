# Run: Docker + cloud LLM

SnoScribe and Infinity run in Docker; the chat model is a **cloud** endpoint (Microsoft Foundry, OpenAI, Anthropic, or Google). No Ollama container — expect about **6 GB RAM** for Infinity + the JVM.

## Prerequisites

- Docker Compose v2
- A deployed cloud model and API credentials
- A **SNOMED CT-capable FHIR terminology server** base URL (you will set `FHIR_TX_URL` in `.env`)
- Network from the app container to your configured `FHIR_TX_URL` and LLM endpoint

## Steps

1. Copy and edit environment variables:

   ```bash
   cp .env.example .env
   ```

2. Set `FHIR_TX_URL` in `.env` to the base URL of your FHIR terminology server (for example `https://your-tx.example.org/fhir`). The server must support SNOMED CT ValueSet `$expand`; ConceptMap `$translate` is optional but enables ICD-10 mapping for patient conditions. SnoScribe does not bundle terminology — without a reachable TX server, SNOMED enrichment fails and annotations show terminology errors in the UI. See [configuration.md](../configuration.md) (`fhir.tx.url`).

3. Set `LLM_PROVIDER` and the matching keys in `.env` (see provider docs below).

4. Start the stack:

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.cloud-llm.yml up --build
   ```

5. Open **http://localhost:8080**.

## Provider setup

| Provider | Doc |
|----------|-----|
| Microsoft Foundry (Qwen, Gemma) | [azure.md](../providers/azure.md) |
| OpenAI | [openai.md](../providers/openai.md) |
| Anthropic | [anthropic.md](../providers/anthropic.md) |
| Google Gemini | [google.md](../providers/google.md) |

Example `.env` for Foundry:

```bash
FHIR_TX_URL=https://your-tx.example.org/fhir
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
