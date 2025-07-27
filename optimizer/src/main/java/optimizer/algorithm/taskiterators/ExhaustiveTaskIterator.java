package optimizer.algorithm.taskiterators;

import core.structs.Tuple;
import optimizer.algorithm.cost.PlanCostEstimatorInterface;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.newalgs.AlgorithmUtils;
import optimizer.algorithm.tasks.SinglePlanSignatureTask;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public class ExhaustiveTaskIterator extends AbstractTaskIterator {
    private final BigInteger possiblePlans;
    private final AtomicReference<BigInteger> currentPlan;
    private final int targetBase;
    private final ArrayList<Tuple<Integer, Integer>> signatureArchetype;

    public ExhaustiveTaskIterator(Graph rootFlow,
                                  Set<Integer> cloudOnlyOperatorIds,
                                  Map<String, Integer> siteMappingReverse,
                                  Map<String, Integer> platformMappingReverse,
                                  PlanCostEstimatorInterface costEstimation,
                                  ArrayList<Tuple<Integer, Integer>> actions,
                                  BigInteger possiblePlans,
                              int targetBase) {
        super(rootFlow, cloudOnlyOperatorIds, siteMappingReverse, platformMappingReverse, costEstimation, actions);
        this.signatureArchetype = createSignatureArchetype();
        this.currentPlan = new AtomicReference<>(BigInteger.ZERO);
        this.possiblePlans = possiblePlans;
        this.targetBase = targetBase;
    }

    private ArrayList<Tuple<Integer, Integer>> createSignatureArchetype() {
        int numOperators = rootFlow.getVertices().size();
        ArrayList<Tuple<Integer, Integer>> signatureArchetype = new ArrayList<>();
        for (int i = 0; i < numOperators; i++) {
            if (cloudOnlyOperatorIds.contains(rootFlow.getVertices().get(i).getOperatorId())) {
                signatureArchetype.add(new Tuple<>(1, siteMappingReverse.get("cloud"))); // Pin cloud-only operators to cloud and platform 1 (randomly chosen)
            } else {
                signatureArchetype.add(new Tuple<>(-1, -1));
            }
        }
        return signatureArchetype;
    }

    @Override
    public boolean hasNext() {
        return currentPlan.get().compareTo(possiblePlans) < 0;
//        return currentPlan.get() < possiblePlans;
    }

    @Override
    public Callable<Tuple<Graph, Integer>> next() {
        BigInteger planNo = currentPlan.get();
        currentPlan.set(planNo.add(BigInteger.ONE));

        // Convert plan number to base targetBase
        ArrayList<Integer> actionsToApply = AlgorithmUtils.convertToBaseWithPadding(planNo, targetBase, rootFlow.getVertices().size() - cloudOnlyOperatorIds.size());

        // Need to merge the signature archetype with the actionsToApply
        ArrayList<Tuple<Integer, Integer>> signature = new ArrayList<>(signatureArchetype);
        for (int i = 0; i < signature.size(); i++) {
            if (signature.get(i)._2 == -1) { // If the site is -1, it means that the operator is not cloud-only and must be set to the next action
                Tuple<Integer, Integer> action = this.actions.get(actionsToApply.remove(0));
                int platform = action._1; // .1 is the platform
                int site = action._2; // .2 is the site
                signature.set(i, new Tuple<>(platform, site));
            }
        }

        // Convert the signature array to a string such as: 1-arr[0]_1-arr[1] ...
        StringBuilder signatureString = new StringBuilder();
        for (Tuple<Integer, Integer> signaturePart : signature) {
            signatureString.append("_").append(signaturePart._1).append("-").append(signaturePart._2);
        }

        return new SinglePlanSignatureTask(rootFlow, signatureString.toString().replaceFirst("_", ""), costEstimation);
    }
}
