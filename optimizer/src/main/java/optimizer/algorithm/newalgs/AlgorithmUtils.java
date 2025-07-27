package optimizer.algorithm.newalgs;

import core.parser.network.AvailablePlatform;
import core.parser.network.Site;
import core.structs.Tuple;

import java.math.BigInteger;
import java.util.*;

import optimizer.algorithm.graph.Graph;
import optimizer.algorithm.graph.Vertex;

public class AlgorithmUtils {


    public static BigInteger getPlanNumberForPlan(Graph plan, int targetBase, ArrayList<Tuple<Integer, Integer>> actions) {

        ArrayList<Integer> actionDigits = new ArrayList<>();
        for (Vertex v : plan.getVertices()) {
            int platform = v.getPlatform();
            int site = v.getSite();
            actionDigits.add(actions.indexOf(new Tuple<>(platform, site)));
        }

        return toBase10(actionDigits, targetBase);
    }

    public static BigInteger toBase10(List<Integer> digits, int base) {
        if (base < 2) {
            throw new IllegalArgumentException("Base must be at least 2");
        }

        BigInteger result = BigInteger.ZERO;
        BigInteger radix   = BigInteger.valueOf(base);

        for (int digit : digits) {
            if (digit < 0 || digit >= base) {
                throw new IllegalArgumentException(
                        "Digit " + digit + " is not valid for base " + base);
            }
            // Horner's rule: result = result * base + digit
            result = result.multiply(radix).add(BigInteger.valueOf(digit));
        }
        return result;
    }


    public static ArrayList<Tuple<Integer, Integer>> getActionsFromSitePlatformMapping(Map<String, ArrayList<String>> sitePlatformMapping,
                                                                                       HashMap<String, Integer> siteMappingReverse,
                                                                                       HashMap<String, Integer> platformMappingReverse) {
        ArrayList<Tuple<Integer, Integer>> actions = new ArrayList<>();
        for (Map.Entry<String, ArrayList<String>> entry : sitePlatformMapping.entrySet()) {
            String siteName = entry.getKey();
            ArrayList<String> platforms = entry.getValue();
            if (!siteMappingReverse.containsKey(siteName)) {
                throw new IllegalArgumentException("Site " + siteName + " not found in site mapping.");
            }
            int siteId = siteMappingReverse.get(siteName);
            for (String platformName : platforms) {
                if (!platformMappingReverse.containsKey(platformName)) {
                    throw new IllegalArgumentException("Platform " + platformName + " not found in platform mapping.");
                }
                int platformId = platformMappingReverse.get(platformName);
                actions.add(new Tuple<>(platformId, siteId));
            }
        }

        return actions;
    }

    /**
     * Method to create all the possible actions for the given platforms and sites.
     * @return List of all possible actions
     */
    public static ArrayList<Tuple<Integer, Integer>> getActions(int numPlatforms, int numSites) {
        ArrayList<Tuple<Integer, Integer>> actions = new ArrayList<>();
        for (int i = 0; i < numPlatforms; i++) {
            for (int j = 0; j < numSites; j++) {
                actions.add(new Tuple<>((i + 1), (j + 1)));
            }
        }
        return actions;
    }

    /**
     * Calculates the target base value.
     *
     * @param numPlatforms the number of platforms
     * @param numSites     the number of sites
     * @return the target base value
     */
    private int calculateTargetBase(int numPlatforms, int numSites) {
        return numPlatforms * numSites;
    }

    /**
     * Method to calculate the total number of plans for a specific hop.
     * @param numOperators  Number of operators in the workflow
     * @param numActions    Number of actions
     * @param hopNum        Hop number to calculate the plans for
     * @return              The total plans for the given hop number
     */
    public static long getTotalPlansForHop(int numOperators, int numActions, int hopNum) {
        double firstPart = Math.pow(numOperators * numActions, hopNum - 1);
        double secondPart = numOperators * numActions - 1;
        return (long) (firstPart * secondPart);
    }

    public static Graph applyActionsToGraph(
            Graph initialGraph,
            ArrayList<Integer> actionsToApply,
            ArrayList<Tuple<Integer, Integer>> actions) {
        Graph flow = new Graph(initialGraph);
        int actionNo = 0;
        for (Vertex v : flow.getVertices()) {
            Tuple<Integer, Integer> action = actions.get(actionsToApply.get(actionNo++));
            int platform = action._1;
            int site = action._2;
            v.setSite(site);
            v.setPlatform(platform);
        }
        return flow;
    }


    public static ArrayList<Integer> convertToBaseWithPadding(BigInteger number, int base, int length) {
        if (base < 2) {
            throw new IllegalArgumentException("Base must be at least 2.");
        }

        if (number.compareTo(BigInteger.ZERO) < 0) {
            throw new IllegalArgumentException("Number must be positive.");
        }

        ArrayList<Integer> digits = new ArrayList<>(Collections.nCopies(length, 0));

        if (number.equals(BigInteger.ZERO)) {
            return digits;
        }

        BigInteger numberToTransform = number;

        // Convert the number to the new base
        int index = length - 1;
        while (numberToTransform.compareTo(BigInteger.ZERO) > 0) {
            if (index < 0) {
                throw new IllegalArgumentException("Number is too large to fit in the specified length: " + number);
            }
            BigInteger[] divMod = numberToTransform.divideAndRemainder(BigInteger.valueOf(base));
            digits.set(index--, divMod[1].intValue());
            numberToTransform = divMod[0]; // Update numberToTransform to quotient
        }

        return digits;
    }

    /**
     * Converts a number to a new base with padding.
     *
     * @param number The number to convert
     * @param base The new base
     * @param length The length of the resulting array
     * @return The number in the new base with padding
     */
    public static ArrayList<Integer> convertToBaseWithPadding(long number, int base, int length) {
        if (base < 2) {
            throw new IllegalArgumentException("Base must be at least 2.");
        }

        if (number < 0) {
            throw new IllegalArgumentException("Number must be positive.");
        }

        ArrayList<Integer> digits = new ArrayList<>(Collections.nCopies(length, 0));

        if (number == 0) {
            return digits;
        }

        long numberToTransform = number;

        // Convert the number to the new base
        int index = length - 1;
        while (numberToTransform > 0) {
            try {
                int result = (int) (numberToTransform % base);
                digits.set(index--, result);
            } catch (IndexOutOfBoundsException e) {
                System.out.println("Index out of bounds: " + index);
                System.out.println("Number: " + numberToTransform);
                System.out.println("Initial number: " + number);
                System.out.println("Base: " + base);
                System.out.println("Length: " + length);
                System.out.println("Digits: " + digits);
                System.exit(1);
            }
            numberToTransform /= base;
        }
        return digits;
    }
}
