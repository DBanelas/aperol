package optimizer.algorithm.cost;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import core.parser.network.Site;
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

public class DagStarCostEstimator implements CostEstimatorIface {

    /**
     * Enum representing different strategies for aggregating costs
     * when calculating the total cost across multiple operators.
     */
    public enum AggregationStrategy {
        MAX,        // Takes the maximum cost path (critical path)
        SUM,        // Sums all operator costs
    }

    private String workflowFile;
    private String datasetFile;
    private Map<String, Double> pairLats;

    private Map<String, Double> operatorDeviceLatency;
    private AggregationStrategy aggregationStrategy = AggregationStrategy.MAX;

    private final Map<String, List<String>> operatorNeighbors = new HashMap<>();
    private final Map<String, List<String>> operatorPredecessors = new HashMap<>();
    private final Map<Integer, List<String>> operatorLevels = new HashMap<>();
    private final Map<String, Integer> inDegree = new HashMap<>();

    private Map<Integer, String> idToOperatorMapping;
    private Map<Integer, Site> siteMapping;

    private Graph root;

    public DagStarCostEstimator() {
    }

    public void setSiteMapping(Map<Integer, Site> siteMapping) {
        this.siteMapping = siteMapping;
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

    public void loadDataset() {
        HashMap<String, Double> operatorDeviceLatency = new HashMap<>();

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

                    // Create key by concatenating operator name and device name
                    String key = operatorName + ":" + deviceName;
                    operatorDeviceLatency.put(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading Excel file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error while parsing Excel: " + e.getMessage());
        }

        this.operatorDeviceLatency = operatorDeviceLatency;
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
                    // Decrement inDegree; if it becomes zero, add neighbor to the queue.
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

    private Map<String, String> getPlacement(Graph flow) {
        Map<String, String> placement = new HashMap<>();

        for (Vertex v : flow.getVertices()) {
            String operatorName = idToOperatorMapping.get(v.getOperatorId());
            String operatorDevice = siteMapping.get(v.getSite()).getSiteName();
            placement.put(operatorName, operatorDevice);
        }

        return placement;
    }


    @Override
    public int calculateCost(Graph flow) {
        int realCost = getRealCost(flow);
        int migrationCost = getMigrationCost(flow);
        return realCost + migrationCost;
    }

    @Override
    public int getMigrationCost(Graph flow) {
        ArrayList<String> rootDevices = root.getVertices().stream()
                .map(v -> siteMapping.get(v.getSite()).getSiteName())
                .collect(Collectors.toCollection(ArrayList::new));

        ArrayList<String> currentDevices = flow.getVertices().stream()
                .map(v -> siteMapping.get(v.getSite()).getSiteName())
                .collect(Collectors.toCollection(ArrayList::new));

        double migrationCost = 0.0;
        for (int i = 0; i < rootDevices.size(); i++) {
            String rootDevice = rootDevices.get(i);
            String currentDevice = currentDevices.get(i);
            if (!rootDevice.equals(currentDevice)) {
                String key = rootDevice + ":" + currentDevice;
                double pathLatency = pairLats.get(key);
                migrationCost = Math.max(migrationCost, pathLatency);
            }
        }
        return (int) (migrationCost * 1000);
    }


    @Override
    public int getRealCost(Graph flow) {
        Map<String, String> placement = getPlacement(flow);
        HashMap<String, Double> operatorCosts = new HashMap<>();

        for (Map.Entry<Integer, List<String>> opsOfLevel: operatorLevels.entrySet()) {
            int level = opsOfLevel.getKey();
            for (String operator : opsOfLevel.getValue()) {
                if (level == 0) {
                    // This is a source operator.
                    String sourceDevice = placement.get(operator);
                    String key = operator + ":" + sourceDevice;
                    operatorCosts.put(operator, operatorDeviceLatency.get(key));
                } else {
                    // This is an intermediate operator.
                    String operatorDevice = placement.get(operator);
                    double maxCost = 0;
                    double operatorCost = operatorDeviceLatency.get(operator + ":" + operatorDevice);

                    switch (aggregationStrategy) {
                        case MAX:
                            for (String predecessor : operatorPredecessors.get(operator)) {
                                String predecessorDevice = placement.get(predecessor);
                                String edgeKey = predecessorDevice + ":" + operatorDevice;
                                double predecessorCost = operatorCosts.get(predecessor);
                                double edgeLat = pairLats.get(edgeKey);
                                double totalCost = predecessorCost + edgeLat;
                                if (totalCost > maxCost) {
                                    maxCost = totalCost;
                                }
                            }
                            operatorCosts.put(operator, maxCost + operatorCost);
                            break;
                        case SUM:
                            double sumCost = 0;
                            for (String predecessor : operatorPredecessors.get(operator)) {
                                String predecessorDevice = placement.get(predecessor);
                                String edgeKey = predecessorDevice + ":" + operatorDevice;
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
        return (int) (operatorCosts.get("sink") * 1000);
    }
}
