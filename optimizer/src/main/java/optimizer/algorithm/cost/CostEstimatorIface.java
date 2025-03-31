package optimizer.algorithm.cost;

import optimizer.algorithm.graph.Graph;

public interface CostEstimatorIface {
    int calculateCost(Graph flow);
    int getMigrationCost(Graph flow);
    int getRealCost(Graph flow);
}
