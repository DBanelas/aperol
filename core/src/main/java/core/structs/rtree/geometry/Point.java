package core.structs.rtree.geometry;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import core.structs.rtree.ObjectsHelper;

public final class Point implements Geometry {

    private final Rectangle mbr;

    public Point(float x, float y) {
        this.mbr = Rectangle.create(x, y, x, y);
    }

    //TODO extend for more
    public Point(double[] coords) {
        this.mbr = Rectangle.create(coords[0], coords[1], coords[0], coords[1]);
    }

    public static Point create(double x, double y) {
        return new Point((float) x, (float) y);
    }

    @Override
    public Rectangle mbr() {
        return mbr;
    }

    @Override
    public double distance(Rectangle r) {
        return mbr.distance(r);
    }

    public double distance(Point p) {
        float dx = mbr().x1() - p.mbr().x1();
        float dy = mbr().y1() - p.mbr().y1();
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public boolean intersects(Rectangle r) {
        return mbr.intersects(r);
    }

    public float x() {
        return mbr.x1();
    }

    public float y() {
        return mbr.y1();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mbr);
    }

    @Override
    public boolean equals(Object obj) {
        Optional<Point> other = ObjectsHelper.asClass(obj, Point.class);
        if (other.isPresent()) {
            return Objects.equal(mbr, other.get().mbr());
        } else
            return false;
    }

    @Override
    public String toString() {
        return "Point [x=" + x() + ", y=" + y() + "]";
    }

}