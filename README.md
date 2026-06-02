# SnoScribe

A demonstration tool that reads free-text clinical notes and identifies conditions, procedures, and medications, linking each finding to SNOMED CT with context (negation, subject, laterality, timing, and medication details where stated).

**A clinician must review all output** before use in a medical record. This project is in development; run your own evaluation for any clinical use.

## Strengths

- **Clinical meaning, not just codes** — The model extracts structured findings with negation, subject (patient vs. family), laterality, timing (current / historical / suspected / planned), and medication details where stated, then maps them to SNOMED CT. The output is meant to reflect what the note actually asserts, not a flat list of codes.
- **No training step** — You deploy and run it: configure a general-purpose chat model and a FHIR terminology endpoint. There is no dataset labelling, fine-tuning, or retraining cycle to operate the pipeline.
- **SNOMED updates without app changes** — Point `fhir.tx.url` at your terminology server.
- **Flexible deployment** — Local LLM (Ollama), cloud LLM (Foundry, OpenAI, etc.), or full Docker stacks.

Privacy depends on configuration: local Ollama keeps note text on your machine; cloud LLMs send notes to that provider. The default FHIR demo server receives terminology queries unless you use a local TX server.

## Limitations

This project is open source and in development. You must perform your own clinical testing and evaluation for your use case.  
A language model may miss findings or misclassify context (e.g. family history vs. patient history). Hallucinations are unlikely but possible. Outputs must be reviewed.

## How to run

| Goal | Guide |
|------|--------|
| **Everything in Docker** (Ollama + Infinity + app) | [docs/run/docker-ollama.md](docs/run/docker-ollama.md) |
| **Docker + cloud LLM** (Infinity + app; Foundry / OpenAI / …) | [docs/run/docker-cloud-llm.md](docs/run/docker-cloud-llm.md) |

The web UI is at **http://localhost:8080** by default (`server.port`; see [configuration](docs/configuration.md)).

## Development

Working on the code (Maven on the host, Infinity in Docker only, frontend watch mode, evaluation): [docs/development.md](docs/development.md).

## More documentation

| Topic | Guide |
|-------|--------|
| Pipeline overview | [docs/architecture.md](docs/architecture.md) |
| All properties & env vars | [docs/configuration.md](docs/configuration.md) |
| LLM providers | [docs/providers/](docs/providers/) |
| Model benchmarking | [docs/evaluation.md](docs/evaluation.md) |

## Requirements

| Component | Details |
|-----------|---------|
| Java / Maven / Node | 17+, 3.8+, npm — for building and [development](docs/development.md) |
| Docker Compose v2 | For the two [run](docs/run/) paths |
| FHIR TX | SNOMED CT-capable server (`fhir.tx.url`) |
| LLM | Ollama locally or a cloud provider — see [providers](docs/providers/) |

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) — see [LICENSE](LICENSE).
