package optimizer;

import core.exception.OptimizerException;
import core.graph.ThreadSafeDAG;
import core.iterable.DAGIterable;
import core.parser.dictionary.Dictionary;
import core.parser.network.AvailablePlatform;
import core.parser.network.Network;
import core.parser.network.Site;
import core.parser.workflow.OptimizationRequest;
import core.parser.workflow.Operator;
import core.structs.BoundedPriorityQueue;
import core.structs.Tuple;
import core.utils.FileUtils;
import core.utils.GraphUtils;
import optimizer.algorithm.GraphTraversalAlgorithm;
import optimizer.algorithm.cost.*;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;
import optimizer.algorithm.newalgs.*;
import optimizer.plan.OptimizationPlan;
import optimizer.plan.SimpleOptimizationPlan;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Driver for testing plan space generation algorithms.
 */
public class LightweightFlowOptimizer implements GraphTraversalAlgorithm {
    private final String algoName;
    private final PlanCostEstimatorInterface costEstimation;
    private final Map<Integer, String> idToOperatorMapping;
    private final Map<String, Integer> operatorToIdMapping;
    private final Map<Integer, Site> siteMapping;
    private final HashMap<String, Integer> siteMappingReverse;
    private final Map<Integer, AvailablePlatform> platformMapping;
    private final HashMap<String, Integer> platformMappingReverse;
    private final Map<Integer, HashSet<Integer>> operatorToAvailableSites;
    private final Set<Integer> cloudOnlyOperatorIds;
    private OptimizationResourcesBundle bundle;
    private BoundedPriorityQueue<OptimizationPlan> validPlans;
    private Graph root;
    private ExecutorService executor;
    private int timeout;
    private Map<String, ArrayList<String>> sitePlatformMapping;
    private final double percentage;
    private final int numIterations;
    private Logger logger;
    // Fields that are necessary to get plan cost from ifogsim metrics
    private Map<String, Double> pairLats;
    private Map<String, Double> pairLinks;
    private String datasetFile;

    // USED AS the STATE OF THE CLASS
    // WILL QUERY THIS WITH A METHOD WHEN THE ALGORITHM FINISHES EXECUTION
    // WILL BE USED TO GET INFORMATION ABOUT THE PLAN.
    private Graph bestPlan = null;
    private double algorithmDuration;
    private double[] weightVec;

    public LightweightFlowOptimizer(String algoName,
                         double[] weight,
                         double percentage,
                         int numIterations) {
        this.algoName = algoName;
        this.weightVec = weight;
        this.percentage = percentage;
        this.numIterations = numIterations;
        this.idToOperatorMapping = new HashMap<>();
        this.operatorToIdMapping = new HashMap<>();
        this.sitePlatformMapping = new HashMap<>();
        this.siteMapping = new HashMap<>();
        this.siteMappingReverse = new HashMap<>();
        this.operatorToAvailableSites = new HashMap<>();
        this.platformMapping = new HashMap<>();
        this.platformMappingReverse = new HashMap<>();
        this.cloudOnlyOperatorIds = new HashSet<>();
        this.costEstimation = new WeightedFitIoTCostEstimator(weight);
    }

