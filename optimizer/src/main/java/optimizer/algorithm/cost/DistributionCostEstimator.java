package optimizer.algorithm.cost;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import optimizer.algorithm.cost.distribution.Distribution;
import optimizer.algorithm.cost.distribution.Histogram;
import optimizer.algorithm.cost.distribution.SamplingMethod;
import optimizer.algorithm.graph.Graph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DistributionCostEstimator implements CostEstimatorIface {
    private int networkSize;
    private String workflowName;
    private String distributionsDirectory;
    private HashMap<String, SamplingMethod> samplingMethods;

    public DistributionCostEstimator() {
    }

    public void loadDistributionAndHistogramData() {
        this.samplingMethods = getDistributionMap();
    }

    public void setDistributionsDirectory(String distributionsDirectory) {
        this.distributionsDirectory = distributionsDirectory;
        System.out.println("Initialized distributions directory: " + this.distributionsDirectory);
    }

    public void setNetworkSize(int networkSize) {
        this.networkSize = networkSize;
        System.out.println("Initialized network size: " + this.networkSize);
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
        System.out.println("Initialized workflow name: " + this.workflowName);
    }

    private HashMap<String, SamplingMethod> getDistributionMap() {
        HashMap<String, SamplingMethod> distMap = new HashMap<>();
        assert this.workflowName != null : "Workflow name is not set";
        distMap.putAll(getMetricScoreDistributionsFor(this.workflowName));
        distMap.putAll(getMigrationCostHistogramsFor(this.workflowName));
        return distMap;
    }

    private HashMap<String, SamplingMethod> getMetricScoreDistributionsFor(String workflowName) {
        String prefix = workflowName.toUpperCase() + "_";
        String suffix = "_METRIC";
        String filename = this.distributionsDirectory + "/" + workflowName + "_distributions.json";
        HashMap<String, SamplingMethod> distributions = parseDistributions(filename, prefix, suffix);
        return distributions;
    }

    private HashMap<String, SamplingMethod> getMigrationCostHistogramsFor(String workflowName) {
        String prefix = workflowName.toUpperCase() + "_";
        String suffix = "_MIGRATION";
        String filename = this.distributionsDirectory + "/" + workflowName + "_histograms.json";
        HashMap<String, SamplingMethod> histograms = parseHistograms(filename, prefix, suffix);
        return histograms;
    }

    public HashMap<String, SamplingMethod> parseHistograms(String filePath, String prefix, String suffix) {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            // Parse JSON into a map where the key is the network ID
            Map<String, List<Map<String, Double>>> rawData =
                    objectMapper.readValue(new File(filePath), new TypeReference<>() {});

            return buildHistograms(rawData, prefix, suffix);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private HashMap<String, SamplingMethod> buildHistograms(
            Map<String, List<Map<String, Double>>> rawData,
            String prefix,
            String suffix) {
        HashMap<String, SamplingMethod> histograms = new HashMap<>();

        for (Map.Entry<String, List<Map<String, Double>>> entry : rawData.entrySet()) {
            String networkId = entry.getKey();
            String histogramKey = prefix + networkId + suffix;
            List<Map<String, Double>> binsData = entry.getValue();

            // Create a new Histogram object
            Histogram histogram = new Histogram();

            for (Map<String, Double> binData : binsData) {
                int binStart = (int) Math.round(binData.get("bin_start"));
                int binEnd = (int) Math.round(binData.get("bin_end"));
                double probability = binData.get("probability");
                histogram.addBin(binStart, binEnd, probability);
            }
            histograms.put(histogramKey, histogram);
        }

        return histograms;
    }


    private HashMap<String, SamplingMethod> parseDistributions(String filePath, String prefix, String suffix) {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            // Parse JSON into a map where the key is the network ID
            Map<String, Map<String, Map<String, Double>>> rawData =
                    objectMapper.readValue(new File(filePath), new TypeReference<>() {});

            return buildDistributions(rawData, prefix, suffix);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private HashMap<String, SamplingMethod> buildDistributions(
            Map<String, Map<String, Map<String, Double>>> rawData,
            String prefix,
            String suffix) {
        HashMap<String, SamplingMethod> distributions = new java.util.HashMap<>();

        for (Map.Entry<String, Map<String, Map<String, Double>>> entry : rawData.entrySet()) {
            String networkId = entry.getKey();
            String distributionKey = prefix + networkId + suffix;
            Map<String, Map<String, Double>> distributionData = entry.getValue();

            // Only one distribution type per network
            for (Map.Entry<String, Map<String, Double>> distEntry : distributionData.entrySet()) {
                String jsonDistType = distEntry.getKey();
                String realDistType = "";
                Map<String, Double> params = distEntry.getValue();

                List<Double> paramList = new ArrayList<>();
                if ("norm".equals(jsonDistType)) {
                    realDistType = "normal";
                    paramList.add(params.get("loc"));
                    paramList.add(params.get("scale"));
                } else if ("lognorm".equals(jsonDistType)) {
                    realDistType = "lognorm";
                    paramList.add(params.get("s"));
                    paramList.add(params.get("loc"));
                    paramList.add(params.get("scale"));
                } else if ("gamma".equals(jsonDistType)) {
                    realDistType = "gamma";
                    paramList.add(params.get("a"));
                    paramList.add(params.get("loc"));
                    paramList.add(params.get("scale"));
                }

                distributions.put(distributionKey, new Distribution(realDistType, paramList));
            }
        }

        return distributions;
    }

    @Override
    public int calculateCost(Graph flow) {
        int realCost = getRealCost(flow);
        int migrationCost = getMigrationCost(flow);
        int score = realCost - migrationCost;
//        System.out.println("COST ESTIMATOR: " + flow.getSignatureDashed() + " is: " + score);
        return score;
    }

    @Override
    public int getMigrationCost(Graph flow) {
        String distributionKey = this.workflowName.toUpperCase() + "_" + this.networkSize + "_MIGRATION";
        SamplingMethod distribution = this.samplingMethods.get(distributionKey);
        int sample = Math.max((int) distribution.getSample(flow, this.networkSize, false), 0);
//        System.out.println("MIGRATION SCORE for " + flow.getSignatureDashed() + " is: " + sample);
        return sample;
    }

    @Override
    public int getRealCost(Graph flow) {
        String distributionKey = this.workflowName.toUpperCase() + "_" + this.networkSize + "_METRIC";
        SamplingMethod distribution = this.samplingMethods.get(distributionKey);
        int sample = (int) distribution.getSample(flow, this.networkSize, true);
//        System.out.println("METRIC SCORE for " + flow.getSignatureDashed() + " is: " + sample);
        return sample;
    }
}