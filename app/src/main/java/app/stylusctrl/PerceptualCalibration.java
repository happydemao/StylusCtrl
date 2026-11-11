package app.stylusctrl;

import java.util.ArrayList;
import java.util.List;

/**
 * Perceptual matching: instead of asking the user to reproduce a force, the app
 * presents both pens at the same output level and the user only says which one
 * needed more pressure. A staircase moves the current pen's curve at that output
 * level until the two feel equal (the point of subjective equality), and the
 * levels are interleaved so fatigue does not bias one of them.
 */
final class PerceptualCalibration {
    enum Judgment { REFERENCE_HARDER, TARGET_HARDER, SIMILAR }

    static final float[] LEVELS = {0.15f, 0.35f, 0.55f, 0.75f, 0.90f};
    static final int TRIALS_PER_LEVEL = 3;
    static final float INITIAL_STEP = 0.08f;
    static final float MIN_STEP = 0.02f;
    private static final float MIN_GAP = 0.02f;
    private static final float X_MIN = 0.03f;
    private static final float X_MAX = 0.97f;
    /** Without a direction reversal the staircase would run away; keep the shift sane. */
    private static final float MAX_SHIFT = 0.20f;

    private PerceptualCalibration() {
    }

    static final class Level {
        final float output;
        final float startX;
        /** Curve-domain input where this level currently sits. */
        float x;
        float step = INITIAL_STEP;
        int direction;
        int similar;
        int trials;
        final List<Float> seen = new ArrayList<>();

        Level(float output, float startX) {
            this.output = output;
            this.startX = startX;
            this.x = startX;
        }

        boolean done() {
            return trials >= TRIALS_PER_LEVEL || similar >= 2 || step <= MIN_STEP + 1e-4f;
        }

        float estimate() {
            int from = Math.max(0, seen.size() - 3);
            float sum = 0f;
            int n = 0;
            for (int i = from; i < seen.size(); i++) {
                sum += seen.get(i);
                n++;
            }
            return n == 0 ? x : sum / n;
        }

        float confidence() {
            float byStep = 1f - Math.min(1f, step / INITIAL_STEP);
            float byTrials = Math.min(1f, trials / (float) TRIALS_PER_LEVEL);
            float bySimilar = Math.min(1f, similar / 2f);
            return Math.max(0f, Math.min(1f, 0.35f * byStep + 0.35f * byTrials + 0.3f * bySimilar));
        }

        /** One answer from the user: move x, and shrink the step when the direction turns. */
        void update(Judgment judgment) {
            if (judgment == Judgment.SIMILAR) {
                // "Cannot tell them apart" is the answer we are looking for.
                similar++;
                step = Math.max(MIN_STEP, step * 0.5f);
            } else {
                int next = judgment == Judgment.TARGET_HARDER ? -1 : 1;
                if (direction != 0 && next != direction) {
                    step = Math.max(MIN_STEP, step * 0.5f);
                }
                direction = next;
                x = clamp(x + next * step, Math.max(X_MIN, startX - MAX_SHIFT),
                        Math.min(X_MAX, startX + MAX_SHIFT));
            }
            seen.add(x);
            trials++;
        }
    }

    /**
     * Anchors for the five output levels. Untested levels keep the current curve's
     * position, so the candidate curve stays close to what the user already has.
     */
    static float[] curve(List<Level> levels, float[] original) {
        if (original.length < 4) return Profile.DEFAULT_CURVE.clone();
        float[] table = MainActivity.inverseTable(original, MainActivity.INVERSE_STEPS);
        float[] raw = new float[4 + levels.size() * 2];
        raw[0] = 0f;
        raw[1] = 0f;
        float last = 0f;
        for (int i = 0; i < levels.size(); i++) {
            Level level = levels.get(i);
            float x = level.trials == 0 ? MainActivity.invert(table, level.output) : level.estimate();
            x = clamp(x, X_MIN, X_MAX);
            x = Math.max(x, last + MIN_GAP);
            x = Math.min(x, 0.97f - (levels.size() - 1 - i) * MIN_GAP);
            raw[i * 2 + 2] = x;
            raw[i * 2 + 3] = clamp(level.output, 0f, 1f);
            last = x;
        }
        raw[raw.length - 2] = 1f;
        raw[raw.length - 1] = 1f;
        return MainActivity.cleanKnots(raw);
    }

    /** Builds all five level states around the current curve. */
    static List<Level> levels(float[] original) {
        float[] table = MainActivity.inverseTable(original, MainActivity.INVERSE_STEPS);
        List<Level> out = new ArrayList<>();
        for (float level : LEVELS) {
            out.add(new Level(level, MainActivity.invert(table, level)));
        }
        return out;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