    /**
     * Invokes the various plan space generation algorithms.
     */
    public void createPlanSpace(Map<Integer, AvailablePlatform> platformMapping,
                                Map<Integer, Site> siteMapping,
                                Graph flow,
                                int threads) {
        //
        // show flow details
        //
        this.root = flow;
        flow.updateCost(costEstimation);
        System.out.println("SCORE OF ROOT: " + flow.getCost());

        int numPlatforms = platformMapping.size() - 1;
        int numSites = siteMapping.size() - 1;
        int numOperators = flow.getVertices().size();
        BigInteger possiblePlans = getPossiblePlans(flow);
        ArrayList<Tuple<Integer, Integer>> actions = AlgorithmUtils.getActionsFromSitePlatformMapping(sitePlatformMapping, siteMappingReverse, platformMappingReverse);
        int targetBase = actions.size();
        logger.info(String.format("Number of platforms: [%s], Number of sites: [%s]", numPlatforms, numSites));
        logger.info(String.format("Number of operators: [%s]", numOperators));
        logger.info(String.format("Target base for the enumeration scheme: [%s]", targetBase));
        logger.info(String.format("Total number of plans that can be generated: [%s]", possiblePlans));
        logger.info(String.format("Number of actions: [%s]", actions.size()));

        long startTime, endTime, duration;
        double avgDuration;

        switch (this.algoName) {
            case "e-esq":
                startTime = (int) System.currentTimeMillis();
                this.bestPlan = flow;
                for (int i = 0; i < this.numIterations; i++) {
                    NewESQ esq2 = new NewESQ();
                    this.bestPlan = esq2.createPlanSpaceWithQueue(new Graph(this.bestPlan), actions, possiblePlans, costEstimation, threads, timeout);
                }
                endTime = (int) System.currentTimeMillis();
                duration = (endTime - startTime);
                avgDuration = (double) duration / this.numIterations;
                this.algorithmDuration = avgDuration;
                logger.info(String.format("Ran ESQ for [%s] iterations", this.numIterations));
                logger.info(String.format("Average duration per iteration: [%s]", avgDuration));
                printBestPlanCost(this.bestPlan);
                break;
            case "e-gsp":
                startTime = (int) System.currentTimeMillis();
                this.bestPlan = flow;
                for (int i = 0; i < this.numIterations; i++) {
                    this.bestPlan = NewGSP.createPlanSpaceGSProgressive(new Graph(this.bestPlan), actions, cloudOnlyOperatorIds, costEstimation, executor, timeout);
                    NewGSP.cleanUp();
                }
                endTime = (int) System.currentTimeMillis();
                duration = (endTime - startTime);
                avgDuration = (double) duration / this.numIterations;
                this.algorithmDuration = avgDuration;
                logger.info(String.format("Ran GSP for [%s] iterations", this.numIterations));
                logger.info(String.format("Average duration per iteration: [%s]", avgDuration));
                printBestPlanCost(this.bestPlan);
                break;
            case "e-hsp":
                startTime = (int) System.currentTimeMillis();
                NewHSp newHSp;
                bestPlan = flow;
                for (int i = 0; i < numIterations; i++) {
                    newHSp = new NewHSp(new Graph(this.bestPlan), actions, cloudOnlyOperatorIds, costEstimation, timeout, threads);
                    newHSp.setup();
                    this.bestPlan = newHSp.execute();
                    newHSp.cleanUp();
                }
                endTime = (int) System.currentTimeMillis();
                duration = (endTime - startTime);
                avgDuration = (double) duration / this.numIterations;
                this.algorithmDuration = avgDuration;
                logger.info(String.format("Ran HSP for [%s] iterations", this.numIterations));
                logger.info(String.format("Average duration per iteration: [%s]", avgDuration));
                printBestPlanCost(this.bestPlan);
                break;
            case "e-qp":
                startTime = (int) System.currentTimeMillis();
                this.bestPlan = flow;
                for (int i = 0; i < numIterations; i++) {
                    NewQuickPick newQuickPick = new NewQuickPick(new Graph(this.bestPlan), actions, possiblePlans, targetBase, cloudOnlyOperatorIds, siteMappingReverse, platformMappingReverse, costEstimation, timeout, false, threads, percentage, 4);
                    this.bestPlan = newQuickPick.execute();
                }
                endTime = (int) System.currentTimeMillis();
                duration = (endTime - startTime);
                avgDuration = (double) duration / this.numIterations;
                this.algorithmDuration = avgDuration;
                logger.info(String.format("Ran RSS for [%s] iterations", this.numIterations));
                logger.info(String.format("Average duration per iteration: [%s]", avgDuration));
                printBestPlanCost(this.bestPlan);
                break;
            case "e-escp":
                startTime = (int) System.currentTimeMillis();
                NewESCp newESCp = new NewESCp(new Graph(flow), actions, possiblePlans, targetBase, cloudOnlyOperatorIds, siteMappingReverse, platformMappingReverse, costEstimation, timeout, false, threads);
                this.bestPlan = newESCp.execute();
                endTime = (int) System.currentTimeMillis();
                duration = (endTime - startTime);
                this.algorithmDuration = duration;
                printBestPlanCost(this.bestPlan);
                break;
            case "hybrid":
                startTime = (int) System.currentTimeMillis();
                bestPlan = flow;
                for (int i = 0; i < this.numIterations; i++) {
                    Graph greedyBest = NewGSP.createPlanSpaceGSProgressive(new Graph(bestPlan), actions, cloudOnlyOperatorIds, costEstimation, executor, timeout);
                    NewGSP.cleanUp();
                    NewQuickPick newQuickPick = new NewQuickPick(new Graph(greedyBest), actions, possiblePlans, targetBase, cloudOnlyOperatorIds, siteMappingReverse, platformMappingReverse, costEstimation, timeout, false, threads, 3000, 4);
                    bestPlan = newQuickPick.execute();
                }
                endTime = (int) System.currentTimeMillis();
                duration = (endTime - startTime);
                avgDuration = (double) duration / this.numIterations;
                this.algorithmDuration = avgDuration;
                logger.info(String.format("Ran Hybrid for [%s] iterations", this.numIterations));
                logger.info(String.format("Average duration per iteration: [%s]", avgDuration));
                printBestPlanCost(this.bestPlan);
                break;
            default:
                throw new IllegalStateException("Default case in switch case.");
        }

        System.out.println();
        printGraphInfo(this.bestPlan);

        //Map Flow optimizer plans to the standard @{OptimizationPlan}
        if (this.bestPlan != null) {
            int total_cost = this.costEstimation.calculateCost(bestPlan);
//            int real_cost = getGraphRealCost(bestPlan);
            LinkedHashMap<String, Tuple<String, String>> plan = new LinkedHashMap<>();
            for (Vertex v : bestPlan.getVertices()) {
                String site = this.siteMapping.get(v.getSite()).getSiteName();
                String platform = this.platformMapping.get(v.getPlatform()).getPlatformName();
                plan.put(this.idToOperatorMapping.get(v.getOperatorId()), new Tuple<>(site, platform));
            }
            //
            validPlans.offer(new SimpleOptimizationPlan(plan, total_cost, 0));
        }
    }


