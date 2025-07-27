package optimizer.algorithm.aggregators;

import core.structs.Tuple;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.newalgs.OptimizationResult;

public interface ResultAggregator {
    void addResult(Tuple<Graph, Integer> result);
    int getBestScore();
    OptimizationResult getResults();
}
