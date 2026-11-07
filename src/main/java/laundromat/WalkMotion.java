package laundromat;

import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.Deque;

/** EDT-owned visual motion. It never sleeps, acquires resources or changes customer work. */
final class WalkMotion {
    private double x, y, goalX, goalY;
    private double speed;
    private final Deque<Point2D.Double> route = new ArrayDeque<>();

    WalkMotion(double x, double y, double speed) {
        this.x = goalX = x;
        this.y = goalY = y;
        this.speed = speed;
    }
    double x() { return x; }
    double y() { return y; }
    boolean moving() { return !route.isEmpty(); }
    void speed(double pixelsPerSecond) { speed = pixelsPerSecond; }

    void target(double x, double y, boolean useAisle) {
        if (Math.abs(x - goalX) < .01 && Math.abs(y - goalY) < .01) return;
        goalX = x; goalY = y;
        route.clear(); // Retarget from the current position; never teleport to a former endpoint.
        if (useAisle && Math.abs(this.x - x) > 130 && Math.hypot(this.x-x, this.y-y)>150) {
            route.add(new Point2D.Double(this.x, 396));
            route.add(new Point2D.Double(x, 396));
        }
        route.add(new Point2D.Double(x, y));
    }

    void advance(double elapsedSeconds) {
        double distance = Math.max(0, elapsedSeconds) * speed;
        while (!route.isEmpty()) {
            Point2D.Double point = route.peek();
            double dx=point.x-x, dy=point.y-y, length=Math.hypot(dx,dy);
            if (length <= distance + 1e-9) {
                x=point.x; y=point.y;
                distance -= length;
                route.remove();
            } else {
                x += dx/length*distance;
                y += dy/length*distance;
                break;
            }
        }
    }
}

