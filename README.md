# SnoScribe

A demonstration tool that reads free-text clinical notes and automatically identifies clinical conditions, procedures, and medications, linking each finding to a standard SNOMED CT concept code with context.

A clinician must review the results and pick the information that is useful for the medical record.  

## Strengths

- **Clinical meaning, not just codes** — The model extracts structured findings with negation, subject (patient vs. family), laterality, timing (current / historical / suspected / planned), and medication details where stated, then maps them to SNOMED CT. The output is meant to reflect what the note actually asserts, not a flat list of codes.
- **No training step** — You deploy and run it: configure a general-purpose chat model and a FHIR terminology endpoint. There is no dataset labelling, fine-tuning, or retraining cycle to operate the pipeline.
- **SNOMED CT updates without changing the app** — The application does not bundle terminology. Point `fhir.tx.url` at a server that serves your edition; upgrading SNOMED is a matter of loading a new release (or repointing to an updated endpoint) on that infrastructure.
- **Offline-capable deployment** — With a local LLM (e.g. Ollama) and a local FHIR terminology server on your network, processing can stay disconnected from public APIs. Cloud LLMs or a remote demo terminology server require connectivity.

Privacy depends on how you configure the LLM and terminology server: **Ollama** keeps extraction on your machine; **OpenAI**, **Anthropic**, or **Google** send note text to that provider’s API. The default FHIR terminology endpoint below is a public demo server, so queries leave your machine unless you point `fhir.tx.url` at a local server.

---

## What it does

Given a clinical note such as a GP encounter summary, the tool extracts:

**Conditions** — including:

- Whether the condition is active (current), historical, or suspected
- Which person it applies to (the patient, or a family member)
- Laterality where relevant (left, right, bilateral)
- Negated findings (e.g. “denies chest pain”)

**Procedures** — surgeries, endoscopies, imaging, and similar (not diagnoses or drugs), with the same style of subject, laterality, negation, and context, including **planned** procedures where appropriate.

**Medications** — including dose, frequency, route, and dose form where stated in the note.

Each extracted item is then resolved against SNOMED CT via a FHIR terminology server (`ValueSet/$expand` with ECL filters for findings, procedures, and medicinal products). When there is no exact match, the pipeline can use optional **LLM-generated synonyms** and an optional **Infinity** rerank server to choose among expansion candidates.

The web UI (`index.html` plus a Vite-built bundle) shows the original note on the left with entities highlighted by type, and structured annotations on the right.

---

## How it works

```
Clinical note
     │
     ▼
Configured chat model (see Configuration)  ←  Ollama (local) or OpenAI / Anthropic / Google
     │  one call; extracts conditions, procedures & medications as structured JSON
     │
     ▼
Per-entity enrichment (each entity processed in parallel)
     │
     │  1. FHIR $expand on terminology server  —  SNOMED CT lookup; fuzzy filter if expansion empty
     │  2. Optional synonym LLM + $expand again  —  if still no hits
     │  3. Optional Infinity rerank              —  when there is no exact expansion match, score
     │                                            candidate terms and accept the best concept if ≥ threshold
     │
     ▼
Structured annotations (codes + displays)
     │
     ▼
Web UI                                      ←  highlights in note, cards per entity
```

For each request, the app runs **one** LLM call over the full note, then runs this **three-step** enrichment pipeline **in parallel** across entities (FHIR resolution with fuzzy/synonym fallbacks, then optional **reranking**).

---

## Requirements

