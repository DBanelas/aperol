package optimizer.algorithm.taskiterators;

import core.structs.Tuple;
import optimizer.algorithm.cost.PlanCostEstimatorInterface;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.tasks.BatchTask;
import scala.math.BigInt;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class BatchTaskIterator extends AbstractTaskIterator {
    /**
     * Thread safe counter to keep track of the number of batches
     */
    private final AtomicReference<BigInteger> currentBatch;

    /**
     * Batch size
     */
    private final long batchSize;

    /**
     * Number of batches
     */
    private final BigInteger numBatches;

    /**
     * Last batch size
     */
    private final BigInteger lastBatchSize;

    /**
     * Target base for the base conversion algorithm
     */
    private final int targetBase;

    private final long timeoutTimestamp;

    private final boolean randomize;

    /**
     * Creates a new {@code BatchTaskIterator}
     * @param rootFlow The root flow
     * @param costEstimation The cost estimator
     * @param actions The list of actions
     * @param numBatches The number of batches
     * @param lastBatchSize The last batch size
     * @param targetBase The target base
     * @param batchSize The batch size
     */
    public BatchTaskIterator(Graph rootFlow,
                             PlanCostEstimatorInterface costEstimation,
                             ArrayList<Tuple<Integer, Integer>> actions,
                             BigInteger numBatches,
                             BigInteger lastBatchSize,
                             int targetBase,
                             long batchSize,
                             int timeout,
                             boolean randomize) {
        super(rootFlow, null, null, null, costEstimation, actions);
        this.currentBatch = new AtomicReference<>(BigInteger.ZERO);
        this.batchSize = batchSize;
        this.targetBase = targetBase;
        this.timeoutTimestamp = System.currentTimeMillis() + timeout;
        this.randomize = randomize;
        this.numBatches = numBatches;
        this.lastBatchSize = lastBatchSize;

        System.out.println("BatchTaskIterator created with " + numBatches + " batches, last batch size: " + lastBatchSize);
    }

    /**
     * Method to check if there are more batches
     * @return True if there are more batches, false otherwise
     */
    @Override
    public boolean hasNext() {
        return currentBatch.get().compareTo(numBatches) <= 0;
    }

    /**
     * Method to return the next BatchTask
     * @return The next BatchTask
     */
    @Override
    public Callable<Tuple<Graph, Integer>> next() {
        BatchTask task = new BatchTask(rootFlow, costEstimation, currentBatch.get().multiply(BigInteger.valueOf(batchSize)), BigInteger.valueOf(batchSize), actions, targetBase, timeoutTimestamp, randomize);
        if (currentBatch.get().equals(numBatches)) {
            task = new BatchTask(rootFlow, costEstimation, currentBatch.get().multiply(BigInteger.valueOf(batchSize)), lastBatchSize, actions, targetBase, timeoutTimestamp, randomize);
        }

        BigInteger currentBatchVal = this.currentBatch.get();
        currentBatch.set(currentBatchVal.add(BigInteger.ONE));

        return task;
    }
}
