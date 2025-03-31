package optimizer.algorithm.taskiterators;

import core.structs.Tuple;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.tasks.SinglePlanTask;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public class RandomTaskIterator extends AbstractTaskIterator {

    private final BigInteger possiblePlans;
    private final int targetBase;
    private final BigInteger sampleSize;
//    private final AtomicLong currentPlan;
    private final AtomicReference<BigInteger> currentPlan;
    private final SecureRandom secureRandom;
    private final HashSet<BigInteger> generatedPlansNumbers;

    public RandomTaskIterator(Graph rootFlow,
                              Set<Integer> cloudOnlyOperatorIds,
                              Map<String, Integer> siteMappingReverse,
                              CostEstimatorIface costEstimation,
                              ArrayList<Tuple<Integer, Integer>> actions,
                              BigInteger possiblePlans,
                              int targetBase,
                              BigInteger sampleSize) {
        super(rootFlow, cloudOnlyOperatorIds, siteMappingReverse, costEstimation, actions);
        this.secureRandom = new SecureRandom();
        this.secureRandom.setSeed(System.currentTimeMillis());
        this.generatedPlansNumbers = new HashSet<>();
        this.currentPlan = new AtomicReference<>(BigInteger.ZERO);
        this.possiblePlans = possiblePlans;
        this.targetBase = targetBase;
        this.sampleSize = sampleSize;
    }

    @Override
    public boolean hasNext() {
        return currentPlan.get().compareTo(sampleSize) < 0;
    }

    private BigInteger generateNextPlanNo() {
        BigInteger randomNumber;
        do {
            randomNumber = new BigInteger(possiblePlans.bitLength(), secureRandom);
        } while (randomNumber.compareTo(possiblePlans) >= 0); // Ensure it's in range
        return randomNumber;
    }

    @Override
    public Callable<Tuple<Graph, Integer>> next() {
        BigInteger planNo = generateNextPlanNo();
        currentPlan.set(currentPlan.get().add(BigInteger.ONE));
//        ArrayList<Integer> actionsToApply = AlgorithmUtils.convertToBaseWithPadding(planNo, targetBase, rootFlow.getVertices().size());
//
//        for (int i = 0; i < actionsToApply.size(); i++) {
//            if (cloudOnlyOperatorIds.contains(rootFlow.getVertices().get(i).getOperatorId())) {
//                actionsToApply.set(i, siteMappingReverse.get("cloud"));
//            }
//        }
//
//        Graph flow = AlgorithmUtils.applyActionsToGraph(rootFlow, actionsToApply, actions);
//        return new SinglePlanSignatureTask(flow, flow.getSignatureDashed(), costEstimation);
        return new SinglePlanTask(rootFlow, costEstimation, actions, planNo, targetBase);
    }
}
