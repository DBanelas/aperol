# APEROL: Adaptive Parallel Edge-to-cloud Runtime Optimization for Layered Workflow Execution
***

## Table of Contents

- [Abstract](#abstract)
- [Folder Structure](#folder-structure)
- [Workflows](#workflows)

### Abstract
The execution of streaming analytics workflows across large-scale IoT infrastructures poses unique challenges.
Central data collection depletes the available bandwidth and leaves IoT device resources unutilized. Therefore,
workflow execution should be performed in-network, assigning workflow operator execution on devices across the
cloud-to-edge continuum. However, the vast scale of devices and interconnectivity of operators result in an
exponential number of possible operator assignments. On top of that, workflows are executed on dynamic environments
where volatile data stream distributions and device churn may render a deployed plan inefficient and delayed adaptation
decisions obsolete. To address these challenges, we present APEROL, the first suite of parallel optimization algorithms
for timely and efficient workflow execution in IoT environments. APEROL introduces a novel conceptualization of the optimization
search space, coupled with a signature-based execution plan enumeration scheme, that enable scalable, parallel exploration.
The suite includes exhaustive, heuristic, greedy, and random sampling algorithms, which are complementary in algorithm
speed vs. plan quality trade-offs under different setups. The current implementation examines up to 1M candidate plans per
second on commodity hardware and up to 2M/s on high end servers. Experiments with 5 demanding workflows from 2 streaming
benchmarks, over real and simulated networks ranging from 10s to 1000s sites show APEROL's effectiveness and timeliness.


## Folder Structure
***
 - `optimizer/`: Contains the source code of the optimization algorithms along with the main class (StandaloneRunner).
 - `core/`: Contains some core functionality used by the optimizer (graphs, operators, etc).
 - `workflows/`: Contains the workflow files used in the experiments in two formats: ifogsim and optimizer.'
 - `networks/`: Contains the network configurations (7, 15, 31, 127, 1023, 2047) used in the experiments for 
both heterogeneous and homogeneous scenarios. Each configuration contains the network graph and the pair
latencies between every pair of devices.
 - `costs/`: Contains the necessary files for all cost estimation methods tha APEROL utilizes. There are 
three subfolders: `models/`, `distributions/` and `latency/`. The models folder contains the xgboost 
models used for the homogeneous scenario, the distributions folder contains the distributions used for the heterogeneous scenario,
and the latency folder contains per operator latencies for the comparison experiments.

## Workflows
***

**All workflows are derived from a well known and higly cited benchmark from the smart city domain,
namely RioTBench: https://github.com/dream-lab/riot-bench**
- `Extraction, Transfrom & Load (ETL)`: Ingests incoming data streams in SenML format, performs data filtering of
outliers on individual observation types using a Range and Bloom filter, and subsequently
interpolates missing values. It then annotates additional meta-data into
the observed fields of the message and then inserts the resulting tuples
into Azure table storage, while also converting the data back to SenML and
publishing it to MQTT. A dummy sink task shown is used for logging purposes.


- `Statistical Summarization (STATS)`: parses the input messages that arrive in
SenML format – typically from the ETL, but kept separate here for modularity.
It then performs three types of statistical analytics in parallel on individual
observation fields present in the message: an average over a 10 message window, Kalman
filtering to smooth the observation fields followed by a sliding window linear regression,
and an approximate count of distinct values that arrive. These three output streams are then grouped for
each sensor IDs, plotted and the resulting image files zipped. These three tasks are tightly coupled
and we combine them into a single meta-task for manageability, as is common. and the output file is
written to Cloud storage for hosting on a portal.


- ``` Model Training (TRAIN)```: application uses a timer to periodically (e.g., for every minute)
trigger a model training run. Each run fetches data from the Azure table available since the last run and
uses ti to train a Linear Regression model. In addition, these fetched tuples are also annotated to allow a
Decision Tree classifier to be trained. Both these trained model files are then uploaded to Azure blob storage
and their files URLs are published to the MQTT broker.


- ``` Predictive Analytics (PRED)```: application subscribes to these notifications and
fetches the new model files from the blob store, and updates the downstream prediction
tasks. Meanwhile, the dataflow also consumes pre-processed messages streaming in, say
from the ETL dataflow, and after parsing it forks it to the decision tree
classifier and the multi-variate regression tasks. The classifier assigns
messages into classes, such as good, average or poor, based on one or more
of their field values, while linear regression predicts a numerical
attribute value in the message using several others. The regression task
also compares the predicted values against a moving average and estimates
the residual error between them. The predicted classes, values and errors 
are published to the MQTT broker.

- ``` Yahoo Benchmark (YAHOO)```: implements the Yahoo! Streaming Benchmark workload. A synthetic ad‑event generator
- publishes JSON messages (impression/click) with fields like event time, adId, campaignId, userId, and pageId to the broker.
- The streaming dataflow enriches events via an adId→campaignId lookup, then computes per‑campaign aggregates
- in sliding windows (e.g., 1s/10s/1m): impressions, clicks, unique users, and CTR (clicks/impressions).
- It also maintains top‑K campaigns by throughput and emits end‑to‑end latency metrics (now − event timestamp) for SLO
- tracking. Aggregates and latency snapshots are written to a fast key‑value store for a dashboard and are additionally
- published to the MQTT broker. The generator allows tuning event rate and key cardinality, while the dataflow handles
- out‑of‑order events with watermarks and late‑data side outputs.

## Experimental scenarios

Our experimental evaluation consists of three scenarios:
- **Heterogeneous**: The devices have different capacities and the cost of each operator is estimated
derived using distributions and histograms.
The networks for the heterogeneous scenario are located in the `networks/heterogeneous` folder.


- **Homogeneous**: The devices are Raspberry-pi class and the cost of each execution plan is estimated using xgboost models.
The networks for the heterogeneous scenario are located in the `networks/homogeneous` folder.

- **Comparison to DAG\*, Governor, Spring-Relax**: The comparisons are done both in with simulated and real costs
from the FiTIoT testbed. FiTIoT networks are located in the `networks/fitiot/` folder.


## Usage
***
## Prerequisites
- Bash shell
- Java 11 (JRE/JDK) available on your `PATH`

Make scripts executable once:
```bash
chmod +x run_*.sh
````

## Parameters
 - **NN**: Network Name (7, 15, 31, 127, 194, 1023, 2047)
 - **WFN**: workflow name (pred, etl, train, pred, yahoo-benchmark)
 - **ALG**: Algorithm (e-escp, e-esq, e-gsp, e-qp, e-hsp, dagstar, governor, spring-relax)
 - **CHAIN_SIZE**: Number of iterations (1, 10, 20, 40, 80, 100)
 - **TIMEOUT_MS**: Timeout in milliseconds

## 1) Comparisons on the real FiTIoT testbed
Runs the Yahoo! workload on FiTIoT networks with latency costs.
```bash
./run_comparisons_fitiot_yahoo.sh <NN> <ALG> <TIMEOUT_MS>
# example
./run_comparisons_fitiot_yahoo.sh 7 e-gsp 600000
```

## 2) Comparisons using the simulated costs
Uses latency files under `costs/latency/sim-data/<WFN>_xlsx/` on a heterogeneous network.
```bash
./run_comparisons_sim.sh <NN> <WFN> <ALG> <TIMEOUT_MS>
# example
./run_comparisons_sim.sh 7 pred e-gsp 600000
```

## 3) Heterogeneous (distribution-based costs)
Uses distribution models from `costs/distributions/` on heterogeneous networks.
```bash
./run_heterogeneous.sh <NN> <WFN> <ALG> <CHAIN_SIZE> <TIMEOUT_MS>
# example
./run_heterogeneous.sh 7 pred e-gsp 1 600000
```

## 4) Homogeneous (model-based costs)
Uses analytic models from `costs/models/` on homogeneous networks.
```bash
./run_homogeneous.sh <NN> <WFN> <ALG> <CHAIN_SIZE> <TIMEOUT_MS>
# example
./run_homogeneous.sh 7 pred e-gsp 1 600000
```
## 5) Varying cost dimension weights experiment
No arguments; Uses the Yahoo! workflow on the FiTIoT 127 network. Also uses the latency files under `costs/latency/fitiot-data/`.
```bash
./run_vary_weights.sh
```

## Notes
Common defaults (batch size, parallelism) are set inside each script.

## Authors
- [Dimitrios Banelas](https://www.linkedin.com/in/dimitris-banelas-1129b0182/): dbanelas [.at] tuc.gr
- Alkis Simitsis: alkis [.at] athenarc.gr
- Nikos Giatrakos: ngiatrakos [.at] tuc.gr
