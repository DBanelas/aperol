package optimizer.algorithm.cost.distribution;

import optimizer.algorithm.graph.Graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Histogram implements SamplingMethod {
    private final List<HistogramBin> bins;
    private final List<Double> probabilities;

    public Histogram() {
        this.bins = new ArrayList<>();
        this.probabilities = new ArrayList<>();
    }

    public void addBin(double binStart, double binEnd, double probability) {
        probabilities.add(probability);
        bins.add(new HistogramBin(binStart, binEnd));
    }

    public List<HistogramBin> getBins() {
        return bins;
    }

    @Override
    public double getSample(Graph flow, int networkSize, boolean isMetricScore) {
        Random sampleRandom = new Random(flow.getSignatureDashed().hashCode() + networkSize + (isMetricScore ? 1 : 2));
        Random probabilityRandom = new Random(flow.getSignatureDashed().hashCode() + networkSize + (isMetricScore ? 2 : 1));
        // Generate a random value between 0 and totalProbability
        double randomValue = probabilityRandom.nextDouble();

        double cumulativeProbability = 0.0;

        for (int i = 0; i < probabilities.size(); i++) {
            cumulativeProbability += probabilities.get(i);
            if (randomValue <= cumulativeProbability) {
                HistogramBin histogramBin = bins.get(i);
                double binStart = histogramBin.getBinStart();
                double binEnd = histogramBin.getBinEnd();
                return binStart + (binEnd - binStart) * sampleRandom.nextDouble();
            }
        }

        return -1.0;
    }
}
