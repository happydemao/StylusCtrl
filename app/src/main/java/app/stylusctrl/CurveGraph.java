package app.stylusctrl;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.material.color.MaterialColors;

/**
 * Pressure curve with any number of draggable knots. X is input pressure, Y is output.
 * The first and last knots are the deadzones. Tapping empty space adds a knot, which
 * keeps the editor usable with a pen: no long-press menus, no separate add button.
 */
public class CurveGraph extends View {
    public interface Listener {
        void onCurveChanged(float[] xy);
    }

    public static final int MAX_POINTS = 12;
    private static final int SAMPLES = 160;
    private static final float MIN_GAP = 0.015f;

    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF plot = new RectF();
    private float[] pts = new float[] {0f, 0f, 0.25f, 0.424f, 0.5f, 0.717f, 0.75f, 0.873f, 1f, 1f};
    private float[] preview;
    private Listener listener;
    private int drag = -1;

    public CurveGraph(Context context, AttributeSet attrs) {
        super(context, attrs);
        int primary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0xFF1B6B66);
        int outline = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, 0xFF7D7670);
        int onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF1C1B1A);
        int surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0xFFFFFBFE);
        axisPaint.setStyle(Paint.Style.STROKE);
        axisPaint.setStrokeWidth(dp(1.5f));
        axisPaint.setColor(outline);
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(dp(1));
        guidePaint.setColor(outline);
        guidePaint.setAlpha(120);
        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(dp(3));
        curvePaint.setStrokeCap(Paint.Cap.ROUND);
        curvePaint.setStrokeJoin(Paint.Join.ROUND);
        curvePaint.setColor(primary);
        previewPaint.setStyle(Paint.Style.STROKE);
        previewPaint.setStrokeWidth(dp(2));
        previewPaint.setColor(primary);
        previewPaint.setAlpha(140);
        previewPaint.setPathEffect(new DashPathEffect(new float[] {dp(7), dp(6)}, 0));
        handlePaint.setColor(primary);
        handleEdgePaint.setStyle(Paint.Style.STROKE);
        handleEdgePaint.setStrokeWidth(dp(2));
        handleEdgePaint.setColor(surface);
        labelPaint.setColor(onSurface);
        labelPaint.setTextSize(dp(13));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Absolute knots, x,y pairs, sorted by x. Endpoints are the two deadzones. */
    public void setPoints(float[] xy) {
        if (xy == null || xy.length < 4 || (xy.length % 2) != 0) return;
        pts = xy.clone();
        drag = -1;
        invalidate();
    }

    public float[] getPoints() {
        return pts.clone();
    }

    public int pointCount() {
        return pts.length / 2;
    }

    /** Dashed target shown while adjusting from a stroke. Null clears it. */
    public void setPreview(float[] xy) {
        if (xy == null || xy.length < 4) preview = null;
        else preview = xy.clone();
        invalidate();
    }

    /** Inserts a knot in the largest gap, at the height the curve already has there. */
    public boolean addPoint() {
        int n = pts.length / 2;
        if (n >= MAX_POINTS) return false;
        int best = 0;
        float gap = -1f;
        for (int i = 0; i < n - 1; i++) {
            float g = pts[i * 2 + 2] - pts[i * 2];
            if (g > gap) {
                gap = g;
                best = i;
            }
        }
        if (gap <= MIN_GAP * 2) return false;
        float x = (pts[best * 2] + pts[best * 2 + 2]) / 2f;
        float y = eval(pts, n, x);
        float[] next = new float[pts.length + 2];
        System.arraycopy(pts, 0, next, 0, (best + 1) * 2);
        next[(best + 1) * 2] = x;
        next[(best + 1) * 2 + 1] = y;
        System.arraycopy(pts, (best + 1) * 2, next, (best + 2) * 2, pts.length - (best + 1) * 2);
        pts = next;
        drag = best + 1;
        notifyChanged();
        invalidate();
        return true;
    }

    /** Removes the knot in the middle of the longest span. Endpoints stay. */
    public boolean removePoint() {
        int n = pts.length / 2;
        if (n <= 2) return false;
        int best = 1;
        float gap = -1f;
        for (int i = 1; i < n - 1; i++) {
            float g = pts[i * 2 + 2] - pts[i * 2 - 2];
            if (g > gap) {
                gap = g;
                best = i;
            }
        }
        float[] next = new float[pts.length - 2];
        System.arraycopy(pts, 0, next, 0, best * 2);
        System.arraycopy(pts, best * 2 + 2, next, best * 2, pts.length - best * 2 - 2);
        pts = next;
        drag = -1;
        notifyChanged();
        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        layoutPlot();
        canvas.drawText(getContext().getString(R.string.curve_light), plot.left, getHeight() - dp(6), labelPaint);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(getContext().getString(R.string.curve_heavy), plot.right, getHeight() - dp(6), labelPaint);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(getContext().getString(R.string.curve_output), plot.left, plot.top - dp(8), labelPaint);
        canvas.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, axisPaint);
        canvas.drawLine(plot.left, plot.bottom, plot.left, plot.top, axisPaint);
        canvas.drawLine(plot.left, plot.bottom, plot.right, plot.top, guidePaint);

        int n = pts.length / 2;
        drawCurve(canvas, pts, n, curvePaint);
        if (preview != null && preview.length >= 4) {
            drawCurve(canvas, preview, preview.length / 2, previewPaint);
        }
        for (int i = 0; i < n; i++) {
            float hx = plotX(pts[i * 2]);
            float hy = plotY(pts[i * 2 + 1]);
            float r = dp(i == drag ? 10 : (i == 0 || i == n - 1 ? 9 : 7));
            canvas.drawCircle(hx, hy, r, handlePaint);
            canvas.drawCircle(hx, hy, r, handleEdgePaint);
        }
    }

    private void drawCurve(Canvas canvas, float[] knots, int n, Paint paint) {
        if (n < 2) return;
        path.reset();
        for (int i = 0; i <= SAMPLES; i++) {
            float x = i / (float) SAMPLES;
            float y = eval(knots, n, x);
            if (i == 0) path.moveTo(plotX(x), plotY(y));
            else path.lineTo(plotX(x), plotY(y));
        }
        canvas.drawPath(path, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        layoutPlot();
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            drag = hit(event.getX(), event.getY());
            if (drag < 0 && pts.length / 2 < MAX_POINTS) {
                int added = insert(event.getX(), event.getY());
                if (added >= 0) {
                    drag = added;
                    notifyChanged();
                }
            }
            if (drag < 0) return false;
            move(event.getX(), event.getY());
            return true;
        }
        if (drag < 0) return false;
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            move(event.getX(), event.getY());
            if (action == MotionEvent.ACTION_UP) {
                drag = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            drag = -1;
            getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
        return false;
    }

    private int insert(float x, float y) {
        int n = pts.length / 2;
        float nx = clamp((x - plot.left) / plot.width(), 0f, 1f);
        float ny = clamp(1f - (y - plot.top) / plot.height(), 0f, 1f);
        int at = 1;
        while (at < n && pts[at * 2] < nx) at++;
        float left = pts[(at - 1) * 2];
        float right = at < n ? pts[at * 2] : 1f;
        if (right - left < MIN_GAP * 2) return -1;
        nx = clamp(nx, left + MIN_GAP, right - MIN_GAP);
        float[] next = new float[pts.length + 2];
        System.arraycopy(pts, 0, next, 0, at * 2);
        next[at * 2] = nx;
        next[at * 2 + 1] = ny;
        System.arraycopy(pts, at * 2, next, (at + 1) * 2, pts.length - at * 2);
        pts = next;
        invalidate();
        return at;
    }

    private void move(float x, float y) {
        int n = pts.length / 2;
        float nx = clamp((x - plot.left) / plot.width(), 0f, 1f);
        float ny = clamp(1f - (y - plot.top) / plot.height(), 0f, 1f);
        float min = drag == 0 ? 0f : pts[(drag - 1) * 2] + MIN_GAP;
        float max = drag == n - 1 ? 1f : pts[(drag + 1) * 2] - MIN_GAP;
        if (max < min) max = min;
        pts[drag * 2] = clamp(nx, min, max);
        pts[drag * 2 + 1] = ny;
        notifyChanged();
        invalidate();
    }

    private int hit(float x, float y) {
        float slop = dp(28);
        int best = -1;
        float bestDist = slop;
        int n = pts.length / 2;
        for (int i = 0; i < n; i++) {
            float hx = plotX(pts[i * 2]);
            float hy = plotY(pts[i * 2 + 1]);
            float d = (float) Math.hypot(x - hx, y - hy);
            if (d <= slop && (best < 0 || d < bestDist)) {
                best = i;
                bestDist = d;
            }
        }
        return best;
    }

    private void notifyChanged() {
        if (listener != null) listener.onCurveChanged(getPoints());
    }

    private void layoutPlot() {
        float left = getPaddingLeft() + dp(12);
        float right = getWidth() - getPaddingRight() - dp(12);
        float top = getPaddingTop() + dp(28);
        float bottom = getHeight() - getPaddingBottom() - dp(32);
        float size = Math.min(right - left, bottom - top);
        float cx = (left + right) / 2f;
        float cy = (top + bottom) / 2f;
        plot.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
    }

    private float plotX(float t) {
        return plot.left + plot.width() * t;
    }

    private float plotY(float t) {
        return plot.bottom - plot.height() * t;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    /** Monotone cubic through the knots. Same slopes the daemon uses, so the preview is honest. */
    static float eval(float[] p, int n, float x) {
        if (n < 2) return x;
        if (x <= p[0]) return p[1];
        if (x >= p[(n - 1) * 2]) return p[(n - 1) * 2 + 1];
        float[] d = new float[n];
        for (int i = 0; i < n - 1; i++) {
            float h = p[(i + 1) * 2] - p[i * 2];
            if (h <= 0) h = 1e-6f;
            d[i] = (p[(i + 1) * 2 + 1] - p[i * 2 + 1]) / h;
        }
        float[] m = new float[n];
        m[0] = d[0];
        m[n - 1] = d[n - 2];
        for (int i = 1; i < n - 1; i++) m[i] = (d[i - 1] * d[i] <= 0) ? 0 : (d[i - 1] + d[i]) / 2;
        for (int i = 0; i < n - 1; i++) {
            if (d[i] == 0) {
                m[i] = 0;
                m[i + 1] = 0;
            } else {
                float a = m[i] / d[i];
                float b = m[i + 1] / d[i];
                float s = a * a + b * b;
                if (s > 9) {
                    float k = (float) (3 / Math.sqrt(s));
                    m[i] = k * a * d[i];
                    m[i + 1] = k * b * d[i];
                }
            }
        }
        int i = 0;
        while (i < n - 2 && x > p[(i + 1) * 2]) i++;
        float x0 = p[i * 2];
        float x1 = p[(i + 1) * 2];
        float h = x1 - x0;
        if (h <= 0) return p[(i + 1) * 2 + 1];
        float u = (x - x0) / h;
        float u2 = u * u;
        float u3 = u2 * u;
        float y = (2 * u3 - 3 * u2 + 1) * p[i * 2 + 1] + (u3 - 2 * u2 + u) * h * m[i]
                + (-2 * u3 + 3 * u2) * p[(i + 1) * 2 + 1] + (u3 - u2) * h * m[i + 1];
        return clamp(y, 0f, 1f);
    }
}
