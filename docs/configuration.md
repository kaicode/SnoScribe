# Configuration

Create `application.properties` in the **project root** (gitignored) or use `src/main/resources/application.properties` for defaults. Spring also accepts environment variables (`LLM_PROVIDER`, `LLM_AZURE_BASE_URL`, etc.) — see [Docker](#docker) below.

## Required

| Property | Purpose |
|----------|---------|
| `llm.provider` | `ollama`, `azure`, `openai`, `anthropic`, or `google` |
| `fhir.tx.url` | FHIR terminology base URL for SNOMED CT `$expand` |

Plus provider-specific keys — see [providers/](providers/).

## LLM providers

| Provider | Keys |
|----------|------|
| Ollama | `llm.ollama.base-url`, `llm.ollama.model`, optional `llm.ollama.think` |
| Azure Foundry | `llm.azure.base-url`, `llm.azure.api-key`, `llm.azure.model`, optional `llm.azure.deployment-name`, `llm.azure.max-output-tokens` |
| OpenAI | `llm.openai.api-key`, `llm.openai.model` |
| Anthropic | `llm.anthropic.api-key`, `llm.anthropic.model`, optional `llm.anthropic.max-output-tokens` |
| Google | `llm.google.api-key`, `llm.google.model` |

## Infinity reranker

| Property | Default |
|----------|---------|
| `infinity.rerank.enabled` | `true` |
| `infinity.rerank.base-url` | `http://localhost:7997` |
| `infinity.rerank.model` | `reranker` |
| `infinity.rerank.min-score` | `0.85` |
| `infinity.rerank.max-concurrent-requests` | `1` |

## Other

| Property | Default |
|----------|---------|
| `server.port` | `8080` |
| `terminology.synonym-llm.enabled` | `true` — extra LLM call when `$expand` is empty |

## Docker

For Docker Compose stacks, configure via a `.env` file in the project root (copy from `.env.example`). Compose passes variables into the `snoscribe` container; Spring maps them to the properties above via [relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding).

A host `application.properties` file is **not** read by the container — the image ships a built-in `src/main/resources/application.properties`. Use `.env` (or extra `environment:` entries in compose) instead.

| Stack | Guide |
|-------|--------|
| Ollama + Infinity + app | [run/docker-ollama.md](run/docker-ollama.md) |
| Cloud LLM + Infinity + app | [run/docker-cloud-llm.md](run/docker-cloud-llm.md) |

### Property → environment variable

| Property | Environment variable |
|----------|----------------------|
| `fhir.tx.url` | `FHIR_TX_URL` |
| `llm.provider` | `LLM_PROVIDER` |
| `llm.ollama.base-url` | `LLM_OLLAMA_BASE_URL` |
| `llm.ollama.model` | `LLM_OLLAMA_MODEL` |
| `llm.ollama.think` | `LLM_OLLAMA_THINK` |
| `llm.azure.base-url` | `LLM_AZURE_BASE_URL` |
| `llm.azure.api-key` | `LLM_AZURE_API_KEY` |
| `llm.azure.model` | `LLM_AZURE_MODEL` |
| `llm.azure.deployment-name` | `LLM_AZURE_DEPLOYMENT_NAME` |
| `llm.azure.max-output-tokens` | `LLM_AZURE_MAX_OUTPUT_TOKENS` |
| `llm.openai.api-key` | `LLM_OPENAI_API_KEY` |
| `llm.openai.model` | `LLM_OPENAI_MODEL` |
| `llm.anthropic.api-key` | `LLM_ANTHROPIC_API_KEY` |
| `llm.anthropic.model` | `LLM_ANTHROPIC_MODEL` |
| `llm.anthropic.max-output-tokens` | `LLM_ANTHROPIC_MAX_OUTPUT_TOKENS` |
| `llm.google.api-key` | `LLM_GOOGLE_API_KEY` |
| `llm.google.model` | `LLM_GOOGLE_MODEL` |
| `infinity.rerank.base-url` | `INFINITY_RERANK_BASE_URL` |
| `infinity.rerank.enabled` | `INFINITY_RERANK_ENABLED` |
| `infinity.rerank.model` | `INFINITY_RERANK_MODEL` |
| `infinity.rerank.min-score` | `INFINITY_RERANK_MIN_SCORE` |
| `infinity.rerank.max-concurrent-requests` | `INFINITY_RERANK_MAX_CONCURRENT_REQUESTS` |
| `terminology.synonym-llm.enabled` | `TERMINOLOGY_SYNONYM_LLM_ENABLED` |
| `server.port` | `SERVER_PORT` |

Other properties follow the same pattern: uppercase, underscores instead of dots (e.g. `eval.models` → `EVAL_MODELS`).

### Set in `.env` vs wired by compose

**You set in `.env`:**

- `FHIR_TX_URL` — set in `.env` for both stacks (see [run/docker-cloud-llm.md](run/docker-cloud-llm.md) and [run/docker-ollama.md](run/docker-ollama.md))
- Cloud LLM: `LLM_PROVIDER` and matching `LLM_*` keys — see `.env.example`
- Ollama stack: optional `LLM_OLLAMA_MODEL` (default `gemma4:e2b`)

**Compose sets automatically** (Docker service hostnames, not `localhost`):

| Property | Value in container |
|----------|-------------------|
| `infinity.rerank.base-url` | `http://infinity:7997` |
| `llm.provider` | `ollama` (Ollama stack only) |
| `llm.ollama.base-url` | `http://ollama:11434` (Ollama stack only) |
| `llm.ollama.think` | `false` (Ollama stack only) |

Properties not listed in compose or `.env` use the built-in defaults from `src/main/resources/application.properties` (for example `terminology.synonym-llm.enabled`, `infinity.rerank.min-score`). To override them in Docker, add the corresponding environment variable to `docker-compose.yml` or pass it through `.env` and an `environment:` entry.

### Translating property files

Repo templates such as `application-azure-qwen.properties` use `key=value` syntax. For Docker, map each line to the env var in the table above — e.g. `llm.azure.base-url=...` becomes `LLM_AZURE_BASE_URL=...` in `.env`.

## Example files in the repo

- `application-azure-qwen.properties`, `application-azure-gemma.properties` — Foundry templates (no secrets)
- `application-gemma4.properties` — Ollama profile example
