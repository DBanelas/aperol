package optimizer.algorithm.cost;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import core.parser.network.AvailablePlatform;
import core.parser.network.Site;
import core.structs.Tuple;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class PlanDagStarCostEstimator implements PlanCostEstimatorInterface {

    public static final int COST_MULTIPLIER = 1000; // Multiplier to convert cost to milliseconds
    private final Map<String, List<String>> operatorNeighbors = new HashMap<>();
    private final Map<String, List<String>> operatorPredecessors = new HashMap<>();
    private final Map<Integer, List<String>> operatorLevels = new HashMap<>();
    private final Map<String, Integer> inDegree = new HashMap<>();
    private String workflowFile;
    private String datasetFile;
    private Map<String, Double> pairLats;
    private AggregationStrategy aggregationStrategy = AggregationStrategy.MAX;
    private Map<Integer, String> idToOperatorMapping;
    private Map<Integer, Site> siteMapping;
    private Map<Integer, AvailablePlatform> platformMapping;
    private Map<String, Map<String, Map<String, OperatorStatistics>>> fitIoToperatorStatistics;
    private boolean isSimDataset;
    private Graph root;

    public PlanDagStarCostEstimator() {
    }

    public boolean isSimDataset() {
        return isSimDataset;
    }

    public void setIsSimDataset(boolean isSimDataset) {
        this.isSimDataset = isSimDataset;
    }

    public void setSiteMapping(Map<Integer, Site> siteMapping) {
        this.siteMapping = siteMapping;
    }

    public void setPlatformMapping(Map<Integer, AvailablePlatform> platformMapping) {
        this.platformMapping = platformMapping;
    }

    public void setIdToOperatorMapping(Map<Integer, String> idToOperatorMapping) {
        this.idToOperatorMapping = idToOperatorMapping;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public void setPairLats(Map<String, Double> pairLats) {
        System.out.println("Initialized pair latencies map!");
        this.pairLats = pairLats;
    }

    public void setWorkflow(String workflowFile) {
        this.workflowFile = workflowFile;
        System.out.println("Initialized workflow: " + workflowFile);
    }

    public void setRoot(Graph root) {
        this.root = root;
    }

    public void setDatasetFile(String datasetFile) {
        this.datasetFile = datasetFile;
    }

    public void setFitIoTDataset(String datasetFile) {
        this.datasetFile = datasetFile;
        System.out.println("Initialized Fit IoT dataset: " + datasetFile);
    }

    public void loadFitIotDataset() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        this.fitIoToperatorStatistics = mapper.readValue(
                new File(datasetFile),
                new TypeReference<>() {
                }
        );
    }

    public void loadDataset() {
        this.fitIoToperatorStatistics = new HashMap<>();
        try (FileInputStream fis = new FileInputStream(datasetFile);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);

            // Get device names from the header row (first row)
            Row headerRow = sheet.getRow(0);
            Map<Integer, String> deviceColumns = new HashMap<>();

            // Skip the first cell (empty corner cell)
            for (int i = 1; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    String deviceName = cell.getStringCellValue().trim();
                    deviceColumns.put(i, deviceName);
                }
            }

            // Process each row (starting from row 1)
            for (int rowNum = 1; rowNum < sheet.getLastRowNum() + 1; rowNum++) {
                Map<String, Map<String, OperatorStatistics>> deviceStats = new HashMap<>();
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                // Get operator name from first column
                Cell operatorCell = row.getCell(0);
                if (operatorCell == null) continue;

                String operatorName = operatorCell.getStringCellValue().trim();

                // Process each device column
                for (int colNum : deviceColumns.keySet()) {
                    Cell valueCell = row.getCell(colNum);
                    if (valueCell == null) continue;

                    String deviceName = deviceColumns.get(colNum);
                    Double value = valueCell.getNumericCellValue();

                    // Create an operator statistics object for this operator-device pair
                    // No throughput is set.
                    OperatorStatistics stats = new OperatorStatistics(value, 0L);
                    Map<String, OperatorStatistics> platformStats = Map.of("default", stats);
                    deviceStats.put(deviceName, platformStats);
                }

                this.fitIoToperatorStatistics.put(operatorName, deviceStats);
            }
        } catch (IOException e) {
            System.err.println("Error reading Excel file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error while parsing Excel: " + e.getMessage());
        }
    }

    /**
     * Parses the workflow JSON file using Gson's generic JSON objects and builds:
     * 1. operatorNeighbors: a mapping from each operator to its list of downstream neighbors.
     * 2. operatorLevels: a mapping from each operator to its level (distance from a source operator).
     */
    public void loadWorkflow() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(this.workflowFile)) {
            // Parse the JSON file into a JsonObject.
            JsonObject jsonObj = gson.fromJson(reader, JsonObject.class);

            // Initialize operators and data structures.
            JsonArray operatorsArray = jsonObj.getAsJsonArray("operators");
            for (JsonElement opElem : operatorsArray) {
                JsonObject opObj = opElem.getAsJsonObject();
                String opName = opObj.get("name").getAsString();
                operatorNeighbors.put(opName, new ArrayList<>());
                operatorPredecessors.put(opName, new ArrayList<>());
                inDegree.put(opName, 0);
            }

            // Build graph connections from the "operatorConnections" array.
            JsonArray connectionsArray = jsonObj.getAsJsonArray("operatorConnections");
            for (JsonElement conElem : connectionsArray) {
                JsonObject conObj = conElem.getAsJsonObject();
                String fromOperator = conObj.get("fromOperator").getAsString();
                String toOperator = conObj.get("toOperator").getAsString();

                // Update downstream neighbors and upstream predecessors.
                operatorNeighbors.get(fromOperator).add(toOperator);
                operatorPredecessors.get(toOperator).add(fromOperator);
                // Increment the in-degree for the destination operator.
                inDegree.put(toOperator, inDegree.get(toOperator) + 1);
            }

            // Temporary map to hold each operator's level.
            Map<String, Integer> tempLevels = new HashMap<>();
            Queue<String> queue = new LinkedList<>();

            // Enqueue all source operators (with inDegree == 0) and assign level 0.
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    tempLevels.put(entry.getKey(), 0);
                    queue.offer(entry.getKey());
                }
            }

            // Process the graph in topological order.
            while (!queue.isEmpty()) {
                String currentOp = queue.poll();
                int currentLevel = tempLevels.get(currentOp);
                // Process each downstream neighbor.
                for (String neighbor : operatorNeighbors.get(currentOp)) {
                    int newLevel = currentLevel + 1;
                    // Update the neighbor's level if this path gives a higher level.
                    if (!tempLevels.containsKey(neighbor) || newLevel > tempLevels.get(neighbor)) {
                        tempLevels.put(neighbor, newLevel);
                    }
                    // Decrement inDegree; if it becomes zero, add the neighbor to the queue.
                    inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                    if (inDegree.get(neighbor) == 0) {
                        queue.offer(neighbor);
                    }
                }
            }

            // Build the reversed operatorLevels map (level -> list of operators).
            for (Map.Entry<String, Integer> entry : tempLevels.entrySet()) {
                int level = entry.getValue();
                String operator = entry.getKey();
                operatorLevels.computeIfAbsent(level, k -> new ArrayList<>()).add(operator);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Map<String, Tuple<String, String>> getPlacement(Graph flow) {
        Map<String, Tuple<String, String>> placement = new HashMap<>();

        for (Vertex v : flow.getVertices()) {
            String operatorName = this.idToOperatorMapping.get(v.getOperatorId());
            String site = this.siteMapping.get(v.getSite()).getSiteName();

            String platform = "default";
            if (!this.isSimDataset) {
                platform = this.platformMapping.get(v.getPlatform()).getPlatformName();
            }

            placement.put(operatorName, new Tuple<>(platform, site));
        }

        return placement;
    }

    public void printStatsForGraph(Graph flow) {
        System.out.println("Graph Signature: " + flow.getSignatureDashed());
        System.out.println("Latency: " + getRealCost(flow));
        System.out.println("Migration Cost: " + getMigrationCost(flow));
    }

    @Override
    public int calculateCost(Graph flow) {
        int realCost = getRealCost(flow);
        int migrationCost = getMigrationCost(flow);
//        System.out.println((realCost + migrationCost) / (double) 1000);
        return realCost + migrationCost;
    }

    @Override
    public int getMigrationCost(Graph flow) {
        ArrayList<String> rootSites = root.getVertices().stream()
                .map(v -> siteMapping.get(v.getSite()).getSiteName())
                .collect(Collectors.toCollection(ArrayList::new));

        ArrayList<String> currentSites = flow.getVertices().stream()
                .map(v -> siteMapping.get(v.getSite()).getSiteName())
                .collect(Collectors.toCollection(ArrayList::new));

        double migrationCost = 0.0;
        for (int i = 0; i < rootSites.size(); i++) {
            String rootDevice = rootSites.get(i);
            String currentDevice = currentSites.get(i);
            if (!rootDevice.equals(currentDevice)) {
                String key = rootDevice + ":" + currentDevice;
                double pathLatency = pairLats.get(key);

//                if (isSimDataset) migrationCost = Math.max(migrationCost, pathLatency);
//                else migrationCost += pathLatency;

                migrationCost = Math.max(migrationCost, pathLatency);

                // When running dagstar with ETL, STATS etc., the migration cost is the max
                // When running with the Fit IoT dataset, the migration cost is the sum of all latencies.
//                migrationCost += pathLatency;
//                migrationCost = Math.max(migrationCost, pathLatency);
            }
        }
        return (int) (migrationCost * COST_MULTIPLIER);
    }

    /**
     * Returns the Stats object for the given operator, device and platform.
     * If no platform is provided, that means that only one platform is available for the device category.
     * @throws IllegalArgumentException if any part of the key path is missing.
     */
    public Optional<OperatorStatistics> getStats(String operator, String deviceCategory, String platform) {

        if (platform == null) {
            return Optional.ofNullable(fitIoToperatorStatistics.get(operator))
                    .map(devices -> devices.get(deviceCategory))
                    .flatMap(platforms -> platforms.values().stream().findFirst());
        }

        Map<String, Map<String, OperatorStatistics>> siteStats = fitIoToperatorStatistics.get(operator);
        Map<String, OperatorStatistics> platformStats = siteStats.get(deviceCategory);

        return Optional.ofNullable(fitIoToperatorStatistics.get(operator))
                .map(devices -> devices.get(deviceCategory))
                .map(platforms -> platforms.get(platform));
    }

    @Override
    public int getRealCost(Graph flow) {
        Map<String, Tuple<String, String>> placement = getPlacement(flow);
        HashMap<String, Double> operatorCosts = new HashMap<>();

        for (Map.Entry<Integer, List<String>> opsOfLevel : operatorLevels.entrySet()) {
            int level = opsOfLevel.getKey();
            for (String operator : opsOfLevel.getValue()) {
                if (level == 0) {
                    // This is a source operator.
                    String sourceSite = placement.get(operator)._2;
                    String sourcePlatform = placement.get(operator)._1;

                    String siteCategory = sourceSite;
                    if (!isSimDataset) siteCategory = sourceSite.contains("rpi3") ? "rpi3" : "a8";

                    Optional<OperatorStatistics> statsOpt = getStats(operator, siteCategory, sourcePlatform);
                    if (statsOpt.isEmpty()) {
                        return Integer.MAX_VALUE;
                    }
                    OperatorStatistics stats = statsOpt.get();

                    double latency = isSimDataset ? stats.getLatency() : stats.getLatency() / 1000.0;
                    operatorCosts.put(operator, latency);
                } else {
                    // This is an intermediate operator.
                    String operatorSite = placement.get(operator)._2;
                    String operatorPlatform = placement.get(operator)._1;

                    String siteCategory = operatorSite;
                    if (!isSimDataset) siteCategory = operatorSite.contains("rpi3") ? "rpi3" : "a8";

                    Optional<OperatorStatistics> statsOpt = getStats(operator, siteCategory, operatorPlatform);
                    if (statsOpt.isEmpty()) {
                        return Integer.MAX_VALUE;
                    }

                    OperatorStatistics stats = statsOpt.get();
                    double operatorCost = isSimDataset ? stats.getLatency() : stats.getLatency() / 1000.0;
                    double maxCost = 0;
                    switch (aggregationStrategy) {
                        case MAX:
                            for (String predecessor : operatorPredecessors.get(operator)) {
                                String predecessorSite = placement.get(predecessor)._2;
                                String edgeKey = predecessorSite + ":" + operatorSite;
                                double predecessorCost = operatorCosts.get(predecessor);
                                double edgeLat = pairLats.get(edgeKey);
                                double totalCost = predecessorCost + edgeLat;
                                maxCost = Math.max(maxCost, totalCost);
                            }
                            operatorCosts.put(operator, maxCost + operatorCost);
                            break;
                        case SUM:
                            double sumCost = 0;
                            for (String predecessor : operatorPredecessors.get(operator)) {
                                String predecessorSite = placement.get(predecessor)._2;
                                String edgeKey = predecessorSite + ":" + operatorSite;
                                double predecessorCost = operatorCosts.get(predecessor);
                                double edgeLat = pairLats.get(edgeKey);
                                double totalCost = predecessorCost + edgeLat;
                                sumCost += totalCost;
                            }
                            operatorCosts.put(operator, sumCost + operatorCost);
                            break;
                    }
                }
            }
        }
        return (int) (operatorCosts.get("sink") * COST_MULTIPLIER);
    }

    /**
     * Simple value object for each (latency, throughput) pair.
     * Jackson will populate the public fields automatically.
     */
    public static class OperatorStatistics {
        public double latency;
        public long throughput;

        @JsonProperty("latency_std")
        public double latencyStd;

        @JsonProperty("throughput_std")
        public long throughputStd;

        public OperatorStatistics() {
        }

        public OperatorStatistics(double latency, long throughput) {
            this.latency = latency;
            this.throughput = throughput;
        }

        public double getLatency() {
            return latency;
        }

        public double getLatencyDividedBy1000() {
            return latency / 1000;
        } // Convert to milliseconds

        public double getThroughput() {
            return throughput;
        }

        @Override
        public String toString() {
            return "Stats{" +
                    "latency=" + latency +
                    ", throughput=" + throughput +
                    '}';
        }
    }

    public static class WorkflowStatistics {
        private final double latency;
        private final double throughput;
        private final long networkUsage;

        public WorkflowStatistics(double latency,
                                  double throughput,
                                  long networkUsage) {
            this.latency = latency;
            this.throughput = throughput;
            this.networkUsage = networkUsage;
        }

        public double getLatency() {
            return latency;
        }

        public long getNetworkUsage() {
            return networkUsage;
        }

        public double getThroughput() {
            return throughput;
        }
    }

    /**
     * Enum representing different strategies for aggregating costs
     * when calculating the total cost across multiple operators.
     */
    public enum AggregationStrategy {
        MAX,        // Takes the maximum cost path (critical path)
        SUM,        // Sums all operator costs
    }
}