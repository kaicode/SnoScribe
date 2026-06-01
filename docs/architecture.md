# Architecture

```
Clinical note
     │
     ▼
Chat model (LLM)  —  one call; JSON with conditions, procedures, medications
     │
     ▼
Per-entity enrichment (parallel)
     │  1. FHIR $expand (SNOMED CT)
     │  2. Optional synonym LLM + $expand again
     │  3. Infinity rerank when there is no exact match
     ▼
Structured annotations  →  Web UI (highlights + cards)
```

The app does not bundle terminology. Infinity disambiguates expansion candidates when fuzzy matching is not enough.

Privacy: note text goes to whichever LLM you configure; FHIR queries go to `fhir.tx.url` (often a remote server unless you host your own).
