package core.structs.rtree.geometry;

public final class Geometries {

    private Geometries() {
        // prevent instantiation
    }

    public static Point point(double x, double y) {
        return Point.create(x, y);
    }

    public static Rectangle rectangle(double x1, double y1, double x2, double y2) {
        return Rectangle.create(x1, y1, x2, y2);
    }

    public static Rectangle rectangle(Point p, double x, double y) {
        return Rectangle.create(p.x(), p.y(), x, y);
    }

    public static Circle circle(double x, double y, double radius) {
        return Circle.create(x, y, radius);
    }

}
