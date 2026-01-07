package optimizer.algorithm.newalgs;

import core.structs.Tuple;
import optimizer.algorithm.aggregators.MinAggregator;
import optimizer.algorithm.cost.PlanCostEstimatorInterface;
import optimizer.algorithm.cost.PlanDagStarCostEstimator;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.aggregators.MaxAggregator;
import optimizer.algorithm.aggregators.ResultAggregator;
import optimizer.algorithm.taskiterators.BatchTaskIterator;
import optimizer.algorithm.taskiterators.TaskIterator;

import java.math.BigInteger;
import java.util.ArrayList;

public class BatchESCp extends AbstractPlanBasedAlgorithm {

    private final int batchSize;
    private final BigInteger numBatches;
    private final BigInteger lastBatchSize;

    @Override
    protected void printBeforeExecution() {
        super.printBeforeExecution();
        System.out.println("Batch size: " + batchSize);
        System.out.println("Number of batches: " + numBatches);
        System.out.println("Last batch size: " + lastBatchSize);
        System.out.println();
    }

    public BatchESCp(Graph rootFlow,
                     ArrayList<Tuple<Integer, Integer>> actions,
                     BigInteger possiblePlans,
                     int targetBase,
                     PlanCostEstimatorInterface costEstimation,
                     int timeout,
                     boolean disableStats,
                     int numThreads,
                     int batchSize) {
        super(rootFlow, possiblePlans, timeout, disableStats, numThreads, 10 * numThreads);

        // possiblePlans remains as a BigInteger
        this.batchSize = batchSize;

        // Use BigInteger arithmetic for division and modulus
        BigInteger batchSizeBI = BigInteger.valueOf(batchSize);
        this.numBatches = this.possiblePlans.divide(batchSizeBI);
        this.lastBatchSize = this.possiblePlans.mod(batchSizeBI);

//        TaskIterator batchTaskIterator = new BatchTaskIterator(rootFlow, costEstimation,
//                actions, numBatches, lastBatchSize, targetBase, batchSize, timeout, false);

        // Create a randomized batch task iterator
        TaskIterator batchTaskIterator = new BatchTaskIterator(
                rootFlow, costEstimation, actions, numBatches, lastBatchSize, targetBase, batchSize, timeout, true);


        ResultAggregator aggregator;
        if (costEstimation instanceof PlanDagStarCostEstimator) {
            aggregator = new MinAggregator(rootFlow);
        } else {
            aggregator = new MaxAggregator(rootFlow);
        }

        AlgorithmTerminationPredicate terminationPredicate = new AlgorithmTerminationPredicate(possiblePlans);

        super.setup(batchTaskIterator, aggregator, terminationPredicate);
    }
}
