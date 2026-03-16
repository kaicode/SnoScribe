# SnoScribe

A proof-of-concept tool that reads free-text clinical notes and automatically identifies clinical conditions and medications, 
linking each finding to a standard SNOMED CT concept code and including crucial context. 

Everything runs locally - no patient data leaves the machine.

---

## What it does

Given a clinical note such as a GP encounter summary, the tool extracts:

**Conditions** — including:
- Whether the condition is active (current), historical, or suspected
- Which person it applies to (the patient, or a family member)
- Laterality where relevant (left, right, bilateral)
- Negated findings (e.g. "denies chest pain")

**Medications** — including dose, frequency, route, and dose form where stated in the note.

Each extracted item is then looked up against SNOMED CT via a FHIR Terminology Server, returning the best-matching concept code and preferred display term. This makes the output computable — suitable for downstream analytics, decision support, or integration with clinical systems.

The annotation is displayed in a simple web interface showing the original note on the left (with entities highlighted by type) and the structured annotations on the right.

---

## How it works

```
Clinical note
     │
     ▼
Local LLM (Ollama)          ← runs on your machine, no data sent externally
     │  extracts conditions & medications as structured JSON
     ▼
FHIR Terminology Server     ← SNOMED CT concept lookup
     │  matches each finding to a concept code + display term
     ▼
Structured annotations
     │
     ▼
Web UI                      ← highlights in note, cards per entity
```

The language model and terminology lookup run in parallel for each note, keeping response times low.

---

## Requirements

| Component | Details |
|-----------|---------|
| Java | 17 or later |
| Maven | 3.8 or later |
| [Ollama](https://ollama.com) | Running locally on port 11434 |
| LLM model | `gemma3:12b` (default) or any instruction-tuned model available in Ollama |
| Memory | 16 GB RAM recommended (accommodates a 12B parameter model) |
| FHIR Terminology Server | Snowstorm Lite (default: SNOMED International demo instance) |

Pull the default model before first run:
```bash
ollama pull gemma3:12b
```

---

## Running the application

```bash
mvn spring-boot:run
```

The web interface is available at **http://localhost:8084**

To change the model or terminology server, edit `src/main/resources/application.properties`:

```properties
ollama.model=gemma3:12b
fhir.tx.url=https://implementation-demo.snomedtools.org/snowstorm-lite/fhir
```

---

## Model evaluation

The `evaluate` profile benchmarks one or more Ollama models against all notes in the `example_notes/` folder and records structured JSON output with timing breakdowns (total, LLM, and FHIR lookup separately).

**Stage 1 — benchmark:**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=\
"--spring.profiles.active=evaluate \
 --eval.models=gemma3:12b,qwen2.5:3b-instruct"
```

Output is written to `model-comparison/<model-name>/<note>.json`.

**Stage 2 — ranking** runs automatically after Stage 1 if a `human-expert/` folder exists containing reference annotations (one JSON file per note, using the same format as the model output). Precision, recall, F1 score, and field-level accuracy (subject, context, laterality) are reported per model and written to `model-comparison/ranking.json`.

---

## Limitations

This project is a proof of concept at this stage. The language model may miss findings, or misclassify context (e.g. family history vs. patient history). Hallucinations are unlikely but possible. 
Outputs must be reviewed.
