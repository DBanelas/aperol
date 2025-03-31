package optimizer.algorithm.newalgs;

import java.math.BigInteger;
import java.util.function.Predicate;

public class AlgorithmTerminationPredicate implements Predicate<BigInteger> {

    private final BigInteger plansToExamine;

    public AlgorithmTerminationPredicate(BigInteger plansToExamine) {
        this.plansToExamine = plansToExamine;
    }

    @Override
    public boolean test(BigInteger plansExaminedSoFar) {
        return plansExaminedSoFar.compareTo(plansToExamine) < 0;
//        return plansExaminedSoFar < plansToExamine;
    }
}
