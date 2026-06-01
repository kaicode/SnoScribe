# Configuration

Create `application.properties` in the **project root** (gitignored) or use `src/main/resources/application.properties` for defaults. Spring also accepts environment variables (`LLM_PROVIDER`, `LLM_AZURE_BASE_URL`, etc.).

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

## Example files in the repo

- `application-azure-qwen.properties`, `application-azure-gemma.properties` — Foundry templates (no secrets)
- `application-gemma4.properties` — Ollama profile example