    public VaryWeightsResult getResult() {
        VaryWeightsResult varyWeightsResult = new VaryWeightsResult();
        WeightedFitIoTCostEstimator wce = (WeightedFitIoTCostEstimator) costEstimation;
        double[] normalizedCostDimensions = wce.getNormalizedCostDimensions(this.bestPlan);
        double totalNormalizedScore = (double) wce.calculateCost(this.bestPlan) / WeightedFitIoTCostEstimator.COST_MULTIPLIER;
        double[] realCostDimensions = wce.getRealCostDimensions(this.bestPlan);


        varyWeightsResult.setAlgorithm(this.algoName);
        varyWeightsResult.setDuration(this.algorithmDuration);
        varyWeightsResult.setWeights(this.weightVec);
        varyWeightsResult.setNormalizedScores(normalizedCostDimensions);
        varyWeightsResult.setTotalNormalizedScore(totalNormalizedScore);
        varyWeightsResult.setDenormalizedScores(realCostDimensions);
        return varyWeightsResult;
    }


    private BigInteger getPossiblePlans(Graph flow) {
        BigInteger possiblePlans = BigInteger.ONE;
        for (Vertex operator : flow.getVertices()) {
            int operatorID = operator.getOperatorId();
            HashSet<Integer> availableSitesForOperator = operatorToAvailableSites.get(operatorID);
            int possiblePlansForOperator = availableSitesForOperator.stream()
                    .map(siteMapping::get) // get the Site object for each site ID
                    .map(Site::getSiteName) // get the site name
                    .map(sitePlatformMapping::get) // get the list of platforms for each site name
                    .mapToInt(List::size) // count the number of platforms for each site
                    .sum(); // sum

            possiblePlans = possiblePlans.multiply(BigInteger.valueOf(possiblePlansForOperator));
        }
        return possiblePlans;
    }

