package optimizer.algorithm.aggregators;

import core.structs.Tuple;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.newalgs.OptimizationResult;

import java.util.concurrent.atomic.AtomicReference;

public class MinAggregator implements ResultAggregator {

    /* These two variables represent the state of the aggregator */

    /**
     * Thread safe reference to the best graph object
     */
    private final AtomicReference<Graph> bestGraph;

    /**
     * Number of completed tasks in the aggregator
     */
    private long completedTasks;

    /**
     * Creates a new aggregator
     * @param rootFlow The root flow
     */
    public MinAggregator(Graph rootFlow) {
        this.completedTasks = 0;
        this.bestGraph = new AtomicReference<>(rootFlow);
    }

    @Override
    public int getBestScore() {
        return bestGraph.get().getCost();
    }

    /**
     * Returns an OptimizationResult object with the state of the class
     * at the end of the execution
     * @return OptimizationResult
     */
    @Override
    public OptimizationResult getResults() {
        return new OptimizationResult()
                .withBestPlan(bestGraph.get())
                .withCompletedTasks(completedTasks);
    }

    /**
     * Method to aggregate a given result into the state of the class.
     * Update the best graph if the given graph has a higher score
     * @param result The result to aggregate
     */
    @Override
    public void addResult(Tuple<Graph, Integer> result) {
        Graph resultGraph = result._1;
        int resultCompletedTasks = result._2;
        int score = resultGraph.getCost();
        this.completedTasks += resultCompletedTasks;
        if (score < bestGraph.get().getCost()) {
            bestGraph.set(resultGraph);
        }
    }
}
