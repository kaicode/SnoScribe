# Model evaluation

Activate Spring profile `evaluate` to benchmark models against notes in `example_notes/` and write JSON under `model-comparison/`.

Model names must match the active provider (Ollama tags, Foundry catalog ids, etc.).

**Stage 1 — benchmark:**

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=evaluate --eval.models=qwen3.5:9b"
```

Output: `model-comparison/<model-name>/<note>.json` (`:` in model names becomes `_` in folder names).

**Stage 2 — ranking** runs automatically if `human-expert/` contains reference JSON per note. Results: `model-comparison/ranking.json`.

Run from the host; see [development.md](development.md) for profiles and properties.
