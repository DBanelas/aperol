#!/usr/bin/env bash
# Usage: ./run_aperol.sh <network_size> <workflow> <algorithm> <timeout_ms>
NN="$1"; WFN="$2"; ALG="$3"; TIMEOUT_MS="$4"
[ -z "$NN" ] || [ -z "$WFN" ] || [ -z "$ALG" ] || [ -z "$TIMEOUT_MS" ] && { echo "Usage: $0 <nn> <workflow> <alg> <timeout_ms>"; exit 1; }

BASE="$(cd "$(dirname "$0")" && pwd)"

IWP="$BASE/workflows/riot-${WFN}-ifogsim.json"
WP="$BASE/workflows/riot-${WFN}_optimizer.json"

java -jar "$BASE/aperol.jar" \
  -ccm latency \
  -cp "$BASE/costs/latency/sim-data/${WFN}_xlsx/${WFN}_${NN}_avg.xlsx" \
  -nn "$NN" \
  -wfn "$WFN" \
  -iwp "$IWP" \
  -wp "$WP" \
  -np "$BASE/networks/heterogeneous/net_${NN}/network_${NN}_1_optimizer.json" \
  -lp "$BASE/networks/heterogeneous/net_${NN}/network_${NN}_1_pair_lat.txt" \
  -pl "$BASE/networks/heterogeneous/net_${NN}/network_${NN}_1_links.txt" \
  -a "$ALG" \
  --isSimDataset \
  -bs 1000 \
  -ni 1 \
  -nh 6 \
  -pc 3000 \
  -t "$TIMEOUT_MS" \
  -p 1
