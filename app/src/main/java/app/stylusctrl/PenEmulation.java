package app.stylusctrl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pen pressure emulation: rebuild the current pen's curve so that it produces the
 * pressure response of a reference pen.
 *
 * Both pens are sampled through the daemon, so:
 *  - the reference pen's samples are already the effective output of the current curve;
 *  - the current pen's samples are inverted to raw input before they are compared.
 * The new curve then maps the current pen's raw percentile to the reference pen's
 * output percentile, which reproduces the reference feel without applying a curve twice.
 */
final class PenEmulation {
    /** Quantiles matched between the two pens. */
    static final float[] PROBES = {0.05f, 0.10f, 0.25f, 0.50f, 0.75f, 0.90f, 0.95f};
    private static final float MAX_SAMPLE = 0.985f;
    private static final float MIN_KNOT_GAP = 0.02f;

    private PenEmulation() {
    }

    static final class Result {
        float[] knots;
        /** Share of reference samples already at the measurable maximum. */
        float refSaturation;
        /** Share of current-pen samples already at the measurable maximum. */
        float targetSaturation;
        /** Highest reference output seen; below 1 means the heavy end was never measured. */
        float refMaxOutput;
        /** True when the top of the curve is extrapolated rather than measured. */
        boolean topExtrapolated;
        boolean ok;
    }

    /**
     * @param curve    current curve in curve domain (x,y pairs, 0..1)
     * @param reference strokes recorded with the reference pen, output domain
     * @param target    strokes recorded with the current pen, output domain
     * @param strength  0 keeps the current curve, 1 applies the emulated one
     */
    static Result build(float[] curve, List<float[]> reference, List<float[]> target, float strength) {
        Result res = new Result();
        if (reference.isEmpty() || target.isEmpty() || curve.length < 4) return res;

        float[] table = MainActivity.inverseTable(curve, MainActivity.INVERSE_STEPS);
        List<float[]> targetRaw = new ArrayList<>();
        for (float[] stroke : target) {
            float[] raw = new float[stroke.length];
            for (int i = 0; i < stroke.length; i++) raw[i] = MainActivity.invert(table, stroke[i]);
            targetRaw.add(raw);
        }

        float[] xs = new float[PROBES.length];
        float[] ys = new float[PROBES.length];
        for (int i = 0; i < PROBES.length; i++) {
            xs[i] = meanQuantile(targetRaw, PROBES[i]);
            ys[i] = meanQuantile(reference, PROBES[i]);
        }

        res.refSaturation = saturation(reference);
        res.targetSaturation = saturation(target);
        res.refMaxOutput = maxOf(reference);
        res.topExtrapolated = ys[PROBES.length - 1] < 0.98f
                || xs[PROBES.length - 1] < 0.95f
                || res.refSaturation > 0.05f;

        float[] rawKnots = knots(xs, ys);
        float[] cleaned = MainActivity.cleanKnots(rawKnots);
        res.knots = MainActivity.blend(curve, cleaned, strength);
        res.ok = res.knots.length >= 4;
        return res;
    }

    /**
     * One quantile per stroke, averaged, so a long stroke cannot outvote the short
     * ones. Both sides use the same probes, which makes the percentiles comparable.
     */
    private static float meanQuantile(List<float[]> strokes, float q) {
        float sum = 0f;
        int n = 0;
        for (float[] stroke : strokes) {
            if (stroke.length < 4) continue;
            float[] sorted = stroke.clone();
            Arrays.sort(sorted);
            sum += MainActivity.percentile(sorted, q);
            n++;
        }
        return n == 0 ? 0f : sum / n;
    }

    /** Flat knots sorted by x, monotone in y, with both ends pinned to 0 and 1. */
    private static float[] knots(float[] xs, float[] ys) {
        ArrayList<Float> flat = new ArrayList<>();
        flat.add(0f);
        flat.add(0f);
        float lastX = 0f;
        float lastY = 0f;
        for (int i = 0; i < xs.length; i++) {
            float x = clamp(xs[i]);
            float y = clamp(ys[i]);
            if (x - lastX < MIN_KNOT_GAP) {
                // The current pen never moved past this point (range cap):
                // keep the highest response there instead of adding a duplicate x.
                if (y > lastY) {
                    flat.set(flat.size() - 1, y);
                    lastY = y;
                }
                continue;
            }
            if (y < lastY) y = lastY;
            flat.add(x);
            flat.add(y);
            lastX = x;
            lastY = y;
        }
        flat.add(1f);
        flat.add(1f);
        float[] out = new float[flat.size()];
        for (int i = 0; i < out.length; i++) out[i] = flat.get(i);
        return out;
    }

    private static float saturation(List<float[]> strokes) {
        int total = 0;
        int hot = 0;
        for (float[] stroke : strokes) {
            for (float v : stroke) {
                total++;
                if (v >= MAX_SAMPLE) hot++;
            }
        }
        return total == 0 ? 0f : (float) hot / total;
    }

    private static float maxOf(List<float[]> strokes) {
        float max = 0f;
        for (float[] stroke : strokes) {
            for (float v : stroke) max = Math.max(max, v);
        }
        return max;
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
