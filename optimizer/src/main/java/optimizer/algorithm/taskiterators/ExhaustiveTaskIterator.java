package optimizer.algorithm.taskiterators;

import core.structs.Tuple;
import optimizer.algorithm.cost.CostEstimatorIface;
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
    private final ArrayList<Integer> signatureArchetype;



    public ExhaustiveTaskIterator(Graph rootFlow,
                                  Set<Integer> cloudOnlyOperatorIds,
                                  Map<String, Integer> siteMappingReverse,
                                  CostEstimatorIface costEstimation,
                                  ArrayList<Tuple<Integer, Integer>> actions,
                                  BigInteger possiblePlans,
                              int targetBase) {
        super(rootFlow, cloudOnlyOperatorIds, siteMappingReverse, costEstimation, actions);
        this.signatureArchetype = createSignatureArchetype();
        this.currentPlan = new AtomicReference<>(BigInteger.ZERO);
        this.possiblePlans = possiblePlans;
        this.targetBase = targetBase;
    }

    private ArrayList<Integer> createSignatureArchetype() {
        int numOperators = rootFlow.getVertices().size();
        ArrayList<Integer> signatureArchetype = new ArrayList<>();
        for (int i = 0; i < numOperators; i++) {
            if (cloudOnlyOperatorIds.contains(rootFlow.getVertices().get(i).getOperatorId())) {
                signatureArchetype.add(1);
            } else {
                signatureArchetype.add(-1);
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
        ArrayList<Integer> signature = new ArrayList<>(signatureArchetype);
        for (int i = 0; i < signature.size(); i++) {
            if (signature.get(i) == -1) {
                // If the signature is set to -1, (e.g. not a cloud only operator) then set it to the next action
                signature.set(i, this.actions.get(actionsToApply.remove(0))._2); // .2 is the site
            }
        }

        // Convert the signature array to a string such as: 1-arr[0]_1-arr[1] ...
        StringBuilder signatureString = new StringBuilder();
        for (Integer integer : signature) {
            signatureString.append("_1-").append(integer);
        }

        return new SinglePlanSignatureTask(rootFlow, signatureString.toString().replaceFirst("_", ""), costEstimation);
    }
}
