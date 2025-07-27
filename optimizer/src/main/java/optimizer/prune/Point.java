package optimizer.prune;
import java.util.Arrays;

/**
 * Immutable point in ℝⁿ – any number of coordinates.
 */
public final class Point {

    private final double[] coords;

    public Point(double... coords) {
        if (coords == null || coords.length == 0) {
            throw new IllegalArgumentException("At least one coordinate required");
        }
        this.coords = coords.clone();
    }

    public int dim() {
        return coords.length;
    }

    public double get(int index) {
        return coords[index];
    }

    public double x() { return get(0); }
    public double y() { return dim() > 1 ? get(1) : Double.NaN; }
    public double z() { return dim() > 2 ? get(2) : Double.NaN; }
    public double w() { return dim() > 3 ? get(3) : Double.NaN; }

    public double[] toArray() {
        return coords.clone();
    }

    @Override public String toString() {
        return Arrays.toString(coords);
    }
}