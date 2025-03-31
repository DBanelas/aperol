package optimizer.algorithm.cost.distribution;

public class HistogramBin {

    private final double binStart;
    private final double binEnd;

    public HistogramBin(double binStart, double binEnd) {
        this.binStart = binStart;
        this.binEnd = binEnd;
    }

    public double getBinStart() { return binStart; }
    public double getBinEnd() { return binEnd; }

    @Override
    public String toString() {
        return "Bin [" + binStart + ", " + binEnd + "]";
    }
}
