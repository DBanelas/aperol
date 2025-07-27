package optimizer.algorithm.newalgs;

import optimizer.algorithm.aggregators.ResultAggregator;
import optimizer.algorithm.taskiterators.TaskIterator;

public interface PlanBasedAlgorithm {
    void setup(TaskIterator taskIterator,
               ResultAggregator resultAggregator,
               AlgorithmTerminationPredicate terminationPredicate);
    void generateTasks();
    OptimizationResult aggregateResults();
}
