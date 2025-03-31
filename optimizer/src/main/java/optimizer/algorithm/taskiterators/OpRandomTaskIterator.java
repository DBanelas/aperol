package optimizer.algorithm.taskiterators;

import core.structs.Tuple;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;
import optimizer.algorithm.tasks.SinglePlanSignatureTask;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

public class OpRandomTaskIterator extends AbstractTaskIterator {
    private long sampleSize;
    private final AtomicLong currentPlan;
    private final Random random;

    public OpRandomTaskIterator(Graph rootFlow,
                              Set<Integer> cloudOnlyOperatorIds,
                              Map<String, Integer> siteMappingReverse,
                              CostEstimatorIface costEstimation,
                              ArrayList<Tuple<Integer, Integer>> actions,
                              long sampleSize) {
        super(rootFlow, cloudOnlyOperatorIds, siteMappingReverse, costEstimation, actions);
        this.random = new Random(System.currentTimeMillis());
        this.currentPlan = new AtomicLong(0);
        this.sampleSize = sampleSize;
    }

    public void resetWithNewSampleSize(long sampleSize) {
        this.currentPlan.set(0);
        this.sampleSize = sampleSize;
    }

    @Override
    public boolean hasNext() {
        return currentPlan.get() < sampleSize;
    }

    @Override
    public Callable<Tuple<Graph, Integer>> next() {
        Graph flow = new Graph(rootFlow);
        for (Vertex v : flow.getVertices()) {
            if (cloudOnlyOperatorIds.contains(v.getOperatorId())) {
                v.setSite(siteMappingReverse.get("cloud"));
                continue;
            }
            v.setSite(random.nextInt(actions.size()) + 1);
        }
        currentPlan.incrementAndGet();
        return new SinglePlanSignatureTask(flow, flow.getSignatureDashed(), costEstimation);
    }
}