| Component | Details |
|-----------|---------|
| Java | 17 or later |
| Maven | 3.8 or later |
| Node.js / npm | Required for the default build (Maven runs `npm run ci-build` in `frontend/` during `generate-resources`) |
| LLM | See **Configuration** — default path uses [Ollama](https://ollama.com) on port 11434 |
| Memory | Depends on model; the default Ollama model is 9B-class — allow enough RAM/VRAM (often ~8 GB+ system RAM for inference, more comfortable with headroom) |
| FHIR Terminology Server | Any FHIR TX supporting SNOMED CT expansion (example below uses SNOMED International’s demo Snowstorm Lite) |
| Infinity (optional) | Embedding/rerank HTTP service for disambiguating expansion hits — default base URL `http://localhost:7997` |

`application.properties` is **gitignored**; you need a local file with at least `llm.provider` and `fhir.tx.url` (see example below).

---

## Configuration

Create `application.properties` in the project root (or under `src/main/resources` if you prefer classpath config). Minimal example for **local Ollama** (default model tag **`qwen3.5:9b`**, matching the Spring default when `llm.ollama.model` is omitted):

```properties
llm.provider=ollama
llm.ollama.base-url=http://localhost:11434
llm.ollama.model=qwen3.5:9b
# llm.ollama.think=false is the default (omit or set true for thinking-capable models)
fhir.tx.url=https://implementation-demo.snomedtools.org/snowstorm-lite/fhir
```

Optional keys (defaults shown where applicable):

```properties
server.port=8080

# Cloud providers — set llm.provider to openai | anthropic | google and supply the API key
llm.openai.api-key=
llm.openai.model=gpt-4o
llm.anthropic.api-key=
llm.anthropic.model=claude-opus-4-5
llm.google.api-key=
llm.google.model=gemini-1.5-pro

# Ollama /api/chat "think" (reasoning); false = faster for Qwen 3.x etc. (default false)
llm.ollama.think=false

# Optional Infinity reranker (disable if not running)
infinity.rerank.enabled=true
infinity.rerank.base-url=http://localhost:7997
infinity.rerank.model=reranker
infinity.rerank.min-score=0.5

# Extra LLM calls for terminology synonyms when expansion is empty
terminology.synonym-llm.enabled=true
```

---

## Building and running

**Ollama (default `llm.provider=ollama`, model `qwen3.5:9b`)**

1. Install and start **Ollama** from [ollama.com](https://ollama.com) (macOS/Windows: open the app so the daemon runs; Linux: install the package and ensure `ollama serve` is running if needed).
2. Pull the model the app expects (first download can take a while):

   ```bash
   ollama pull qwen3.5:9b
   ```

3. Optional: confirm the model runs: `ollama run qwen3.5:9b` (then exit the chat with `/bye` or Ctrl+D).

SnoScribe talks to Ollama at `llm.ollama.base-url` (default `http://localhost:11434`).

---

From the repository root:

```bash
mvn spring-boot:run
```

- The first build downloads npm dependencies and runs **Vite**, emitting `src/main/resources/static/js/app.js`.
- To skip the frontend step (you must already have a built `app.js`), run:  
  `mvn spring-boot:run -Dnpm.skip=true`

Open the app at **http://localhost:8080**, or whatever you set in `server.port`.

**Optional Infinity reranker** — If `infinity.rerank.enabled` is true (see Configuration), start a local rerank HTTP service or set `infinity.rerank.enabled=false`. To set one up from this repo:

```bash
bash scripts/setup-infinity-reranker.sh
```

That creates `scripts/.venv-infinity`, installs [infinity-emb](https://github.com/michaelfeil/infinity), and prints the command to run the server on port **7997** (matching `infinity.rerank.base-url`). After `Application startup complete`, check `curl -s http://localhost:7997/health`.

**Frontend-only development** (rebuild on save into `static/js/`):

```bash
cd frontend && npm ci && npm run dev
```

---

## Model evaluation

The `evaluate` profile benchmarks one or more **model names** against all `.txt` files in `example_notes/` and writes JSON under `model-comparison/`. Names must match the configured `llm.provider` (e.g. Ollama tag names when `llm.provider=ollama`).

**Stage 1 — benchmark:**

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=evaluate --eval.models=qwen3.5:9b,qwen2.5:3b-instruct"
```

Output: `model-comparison/<model-name>/<note>.json` (with `:` in model names normalised to `_` in folder names).

**Stage 2 — ranking** runs after Stage 1 if a `human-expert/` directory exists with reference annotations (one JSON file per note, same shape as the model output). Metrics are written to `model-comparison/ranking.json`.

---

## Limitations

This project is a proof of concept. The language model may miss findings or misclassify context (e.g. family history vs. patient history). Hallucinations are unlikely but possible. Outputs must be reviewed.
