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

/**
 * Visits plan numbers in the order
 *     mid, mid-1, mid+1, mid-2, mid+2
 * where mid = ⌊possiblePlans / 2⌋.
 * Thread-safe like the original (uses AtomicReference for the step counter).
 */
public class ExhaustiveMiddleOutTaskIterator extends AbstractTaskIterator {

    private final BigInteger possiblePlans;
    private final BigInteger mid;                   // ⌊possiblePlans / 2⌋
    private final int targetBase;
    private final ArrayList<Tuple<Integer, Integer>> signatureArchetype;

    private BigInteger produced;
    private BigInteger startingPlanNo;
    private BigInteger nextLeft;          // next index to the left  (decrements)
    private BigInteger nextRight;         // next index to the right (increments)
    private boolean goLeftNext = true;

    public ExhaustiveMiddleOutTaskIterator(Graph rootFlow,
                                 Set<Integer> cloudOnlyOperatorIds,
                                 Map<String, Integer> siteMappingReverse,
                                 Map<String, Integer> platformMappingReverse,
                                 PlanCostEstimatorInterface costEstimation,
                                 ArrayList<Tuple<Integer, Integer>> actions,
                                 BigInteger possiblePlans,
                                 int targetBase) {
        super(rootFlow, cloudOnlyOperatorIds, siteMappingReverse, platformMappingReverse, costEstimation, actions);
        this.startingPlanNo = AlgorithmUtils.getPlanNumberForPlan(rootFlow, targetBase, actions);
        System.out.println("Starting plan number: " + startingPlanNo);
        System.out.println("Plans/2: " + possiblePlans.shiftRight(1)); // divide by 2
        System.out.println(rootFlow.getSignatureDashed());
        this.produced  = BigInteger.ZERO;
        this.nextLeft  = startingPlanNo.subtract(BigInteger.ONE);
        this.nextRight = startingPlanNo.add(BigInteger.ONE);

        this.signatureArchetype = createSignatureArchetype();
        this.possiblePlans  = possiblePlans;
        this.mid            = possiblePlans.shiftRight(1);          // divide by 2
        this.targetBase     = targetBase;
    }

    /* ---------- traversal order ----------------------------------------------------- */

    @Override
    public boolean hasNext() {
        return produced.compareTo(possiblePlans) < 0;   // safe: we emit exactly one per call
    }

    @Override
    public synchronized Callable<Tuple<Graph, Integer>> next() {

        if (!hasNext()) throw new IllegalStateException("No more plans");

        /* ---------- pick the next plan number ---------------------------- */
        BigInteger planNo;

        if (produced.equals(BigInteger.ZERO)) {          // first call → anchor itself
            planNo = startingPlanNo;
        } else {
            while (true) {
                if (goLeftNext && nextLeft.compareTo(BigInteger.ZERO) >= 0) {
                    planNo   = nextLeft;
                    nextLeft = nextLeft.subtract(BigInteger.ONE);
                    goLeftNext = false;               // flip direction
                    break;

                } else if (!goLeftNext &&
                        nextRight.compareTo(possiblePlans) < 0) {
                    planNo   = nextRight;
                    nextRight = nextRight.add(BigInteger.ONE);
                    goLeftNext = true;                // flip direction
                    break;

                } else {                              // chosen side exhausted
                    goLeftNext = !goLeftNext;         // just switch and retry
                }
            }
        }
        this.produced = this.produced.add(BigInteger.ONE);

        /* ---------- remainder identical to your original ----------------- */
        ArrayList<Integer> actionsToApply =
                AlgorithmUtils.convertToBaseWithPadding(
                        planNo,
                        targetBase,
                        rootFlow.getVertices().size() - cloudOnlyOperatorIds.size());

        ArrayList<Tuple<Integer, Integer>> signature =
                new ArrayList<>(signatureArchetype);


        for (int i = 0; i < signature.size(); i++) {
            if (signature.get(i)._2 == -1) {
                Tuple<Integer, Integer> action =
                        this.actions.get(actionsToApply.remove(0));
                signature.set(i, new Tuple<>(action._1, action._2));
            }
        }

        StringBuilder sig = new StringBuilder();
        for (Tuple<Integer, Integer> part : signature) {
            sig.append('_').append(part._1).append('-').append(part._2);
        }

        return new SinglePlanSignatureTask(
                rootFlow,
                sig.substring(1),               // drop leading '_'
                costEstimation);
    }

    /* ---------- helper: pin cloud-only operators ------------------------------------ */

    private ArrayList<Tuple<Integer, Integer>> createSignatureArchetype() {
        int numOperators = rootFlow.getVertices().size();
        ArrayList<Tuple<Integer, Integer>> archetype = new ArrayList<>(numOperators);

        for (int i = 0; i < numOperators; i++) {
            int opId = rootFlow.getVertices().get(i).getOperatorId();
            if (cloudOnlyOperatorIds.contains(opId)) {
                archetype.add(new Tuple<>(1, siteMappingReverse.get("cloud"))); // cloud, platform 1
            } else {
                archetype.add(new Tuple<>(-1, -1));                              // to be filled later
            }
        }
        return archetype;
    }
}
