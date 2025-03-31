package optimizer.algorithm.cost;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

public class iFogSimMetrics {

    private final Map<String, String> placement;
    private final double appLoopLatency;
    private final Map<String, Double> latencyPerTupleType;
    private final Map<String, Double> recsOutPerModule;
    private final Map<String, Integer> remainingDataPerDevice;
    private final double networkUsage;
    String signature;
    private final boolean invalid;

    public int getRemainingDataForOperator(String operatorName) {
        return remainingDataPerDevice.get(operatorName) / 1000;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getSignature() {
        return signature;
    }

    public double getThroughput() {
        Optional<Double> throughputOpt = recsOutPerModule.values()
                .stream()
                .min(Comparator.comparingDouble(Double::doubleValue));

        if (throughputOpt.isPresent())
            return throughputOpt.get();
        else
            throw new IllegalStateException("Cannot compute throughput of the pipeline!");
    }

    public double getLatency() {
        return latencyPerTupleType.values()
                .stream()
                .reduce(Double::sum)
                .orElseThrow(() -> new IllegalStateException("Cannot compute latency of the pipeline!"));
    }

    public Map<String, Double> getLatencyPerTupleType() {
        return latencyPerTupleType;
    }

    public double getNetworkCost() {
        return networkUsage / 10000;
    }

    public Map<String, String> getPlacement() {
        return placement;
    }

    public double getEndToEndLatency() {
        return appLoopLatency;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public iFogSimMetrics(Map<String, String> placement,
                          double appLoopLatency,
                          Map<String, Double> latencyPerTupleType,
                          Map<String, Double> recsOutPerModule,
                          Map<String, Integer> remainingDataPerDevice,
                          double networkUsage,
                          boolean invalid) {
        this.placement = placement;
        this.appLoopLatency = appLoopLatency;
        this.latencyPerTupleType = latencyPerTupleType;
        this.recsOutPerModule = recsOutPerModule;
        this.remainingDataPerDevice = remainingDataPerDevice;
        this.networkUsage = networkUsage;
        this.invalid = invalid;
    }
}
