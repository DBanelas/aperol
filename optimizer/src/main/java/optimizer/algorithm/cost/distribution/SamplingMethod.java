package optimizer.algorithm.cost.distribution;

import optimizer.algorithm.graph.Graph;

public interface SamplingMethod {
    double getSample(Graph flow, int networkSize, boolean isMetricScore);
}
