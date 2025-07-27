#!/usr/bin/env bash
# Usage: ./run_aperol.sh <network_size> <algorithm> <timeout_ms>
NN="$1"; ALG="$2"; TIMEOUT_MS="$3"
[ -z "$NN" ] || [ -z "$ALG" ] || [ -z "$TIMEOUT_MS" ] && { echo "Usage: $0 <nn> <alg> <timeout_ms>"; exit 1; }

BASE="$(cd "$(dirname "$0")" && pwd)"

java -jar "$BASE/aperol.jar" \
  -ccm latency \
  -cp "$BASE/costs/latency/fitiot-data/fitiot-yahoo-cost-file.json" \
  -nn "$NN" \
  -wfn yahoo-benchmark \
  -iwp "$BASE/workflows/yahoo-benchmark-ifogsim.json" \
  -wp "$BASE/workflows/yahoo-benchmark_optimizer.json" \
  -np "$BASE/networks/fitiot/net_${NN}/network_${NN}_1_fitiot.json" \
  -lp "$BASE/networks/fitiot/net_${NN}/network_${NN}_1_pair_lat.txt" \
  -pl "$BASE/networks/fitiot/net_${NN}/network_${NN}_1_links.txt" \
  -a "$ALG" \
  -bs 1000 \
  -ni 1 \
  -nh 6 \
  -pc 3000 \
  -t "$TIMEOUT_MS" \
  -p 1
