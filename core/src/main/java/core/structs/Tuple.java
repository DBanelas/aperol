package core.structs;

import java.util.Objects;

public class Tuple<X, Y> {
    public final X _1;
    public final Y _2;


    public Tuple(X _1, Y _2) {
        this._1 = _1;
        this._2 = _2;
    }

    @Override
    public String toString() {
        return "(" + _1 + "," + _2 + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof Tuple))
            return false;

        Tuple<?, ?> other = (Tuple<?, ?>) o;

        // Check for unordered equality, taking care of nulls and type safety in comparisons
        boolean normalOrder = (Objects.equals(this._1, other._1)) && (Objects.equals(this._2, other._2));
        boolean reverseOrder = (Objects.equals(this._1, other._2)) && (Objects.equals(this._2, other._1));

        return normalOrder || reverseOrder;
    }

    @Override
    public int hashCode() {
        // Since order doesn't matter, add hash codes to be order-independent
        int hashFirst = _1 == null ? 0 : _1.hashCode();
        int hashSecond = _2 == null ? 0 : _2.hashCode();

        // Combine hash codes in an order-independent manner using a prime number
        return 31 * (hashFirst + hashSecond);
    }
}