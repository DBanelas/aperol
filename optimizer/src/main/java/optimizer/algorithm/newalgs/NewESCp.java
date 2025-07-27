package optimizer.algorithm.newalgs;

import core.structs.Tuple;
import optimizer.algorithm.aggregators.MaxAggregator;
import optimizer.algorithm.aggregators.MinAggregator;
import optimizer.algorithm.aggregators.ResultAggregator;
import optimizer.algorithm.cost.*;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.taskiterators.ExhaustiveMiddleOutTaskIterator;
import optimizer.algorithm.taskiterators.TaskIterator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NewESCp extends AbstractPlanBasedAlgorithm {

    public NewESCp(Graph rootFlow,
                        ArrayList<Tuple<Integer, Integer>> actions,
                        BigInteger possiblePlans,
                        int targetBase,
                        Set<Integer> cloudOnlyOperatorIds,
                        Map<String, Integer> siteMappingReverse,
                        Map<String, Integer> platformMappingReverse,
                        PlanCostEstimatorInterface costEstimation,
                        int timeout,
                        boolean disableStats,
                        int numThreads) {
        super(rootFlow, possiblePlans, timeout, disableStats, numThreads, 1000 * numThreads);

        PlanDagStarCostEstimator ce;
        ArrayList<Tuple<Integer, Integer>> actionsToPass = actions;
        ArrayList<Tuple<Integer, Integer>> sortedActions = new ArrayList<>(actions);
        if (costEstimation instanceof PlanDagStarCostEstimator) {
            ce = (PlanDagStarCostEstimator) costEstimation;
            if (ce.isSimDataset()) {
                actionsToPass = sortedActions;
            }
        } else if (costEstimation instanceof DistributionCostEstimator
                || costEstimation instanceof XGBRegressorCostEstimator
                || costEstimation instanceof SimulationPlanCostEstimator) {
            actionsToPass = sortedActions;
        }

        TaskIterator exhaustiveTaskIterator = new ExhaustiveMiddleOutTaskIterator(
                rootFlow,
                cloudOnlyOperatorIds,
                siteMappingReverse,
                platformMappingReverse,
                costEstimation,
                actionsToPass,
                possiblePlans,
                targetBase);

        ResultAggregator aggregator;
        if (costEstimation instanceof PlanDagStarCostEstimator) {
            aggregator = new MinAggregator(rootFlow);
        } else {
            aggregator = new MaxAggregator(rootFlow);
        }

        AlgorithmTerminationPredicate terminationPredicate = new AlgorithmTerminationPredicate(possiblePlans);

        super.setup(exhaustiveTaskIterator, aggregator, terminationPredicate);
    }
}
