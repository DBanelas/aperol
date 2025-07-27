package optimizer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.parser.network.AvailablePlatform;
import core.parser.network.Site;
import core.structs.Tuple;
import optimizer.algorithm.cost.PlanCostEstimatorInterface;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class WeightedFitIoTCostEstimator implements PlanCostEstimatorInterface {

    public static final int COST_MULTIPLIER = 1_000;
    private double minMigrationCost = 0.0;
    private double maxMigrationCost = 0.0;

    private double minLatency = 0.0;
    private double maxLatency = 0.0;

    private double minThroughput = 0.0;
    private double maxThroughput = 0.0;

    private double minNetworkUsage = 0.0;
    private double maxNetworkUsage = 0.0;

    private double wT; // Weight for throughput
    private double wL; // Weight for latency
    private double wN; // Weight for network usage
    private double wM; // Weight for migration cost


    private final Map<String, Integer> tupleTypeToSizeMapping = Map.of(
            "source", 24,
            "redis-source", 24,
            "aggregation", 8,
            "map", 24,
            "filter", 24,
            "join", 40
    );
    private Graph root;
    private WorkflowStatistics rootStats;
    private Map<Integer, String> idToOperatorMapping;
    private Map<Integer, Site> siteMapping;
    private Map<Integer, AvailablePlatform> platformMapping;
    private String datasetFile;
    private Map<String, Double> pairLats;
    private Map<String, Map<String, Map<String, OperatorStatistics>>> operatorStatistics;

    public WeightedFitIoTCostEstimator(double[] weightVec) {
        this.wT = weightVec[0];
        this.wL = weightVec[1];
        this.wN = weightVec[2];
        this.wM = weightVec[3];
    }

    @Override
    public int calculateCost(Graph flow) {
        // If any operator's stats are missing, return max cost
        double realCost = getRealCost(flow);
        if (realCost == Integer.MIN_VALUE) {
            return Integer.MIN_VALUE; // Return max cost if any operator's stats are missing
        }

        double migrationCost = getMigrationCost(flow);
        double normMigrationCost = COST_MULTIPLIER * normOther(migrationCost / COST_MULTIPLIER, this.minMigrationCost, this.maxMigrationCost);
        return (int) ((realCost + this.wM * normMigrationCost));
    }



    public void printStatsForGraph(Graph flow) {
        WorkflowStatistics stats = getGraphStats(flow)
                .orElseThrow(() -> new IllegalArgumentException("No stats available for the graph."));

        double migrationCost = getMigrationCost(flow);

        System.out.println("Graph Stats:");
        System.out.println("  Latency: " + stats.getLatency() + " ms");
        System.out.println("  Throughput: " + stats.getThroughput() + " tuples/s");
        System.out.println("  Network Usage: " + stats.getNetworkUsage() + " Bytes * s");
        System.out.println("  Metric Score: " + getRealCost(flow));
        System.out.println("  Migration Cost: " + migrationCost);
    }

    @Override
    public int getMigrationCost(Graph flow) {
        double totalMigrationCost = 0.0;
        for (Vertex v : flow.getVertices()) {
            int operatorID = v.getOperatorId();
            String siteName = this.siteMapping.get(v.getSite()).getSiteName();

            String rootSiteName = this.siteMapping
                    .get(this.root.getVertex(operatorID).getSite())
                    .getSiteName();

            String queryKey = rootSiteName + ":" + siteName;
            totalMigrationCost += this.pairLats.get(queryKey);
        }

        return (int) (COST_MULTIPLIER * totalMigrationCost);
    }


    public double[] getRealCostDimensions(Graph flow) {
        Optional<WorkflowStatistics> candidateStatsOpt = getGraphStats(flow);
        if (candidateStatsOpt.isEmpty()) {
            throw new IllegalStateException("No stats for given plan!");
        }

        WorkflowStatistics candidateStats = candidateStatsOpt.get();
        double candidateLatency = candidateStats.getLatency();
        double candidateThroughput = candidateStats.getThroughput();
        double candidateNetworkUsage = candidateStats.getNetworkUsage();
        double migrationCost = (double) getMigrationCost(flow) / COST_MULTIPLIER;

        return new double[]{
                candidateThroughput,
                candidateLatency,
                candidateNetworkUsage,
                migrationCost
        };
    }

    public double[] getWeightedNormalizedCostDimensions(Graph flow) {
        Optional<WorkflowStatistics> candidateStatsOpt = getGraphStats(flow);
        if (candidateStatsOpt.isEmpty()) {
            throw new IllegalStateException("No stats for given plan!");
        }

        WorkflowStatistics candidateStats = candidateStatsOpt.get();
        double candidateLatency = candidateStats.getLatency();
        double normCandidateLatency = normOther(candidateLatency, this.minLatency, this.maxLatency);

        double candidateThroughput = candidateStats.getThroughput();
        double normCandidateThroughput = normThroughput(candidateThroughput, this.minThroughput, this.maxThroughput);

        double candidateNetworkUsage = candidateStats.getNetworkUsage();
        double normCandidateNetworkUsage = normOther(candidateNetworkUsage, this.minNetworkUsage, this.maxNetworkUsage);

        double migrationCost = (double) getMigrationCost(flow) / COST_MULTIPLIER;
        double normMigrationCost = normOther(migrationCost, this.minMigrationCost, this.maxMigrationCost);

        return new double[]{
                wT * normCandidateThroughput,
                wL * normCandidateLatency,
                wN * normCandidateNetworkUsage,
                wM * normMigrationCost
        };
    }

    public double[] getNormalizedCostDimensions(Graph flow) {
        Optional<WorkflowStatistics> candidateStatsOpt = getGraphStats(flow);
        if (candidateStatsOpt.isEmpty()) {
            throw new IllegalStateException("No stats for given plan!");
        }

        WorkflowStatistics candidateStats = candidateStatsOpt.get();
        double candidateLatency = candidateStats.getLatency();
        double normCandidateLatency = normOther(candidateLatency, this.minLatency, this.maxLatency);

        double candidateThroughput = candidateStats.getThroughput();
        double normCandidateThroughput = normThroughput(candidateThroughput, this.minThroughput, this.maxThroughput);

        double candidateNetworkUsage = candidateStats.getNetworkUsage();
        double normCandidateNetworkUsage = normOther(candidateNetworkUsage, this.minNetworkUsage, this.maxNetworkUsage);

        double migrationCost = (double) getMigrationCost(flow) / COST_MULTIPLIER;
        double normMigrationCost = normOther(migrationCost, this.minMigrationCost, this.maxMigrationCost);

        return new double[]{
                normCandidateThroughput,
                normCandidateLatency,
                normCandidateNetworkUsage,
                normMigrationCost
        };
    }

    public Optional<WorkflowStatistics> getGraphStats(Graph flow) {
        /* ---- 0.  Book-keeping ---- */
        Map<Vertex, Integer> inDeg = new HashMap<>();
        Map<Vertex, List<Vertex>> preds = new HashMap<>();
        for (Vertex v : flow.getVertices()) {
            inDeg.put(v, 0);
            preds.put(v, new ArrayList<>());
        }
        for (Vertex u : flow.getVertices())
            for (Vertex v : u.getAdjVertices()) {
                inDeg.merge(v, 1, Integer::sum);
                preds.get(v).add(u);
            }

        /* ---- 1.  Topological order (Kahn) ---- */
        Deque<Vertex> q = new ArrayDeque<>();
        inDeg.forEach((v, deg) -> {
            if (deg == 0) q.add(v);
        });

        List<Vertex> topo = new ArrayList<>(flow.getVertices().size());
        while (!q.isEmpty()) {
            Vertex v = q.remove();
            topo.add(v);
            for (Vertex nxt : v.getAdjVertices())
                if (inDeg.merge(nxt, -1, Integer::sum) == 0) q.add(nxt);
        }
        if (topo.size() != flow.getVertices().size())
            throw new IllegalArgumentException("Graph is not a DAG.");

        /* ---------- 2.  DP sweep: latency & throughput ---------- */
        Map<Vertex, Double> bestLatency = new HashMap<>();
        double globalMinThroughput = Double.POSITIVE_INFINITY;
        long networkUsage = 0;

        for (Vertex v : topo) {
            String operatorName = idToOperatorMapping.get(v.getOperatorId());
            String siteName = siteMapping.get(v.getSite()).getSiteName();
            String siteCategory = siteName.contains("rpi3") ? "rpi3" : "a8";
            AvailablePlatform nila = this.platformMapping.get(v.getPlatform());
            String platformName = nila.getPlatformName();

            Optional<OperatorStatistics> ownStats = getStats(operatorName, siteCategory, platformName);
            if (ownStats.isEmpty()) {
                return Optional.empty(); // If any operator's stats are missing, return max latency
            }

            globalMinThroughput = Math.min(globalMinThroughput, ownStats.get().getThroughput());

            // Default is 0 to accommodate for the sink. We do not count the sink's network usage.
            // The network usage should be increased only when the receiving operator is placed on a different site
            // than the sending operator
            for (Vertex adj : v.getAdjVertices()) {
                String adjSiteName = siteMapping.get(adj.getSite()).getSiteName();
                if (!siteName.equals(adjSiteName)) {
                    networkUsage += (long) (ownStats.get().getThroughput()
                            * tupleTypeToSizeMapping.getOrDefault(operatorName, 0));
                }
            }

            double vLatency = ownStats.get().getLatencyDividedBy1000();
            double bestPredLat = 0.0;
            for (Vertex p : preds.get(v)) {
                /* edge contributions */
                String pSiteName = siteMapping.get(p.getSite()).getSiteName();
                String networkLatencyQueryKey = pSiteName + ":" + siteName;
                double linkLatency = this.pairLats.get(networkLatencyQueryKey);

                /* candidate latency up to v via p */
                double candLat = bestLatency.get(p) + linkLatency;

                /* update the best latency */
                bestPredLat = Math.max(bestPredLat, candLat);
            }
            /* final numbers at v */
            bestLatency.put(v, bestPredLat + vLatency);
        }

        /* ---- 3.  Pick max among sinks ---- */
        double criticalLatency = 0.0;
        for (Vertex v : flow.getVertices())
            if (v.getAdjVertices().isEmpty())
                criticalLatency = Math.max(criticalLatency, bestLatency.get(v));

        return Optional.of(new WorkflowStatistics(criticalLatency, globalMinThroughput, networkUsage));
    }

    private double normThroughput(double s, double sMin, double sMax) {
        if (s <= sMin) return 0.0;
        if (s >= sMax) return 1.0;
        return (s - sMin) / (sMax - sMin);
    }

    private double normOther(double s, double sMin, double sMax) {
        if (s <= sMin) return 1.0;
        if (s >= sMax) return 0.0;
        return (sMax - s) / (sMax - sMin);
    }

    @Override
    public int getRealCost(Graph flow) {
        Optional<WorkflowStatistics> candidateStatsOpt = getGraphStats(flow);
        if (candidateStatsOpt.isEmpty()) {
            return Integer.MIN_VALUE; // If any operator's stats are missing, return max cost
        }

        WorkflowStatistics candidateStats = candidateStatsOpt.get();
        double candidateLatency = candidateStats.getLatency();
        double normCandidateLatency = normOther(candidateLatency, this.minLatency, this.maxLatency);

        double candidateThroughput = candidateStats.getThroughput();
        double normCandidateThroughput = normThroughput(candidateThroughput, this.minThroughput, this.maxThroughput);

        double candidateNetworkUsage = candidateStats.getNetworkUsage();
        double normCandidateNetworkUsage = normOther(candidateNetworkUsage, this.minNetworkUsage, this.maxNetworkUsage);

        return (int) (COST_MULTIPLIER * (this.wT * normCandidateThroughput
                + this.wL * normCandidateLatency
                + this.wN * normCandidateNetworkUsage));
    }

    public void loadDataset() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        this.operatorStatistics = mapper.readValue(
                new File(datasetFile),
                new TypeReference<>() {
                }
        );
    }

    /**
     * Returns the Stats object for the given operator, device and platform.
     *
     * @throws IllegalArgumentException if any part of the key path is missing.
     */
    public Optional<OperatorStatistics> getStats(String operator, String siteCategory, String platform) {
        return Optional.ofNullable(operatorStatistics.get(operator))
                .map(devices -> devices.get(siteCategory))
                .map(platforms -> platforms.get(platform));
    }

    public void setRoot(Graph root) {
        this.root = root;
        Optional<WorkflowStatistics> rootStatsOpt = getGraphStats(root);
        if (rootStatsOpt.isEmpty()) {
            throw new IllegalArgumentException("Root graph has no stats available.");
        }
        this.rootStats = rootStatsOpt.get();

        // Now set the max migration cost based on the pairLats
        double maxNetworkLatency = pairLats.values().stream()
                .max(Double::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("No pair latencies available."));

        this.minMigrationCost = 0.0;
        this.maxMigrationCost = root.getVertices().size() * maxNetworkLatency;

        this.minThroughput = this.operatorStatistics.values().stream()
                .flatMap(deviceMap -> deviceMap.values().stream())
                .flatMap(platformMap -> platformMap.values().stream())
                .mapToDouble(OperatorStatistics::getThroughput)
                .min()
                .orElse(0.0);

        // Set the max throughput as the minimum throughput of the throughputs that belong to rpi3/java pairs
        this.maxThroughput = this.operatorStatistics.values().stream()
                .flatMap(d -> d.entrySet().stream())
                .filter(d -> d.getKey().equals("rpi3"))
                .flatMap(d -> d.getValue().entrySet().stream())
                .filter(p -> p.getKey().equals("java"))
                .mapToDouble(e -> e.getValue().getThroughput())
                .min()
                .orElse(0.0);

        this.minLatency = this.operatorStatistics.values().stream()
                .flatMap(d -> d.entrySet().stream())
                .filter(d -> d.getKey().equals("rpi3"))
                .flatMap(d -> d.getValue().entrySet().stream())
                .filter(p -> p.getKey().equals("java"))
                .mapToDouble(e -> e.getValue().getLatencyDividedBy1000())
                .sum();

        this.maxLatency = calculateMaxLatency();
        this.minNetworkUsage = 0.0; // All operators placed on the same device, no network in between.
        this.maxNetworkUsage = calculateMaxNetworkUsage();
    }

    private double calculateMaxNetworkUsage() {
        // Get the best throughput for all operators
        HashMap<String, Double> bestThroughputs = new HashMap<>();
        for (Map.Entry<String, Map<String, Map<String, OperatorStatistics>>> entry : this.operatorStatistics.entrySet()) {
            String operatorName = entry.getKey();
            double bestThroughput = getBestThroughputForOperator(operatorName);
            bestThroughputs.put(operatorName, bestThroughput);
        }

        // Calculate the maximum network usage based on the best throughput and tuple sizes
        double maxNetworkUsage = 0.0;
        for (Map.Entry<String, Double> entry : bestThroughputs.entrySet()) {
            String operatorName = entry.getKey();
            double throughput = entry.getValue();
            // The Default value of 0 handles the sink case. We do not want to count the sink's network usage.
            int tupleSize = this.tupleTypeToSizeMapping.getOrDefault(operatorName, 0);
            maxNetworkUsage += throughput * tupleSize;
        }

        return maxNetworkUsage;
    }

    private double getWorstNetworkEdgeFromToType(String fromType, String toType) {
        double worstLatency = 0.0;
        for (Map.Entry<String, Double> entry : this.pairLats.entrySet()) {
            String[] sites = entry.getKey().split(":");
            String leftType = sites[0].split("-")[0];
            String rightType = sites[1].split("-")[0];

            if (leftType.equals(fromType) && rightType.equals(toType)) {
                worstLatency = Math.max(worstLatency, entry.getValue());
            }
        }
        return worstLatency;
    }

    private double getBestThroughputForOperator(String operatorName) {
        return this.operatorStatistics.get(operatorName).values().stream()
                .flatMap(e -> e.values().stream())
                .mapToDouble(OperatorStatistics::getThroughput)
                .max()
                .orElseThrow(() -> new IllegalArgumentException("No throughput available for operator: " + operatorName));
    }

    private Tuple<String, Double> getWorstProcessingLatencyForOperator(String operatorName) {

        double worstLatency = 0.0;
        String worstSite = "";
        Map<String, Map<String, OperatorStatistics>> siteMap = this.operatorStatistics.get(operatorName);

        // Iterate over device -> platforms dict
        for (Map.Entry<String, Map<String, OperatorStatistics>> siteEntry : siteMap.entrySet()) {
            Map<String, OperatorStatistics> platformMap = siteEntry.getValue();

            // Iterate over platform -> stats dict
            for (Map.Entry<String, OperatorStatistics> platformEntry : platformMap.entrySet()) {
                OperatorStatistics stats = platformEntry.getValue();

                // Keep the worst latency and where it occurred
                if (stats.getLatencyDividedBy1000() > worstLatency) {
                    worstLatency = stats.getLatencyDividedBy1000();
                    worstSite = siteEntry.getKey();
                }
            }
        }

        return new Tuple<>(worstSite, worstLatency);
    }

    private double calculateMaxLatency() {
        // Utility code to get the parents' map of vertices in the root graph
        Map<Vertex, List<Vertex>> parentsMapVertices = this.root.getParentsMap();
        Map<String, List<String>> parentsMapNames = new HashMap<>();
        for (Map.Entry<Vertex, List<Vertex>> entry : parentsMapVertices.entrySet()) {
            Vertex vertex = entry.getKey();
            List<Vertex> parents = entry.getValue();
            List<String> parentNames = new ArrayList<>();
            for (Vertex parent : parents) {
                parentNames.add(idToOperatorMapping.get(parent.getOperatorId()));
            }
            parentsMapNames.put(idToOperatorMapping.get(vertex.getOperatorId()), parentNames);
        }

        Map<String, Tuple<String, Double>> pathCost = new HashMap<>();
        for (Vertex v : this.root.getTopologicalOrder()) {
            String currentOperator = idToOperatorMapping.get(v.getOperatorId());
            Tuple<String, Double> base = getWorstProcessingLatencyForOperator(currentOperator);
            double baseCost = base._2;
            String baseSiteType = base._1;

            // Operator has no parents, so its latency is just its own processing latency
            List<String> parents = parentsMapNames.get(currentOperator);
            if (parents.isEmpty()) pathCost.put(currentOperator, base);
            else {
                double worstPathToCurrentOperator = baseCost;
                String currentOperatorSiteType = baseSiteType;
                for (String parent : parents) {
                    Tuple<String, Double> parentCostInfo = pathCost.get(parent);
                    double parentCost = parentCostInfo._2;
                    String parentSiteType = parentCostInfo._1;
                    double worstNetworkEdgeLatency = getWorstNetworkEdgeFromToType(parentSiteType, baseSiteType);

                    if (parentCost + worstNetworkEdgeLatency > worstPathToCurrentOperator) {
                        worstPathToCurrentOperator = parentCost + worstNetworkEdgeLatency;
                        currentOperatorSiteType = parentSiteType;
                    }
                }
                pathCost.put(currentOperator, new Tuple<>(currentOperatorSiteType, worstPathToCurrentOperator + baseCost));
            }
        }
        return pathCost.values().stream()
                .mapToDouble(stringDoubleTuple -> stringDoubleTuple._2)
                .max()
                .orElseThrow(() -> new IllegalStateException("Cannot calculate the max latency of the pathCost map"));
    }

    public void setWeights(double wT, double wL, double wN, double wM) {
        this.wT = wT;
        this.wL = wL;
        this.wN = wN;
        this.wM = wM;
    }

    public void setDatasetFile(String datasetFile) {
        this.datasetFile = datasetFile;
    }

    public void setPairLats(Map<String, Double> pairLats) {
        this.pairLats = pairLats;
    }

    public void setIdToOperatorMapping(Map<Integer, String> idToOperatorMapping) {
        this.idToOperatorMapping = idToOperatorMapping;
    }

    public void setSiteMapping(Map<Integer, Site> siteMapping) {
        this.siteMapping = siteMapping;
    }

    public void setPlatformMapping(Map<Integer, AvailablePlatform> platformMapping) {
        this.platformMapping = platformMapping;
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
}
