#!/usr/bin/env bash
# Start/stop/logs only the Infinity rerank service (base docker-compose.yml, port 7997).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"
COMPOSE=(docker compose -f docker-compose.yml)

usage() {
	echo "Usage: $(basename "$0") [start|stop|logs]" >&2
	echo "  start  — docker compose up -d infinity (default)" >&2
	echo "  stop   — docker compose stop infinity" >&2
	echo "  logs   — docker compose logs -f infinity" >&2
}

usage_err() {
	usage
	exit 1
}

cmd="${1:-start}"
case "${cmd}" in
	start | up)
		"${COMPOSE[@]}" up -d infinity
		echo "Infinity: http://localhost:7997  (health: curl -s http://localhost:7997/health)"
		;;
	stop | down)
		"${COMPOSE[@]}" stop infinity
		;;
	logs)
		"${COMPOSE[@]}" logs -f infinity
		;;
	-h | --help | help)
		usage
		exit 0
		;;
	*)
		usage_err
		;;
esac
