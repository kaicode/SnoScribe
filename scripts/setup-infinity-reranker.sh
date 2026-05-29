#!/usr/bin/env bash
# Creates a Python venv and installs infinity-emb for the SNOMED reranker fallback.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
VENV_DIR="${REPO_ROOT}/scripts/.venv-infinity"
PYTHON="${VENV_DIR}/bin/python"

venv_valid() {
	[[ -x "${PYTHON}" ]] || return 1
	# Moving/copying a venv leaves stale paths in activate/shebangs; pip then hits system Python (PEP 668).
	"${PYTHON}" -c "import sys; raise SystemExit(0 if sys.prefix == '${VENV_DIR}' else 1)" 2>/dev/null || return 1
	grep -qF "${VENV_DIR}" "${VENV_DIR}/bin/activate" 2>/dev/null
}

if ! venv_valid; then
	if [[ -d "${VENV_DIR}" ]]; then
		echo "Removing stale venv at ${VENV_DIR} (paths no longer match this repo)"
		rm -rf "${VENV_DIR}"
	fi
	echo "Creating venv at ${VENV_DIR}"
	python3 -m venv "${VENV_DIR}"
fi

"${PYTHON}" -m pip install --upgrade pip
# [torch,server]: PyTorch + uvicorn/fastapi (without torch you get: torch.nn is not available)
# requests: imported by infinity-emb but not always declared for minimal venvs
# huggingface_hub<1: infinity-emb still imports HfFolder (removed in huggingface_hub 1.x)
# click<8.2: Typer 0.12 + Click 8.2+ raises "Secondary flag is not valid for non-boolean flag" (infinity #650)
# Do not install optimum>=2: breaks infinity_emb's optimum.bettertransformer import; use --no-bettertransformer instead.
"${PYTHON}" -m pip install "infinity-emb[torch,server]" requests "huggingface_hub>=0.24.0,<1.0" "click>=8.0,<8.2"

echo ""
echo "Setup complete. To run the rerank server:"
echo "  ${VENV_DIR}/bin/infinity_emb v2 --model-id BAAI/bge-reranker-v2-m3 --served-model-name reranker --port 7997 --no-bettertransformer"
echo ""
echo "Notes:"
echo "  - First start: Hugging Face model download plus warmup can take several minutes before /health responds."
echo "  - --no-bettertransformer avoids a NameError when optimum is not installed (infinity_emb 0.0.76 + torch-only venv)."
echo "  - CPU works; GPU/MPS is faster."
echo "Health check (after logs show 'Application startup complete'): curl -s http://localhost:7997/health"
