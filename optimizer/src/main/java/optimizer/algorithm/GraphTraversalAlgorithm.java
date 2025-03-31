package optimizer.algorithm;

import core.exception.OptimizerException;
import core.structs.BoundedPriorityQueue;
import optimizer.OptimizationResourcesBundle;
import optimizer.cost.CostEstimator;
import optimizer.plan.OptimizationPlan;
import java.util.logging.Logger;

import java.util.List;
import java.util.concurrent.ExecutorService;

public interface GraphTraversalAlgorithm {

    /**
     * Method used to provide the {@link GraphTraversalAlgorithm} with the necessary resources.
     *
     * @param bundle         A bundle of resources that can be used by the algorithm.
     * @param validPlans      A thread-safe and bounded priority queue that can be used to offer valid plans.
     * @param rootPlan      The root plan of the optimization.
     * @param executorService The executor service that can be used to run tasks in parallel.
     * @param costEstimator The cost estimator that can be used to estimate the cost of a plan.
     * @param logger        The logger that can be used to log messages.
     */
    default void setup(OptimizationResourcesBundle bundle, BoundedPriorityQueue<OptimizationPlan> validPlans, OptimizationPlan rootPlan,
                       ExecutorService executorService, CostEstimator costEstimator, Logger logger) throws OptimizerException {

    }

    //Optional bundled method
    default void setup(OptimizationResourcesBundle bundle) throws OptimizerException {
    }

    /**
     * Applies the logic of this algorithm to the input graph.
     */
    void doWork();

    /**
     * Cleans all state.
     */
    void teardown();

    /**
     * Unique identified of each algorithm.
     *
     * @return A List of Strings that uniquely identify an implementation (e.g. ExhaustiveAlgorithm).
     */
    @SuppressWarnings("unused")
    List<String> aliases();
}
