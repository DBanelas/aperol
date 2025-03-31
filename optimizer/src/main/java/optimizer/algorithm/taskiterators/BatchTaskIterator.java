package optimizer.algorithm.taskiterators;

import core.structs.Tuple;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.tasks.BatchTask;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

public class BatchTaskIterator extends AbstractTaskIterator {
    /**
     * Thread safe counter to keep track of the number of batches
     */
    private final AtomicLong currentBatch;

    /**
     * Batch size
     */
    private final int batchSize;

    /**
     * Number of batches
     */
    private final long numBatches;

    /**
     * Last batch size
     */
    private final long lastBatchSize;

    /**
     * Target base for the base conversion algorithm
     */
    private final int targetBase;

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
                             CostEstimatorIface costEstimation,
                             ArrayList<Tuple<Integer, Integer>> actions,
                             long numBatches,
                             long lastBatchSize,
                             int targetBase,
                             int batchSize) {
        super(rootFlow, null, null, costEstimation, actions);
        this.currentBatch = new AtomicLong(0);
        this.batchSize = batchSize;
        this.targetBase = targetBase;

        this.numBatches = numBatches;
        this.lastBatchSize = lastBatchSize;
    }

    /**
     * Method to check if there are more batches
     * @return True if there are more batches, false otherwise
     */
    @Override
    public boolean hasNext() {
        long possiblePlans = numBatches * batchSize + lastBatchSize;
        return currentBatch.get() <= numBatches;
    }

    /**
     * Method to return the next BatchTask
     * @return The next BatchTask
     */
    @Override
    public Callable<Tuple<Graph, Integer>> next() {
        BatchTask task = new BatchTask(rootFlow, costEstimation, currentBatch.get() * batchSize, batchSize, actions, targetBase);
        if (currentBatch.get() == numBatches) {
            task = new BatchTask(rootFlow, costEstimation, currentBatch.get() * batchSize, lastBatchSize, actions, targetBase);
        }
        currentBatch.addAndGet(1);
        return task;
    }
}
