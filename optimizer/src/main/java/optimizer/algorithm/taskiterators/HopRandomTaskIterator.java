package optimizer.algorithm.taskiterators;

import core.structs.Tuple;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.newalgs.AlgorithmUtils;
import optimizer.algorithm.tasks.SinglePlanSignatureTask;

import java.util.*;
import java.util.concurrent.Callable;

public class HopRandomTaskIterator extends AbstractTaskIterator {
    private final Random random;

    private int currentHop;
    private final int numHops;
    private int plansForCurrentHop;
    private final int numOperators;
    private final Set<String> usedSignatures;
    private final List<Long> totalPlansPerHop;

    public HopRandomTaskIterator(Graph rootFlow,
                                 Set<Integer> cloudOnlyOperatorIds,
                                 Map<String, Integer> siteMappingReverse,
                                 CostEstimatorIface costEstimation,
                                 ArrayList<Tuple<Integer, Integer>> actions,
                                 long sampleSize) {
        super(rootFlow, cloudOnlyOperatorIds, siteMappingReverse, costEstimation, actions);
        this.random = new Random(System.currentTimeMillis());
        this.usedSignatures = new HashSet<>();
        this.numOperators = rootFlow.getVertices().size();
        this.numHops = numOperators;
        this.totalPlansPerHop = calculateTotalPlansPerHop(sampleSize, true);
        this.currentHop = 1;
    }

    public long getResultingSampleSize() {
        return totalPlansPerHop.stream().mapToLong(el -> el).sum();
    }

    private ArrayList<Long> calculateTotalPlansPerHop(long sampleSize, boolean bias) {
        ArrayList<Long> totalPlansPerHop = new ArrayList<>(Collections.nCopies(numHops, 0L));
        for (int i = 1; i <= numHops; i++) {
            if (bias) {
                double coefficient = (Math.pow(2.0, numOperators - i)) / (Math.pow(2.0, numOperators) - 1.0);
                long resultingPlansForCurrentHop = (long) (sampleSize * coefficient);
                long maxPlansForCurrentHop = AlgorithmUtils.getTotalPlansForHop(numOperators, actions.size(), i);
                long previousPlansForCurrentHop = totalPlansPerHop.get(i - 1);

                if (resultingPlansForCurrentHop + previousPlansForCurrentHop > maxPlansForCurrentHop) {
                    totalPlansPerHop.set(i - 1, maxPlansForCurrentHop);
                    totalPlansPerHop.set(i, resultingPlansForCurrentHop + previousPlansForCurrentHop - maxPlansForCurrentHop);
                } else {
                    totalPlansPerHop.set(i - 1, resultingPlansForCurrentHop + previousPlansForCurrentHop);
                }
            } else {
                totalPlansPerHop.set(i - 1, sampleSize / numOperators);
            }
        }
        return totalPlansPerHop;
    }

    @Override
    public boolean hasNext() {
        return this.currentHop <= this.numHops;
    }

    @Override
    public Callable<Tuple<Graph, Integer>> next() {
        String nextPlanSignature = generateNextPlanSignature();
        plansForCurrentHop++;

        if (plansForCurrentHop == totalPlansPerHop.get(currentHop - 1)) {
            currentHop++;
            plansForCurrentHop = 0;
        }
        return new SinglePlanSignatureTask(rootFlow, nextPlanSignature, costEstimation);
    }

    private String generateNextPlanSignature() {
        String rootSignature = rootFlow.getSignatureDashed();
        String[] signatureParts = rootSignature.split("_");
        String newSignature;
        do {
            List<Integer> positionsToChange = generateRandomPositions(currentHop);
            for (Integer i : positionsToChange) {
                String part = signatureParts[i];
                int currentSite = Integer.parseInt(part.split("-")[1]);
                String newSignaturePart = "1-" + getDifferentSite(currentSite);
                signatureParts[i] = newSignaturePart;
            }
            newSignature = String.join("_", signatureParts);
        } while (usedSignatures.contains(newSignature));
        return newSignature;
    }

    private int getDifferentSite(int currentSite) {
        int newSite;
        do {
            Tuple<Integer, Integer> newAction = actions.get(random.nextInt(actions.size()));
            newSite = newAction._2;
        } while (currentSite == newSite);
        return newSite;
    }

    private List<Integer> generateRandomPositions(int k) {
        List<Integer> positions = new ArrayList<>();
        while (positions.size() < k) {
            int pos = random.nextInt(numOperators);
            if (!positions.contains(pos)) {
                positions.add(pos);
            }
        }
        return positions;
    }


}