    private void printBestPlanCost(Graph bestPlan) {
        WeightedFitIoTCostEstimator costEstimation = (WeightedFitIoTCostEstimator) this.costEstimation;
        double fitIoTCost = (bestPlan.getCost());
        logger.info(String.format("Final plan cost: [%s]", fitIoTCost));
        for (Vertex v : bestPlan.getVertices()) {
            // Print the site and platform names for each vertex
            System.out.println("Operator: " + this.idToOperatorMapping.get(v.getOperatorId()) +
                    ", Site: " + this.siteMapping.get(v.getSite()).getSiteName() +
                    ", Platform: " + this.platformMapping.get(v.getPlatform()).getPlatformName());
        }
    }

    //Injected from the Optimizer interface
    @Override
    public void setup(OptimizationResourcesBundle bundle, BoundedPriorityQueue<OptimizationPlan> validPlans, OptimizationPlan rootPlan,
                      ExecutorService executorService, OperatorCostEstimatorInterface costEstimator, Logger logger) throws OptimizerException {
        this.bundle = bundle;
        this.validPlans = validPlans;
        this.executor = executorService;
        this.pairLats = bundle.getPairLats();
        this.pairLinks = bundle.getPairLinks();
        this.datasetFile = bundle.getDatasetFile();
        this.timeout = bundle.getTimeout();
        this.logger = logger;
    }

    @Override
    public void setup(OptimizationResourcesBundle bundle) throws OptimizerException {
        setup(bundle, bundle.getPlanQueue(), bundle.getRootPlan(), bundle.getExecutorService(), bundle.getCostEstimator(), bundle.getLogger());
    }

