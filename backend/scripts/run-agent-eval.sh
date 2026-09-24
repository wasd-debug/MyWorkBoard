#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

if [ "${1:-}" = "--live" ]; then
  test -n "${DEEPSEEK_API_KEY:-}" || { echo "缺少 DEEPSEEK_API_KEY，无法运行真实模型评测。"; exit 2; }
  exec env AGENT_LIVE_EVAL=true mvn -f "$BACKEND_DIR/pom.xml" -pl modules/ai -am \
    -Dtest=AgentLiveModelEvaluationTest -Dsurefire.failIfNoSpecifiedTests=false test
fi

exec mvn -f "$BACKEND_DIR/pom.xml" -pl modules/ai -am \
  -Dtest=AgentEvaluationContractTest -Dsurefire.failIfNoSpecifiedTests=false test
