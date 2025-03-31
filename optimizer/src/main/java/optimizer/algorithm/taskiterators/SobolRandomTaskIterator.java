package optimizer.algorithm.taskiterators;

import core.structs.Tuple;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;
import optimizer.algorithm.tasks.SinglePlanSignatureTask;
import org.apache.commons.math3.random.SobolSequenceGenerator;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

public class SobolRandomTaskIterator extends AbstractTaskIterator {

    private final int numSites;
    private final int numOperators;
    private final long sampleSize;
    private final AtomicLong currentPlan;
    private final HashSet<Long> generatedPlansNumbers;
    private final SobolSequenceGenerator sobolGenerator;

    public SobolRandomTaskIterator(Graph rootFlow,
                              Set<Integer> cloudOnlyOperatorIds,
                              Map<String, Integer> siteMappingReverse,
                              CostEstimatorIface costEstimation,
                              ArrayList<Tuple<Integer, Integer>> actions,
                              int numSites,
                              int numOperators,
                              long sampleSize) {
        super(rootFlow, cloudOnlyOperatorIds, siteMappingReverse, costEstimation, actions);
        this.sobolGenerator = new SobolSequenceGenerator(numOperators);
        this.sobolGenerator.skipTo(new Random().nextInt(100_000_000));
        this.generatedPlansNumbers = new HashSet<>();
        this.currentPlan = new AtomicLong(0);
        this.numSites = numSites;
        this.numOperators = numOperators;
        this.sampleSize = sampleSize;
    }

    @Override
    public boolean hasNext() {
        return currentPlan.get() < sampleSize;
    }

    @Override
    public Callable<Tuple<Graph, Integer>> next() {
        double[] vec = sobolGenerator.nextVector();
        ArrayList<Integer> actionsToApply = new ArrayList<>();
        for (int i = 0; i < numOperators; i++) {
            int site = (int) (vec[i] * (numSites - 1));
            actionsToApply.add(site);
        }

        Graph flow = new Graph(rootFlow);
        int actionNo = 0;
        for (Vertex v : flow.getVertices()) {
            Tuple<Integer, Integer> action = actions.get(actionsToApply.get(actionNo++));
            int platform = action._1;
            int site = action._2;
            if (cloudOnlyOperatorIds.contains(v.getOperatorId())) {
                site = siteMappingReverse.get("cloud");
            }
            v.setSite(site);
            v.setPlatform(platform);
        }
        currentPlan.incrementAndGet();
        return new SinglePlanSignatureTask(flow, flow.getSignatureDashed(), costEstimation);
    }

}
