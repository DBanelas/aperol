#!/usr/bin/env bash
# Runs with no user input.
BASE="$(cd "$(dirname "$0")" && pwd)"

exec java -jar "$BASE/aperol-vary-weights.jar" \
  --workflow-directory "$BASE/workflows" \
  --network-directory "$BASE/networks/fitiot" \
  --dataset-path "$BASE/costs/latency/fitiot-data/fitiot-yahoo-cost-file.json"
