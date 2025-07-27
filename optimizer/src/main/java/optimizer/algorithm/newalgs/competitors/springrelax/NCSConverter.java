/*
 * MdsNcs.java
 *
 * Classical Multi-Dimensional Scaling (2-D) for a full RTT matrix.
 *
 * Dependencies:
 *   1) Apache Commons Math 3.6+   (org.apache.commons.math3:commons-math3:3.6.1)
 *
 * Compile:
 *   javac -cp commons-math3-3.6.1.jar MdsNcs.java
 *
 * Example usage:
 *   Map<String,Double> pairs = ...;            // "a:b" -> RTT
 *   NCSConverter ncs = new NCSConverter(pairs);
 *   ncs.build();                               // run once
 *   double[] xy = ncs.coord("a8-126.grenoble");
 *   double  est = ncs.rttEstimate("a8-126.grenoble", "rpi3-5.grenoble.cloud");
 */

package optimizer.algorithm.newalgs.competitors.springrelax;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;

public final class NCSConverter {

    private final Map<String, Double> pairLat;      // "from:to" -> RTT
    private final List<String> nodes = new ArrayList<>();
    private final Map<String, Integer> indexOf = new HashMap<>();
    private final Map<String, double[]> coord = new HashMap<>(); // internal state

    public NCSConverter(Map<String, Double> pairLat) {
        this.pairLat = Objects.requireNonNull(pairLat, "pairLat");
        collectNodes();                      // fill nodes[] & indexOf
    }

    /* ------------------------------------------------------------------ */

    private static RealMatrix centringMatrix(int n) {
        double inv = 1.0 / n;
        RealMatrix J = new Array2DRowRealMatrix(n, n);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                J.setEntry(i, j, (i == j ? 1.0 : 0.0) - inv);
        return J;
    }

    /* ------------------------------------------------------------------ */

    private static int[] topPositive(double[] λ, int k) {
        int[] idx = new int[k];
        Arrays.fill(idx, -1);
        for (int t = 0; t < k; t++) {
            double best = -1;
            int bestIdx = -1;
            for (int i = 0; i < λ.length; i++)
                if (λ[i] > best && !contains(idx, i)) {
                    best = λ[i];
                    bestIdx = i;
                }
            if (best <= 0) throw new IllegalStateException("Need ≥" + k + " positive eigenvalues");
            idx[t] = bestIdx;
        }
        return idx;
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    /* ------------------------------------------------------------------ */
    /* ---------- internal helpers -------------------------------------- */

    /**
     * Run classical MDS → fills the internal coord map.
     */
    public void build() {
        final int n = nodes.size();
        if (n < 3) throw new IllegalStateException("Need ≥3 distinct nodes");

        /* 1. squared-distance matrix D² */
        RealMatrix D2 = new Array2DRowRealMatrix(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double d = rtt(i, j);
                D2.setEntry(i, j, d * d);
            }
        }

        /* 2. double-centre: B = -½ J D² J */
        RealMatrix J = centringMatrix(n);
//        System.out.println("J calculated" + (J.getRowDimension() == n ? " OK" : " ERROR!"));

        RealMatrix JD2 = J.multiply(D2); // J * D²
//        System.out.println("JD2 calculated" + (JD2.getRowDimension() == n ? " OK" : " ERROR!"));

        RealMatrix JD2J = JD2.multiply(J); // J * D² * J
//        System.out.println("JD2J calculated" + (JD2J.getRowDimension() == n ? " OK" : " ERROR!"));

        RealMatrix B = JD2J.scalarMultiply(-0.5);
//        System.out.println("B calculated" + (B.getRowDimension() == n ? " OK" : " ERROR!"));


        /* --- QUICK FIX -------------------------------------------- */
        /* (1) Force B to be *exactly* symmetric                       */
        if (n == 2047){
            for (int i = 0; i < n; i++)
                for (int j = i + 1; j < n; j++) {
                    double s = 0.5 * (B.getEntry(i, j) + B.getEntry(j, i));
                    B.setEntry(i, j, s);
                    B.setEntry(j, i, s);
                }

//            System.out.println("Symmetrized B matrix");

            /* (2) Add a tiny ridge so B is positive-semi-definite         */
            for (int i = 0; i < n; i++)
                B.addToEntry(i, i, 1e-10);

//            System.out.println("Added tiny ridge to B matrix");
        }
        /* ----------------------------------------------------------- */

        /* 3. eigen-decomposition */
        EigenDecomposition evd = new EigenDecomposition(B);
        double[] eigVals = evd.getRealEigenvalues();
        RealMatrix eigVecs = evd.getV();

        int[] idx = topPositive(eigVals, 2);             // two largest λ > 0
        double l1 = eigVals[idx[0]];
        double l2 = eigVals[idx[1]];

        /* 4. coordinates */
        for (int i = 0; i < n; i++) {
            double x = Math.sqrt(l1) * eigVecs.getEntry(i, idx[0]);
            double y = Math.sqrt(l2) * eigVecs.getEntry(i, idx[1]);
            coord.put(nodes.get(i), new double[]{x, y});
        }

//        try {
//            writeCsv(Path.of("ncs_coordinates_" + nodes.size() + ".csv")); // NEW: dump to CSV
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to write coordinates to CSV: " + e.getMessage(), e);
//        }
    }

    /**
     * 2-D coordinate of a node (immutable copy).
     */
    public double[] coord(String node) {
        double[] c = coord.get(node);
        if (c == null) throw new IllegalArgumentException("Unknown node " + node);
        return c.clone();
    }

    /* ------------------------------------------------------------------ */

    /**
     * Predicted RTT between a and b (Euclidean distance).
     */
    public double rttEstimate(String a, String b) {
        double[] p = coord(a), q = coord(b);
        double dx = p[0] - q[0], dy = p[1] - q[1];
        return Math.hypot(dx, dy);
    }

    private void collectNodes() {
        for (String key : pairLat.keySet()) {
            String[] pq = key.split(":", 2);
            if (pq.length != 2) throw new IllegalArgumentException("Bad key: " + key);
            for (String v : pq) {
                if (!indexOf.containsKey(v)) {
                    indexOf.put(v, nodes.size());
                    nodes.add(v);
                }
            }
        }
    }

    private double rtt(int i, int j) {
        String a = nodes.get(i), b = nodes.get(j);
        Double d = pairLat.get(a + ":" + b);
        if (d == null) {
            d = pairLat.get(b + ":" + a);    // try reverse
            if (d == null)
                throw new IllegalStateException("Missing latency for " + a + ":" + b);
        }
        return d;
    }

    /**
     * NEW: dump “node,x,y” to *file* for plotting.
     */
    public void writeCsv(Path file) throws Exception {
        try (PrintWriter pw = new PrintWriter(file.toFile(), java.nio.charset.StandardCharsets.UTF_8)) {
            pw.println("node,x,y");
            for (String n : nodes) {
                double[] c = coord.get(n);
                pw.printf(Locale.US, "%s,%.6f,%.6f%n", n, c[0], c[1]);
            }
        }
    }
}
