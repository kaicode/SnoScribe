# Ollama

```properties
llm.provider=ollama
llm.ollama.base-url=http://localhost:11434
llm.ollama.model=qwen3.5:9b
llm.ollama.think=false
```

`think=true` enables reasoning mode for supported models (slower).

**Docker (all local):** [../run/docker-ollama.md](../run/docker-ollama.md) — uses `LLM_OLLAMA_*` env vars.

**Development:** [../development.md](../development.md).
