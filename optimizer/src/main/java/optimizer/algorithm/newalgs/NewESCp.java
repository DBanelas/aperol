package optimizer.algorithm.newalgs;

import core.structs.Tuple;
import optimizer.algorithm.aggregators.MaxAggregator;
import optimizer.algorithm.aggregators.MinAggregator;
import optimizer.algorithm.aggregators.ResultAggregator;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.cost.DagStarCostEstimator;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.taskiterators.ExhaustiveTaskIterator;
import optimizer.algorithm.taskiterators.TaskIterator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class NewESCp extends AbstractPlanBasedAlgorithm {

    public NewESCp(Graph rootFlow,
                        int numPlatforms,
                        int numSites,
                        Set<Integer> cloudOnlyOperatorIds,
                        Map<String, Integer> siteMappingReverse,
                        CostEstimatorIface costEstimation,
                        int timeout,
                        boolean disableStats,
                        int numThreads) {
        super(rootFlow, numPlatforms, numSites, timeout, disableStats, numThreads, 1000 * numThreads);

        // Set the target base
        int targetBase = numPlatforms * numSites;

        // Get the available action list
        ArrayList<Tuple<Integer, Integer>> actions = AlgorithmUtils.getActions(numPlatforms, numSites);

        // Calculate the number of possible plans, based on how many of the operators are fixed to the cloud.
        this.possiblePlans = BigInteger.valueOf((long) this.numPlatforms * this.numSites).pow(this.numOperators - cloudOnlyOperatorIds.size());

        TaskIterator exhaustiveTaskIterator = new ExhaustiveTaskIterator(
                rootFlow,
                cloudOnlyOperatorIds,
                siteMappingReverse,
                costEstimation,
                actions,
                possiblePlans,
                targetBase);

//        FileTaskIterator fileTaskIterator = new FileTaskIterator(rootFlow, costEstimation);

        ResultAggregator aggregator;
        if (costEstimation instanceof DagStarCostEstimator) {
            aggregator = new MinAggregator(rootFlow);
        } else {
            aggregator = new MaxAggregator(rootFlow);
        }

        AlgorithmTerminationPredicate terminationPredicate = new AlgorithmTerminationPredicate(possiblePlans);

        super.setup(exhaustiveTaskIterator, aggregator, terminationPredicate);
    }
}
