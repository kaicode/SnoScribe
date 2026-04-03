#!/usr/bin/env bash
# Creates a Python venv and installs infinity-emb for the SNOMED reranker fallback.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
VENV_DIR="${REPO_ROOT}/scripts/.venv-infinity"

if [[ ! -d "${VENV_DIR}" ]]; then
	echo "Creating venv at ${VENV_DIR}"
	python3 -m venv "${VENV_DIR}"
fi

# shellcheck source=/dev/null
source "${VENV_DIR}/bin/activate"
python -m pip install --upgrade pip
# [torch,server]: PyTorch + uvicorn/fastapi (without torch you get: torch.nn is not available)
# requests: imported by infinity-emb but not always declared for minimal venvs
# huggingface_hub<1: infinity-emb still imports HfFolder (removed in huggingface_hub 1.x)
# click<8.2: Typer 0.12 + Click 8.2+ raises "Secondary flag is not valid for non-boolean flag" (infinity #650)
# Do not install optimum>=2: breaks infinity_emb's optimum.bettertransformer import; use --no-bettertransformer instead.
pip install "infinity-emb[torch,server]" requests "huggingface_hub>=0.24.0,<1.0" "click>=8.0,<8.2"

echo ""
echo "Setup complete. To run the rerank server:"
echo "  source ${VENV_DIR}/bin/activate"
echo "  infinity_emb v2 --model-id BAAI/bge-reranker-v2-m3 --served-model-name reranker --port 7997 --no-bettertransformer"
echo ""
echo "Notes:"
echo "  - First start: Hugging Face model download plus warmup can take several minutes before /health responds."
echo "  - --no-bettertransformer avoids a NameError when optimum is not installed (infinity_emb 0.0.76 + torch-only venv)."
echo "  - CPU works; GPU/MPS is faster."
echo "Health check (after logs show 'Application startup complete'): curl -s http://localhost:7997/health"
