# Run: everything in Docker (local Ollama)

SnoScribe, Infinity reranker, and Ollama run in containers. Clinical notes are sent to Ollama inside Docker; terminology uses your configured FHIR server.

## Prerequisites

- Docker Compose v2
- About **14 GB RAM** on the host (Ollama ~8 GB, Infinity ~4 GB, app ~1.5 GB). Prefer a smaller model such as `gemma4:e2b` if memory is tight.
- A **SNOMED CT-capable FHIR terminology server** base URL (you will set `FHIR_TX_URL` in `.env`)
- Network from the app container to your configured `FHIR_TX_URL`

## Steps

1. Copy and edit environment variables:

   ```bash
   cp .env.example .env
   ```

2. Set `FHIR_TX_URL` in `.env` to the base URL of your FHIR terminology server (for example `https://your-tx.example.org/fhir`). The server must support SNOMED CT ValueSet `$expand`; ConceptMap `$translate` is optional but enables ICD-10 mapping for patient conditions. SnoScribe does not bundle terminology — without a reachable TX server, SNOMED enrichment fails and annotations show terminology errors in the UI. See [configuration.md](../configuration.md) (`fhir.tx.url`).

3. Optionally set `LLM_OLLAMA_MODEL` in `.env` (default `gemma4:e2b`). See [ollama.md](../providers/ollama.md).

4. Start the stack:

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.ollama.yml up --build
   ```

5. After the stack is healthy, pull the Ollama model once (match `LLM_OLLAMA_MODEL` in `.env`):

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.ollama.yml exec ollama ollama pull gemma4:e2b
   ```

6. Open **http://localhost:8080**.

Example `.env`:

```bash
FHIR_TX_URL=https://your-tx.example.org/fhir
LLM_OLLAMA_MODEL=gemma4:e2b
```

## Verify

```bash
curl -s http://localhost:7997/health
curl -s http://localhost:11434/
```

## Troubleshooting

- **Apple Silicon:** Infinity runs as `linux/amd64` (emulation); first start can be slow.
- **Infinity fails on `--engine torch`:** see [infinity CPU image notes](https://github.com/michaelfeil/infinity) or adjust the `command` in `docker-compose.yml`.

## See also

- [Ollama settings](../providers/ollama.md)
- [Configuration reference](../configuration.md)
- [Development on the host](../development.md) — Maven + host Ollama instead of this stack
