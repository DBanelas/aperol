package optimizer.algorithm.taskiterators;

import core.structs.Tuple;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;
import optimizer.algorithm.tasks.SinglePlanSignatureTask;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class HopUniformTaskIterator extends AbstractTaskIterator {

    private final Random random;

    private AtomicReference<BigInteger> currentPlanNo;
    private final int maxHops;
    private final BigInteger sampleSize;
    private final int numOperators;
    private final Set<String> usedSignatures;
    private final List<Integer> rootSites;
    private final BigInteger possiblePlans;
    private final int targetBase;

    public HopUniformTaskIterator(Graph rootFlow,
                                 Set<Integer> cloudOnlyOperatorIds,
                                 Map<String, Integer> siteMappingReverse,
                                 CostEstimatorIface costEstimation,
                                 ArrayList<Tuple<Integer, Integer>> actions,
                                 int targetBase,
                                 BigInteger possiblePlans,
                                 BigInteger sampleSize,
                                 int maxHops) {
        super(rootFlow, cloudOnlyOperatorIds, siteMappingReverse, costEstimation, actions);
        this.random = new Random(System.currentTimeMillis());
        this.usedSignatures = new HashSet<>();
        this.numOperators = rootFlow.getVertices().size();
        this.currentPlanNo = new AtomicReference<>(BigInteger.ZERO);
        this.maxHops = maxHops;
        this.sampleSize = sampleSize;
        this.possiblePlans = possiblePlans;
        this.targetBase = targetBase;

        this.rootSites = rootFlow.getVertices().stream()
                .map(Vertex::getSite)
                .collect(Collectors.toList());

    }

    public BigInteger getResultingSampleSize() {
        return this.sampleSize;
    }

    @Override
    public boolean hasNext() {
        return this.currentPlanNo.get().compareTo(this.sampleSize) < 0;
//        return this.currentPlanNo <= this.sampleSize;
    }

    @Override
    public Callable<Tuple<Graph, Integer>> next() {
        String nextPlanSignature = generateNextPlanSignature();
        this.usedSignatures.add(nextPlanSignature);
        currentPlanNo.set(currentPlanNo.get().add(BigInteger.ONE));
        return new SinglePlanSignatureTask(rootFlow, nextPlanSignature, costEstimation);
    }

    /**
     * Method that generates a plan signature. It chooses a random number of changes to be done
     * to the root plan, and then randomly selects them.
     * @return A String representing the new plan
     */
    private String generateNextPlanSignature() {
        String rootSignature = this.rootFlow.getSignatureDashed();
        String[] signatureParts = rootSignature.split("_");
        String newSignature;

        do {
            int numChanges = this.random.nextInt(this.maxHops + 1);
            List<Integer> positionsToChange = generateRandomPositions(numChanges);
            for (Integer i : positionsToChange) {
                String part = signatureParts[i];
                int currentSite = Integer.parseInt(part.split("-")[1]);
                String newSignaturePart = "1-" + getDifferentSite(currentSite);
                signatureParts[i] = newSignaturePart;
            }
            newSignature = String.join("_", signatureParts);
        } while (this.usedSignatures.contains(newSignature));
        return newSignature;
    }

    /**
     * Method to generate a different site from the current site in a random manner
     * @param currentSite The current site
     * @return The new site
     */
    private int getDifferentSite(int currentSite) {
        int newSite;
        do {
            Tuple<Integer, Integer> newAction = this.actions.get(this.random.nextInt(this.actions.size()));
            newSite = newAction._2;
        } while (currentSite == newSite);
        return newSite;
    }

    /**
     * Method to generate k random positions (0-indexed) in an array
     * @param k Number of positions to generate
     * @return A List<Integer> with the generated positions
     */
    private List<Integer> generateRandomPositions(int k) {
        List<Integer> positions = new ArrayList<>();
        while (positions.size() < k) {
            int pos = this.random.nextInt(this.numOperators);
            if (!positions.contains(pos)) {
                positions.add(pos);
            }
        }
        return positions;
    }
}