    @Override
    public void doWork() {
        // Parse the OptimizationBundle
        final OptimizationRequest optimizationRequest = bundle.getWorkflow();
        final Network network = bundle.getNetwork();
        final Dictionary dictionary = bundle.getNewDictionary();
        final ThreadSafeDAG<Operator> operatorGraph = FileUtils.getOperatorGraph(optimizationRequest);
        final Set<AvailablePlatform> ogPlatforms = network.getPlatforms();
        final Set<Site> ogSites = network.getSites();
        final int threads = bundle.getThreads();
        LinkedHashMap<String, Map<String, List<String>>> opImpl = FileUtils.getOperatorImplementations(operatorGraph, network, dictionary, FileUtils.getOpNameToClassKeyMapping(optimizationRequest));

        // Fast check to make sure nothing went wrong during parsing
        if (ogPlatforms.isEmpty() || ogSites.isEmpty()) {
            throw new IllegalStateException("Need at least one platform and site");
        }

        // Necessary processing to fill the FlowOptimizer's fields
        // and create a Graph object out of the legacy graph
        setPlatformMapping(ogPlatforms);
        setSiteMapping(ogSites);
        setOperatorMapping(operatorGraph);
        final Map<Integer, List<Integer>> childMapping = createAdjacencyMap(operatorGraph);
        setOperatorToAvailableSiteMapping(network);
        this.sitePlatformMapping = network.getSitePlatformMapping(false);
        // Very important step in order to have compatibility with the statistics file names
        // Operators must be sorted with respect to their name and inserted in the graph in that order
        // This variable will be used in various places in the code below
        ArrayList<String> sortedOperatorNames = operatorToIdMapping.keySet().stream()
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));

        // First we need to get an initial graph, with no placement present
        Graph rootFlow = getInitialGraph(sortedOperatorNames, operatorToIdMapping, childMapping);

        // Get a pseudorandom placement to serve as the root plan of the optimization
        // In this version of the FlowOptimizer, the workflow is always going to be the yahoo-streaming
        String rootFlowSignature = getRandomRootSignatureFitIoT(sortedOperatorNames);

        // Now we need to place the operators based on the signature
        placeOperatorsUsingSignature(rootFlow, rootFlowSignature);

        // Validate that the placement does not contain any UNKNOWN_
        validatePlacement(rootFlow);

        // Print id to operator name mapping of the graph
        printGraphInfo(rootFlow);

        // Set up the different cost estimation classes based on whether simulation is used
        setUpFitIoTCostEstimation(rootFlow);

        //plan space generation algorithms
        createPlanSpace(platformMapping, siteMapping, rootFlow, threads);
    }

    /**
     * Method to set the operatorToAvailableSites field.
     * This field maps each operator to the devices that it can be executed on
     *
     * @param ogNetwork The legacy network object
     */
    private void setOperatorToAvailableSiteMapping(Network ogNetwork) {
        Dictionary dictionary = bundle.getNewDictionary();
        for (Map.Entry<Integer, String> entry : idToOperatorMapping.entrySet()) {
            int vertexID = entry.getKey();
            if (vertexID == 0) continue;
            // Get the classKey (name) of the operator
            String classKey = idToOperatorMapping.get(vertexID);
            // Get the sites where this operator can be implemented

            HashSet<Integer> sites = dictionary.getImplementationsForClassKey(classKey, ogNetwork).keySet()
                    .stream()
                    .map(siteMappingReverse::get)
                    .collect(Collectors.toCollection(HashSet::new));

            operatorToAvailableSites.put(vertexID, sites);
        }
    }

    /**
     * Method to create an adjacency map of the legacy graph.
     * This map will be later used in order to create the Graph object which is used in the algorithms
     *
     * @param operatorGraph The legacy graph representation
     * @return An adjacency map of the given graph
     */
    private Map<Integer, List<Integer>> createAdjacencyMap(ThreadSafeDAG<Operator> operatorGraph) {
        //Insert the operator IDs in a Graph
        final Map<Integer, List<Integer>> adjecencyMap = new HashMap<>();
        final Map<String, Set<String>> operatorParents = GraphUtils.getOperatorParentMap(operatorGraph);
        String DEFAULT_OPERATOR = "UNKNOWN_OPERATOR";
        for (String vertexName : idToOperatorMapping.values()) {
            int vid = operatorToIdMapping.get(vertexName);
            adjecencyMap.put(vid, new ArrayList<>());
        }
        adjecencyMap.put(0, Collections.emptyList());
        for (String vertexName : idToOperatorMapping.values()) {
            if (vertexName.equals(DEFAULT_OPERATOR)) {
                continue;
            }
            int vertexId = operatorToIdMapping.get(vertexName);
            for (String parentVertexName : operatorParents.get(vertexName)) {
                int parentVertexId = operatorToIdMapping.get(parentVertexName);
                adjecencyMap.get(parentVertexId).add(vertexId);
            }
        }
        return adjecencyMap;
    }

    /**
     * Method to set the idToOperatorMapping and operatorToIdMapping fields
     *
     * @param operatorGraph The legacy graph representation
     */
    private void setOperatorMapping(ThreadSafeDAG<Operator> operatorGraph) {
        int idx = 1;
        for (core.graph.Vertex<Operator> vertex : new DAGIterable<>(operatorGraph)) {
            idToOperatorMapping.put(idx, vertex.getData().getName());
            operatorToIdMapping.put(vertex.getData().getName(), idx);
            idx++;
        }
        String DEFAULT_OPERATOR = "UNKNOWN_OPERATOR";
        idToOperatorMapping.put(0, DEFAULT_OPERATOR);
        operatorToIdMapping.put(DEFAULT_OPERATOR, 0);
    }

    /**
     * Method to set the siteMapping and siteMapping reverse fields
     *
     * @param ogSites The legacy Sites
     */
    private void setSiteMapping(Set<Site> ogSites) {
        int idx = 1;
        for (Site site : ogSites.stream()
                .sorted(Comparator.comparing(Site::getSiteName))
                .collect(Collectors.toCollection(LinkedHashSet::new))) {
            siteMappingReverse.put(site.getSiteName(), idx);
            siteMapping.put(idx, site);
            idx++;
        }
        Site DEFAULT_SITE = new Site("UNKNOWN_SITE", Collections.emptyList());
        siteMapping.put(0, DEFAULT_SITE);
        siteMappingReverse.put(DEFAULT_SITE.getSiteName(), 0);
    }

    /**
     * Method to set the platformMapping field
     *
     * @param ogPlatforms The legacy set of available platforms
     */
    private void setPlatformMapping(Set<AvailablePlatform> ogPlatforms) {
        // Mapping
        int idx = 1;
        for (AvailablePlatform platform : ogPlatforms.stream()
                .sorted(Comparator.comparing(AvailablePlatform::getPlatformName))
                .collect(Collectors.toCollection(LinkedHashSet::new))) {
            platformMappingReverse.put(platform.getPlatformName(), idx);
            platformMapping.put(idx, platform);
            idx++;
        }

        AvailablePlatform DEFAULT_PLATFORM = new AvailablePlatform("UNKNOWN_PLATFORM");
        platformMapping.put(0, DEFAULT_PLATFORM);
        platformMappingReverse.put(DEFAULT_PLATFORM.getPlatformName(), 0);
    }

    /**
     * Method to get the initial graph that will be used in the optimization process.
     * Takes into consideration the fact that we are working with the fitiot dataset.
     * It contains multiple platforms, and each site may support different number of platforms.
     *
     * @return A String signature for the starting plan
     */
    private String getRandomRootSignatureFitIoT(ArrayList<String> sortedOperatorNames) {
        StringBuilder sb = new StringBuilder();
        Random random = new Random(7);
        ArrayList<String> availableSites = siteMapping.keySet().stream()
                .filter(i -> i != 0)
                .map(siteMapping::get)
                .map(Site::getSiteName)
                .collect(Collectors.toCollection(ArrayList::new));

        for (String operatorName : sortedOperatorNames) {
            int operatorID = this.operatorToIdMapping.get(operatorName);
            if (operatorID == 0) continue;

            String siteName = availableSites.get(random.nextInt(availableSites.size()));
            int siteID = siteMappingReverse.get(siteName);

            ArrayList<String> availablePlatforms = sitePlatformMapping.get(siteName);
            String platformName = availablePlatforms.get(random.nextInt(availablePlatforms.size()));
            int platformID = platformMappingReverse.get(platformName);

            sb.append("_").append(platformID).append("-").append(siteID);
        }

        return sb.toString().replaceFirst("_", "");
    }

    /**
     * Method to return a random signature string
     * to be used as the starting plan in the optimization process
     *
     * @param sortedOperatorNames A list with the operator names in lexicographical order
     * @return A graph signature that represents the starting plan
     */
    private String getRandomRootSignature(ArrayList<String> sortedOperatorNames) {
        StringBuilder sb = new StringBuilder();
        ArrayList<Integer> availableSites = siteMapping.keySet().stream()
                .filter(i -> i != 0)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Random random;
        if (bundle.getIFogWorkflowPath().contains("train") && availableSites.size() == 7) {
            random = new Random(6);
        } else {
            random = new Random(5);
        }

        for (String operatorName : sortedOperatorNames) {
            int operatorID = this.operatorToIdMapping.get(operatorName);
            if (operatorID == 0) continue;
            int deviceID = siteMappingReverse.get("cloud");
            if (!cloudOnlyOperatorIds.contains(operatorID)) {
                int randomIndex = random.nextInt(availableSites.size());
                deviceID = availableSites.get(randomIndex);
            }
            sb.append("_").append("1-").append(deviceID);
        }
        return sb.toString().replaceFirst("_", "");
    }

    private void printGraphInfo(Graph flow) {
        for (Vertex v : flow.getVertices())
            System.out.println(idToOperatorMapping.get(v.getOperatorId()) +
                    " -> " + siteMapping.get(v.getSite()).getSiteName() +
                    " -> " + platformMapping.get(v.getPlatform()).getPlatformName());
    }

    private void validatePlacement(Graph rootFlow) {
        // Validate that no operator has been placed in UNKNOWN_SITE
        for (Vertex v : rootFlow.getVertices()) {
            if (v.getSite() == 0) {
                throw new IllegalStateException("Operator " + idToOperatorMapping.get(v.getOperatorId()) + " has been placed in UNKNOWN_SITE");
            }
        }

        // Validate that no operator has been placed in UNKNOWN_SITE
        for (Vertex v : rootFlow.getVertices()) {
            if (v.getPlatform() == 0) {
                throw new IllegalStateException("Operator " + idToOperatorMapping.get(v.getOperatorId()) + " has been placed in UNKNOWN_PLATFORM");
            }
        }
    }

    private void placeOperatorsUsingSignature(Graph rootFlow, String signature) {
        String[] parts = signature.split("_");
        assert parts.length == rootFlow.getVertices().size();
        int i = 0;
        for (Vertex v : rootFlow.getVertices()) {
            String signaturePart = parts[i++];
            int platform = Integer.parseInt(signaturePart.split("-")[0]);
            int site = Integer.parseInt(signaturePart.split("-")[1]);
            v.setPlatform(platform);
            v.setSite(site);
        }
    }

    private Graph getInitialGraph(ArrayList<String> sortedOperatorNames,
                                  Map<String, Integer> operatorToIdMapping,
                                  Map<Integer, List<Integer>> childMapping) {
        Graph flow = new Graph();
        final Map<Integer, Vertex> vertexIdToVertex = new HashMap<>();//Temp
        for (String opName : sortedOperatorNames) {
            int vertexID = operatorToIdMapping.get(opName);
            if (vertexID == 0) {
                continue;
            }
            // Add vertex with placeholder platform and site
            // The correct platform and site will be set later,
            // with respect to which signature will be used (user-provided or random)
            Vertex newVtx = new Vertex(vertexID, 0, 0);
            flow.addVertex(newVtx);
            vertexIdToVertex.put(vertexID, newVtx);
        }

        for (Vertex v : flow.getVertices()) {
            for (int nbrId : childMapping.get(v.getOperatorId())) {
                v.addAdj(vertexIdToVertex.get(nbrId));
            }
        }
        return flow;
    }

    private void setUpFitIoTCostEstimation(Graph rootFlow) {
        WeightedFitIoTCostEstimator fitIotCostEstimator = (WeightedFitIoTCostEstimator) costEstimation;
        fitIotCostEstimator.setDatasetFile(this.datasetFile);
        fitIotCostEstimator.setSiteMapping(siteMapping);
        fitIotCostEstimator.setPlatformMapping(platformMapping);
        fitIotCostEstimator.setIdToOperatorMapping(idToOperatorMapping);
        try {
            fitIotCostEstimator.loadDataset();
        } catch (IOException e) {
            System.out.println("Error loading dataset for FitIoT cost estimation! Exiting...");
            e.printStackTrace();
            System.exit(1);
        }
        fitIotCostEstimator.setPairLats(this.pairLats);
        fitIotCostEstimator.setRoot(rootFlow);
    }

    @Override
    public void teardown() {
        System.out.println("FlowOptimizer Tear down");
    }

    @Override
    public List<String> aliases() {
        return Collections.singletonList("exp");
    }
}