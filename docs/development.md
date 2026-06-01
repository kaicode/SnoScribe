# Development

For day-to-day work on the codebase: run the Spring Boot app on the host with Maven. Use Docker only for Infinity (or run the full stacks in [run/](run/) when you need an integrated demo).

## Requirements

- Java 17+, Maven 3.8+, Node.js/npm (first `mvn` build runs `npm run ci-build` in `frontend/`)
- Gitignored `application.properties` in the project root with at least `llm.provider` and `fhir.tx.url` — see [configuration.md](configuration.md)

## App on the host

```bash
mvn spring-boot:run
```

Skip the frontend build if `src/main/resources/static/js/app.js` already exists:

```bash
mvn spring-boot:run -Dnpm.skip=true
```

Default UI: **http://localhost:8080** (or `server.port` in your properties).

### Local Ollama (no Docker for LLM)

1. Install [Ollama](https://ollama.com) and pull a model, e.g. `ollama pull qwen3.5:9b`.
2. In `application.properties`:

   ```properties
   llm.provider=ollama
   llm.ollama.base-url=http://localhost:11434
   llm.ollama.model=qwen3.5:9b
   fhir.tx.url=https://implementation-demo.snomedtools.org/snowstorm-lite/fhir
   ```

3. `mvn spring-boot:run`

Details: [providers/ollama.md](providers/ollama.md).

### Cloud LLM on the host

Use a profile or properties file, e.g. Foundry:

```bash
export AZURE_ML_API_KEY='...'
# edit application-azure-qwen.properties (endpoint + model id)
mvn spring-boot:run -Dspring-boot.run.profiles=azure-qwen
```

Provider docs: [providers/](providers/).

## Infinity only (Docker)

Start reranking on port **7997** while the app runs via Maven:

```bash
./scripts/docker-infinity.sh start
curl -s http://localhost:7997/health
```

Ensure `infinity.rerank.base-url=http://localhost:7997` in your properties (default in `src/main/resources/application.properties`).

```bash
./scripts/docker-infinity.sh logs
./scripts/docker-infinity.sh stop
```

## Frontend watch mode

Rebuild JS on save into `static/js/`:

```bash
cd frontend && npm ci && npm run dev
```

Run the backend separately with `mvn spring-boot:run -Dnpm.skip=true`.

## Model evaluation

See [evaluation.md](evaluation.md).

## Production-like runs

Use the two deployment paths in [run/](run/) — not required for coding.
