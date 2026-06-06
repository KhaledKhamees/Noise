package com.example.noiselevel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * NoiseChartView is a custom View that draws a live line chart of dB readings.
 *
 * Why custom and not a library?
 *   • Keeps the project dependency-free.
 *   • The chart logic is simple enough (≈ 100 lines) that a library would be overkill.
 *   • We get full control over style.
 *
 * How it works:
 *   • addReading(double db) appends a new value and calls invalidate() to trigger redraw.
 *   • onDraw() maps each stored value to an (x, y) pixel position and draws a polyline.
 *   • A horizontal dashed red line at 70 dB marks the alert threshold.
 *   • We cap the stored readings at MAX_POINTS so the chart doesn't grow forever.
 */
public class NoiseChartView extends View {

    // ── Configuration constants ──────────────────────────────────────────────

    /** Maximum number of data points shown on screen at once. */
    private static final int MAX_POINTS = 100;

    /** The alert threshold — a red dashed line is drawn here. */
    private static final double ALERT_DB = 70.0;

    /** Y-axis range: we display 0 dB to DB_MAX dB vertically. */
    private static final double DB_MIN = 0.0;
    private static final double DB_MAX = 100.0;

    // ── Paint objects (created once, reused every draw frame) ────────────────

    private final Paint linePaint;        // The main signal line
    private final Paint gridPaint;        // Horizontal grid lines
    private final Paint thresholdPaint;   // Dashed red line at 70 dB
    private final Paint labelPaint;       // Axis labels (text)
    private final Paint backgroundPaint;  // Chart background fill
    private final Paint fillPaint;        // Semi-transparent area under the line

    // ── Data ─────────────────────────────────────────────────────────────────

    private final List<Double> readings = new ArrayList<>();

    // ── Constructor ──────────────────────────────────────────────────────────

    public NoiseChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Main signal line — blue, 3px wide, anti-aliased
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#1565C0"));
        linePaint.setStrokeWidth(3f);
        linePaint.setStyle(Paint.Style.STROKE);

        // Soft grey grid lines
        gridPaint = new Paint();
        gridPaint.setColor(Color.parseColor("#E0E0E0"));
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);

        // Dashed red threshold line
        thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thresholdPaint.setColor(Color.parseColor("#D32F2F"));
        thresholdPaint.setStrokeWidth(2f);
        thresholdPaint.setStyle(Paint.Style.STROKE);
        // Dashes: 15px on, 8px off
        thresholdPaint.setPathEffect(
                new android.graphics.DashPathEffect(new float[]{15f, 8f}, 0f)
        );

        // Text labels on the Y-axis
        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#757575"));
        labelPaint.setTextSize(28f);

        // White background
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.WHITE);
        backgroundPaint.setStyle(Paint.Style.FILL);

        // Light-blue fill under the line
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(Color.parseColor("#1A1565C0")); // 10% opacity blue
        fillPaint.setStyle(Paint.Style.FILL);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Append a new dB reading and refresh the chart.
     * Safe to call from any thread — invalidate() is thread-safe.
     *
     * @param db  The latest noise reading in decibels.
     */
    public void addReading(double db) {
        readings.add(db);

        // Trim the oldest entries so we don't accumulate unbounded data
        if (readings.size() > MAX_POINTS) {
            readings.remove(0);
        }

        // Request a redraw on the next VSYNC frame
        postInvalidate(); // postInvalidate is safe from background threads
    }

    /** Clear all stored readings and repaint. */
    public void clear() {
        readings.clear();
        invalidate();
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width  = getWidth();
        int height = getHeight();

        // Internal padding so labels aren't clipped
        float paddingLeft   = 50f;
        float paddingRight  = 12f;
        float paddingTop    = 12f;
        float paddingBottom = 30f;

        // The drawable area for the chart lines
        float chartLeft   = paddingLeft;
        float chartRight  = width  - paddingRight;
        float chartTop    = paddingTop;
        float chartBottom = height - paddingBottom;
        float chartWidth  = chartRight  - chartLeft;
        float chartHeight = chartBottom - chartTop;

        // 1. Fill background
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        // 2. Draw Y-axis grid lines and labels (0, 25, 50, 75, 100 dB)
        int[] gridLevels = {0, 25, 50, 70, 100};
        for (int level : gridLevels) {
            float y = dbToY(level, chartTop, chartHeight);
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint);

            // Label on the left margin
            canvas.drawText(level + "", 2f, y + 9f, labelPaint);
        }

        // 3. Draw the 70 dB threshold line (dashed red, on top of grid)
        float thresholdY = dbToY(ALERT_DB, chartTop, chartHeight);
        canvas.drawLine(chartLeft, thresholdY, chartRight, thresholdY, thresholdPaint);

        // 4. Draw the signal line (and filled area beneath it)
        if (readings.size() < 2) return; // Nothing to draw yet

        float xStep = chartWidth / (MAX_POINTS - 1); // pixel gap between data points

        Path linePath = new Path();
        Path fillPath = new Path();

        for (int i = 0; i < readings.size(); i++) {
            // Offset from the right so new data "pushes" old data left
            int indexFromRight = (readings.size() - 1) - i;
            float x = chartRight - (indexFromRight * xStep);
            float y = dbToY(readings.get(i), chartTop, chartHeight);

            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, chartBottom);  // start fill from bottom-left
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        // Close the fill path back down to the bottom baseline
        float lastX = chartRight; // the most recent point is at the right edge
        fillPath.lineTo(lastX, chartBottom);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * Maps a dB value to a Y pixel coordinate within the chart area.
     *
     * DB_MAX maps to chartTop (top of screen = loudest).
     * DB_MIN maps to chartBottom (bottom of screen = silent).
     */
    private float dbToY(double db, float chartTop, float chartHeight) {
        // Clamp to visible range
        double clamped = Math.max(DB_MIN, Math.min(DB_MAX, db));

        // Normalise to [0, 1] where 1 = max
        double normalized = (clamped - DB_MIN) / (DB_MAX - DB_MIN);

        // Invert because screen Y increases downward
        return (float) (chartTop + chartHeight * (1.0 - normalized));
    }
}