package optimizer.algorithm.cost;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.structs.Tuple;
import optimizer.algorithm.newalgs.competitors.DAGStar;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class OperatorDagStarCostEstimator implements OperatorCostEstimatorInterface {

    public static final int COST_MULTIPLIER = 1_000_000;
    private final String datasetFile;
    private final Map<String, Double> pairLats;
    private final boolean isSimDataset;
    private Map<String, Map<String, Map<String, OperatorDagStarCostEstimator.OperatorStatistics>>> operatorStatistics;

    public OperatorDagStarCostEstimator(String datasetFile,
                                        Map<String, Double> pairLats,
                                        boolean isSimDataset) {
        this.datasetFile = datasetFile;
        this.pairLats = pairLats;
        this.isSimDataset = isSimDataset;
        this.operatorStatistics = new HashMap<>();
        if (isSimDataset) {
            loadSimDataset();
        } else {
            try {
                loadFitIotDataset();
            } catch (IOException e) {
                throw new RuntimeException("Failed to load FIT IoT dataset: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public int getPlanTotalCost(LinkedHashMap<String, Tuple<String, String>> implementationMap) {
        return 0;
    }

    @Override
    public int getHeuristicCostForOperator(String operator) {
        return 0;
    }

    @Override
    public int getMigrationCost(String prevOp, Tuple<String, String> prevImpl, Tuple<String, String> newImpl) {
        return 0;
    }

    @Override
    public int getPlanPlatformCost(LinkedHashMap<String, Tuple<String, String>> implementationMap) {
        return 0;
    }

    @Override
    public int getOperatorAndImplementationCost(String newOp, Tuple<String, String> newImpl) {
        String site = newImpl._1;
        String platform = newImpl._2;
        String siteCategory;
        if (isSimDataset) {
            siteCategory = site;
        } else {
            siteCategory = (site.contains("rpi3") ? "rpi3" : "a8");
        }

        Optional<OperatorDagStarCostEstimator.OperatorStatistics> statsOpt = getStats(newOp, siteCategory, platform);
        return statsOpt
                .map(statistics -> (int) (COST_MULTIPLIER * statistics.getLatency()))
                .orElse(Integer.MAX_VALUE);

    }

    @Override
    public int getCommunicationCost(String site1, String site2) {
        String queryKey = site1 + ":" + site2;
        return (int) (COST_MULTIPLIER * this.pairLats.getOrDefault(queryKey, 0.0));
    }

    @Override
    public int getMinCostForOperator(String operator) {

        if (operator.equals(DAGStar.VIRTUAL_END)) return 0;

        Map<String, Map<String, OperatorDagStarCostEstimator.OperatorStatistics>> siteStats = operatorStatistics.get(operator);
        double minCost = Double.MAX_VALUE;
        for (String site : siteStats.keySet()) {
            Map<String, OperatorDagStarCostEstimator.OperatorStatistics> platformStats = siteStats.get(site);
            for (OperatorDagStarCostEstimator.OperatorStatistics stats : platformStats.values()) {
                minCost = Math.min(minCost, stats.getLatency());
            }
        }
        return (int) (COST_MULTIPLIER * minCost);
    }

    /**
     * Returns the Stats object for the given operator, device and platform.
     * If no platform is provided, that means that only one platform is available for the device category.
     *
     * @throws IllegalArgumentException if any part of the key path is missing.
     */
    public Optional<OperatorDagStarCostEstimator.OperatorStatistics> getStats(String operator, String deviceCategory, String platform) {

        if (platform == null) {
            return Optional.ofNullable(operatorStatistics.get(operator))
                    .map(devices -> devices.get(deviceCategory))
                    .flatMap(platforms -> platforms.values().stream().findFirst());
        }

        return Optional.ofNullable(operatorStatistics.get(operator))
                .map(devices -> devices.get(deviceCategory))
                .map(platforms -> platforms.get(platform));
    }

    public void loadSimDataset() {
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
                Map<String, Map<String, OperatorDagStarCostEstimator.OperatorStatistics>> deviceStats = new HashMap<>();
                // Process each device column
                for (int colNum : deviceColumns.keySet()) {
                    Cell valueCell = row.getCell(colNum);
                    if (valueCell == null) continue;

                    String deviceName = deviceColumns.get(colNum);
                    double value = valueCell.getNumericCellValue();

                    // Create an operator statistics object for this operator-device pair
                    // No throughput is set.
                    OperatorDagStarCostEstimator.OperatorStatistics stats = new OperatorDagStarCostEstimator.OperatorStatistics(value, 0L);
                    Map<String, OperatorDagStarCostEstimator.OperatorStatistics> platformStats = Map.of("platform_0", stats);
                    deviceStats.put(deviceName, platformStats);
                }

                this.operatorStatistics.put(operatorName, deviceStats);
            }
        } catch (IOException e) {
            System.err.println("Error reading Excel file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error while parsing Excel: " + e.getMessage());
        }
    }

    public void loadFitIotDataset() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        this.operatorStatistics = mapper.readValue(
                new File(datasetFile),
                new TypeReference<>() {
                }
        );
    }

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

        // Optionally, add getters if you prefer immutability or frameworks that need them
        public double getLatency() {
            return latency / 1000.0;
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
}
