# Run: everything in Docker (local Ollama)

SnoScribe, Infinity reranker, and Ollama run in containers. Clinical notes are sent to Ollama inside Docker; terminology still uses your configured FHIR server (default: public Snowstorm demo).

## Prerequisites

- Docker Compose v2
- About **14 GB RAM** on the host (Ollama ~8 GB, Infinity ~4 GB, app ~1.5 GB). Prefer a smaller model such as `gemma4:e2b` if memory is tight.
- Network access from the app container to `FHIR_TX_URL`

## Steps

```bash
cp .env.example .env    # optional: FHIR_TX_URL, LLM_OLLAMA_MODEL
docker compose -f docker-compose.yml -f docker-compose.ollama.yml up --build
```

After the stack is healthy, pull the Ollama model once (match `LLM_OLLAMA_MODEL` in `.env`, default `gemma4:e2b`):

```bash
docker compose -f docker-compose.yml -f docker-compose.ollama.yml exec ollama ollama pull gemma4:e2b
```

Open **http://localhost:8080**.

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
