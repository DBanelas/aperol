package optimizer.algorithm.newalgs;

import core.structs.Tuple;
import optimizer.algorithm.cost.CostEstimatorIface;
import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.aggregators.MaxAggregator;
import optimizer.algorithm.aggregators.ResultAggregator;
import optimizer.algorithm.taskiterators.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Set;
import java.util.Map;

import java.util.ArrayList;

public class NewQuickPick extends AbstractPlanBasedAlgorithm {

    private final double percentage;
    private final BigInteger sampleSize;

    public NewQuickPick(Graph rootFlow,
                        int numPlatforms,
                        int numSites,
                        Set<Integer> cloudOnlyOperatorIds,
                        Map<String, Integer> siteMappingReverse,
                        CostEstimatorIface costEstimation,
                        int timeout,
                        boolean disableStats,
                        int numThreads,
                        double percentage,
                        int numHops) {
        super(rootFlow, numPlatforms, numSites, timeout, disableStats, numThreads, 1000 * numThreads);
        this.percentage = percentage;

        if (percentage > 1) {
            this.sampleSize = BigInteger.valueOf((long) percentage);
        } else {
            // Convert BigInteger to BigDecimal for precise multiplication
            BigDecimal upperBoundDecimal = new BigDecimal(this.possiblePlans);
            // Multiply by percentage using BigDecimal
            BigDecimal percentageMultiplier = new BigDecimal(percentage);
            BigDecimal result = upperBoundDecimal.multiply(percentageMultiplier);
            this.sampleSize = result.setScale(0, RoundingMode.FLOOR).toBigInteger();
        }

        int targetBase = numPlatforms * numSites;
        ArrayList<Tuple<Integer, Integer>> actions = AlgorithmUtils.getActions(numPlatforms, numSites);

//        RandomTaskIterator randomTaskIterator = new RandomTaskIterator(
//                rootFlow,
//                cloudOnlyOperatorIds,
//                siteMappingReverse,
//                costEstimation,
//                actions,
//                possiblePlans,
//                targetBase,
//                this.sampleSize
//        );

//        OpRandomTaskIterator randomTaskIterator = new OpRandomTaskIterator(
//                rootFlow,
//                cloudOnlyOperatorIds,
//                siteMappingReverse,
//                costEstimation,
//                actions,
//                sampleSize
//        );

        HopUniformTaskIterator randomTaskIterator = new HopUniformTaskIterator(
                rootFlow,
                cloudOnlyOperatorIds,
                siteMappingReverse,
                costEstimation,
                actions,
                targetBase,
                possiblePlans,
                sampleSize,
                numHops
        );

        ResultAggregator maxAggregator = new MaxAggregator(rootFlow);

        AlgorithmTerminationPredicate terminationPredicate = new AlgorithmTerminationPredicate(sampleSize);

        super.setup(randomTaskIterator, maxAggregator, terminationPredicate);
    }

    @Override
    protected void printBeforeExecution() {
        super.printBeforeExecution();
        System.out.println("Percentage: " + this.percentage);
        System.out.println("Sample size: " + this.sampleSize);
        System.out.println();
    }
}
