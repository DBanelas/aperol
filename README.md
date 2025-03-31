# APEROL: Adaptive Parallel Edge-to-cloud Runtime Optimization for Layered Workflow Execution
***

## Table of Contents

- [Abstract](#abstract)
- [Folder Structure](#folder-structure)
- [Workflows](#workflows)

### Abstract
We present APEROL, the first parallel optimization framework that
can provide layered, in-network workflow execution plans with
minimal plan suggestion delay. APEROL introduces a new concep
tualization for the optimization search space which is a key en
abler for scalable parallel candidate plan exploration. APEROL also
provides a novel signature scheme for plan enumeration to boost
efficient parallel search space exploration. The suite provides config
urable multi-threaded search over edge-to-cloud deployments and
includes exhaustive, heuristic, greedy, and random sampling algo
rithms, which are complementary in terms of speed vs. plan quality
trade-offs under different setups. APEROL can examine up to 1M
candidate plans per second on commodity hardware. Experiments
on a well-known IoT benchmark with four demanding workflows,
over networks ranging from 10s to 1000s sites, homogeneous and
heterogeneous device capacities, show the effectiveness and the
timeliness of APEROL decisions.


## Folder Structure
***
 - `optimizer/`: Contains the source code of the optimization algorithms along with the main class (StandaloneRunner).
 - `core/`: Contains some core functionality used by the optimizer (graphs, operators, etc).
 - `workflows/`: Contains the workflow files used in the experiments in two formats: ifogsim and optimizer.'
 - `networks/`: Contains the network configurations (7, 15, 31, 127, 1023, 2047) used in the experiments for 
both heterogeneous and homogeneous scenarios. Each configuration contains the network graph and the pair
latencies between every pair of devices.
 - `models/`: XGBoost models used in the homogeneous experiments. Each network, workflow combination has a model.
 - `distributions/`: Parameters of the distribution that characterize the cost of each plan for each network,
workflow combination.
 - `dag-star-data/`: Contains the cost per operator for each network, workflow combination used in the 
comparison of APEROL against DAG*.


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

## Experimental scenarios

Our experimental evaluation consists of three scenarios:
- **Heterogeneous**: The devices have different capacities and the cost of each operator is estimated
derived using distributions and histograms.
The networks for the heterogeneous scenario are located in the `networks/heterogeneous` folder.


- **Homogeneous**: The devices are Raspberry-pi class and the cost of each execution plan is estimated using xgboost models.
The networks for the heterogeneous scenario are located in the `networks/homogeneous` folder.


- **DAG\* comparison**: The cost of each operator is taken from the given .xlsx files.
The networks used for the comparison are the same as the ones used in the heterogeneous scenario.


## Usage
***
This section demonstrates how to run the APEROL algorithmic suite for each one of the experiments
(heterogeneous, homogeneous, and comparison to DAG*). In every scenario, the user can 
change the **algorithm**, the **timeout (ms)**, the **chain length** (num-iterations argument) as well as the **parallelism** of the search. When the 
RSS algorithm is chosen, its **sample size** is also configurable. **The scenarios differentiate in the
type of cost estimation used (distributions, models, or DAG\*)**.

The \$() argument take one of the following values: 
- **\$(algorithm)** : e-escp (Exhaustive with counting), e-bescp (Exhaustive with counting and batching),
e-esq (Exhaustive with a queue), e-gsp (Greedy with progressive global optima),
e-rss (Random sampling search), e-hsp (Pareto guided heuristic search), hybrid (Greedy + Random sampling search),
 

- **\$(network-name)** : 7, 15, 31, 127, 1023, 2047

- **\$(workflow-name)** : etl, stats, train, pred

- **\$(num-iterations)** : 1, 10, 20, 40, 80, 100


- **Cost Estimation method: Distributions (Heterogeneous scenario)**
```shell
java -Xmx8g -Xms256m -jar aperol.jar \
--cost-calculation-method dist  \
--network-name $(network-name) \
--workflow-name $(workflow-name) \
--workflow-path ./workflows/riot-$(workflow-name)_optimizer.json \
--network-path ./networks/heterogeneous/net_$(network-name)/network_$(network-name)_1_optimizer.json \
--algorithm $(algorithm) \
--num-iterations 5 \
--timeout 10000 \
--parallelism 4
```

- **Cost Estimation method: model (Homogeneous scenario)** 
```shell
java -Xmx8g -Xms256m -jar aperol.jar \
--cost-calculation-method model  \
--network-name $(network-name) \
--workflow-name $(workflow-name) \
--workflow-path ./workflows/riot-$(workflow-name)_optimizer.json \
--network-path ./networks/heterogeneous/net_$(network-name)/network_$(network-name)_1_optimizer.json \
--model-directory ./models/ \
--algorithm $(algorithm) \
--num-iterations 5 \
--timeout 10000 \
--parallelism 4
```


- **Cost Estimation method: dagstar (DAG\* Comparison scenario)**
```shell
java -Xmx8g -Xms256m -jar aperol.jar \
--cost-calculation-method dagstar  \
--network-name $(network-name) \
--workflow-name $(workflow-name) \
--ifogsim-workflow-path ./workflows/riot-$(workflow-name)-ifogsim.json \
--workflow-path ./workflows/riot-$(workflow-name)_optimizer.json \
--network-path ./networks/heterogeneous/net_$(network-name)/network_$(network-name)_1_optimizer.json \
--pairlats-path ./networks/heterogeneous/net_$(network-name)/network_$(network-name)_1_pair_lat.txt \
--dataset-path ./dag-star-data/$(workflow-name)_xlsx/$(workflow-name)_$(network_name)_avg.xlsx \
--algorithm $(algorithm) \
--timeout 10000 \
--parallelism 4
```

**Warning:** The pair network_$(network-name)_1_pair_lat.txt files may need to be unzipped
before running the experiments. Too large to be uploaded to the repository in their txt format. 

In all the above cases, these extra argument can be added:
- Should the RSS algorithm be used, the sample size can be set using the `--percentage` argument.
If the `--percentage` is < 1, then the sample size is calculated as the percentage of the total number of plans.
If the `--percentage` is > 1, then it represents the number of plans to be sampled
(e.g --percentage 3000 will sample 3k plans).


- Should the Batch Exhaustive algorithm be used, the batch size can be set using the `--batch-size` argument.


## Authors
- [Dimitrios Banelas](https://www.linkedin.com/in/dimitris-banelas-1129b0182/): dbanelas [.at] tuc.gr
- Alkis Simitsis: alkis [.at] athenarc.gr
- Nikos Giatrakos: ngiatrakos [.at] tuc.gr
