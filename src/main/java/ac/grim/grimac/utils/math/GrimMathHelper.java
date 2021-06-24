package ac.grim.grimac.utils.math;

import java.util.List;

public class GrimMathHelper {
    public static int floor(double d) {
        return (int) Math.floor(d);
    }

    public static int ceil(double d) {
        return (int) Math.ceil(d);
    }

    public static double clamp(double d, double d2, double d3) {
        if (d < d2) {
            return d2;
        }
        return Math.min(d, d3);
    }

    public static boolean equal(double d, double d2) {
        return Math.abs(d2 - d) < 9.999999747378752E-6;
    }

    public static double calculateAverage(List<Integer> marks) {
        long sum = 0;
        for (Integer mark : marks) {
            sum += mark;
        }
        return marks.isEmpty() ? 0 : 1.0 * sum / marks.size();
    }
}
