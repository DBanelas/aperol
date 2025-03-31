package optimizer;

import core.parser.dictionary.Dictionary;
import core.parser.dictionary.OldDictionary;
import core.parser.network.Network;
import core.parser.workflow.OptimizationRequest;
import core.structs.BoundedPriorityQueue;
import optimizer.cost.CostEstimator;
import optimizer.plan.OptimizationPlan;
import optimizer.plan.SimpleOptimizationPlan;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class OptimizationResourcesBundle {
    //Resources
    private OldDictionary dictionary;
    private Dictionary newDictionary;
    private Network network;
    private OptimizationRequest workflow;
    private HashMap<String, Integer> pairHops;
    private HashMap<String, Double> pairLats;
    private String statisticsDir;
    private String datasetFile;
    private String aggregationStrategy;
    private String jarPath;
    private String intermediateDir;
    private String iFogNetworkPath;
    private String iFogWorkflowPath;
    private String modelDirectory;
    private String modelWorkflow;
    private String modelNetwork;


    //Optional stats, mostly for debugging
    private OptimizationRequestStatisticsBundle statisticsBundle;

    //Plans produces by the optimizer
    private AtomicInteger numOfPlans;

    //Multi-thread options
    private int threads;

    //misc
    private int timeout;
    private int totalPlans;
    private String user;

    //Algorithm used to traverse and manipulate the graph
    private String traversalAlgorithmName;
    private BoundedPriorityQueue<OptimizationPlan> planQueue;
    private ExecutorService executorService;
    private CostEstimator costEstimator;
    private Logger logger;
    private SimpleOptimizationPlan rootPlan;
    private String requestId;

    //Private constructor, for internal instantiation only
    private OptimizationResourcesBundle() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public HashMap<String, Integer> getPairHops() {
        return pairHops;
    }

    public void setPairHops(HashMap<String, Integer> pairHops) {
        this.pairHops = pairHops;
    }

    public void setPairLats(HashMap<String, Double> pairLats) { this.pairLats = pairLats; }

    public HashMap<String, Double> getPairLats() { return this.pairLats; }

    public void setStatisticsDir(String statisticsDir) {
        this.statisticsDir = statisticsDir;
    }

    public String getStatisticsDir() {
        return this.statisticsDir;
    }

    public void setDatasetFile(String datasetFile) {
        this.datasetFile = datasetFile;
    }

    public String getDatasetFile() {
        return this.datasetFile;
    }
    public void setAggregationStrategy(String aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public String getAggregationStrategy() {
        return this.aggregationStrategy;
    }

    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }

    public String getJarPath() {
        return this.jarPath;
    }

    public void setIntermediateDir(String intermediateDir) {
        this.intermediateDir = intermediateDir;
    }

    public String getIntermediateDir() {
        return this.intermediateDir;
    }

    public void setIFogNetworkPath(String iFogNetworkPath) {
        this.iFogNetworkPath = iFogNetworkPath;
    }

    public String getIFogNetworkPath() {
        return this.iFogNetworkPath;
    }

    public void setiFogWorkflowPath(String iFogWorkflowPath) {
        this.iFogWorkflowPath = iFogWorkflowPath;
    }

    public String getIFogWorkflowPath() {
        return this.iFogWorkflowPath;
    }

    public void setModelDirectory(String modelDirectory) {
        this.modelDirectory = modelDirectory;
    }

    public String getModelDirectory() {
        return this.modelDirectory;
    }

    public void setModelWorkflow(String modelWorkflow) {
        this.modelWorkflow = modelWorkflow;
    }

    public String getModelWorkflow() {
        return this.modelWorkflow;
    }

    public void setModelNetwork(String modelNetwork) {
        this.modelNetwork = modelNetwork;
    }

    public String getModelNetwork() {
        return this.modelNetwork;
    }

    //Stats
    public int incrCreatedPlans() {
        return 0;
    }

    public int incrExploredPlans() {
        return 0;
    }

    public int incrExploredDimensions() {
        return 0;
    }

    public int addStat(String k, int v) {
        return this.statisticsBundle.addStat(k, v);
    }

    //Getters and Setters
    public OldDictionary getDictionary() {
        return dictionary;
    }

    public void setDictionary(OldDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public Dictionary getNewDictionary() {
        return newDictionary;
    }

    public void setNewDictionary(Dictionary dictionary) {
        this.newDictionary = dictionary;
    }

    public int getTotalPlans() {
        return totalPlans;
    }

    public void setTotalPlans(int totalPlans) {
        this.totalPlans = totalPlans;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public Network getNetwork() {
        return network;
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    public OptimizationRequest getWorkflow() {
        return workflow;
    }

    public void setWorkflow(OptimizationRequest workflow) {
        this.workflow = workflow;
    }

    public String getTraversalAlgorithmName() {
        return traversalAlgorithmName;
    }

    public void setTraversalAlgorithmName(String traversalAlgorithmName) {
        this.traversalAlgorithmName = traversalAlgorithmName.toLowerCase().trim();
    }

    public OptimizationRequestStatisticsBundle getStatisticsBundle() {
        return statisticsBundle;
    }

    public void setStatisticsBundle(OptimizationRequestStatisticsBundle statisticsBundle) {
        this.statisticsBundle = statisticsBundle;
    }

    public AtomicInteger getNumOfPlans() {
        return numOfPlans;
    }

    public void setNumOfPlans(AtomicInteger numOfPlans) {
        this.numOfPlans = numOfPlans;
    }

    public int getThreads() {
        return threads;
    }

    private void setThreads(int threads) {
        this.threads = threads;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public BoundedPriorityQueue<OptimizationPlan> getPlanQueue() {
        return planQueue;
    }

    public void setPlanQueue(BoundedPriorityQueue<OptimizationPlan> planQueue) {
        this.planQueue = planQueue;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public CostEstimator getCostEstimator() {
        return costEstimator;
    }

    public void setCostEstimator(CostEstimator costEstimator) {
        this.costEstimator = costEstimator;
    }

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public SimpleOptimizationPlan getRootPlan() {
        return rootPlan;
    }

    public void setRootPlan(SimpleOptimizationPlan rootPlan) {
        this.rootPlan = rootPlan;
    }

    public String getRequestId() {
        return this.requestId;
    }

    //Builder pattern to ease the object instantiation
    public static class Builder {
        private final OptimizationResourcesBundle bundle;

        public Builder() {
            this.bundle = new OptimizationResourcesBundle();
            this.bundle.setNumOfPlans(new AtomicInteger(1));
        }

        public Builder withPairHops(HashMap<String, Integer> pairHops) {
            this.bundle.setPairHops(pairHops);
            return this;
        }

        public Builder withPairLats(HashMap<String, Double> pairLats) {
            this.bundle.setPairLats(pairLats);
            return this;
        }

        public Builder withStatisticsDir(String statisticsDir) {
            this.bundle.setStatisticsDir(statisticsDir);
            return this;
        }

        public Builder withDatasetFile(String datasetFile) {
            this.bundle.setDatasetFile(datasetFile);
            return this;
        }

        public Builder withAggregationStrategy(String aggregationStrategy) {
            this.bundle.setAggregationStrategy(aggregationStrategy);
            return this;
        }

        public Builder withJarPath(String jarPath) {
            this.bundle.setJarPath(jarPath);
            return this;
        }

        public Builder withIntermediateDir(String intermediateDir) {
            this.bundle.setIntermediateDir(intermediateDir);
            return this;
        }

        public Builder withIFogNetworkPath(String iFogNetworkPath) {
            this.bundle.setIFogNetworkPath(iFogNetworkPath);
            return this;
        }

        public Builder withIFogWorkflowPath(String iFogWorkflowPath) {
            this.bundle.setiFogWorkflowPath(iFogWorkflowPath);
            return this;
        }

        public Builder withModelDirectory(String modelDirectory) {
            this.bundle.modelDirectory = modelDirectory;
            return this;
        }

        public Builder withModelWorkflow(String modelWorkflow) {
            this.bundle.modelWorkflow = modelWorkflow;
            return this;
        }

        public Builder withModelNetwork(String modelNetwork) {
            this.bundle.modelNetwork = modelNetwork;
            return this;
        }

        public Builder withDictionary(OldDictionary dictionary) {
            this.bundle.setDictionary(dictionary);
            return this;
        }

        public Builder withNewDictionary(Dictionary dictionary) {
            this.bundle.setNewDictionary(dictionary);
            return this;
        }

        public Builder withNetwork(Network network) {
            this.bundle.setNetwork(network);
            return this;
        }

        public Builder withWorkflow(OptimizationRequest workflow) {
            this.bundle.setWorkflow(workflow);
            return this;
        }

        public Builder withAlgorithm(String algorithm) {
            this.bundle.setTraversalAlgorithmName(algorithm);
            return this;
        }

        public Builder withThreads(int threads) {
            this.bundle.setThreads(threads);
            return this;
        }

        public Builder withTotalPlans(int plans) {
            this.bundle.setTotalPlans(plans);
            return this;
        }

        public Builder withUser(String user) {
            this.bundle.setUser(user);
            return this;
        }

        public Builder withTimeout(int timeout) {
            this.bundle.setTimeout(timeout);
            return this;
        }

        public Builder withPlanQueue(BoundedPriorityQueue<OptimizationPlan> validPlansQueue) {
            this.bundle.planQueue = validPlansQueue;
            return this;
        }

        public Builder withExecutorService(ExecutorService executorService) {
            this.bundle.executorService = executorService;
            return this;
        }

        public Builder withCostEstimator(CostEstimator costEstimator) {
            this.bundle.costEstimator = costEstimator;
            return this;
        }

        public Builder withLogger(Logger log) {
            this.bundle.logger = log;
            return this;
        }

        public Builder withRootPlan(SimpleOptimizationPlan rootPlan) {
            this.bundle.rootPlan = rootPlan;
            return this;
        }

        public OptimizationResourcesBundle build() {
            //Instantiate remaining fields
            this.bundle.setStatisticsBundle(new OptimizationRequestStatisticsBundle());

            //Return the built object
            return this.bundle;
        }

        public Builder withRequestId(String requestId) {
            this.bundle.requestId  = requestId;
            return this;
        }
    }
}
