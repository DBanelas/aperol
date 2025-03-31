package optimizer.algorithm.cost;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.*;
import core.parser.network.Site;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;
import org.apache.commons.io.FileUtils;
import org.dbanelas.crexdata.placement.Placement;
import org.dbanelas.crexdata.simulation.SimulationWorker;
import org.dbanelas.crexdata.topology.Topology;
import org.dbanelas.crexdata.workflow.Workflow;
import org.dbanelas.crexdata.workflow.WorkflowParser;
import org.dbanelas.crexdata.topology.RealTopologyGenerator;
import org.dbanelas.crexdata.topology.SimulationTopologyGenerator;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PlanCostEstimator implements CostEstimatorIface {
    private static final int WORST_SCORE = Integer.MIN_VALUE;
    public static int failedSimulations = 0;
    private Graph root;
    private iFogSimMetrics rootMetrics;
    private String rootSignature;
    private String  statisticsDir;
    private Map<Integer, String> idToOperatorMapping;
    private Map<String, Integer> operatorToIdMapping;
    private Map<Integer, Site> siteMapping;
    private Map<String, Integer> pairHops;
    private int networkSize;

    // Fields necessary for when the simulator is used
    private String jarPath;
    private String iFogWorkflowPath;
    private String intermediatePath;
    private Workflow workflow;
    private Topology topology;
    private SimulationTopologyGenerator simulationTopologyGenerator;

    // Fields necessary for when KNN estimation is used
    private Map<String, Integer> flowsWithScores;

    private final String SCORE_DIR = "/Users/dbanelas/Developer/CREXDATA/nn-cost-estimator/data/scores/scores_pred_2047_uniform_migration_test";

    public PlanCostEstimator() {
    }

    @Override
    public int calculateCost(Graph flow) {
        return getSimulatedTotalCost(flow);
    }

    public void printMetricsForFlow(Graph flow) {
        iFogSimMetrics flowMetrics = getMetricsForFlow(flow.getSignatureDashed())
                    .orElseThrow(() -> new RuntimeException("printMetricsForFlow(): " + "Something went wrong in parsing the metrics file."));

        System.out.printf("Throughput: %.2f\n", flowMetrics.getThroughput());
        System.out.printf("Latency: %.2f\n", flowMetrics.getLatency());
        System.out.printf("Network Usage: %.2f\n" , flowMetrics.getNetworkCost());
        System.out.println("Migration Cost: " + getMigrationCost(flow));
        System.out.println("Real Cost: " + getRealCost(flow));
        System.out.println();
    }

    /**
     * Method to calculate the KNN cost for a given flow for a specified K
     * It finds the top K the closest signature names to the given flow and calculates
     * their average scores.
     * @param flow - the flow to calculate the score for
     * @param K - the number of closest signatures to take into consideration
     * @return - the calculated score for the
     */
    public int calculateCostKNN(Graph flow, int K) {
        String flowSignature = flow.getSignatureDashed();

        PriorityQueue<String> queue =
                new PriorityQueue<>(K, Comparator.comparingInt(str ->
                        getLexicographicDistance(str, flowSignature)));

        for (Map.Entry<String, Integer> entry : flowsWithScores.entrySet()) {
            String currFlowName = entry.getKey();
            if (queue.size() < K) {
                queue.add(entry.getKey());
            } else {
                String queueHead = queue.peek();
                assert queueHead != null;
                if (getLexicographicDistance(currFlowName, flowSignature) < getLexicographicDistance(queueHead, flowSignature)) {
                    queue.poll();
                    queue.add(entry.getKey());
                }
            }
        }

        // By now, the priority queue contains the K closest flow names to the input flow
        // We need to calculate the score of these flows and return it as the score of the given flow
        return (int) queue.stream()
                .mapToInt(flowName -> flowsWithScores.get(flowName))
                .average()
                .orElseThrow(() -> new IllegalStateException("Could not calculate the average KNN score for " + flowSignature));
    }

    /**
     * Method to calculate the distance between two strings of equal length.
     * The strings we are dealing with here are flow signatures that look like:
     * 1-12_1-5_1-10_....
     * The distance is defined as the number of changes in the x-y pairs between the two strings
     * Attention: The distance metric may change
     * @param s1 - String 1
     * @param s2 - String 2
     * @return - The distance between the two strings
     */
    private int getLexicographicDistance(String s1, String s2) {
        String[] s1Parts = s1.split("_");
        String[] s2Parts = s2.split("_");
        int changes = 0;
        for (int i = 0; i < s1Parts.length; i++) {
            String s1Part = s1Parts[i].split("-")[1];
            String s2Part = s2Parts[i].split("-")[1];
            if (!s1Part.equals(s2Part)) {
                changes++;
            }
        }
        return changes;
    }

    /**
     * IMPORTANT!!!
     * Only used in the HSp algorithm.
     * In case of invalid metric file, it returns Integer.MAX_VALUE in order to place it
     * in the worst position on the pareto front
     * @param flow The plan to calculate the migration cost from
     * @return The migration cost of the plan
     */
    @Override
    public int getMigrationCost(Graph flow) {
        Optional<iFogSimMetrics> graphMetricsOpt = getMetricsForFlow(flow.getSignatureDashed());
        iFogSimMetrics metrics = graphMetricsOpt
                .orElseThrow(() -> new RuntimeException("getMigrationCost(): Something went wrong in parsing the metrics file:" + flow.getSignatureDashed()));

        if (metrics.isInvalid()) return Integer.MAX_VALUE;
        return (int) getMigrationCost(metrics);
    }

    /**
     * IMPORTANT!!!
     * Only used in the HSP algorithm
     * In case of invalid metric file, it returns Integer.MIN_VALUE in order to place it
     * in the worst position on the pareto front
     * @param flow The plan to calculate the realCost from
     * @return The real cost (score in our case) of the plan
     */
    @Override
    public int getRealCost(Graph flow) {
        Optional<iFogSimMetrics> graphMetricsOpt = getMetricsForFlow(flow.getSignatureDashed());
        iFogSimMetrics metrics = graphMetricsOpt
                .orElseThrow(() -> new RuntimeException("getMigrationCost(): Something went wrong in parsing the metrics file:" + flow.getSignatureDashed()));

        if (metrics.isInvalid()) return Integer.MIN_VALUE;
        return (int) getMetricsScore(metrics);
    }

    /**
     * Method to get the metrics for a given flow
     * @param signature The candidate plan's signature
     * @return The metrics of the candidate plan
     */
    public Optional<iFogSimMetrics> getMetricsForFlow(String signature) {
        String filename = Path.of(statisticsDir, signature + ".json").toString();

        try (FileReader fileReader = new FileReader(filename)) {
            Gson gson = new Gson();

            try {
                iFogSimMetrics metrics = gson.fromJson(fileReader, iFogSimMetrics.class);
                if (metrics == null) {
                    return Optional.empty();
                }
                metrics.setSignature(signature);
                return Optional.of(metrics);
            } catch (JsonSyntaxException e) {
                System.out.println("getMetricsForFlow(): Syntax error at file: " + filename);
            }

        } catch (IOException e) {
            System.out.println("getMetricsForFlow(): Did not find metrics file: " + filename);
        }
        return Optional.empty();
    }

    /**
     * Method to check if the candidate plan has a metric file
     * @param signature The signature of the candidate plan
     * @return True if the file exists, false otherwise
     */
    public boolean metricFileExistsForGraph(String signature) {
        Path path = Path.of(statisticsDir, signature + ".json");
        return Files.exists(path);
    }

    /**
     * Cost formula for the MAXIMIZATION problem.
     * The optimizer needs to maximize the score given by this method
     * @param flowSignature The signature of the plan
     * @return The score of the plan
     */
    public int calculateScoreFromMetricsRevised(String flowSignature) {
        Optional<iFogSimMetrics> candidateMetricsOpt = getMetricsForFlow(flowSignature);
        if (candidateMetricsOpt.isEmpty()) {
            return WORST_SCORE;
        }

        // If the metrics file was an invalid one, the simulation of the placement
        // was not completed. Therefore, these plans are bad plans -> Worst score returned.
        iFogSimMetrics candidateMetrics = candidateMetricsOpt.get();
        if (candidateMetrics.isInvalid()) {
            return WORST_SCORE;
        }

        double metricsScore = getMetricsScore(candidateMetrics);
        double migrationCost = getMigrationCost(candidateMetrics);

        int score = (int) Math.round(metricsScore - migrationCost);
        ObjectMapper mapper = new ObjectMapper();
        HashMap<String, Integer> scoreJson = new HashMap<>();
        scoreJson.put("metricScore", (int) metricsScore);
        scoreJson.put("migrationCost", (int) migrationCost);
        try {
            mapper.writeValue(new File(SCORE_DIR + '/' + flowSignature), scoreJson);
        } catch (IOException e) {
            System.out.println("Could not write score to file!");
        }
        return score;
    }

    public double getMetricsScore(iFogSimMetrics candidateMetrics) {
        double candidateNetwork = candidateMetrics.getNetworkCost();
        double rootNetwork = rootMetrics.getNetworkCost();

        double candidateThroughput = candidateMetrics.getThroughput();
        double rootThroughput = rootMetrics.getThroughput();

        double candidateLatency = candidateMetrics.getEndToEndLatency();
        double rootLatency = rootMetrics.getEndToEndLatency();

        double throughputScore = (candidateThroughput / rootThroughput) - 1;
        double latencyScore = 1 - (candidateLatency / rootLatency);
        double networkScore = 1 - (candidateNetwork / rootNetwork);

        return 1000 * (throughputScore + latencyScore + networkScore);
    }

    @SuppressWarnings("unused")
    private void printPlacement(Graph flow) {
        for (Vertex vertex : flow.getVertices()) {
            System.out.println(idToOperatorMapping.get(vertex.getOperatorId()) + " : " + siteMapping.get(vertex.getSite()).getSiteName());
        }
    }

    public int getSimulatedTotalCost(Graph flow) {
        String candidateSignature = flow.getSignatureDashed();

        if (candidateSignature.equals(rootSignature)) {
            return WORST_SCORE;
        }

        if (metricFileExistsForGraph(candidateSignature)) {
            return calculateScoreFromMetricsRevised(candidateSignature);
        }

        // If the specific flow has not been simulated, perform the simulation
        simulatePlan(flow);

        // Calculate the cost based on the statistics of the simulation
        return calculateScoreFromMetricsRevised(flow.getSignatureDashed());
    }

    /**
     * Method to simulate a given flow using the simulator.
     * After the execution of this method, a file named with the signature of the flow
     * should exist in the statistics directory
     * @param flow The flow that is going to be simulated
     */
    public void simulatePlan(Graph flow) {
        String signature = flow.getSignatureDashed();
        LinkedHashMap<String, String> operatorToDeviceMap = getOperatorToDeviceMap(flow);

        // Create the directory for this signature
        Path signatureDir = Path.of(intermediatePath, signature);
        if (!signatureDir.toFile().exists()) {
            boolean created = signatureDir.toFile().mkdirs();
            if (!created) {
                System.out.println("Failed to create directory.");
                return;
            }
        }

        // Save the placement file
        Path placementPath = Path.of(signatureDir.toString(), "placement.json");
        savePlacementFile(placementPath, operatorToDeviceMap);

        Placement placement = new Placement(operatorToDeviceMap);

        Topology simulationTopology = this.simulationTopologyGenerator.generateSimulationTopology(placement, this.workflow);

        // Write the sim topology into a file in the directory
        Path simTopologyPath = Path.of(signatureDir.toString(), "topology.json");
        simulationTopology.printToJsonFile(simTopologyPath.toString(), true);

        SimulationWorker simWorker = new SimulationWorker(
                simTopologyPath.toAbsolutePath().toString(),
                Path.of(iFogWorkflowPath).toAbsolutePath().toString(),
                placementPath.toAbsolutePath().toString(),
                Path.of(statisticsDir).toAbsolutePath().toString(),
                Path.of(jarPath).toAbsolutePath().toString(),
                3600
        );

        try {
            // Mind that executeSimulation() method, waits for timeout seconds and then
            // another 5 seconds in the case that it did not gracefully terminate.
            int exitCode = simWorker.executeSimulation(signature, 20, TimeUnit.SECONDS);
            if (exitCode != 0) {
                // Save a statistics file that will denote that the placement could not be
                // executed within a sensible timeframe
                failedSimulations++;
                saveErrorStatisticsFile(signature);
            }

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                System.out.println("Simulation interrupted.");
                Thread.currentThread().interrupt();
            } else {
                System.out.println("Something went wrong: " + e.getMessage());
                System.exit(1);
            }
        }

        try {
            FileUtils.deleteDirectory(signatureDir.toFile());
        } catch (IOException e) {
            System.out.println("Cannot delete file: " + signatureDir);
        }
    }

    private void saveErrorStatisticsFile(String signature) {
        Path statisticsFile = Path.of(this.statisticsDir, signature + ".json");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonObject = mapper.createObjectNode();
        jsonObject.put("invalid", true);
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(statisticsFile.toFile(), jsonObject);
        } catch (JsonMappingException e) {
            System.out.println("saveErrorStatisticsFile(): Error mapping object to json");
        } catch (JsonGenerationException e) {
            System.out.println("saveErrorStatisticsFile(): Error generating json from object");
        } catch (IOException e) {
            System.out.println("saveErrorStatisticsFile(): Error saving statisticsFile");
        }
    }

    /**
     * Method to save the placement file to the intermediate directory
     * @param path Path to intermediate directory
     * @param operatorToDeviceMap Placement Map
     */
    private void savePlacementFile(Path path, Map<String, String> operatorToDeviceMap) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.valueToTree(operatorToDeviceMap);
        File file = path.toFile();

        try {
            mapper.writeValue(file, node);
        } catch (IOException e) {
            System.out.println("Could not save placement file!");
            System.exit(1);
        }
    }

    /**
     * Method to generate placement map from the signature of the graph
     * @param flow The given graph
     * @return The Map with the operator to device placement
     */
    private LinkedHashMap<String, String> getOperatorToDeviceMap(Graph flow) {
        LinkedHashMap<String, String> operatorToDeviceMap = new LinkedHashMap<>();
        String graphSignature = flow.getSignatureDashed();
        String[] signatureParts = graphSignature.split("_");

        int i = 0;
        for (Vertex v : flow.getVertices()) {
            int operatorID = v.getOperatorId();
            String operatorName = idToOperatorMapping.get(operatorID);
            int deviceID = Integer.parseInt(signatureParts[i++].split("-")[1]);
            String deviceName = siteMapping.get(deviceID).getSiteName();
            operatorToDeviceMap.put(operatorName, deviceName);
        }
        return operatorToDeviceMap;
    }

    private void initMap() {
        try (DirectoryStream<Path> pathStream = Files.newDirectoryStream(Path.of(this.statisticsDir))) {
            for (Path path : pathStream) {
                String flowSignature = path.getFileName().toString().split("\\.")[0];
                int flowCost = calculateScoreFromMetricsRevised(flowSignature);
                this.flowsWithScores.put(flowSignature, flowCost);
            }
        } catch (NotDirectoryException e) {
            System.out.println("initPrefixTrie(): Not a directory: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("initPrefixTrie(): IO exception: " + e.getMessage());
        }
    }

    /**
     * Method to calculate the migration cost from the root plan to the candidate plan
     * @param candidateMetrics The metrics object of the candidate plan
     * @return The migration cost
     */
    private double getMigrationCost(iFogSimMetrics candidateMetrics) {
        double migrationCost = 0.0;
        Map<String, String> placement = candidateMetrics.getPlacement();

        for (String operator : placement.keySet()) {
            int operatorID = operatorToIdMapping.get(operator);
            Vertex rootVertex = root.getVertex(operatorID);
            int rootSite = rootVertex.getSite();
            String rootSiteName = siteMapping.get(rootSite).getSiteName();
            String candidateSiteName = placement.get(operator);

            String key = rootSiteName + ":" + candidateSiteName;
            double latency = pairHops.get(key);

            int remainingData = rootMetrics.getRemainingDataForOperator(operator);
            migrationCost += remainingData * latency;
        }
        return migrationCost;
    }

    public Graph getRoot() {
        return root;
    }

    public void setRoot(Graph root) {
        this.root = root;
        String signature = root.getSignatureDashed();
        this.rootSignature = signature;
        Optional<iFogSimMetrics> rootMetrics;
        if (!metricFileExistsForGraph(signature)) {
            simulatePlan(root);
        }
        rootMetrics = getMetricsForFlow(signature);
        this.rootMetrics = rootMetrics.orElseThrow(() -> new RuntimeException("setRoot(): No root metrics found for signature: " + signature));
    }

    public void loadWorkflow(String workflowPath) {
        WorkflowParser parser = new WorkflowParser();
        this.workflow = parser.parseWorkflow(workflowPath);
    }

    public void loadSimTopologyGeneratorObject() {
        assert this.topology != null;
        double startTime = System.currentTimeMillis();
        System.out.println("Started creating the simulation topology generator object...");
        this.simulationTopologyGenerator = new SimulationTopologyGenerator(this.topology);
        double endTime = System.currentTimeMillis();
        double duration = Math.round((endTime - startTime) / 1000);
        System.out.println("Object created in " + duration + "s");
    }

    public void loadTopology(String topologyPath) {
        RealTopologyGenerator realTopologyGenerator = new RealTopologyGenerator();
        this.topology = realTopologyGenerator.generateFromFile(topologyPath);
    }

    public void setMapOfFlowsWithScores() {
        this.flowsWithScores = new HashMap<>();
        initMap();
    }

    public void setStatisticsDir(String statisticsDir) {
        this.statisticsDir = statisticsDir;
    }

    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }

    public void setNetworkSize(int networkSize) {
        this.networkSize = networkSize;
    }

    public void setIntermediatePath(String intermediatePath) {
        this.intermediatePath = intermediatePath;
    }

    public void setIFogWorkflowPath(String iFogWorkflowPath) {
        this.iFogWorkflowPath = iFogWorkflowPath;
    }

    public void setIdToOperatorMapping(Map<Integer, String> idToOperatorMapping) {
        this.idToOperatorMapping = idToOperatorMapping;
    }

    public void setOperatorToIdMapping(Map<String, Integer> operatorToIdMapping) {
        this.operatorToIdMapping = operatorToIdMapping;
    }

    public void setSiteMapping(Map<Integer, Site> siteMapping) {
        this.siteMapping = siteMapping;
    }

    public void setPairHops(Map<String, Integer> pairHops) {
        this.pairHops = pairHops;
    }
}
