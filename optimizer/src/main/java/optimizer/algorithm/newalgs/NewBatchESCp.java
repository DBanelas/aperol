package optimizer.algorithm.newalgs;

import core.structs.Tuple;
import optimizer.algorithm.aggregators.MinAggregator;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.cost.DagStarCostEstimator;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.aggregators.MaxAggregator;
import optimizer.algorithm.aggregators.ResultAggregator;
import optimizer.algorithm.taskiterators.BatchTaskIterator;
import optimizer.algorithm.taskiterators.TaskIterator;

import java.math.BigInteger;
import java.util.ArrayList;

public class NewBatchESCp extends AbstractPlanBasedAlgorithm {

    private final int batchSize;
    private final long numBatches;
    private final long lastBatchSize;

    @Override
    protected void printBeforeExecution() {
        super.printBeforeExecution();
        System.out.println("Batch size: " + batchSize);
        System.out.println("Number of batches: " + numBatches);
        System.out.println("Last batch size: " + lastBatchSize);
        System.out.println();
    }

    public NewBatchESCp(Graph rootFlow,
                        int numPlatforms,
                        int numSites,
                        CostEstimatorIface costEstimation,
                        int timeout,
                        boolean disableStats,
                        int numThreads,
                        int batchSize) {
        super(rootFlow, numPlatforms, numSites, timeout, disableStats, numThreads, 10 * numThreads);

        // possiblePlans remains as a BigInteger
        this.possiblePlans = BigInteger.valueOf((long) numPlatforms * numSites).pow(this.numOperators);
        this.batchSize = batchSize;

        // Use BigInteger arithmetic for division and modulus
        BigInteger batchSizeBI = BigInteger.valueOf(batchSize);
        this.numBatches = this.possiblePlans.divide(batchSizeBI).longValue();
        this.lastBatchSize = this.possiblePlans.mod(batchSizeBI).longValue();

        int targetBase = numPlatforms * numSites;
        ArrayList<Tuple<Integer, Integer>> actions = AlgorithmUtils.getActions(numPlatforms, numSites);

        TaskIterator batchTaskIterator = new BatchTaskIterator(rootFlow, costEstimation,
                actions, numBatches, lastBatchSize, targetBase, batchSize);


        ResultAggregator aggregator;
        if (costEstimation instanceof DagStarCostEstimator) {
            aggregator = new MinAggregator(rootFlow);
        } else {
            aggregator = new MaxAggregator(rootFlow);
        }

        AlgorithmTerminationPredicate terminationPredicate = new AlgorithmTerminationPredicate(possiblePlans);

        super.setup(batchTaskIterator, aggregator, terminationPredicate);
    }
}
