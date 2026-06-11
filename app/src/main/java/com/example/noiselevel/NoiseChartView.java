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

public class NoiseChartView extends View {

    private static final int MAX_POINTS = 100;
    private static final double ALERT_DB = 70.0;
    private static final double DB_MIN = 0.0;
    private static final double DB_MAX = 100.0;

    private final Paint linePaint;
    private final Paint gridPaint;
    private final Paint thresholdPaint;
    private final Paint labelPaint;
    private final Paint backgroundPaint;
    private final Paint fillPaint;

    private final List<Double> readings = new ArrayList<>();

    public NoiseChartView(Context context, AttributeSet attrs) {
        super(context, attrs);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#1565C0"));
        linePaint.setStrokeWidth(3f);
        linePaint.setStyle(Paint.Style.STROKE);

        gridPaint = new Paint();
        gridPaint.setColor(Color.parseColor("#E0E0E0"));
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);

        thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thresholdPaint.setColor(Color.parseColor("#D32F2F"));
        thresholdPaint.setStrokeWidth(2f);
        thresholdPaint.setStyle(Paint.Style.STROKE);
        thresholdPaint.setPathEffect(
                new android.graphics.DashPathEffect(new float[]{15f, 8f}, 0f)
        );

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#757575"));
        labelPaint.setTextSize(28f);

        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.WHITE);
        backgroundPaint.setStyle(Paint.Style.FILL);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(Color.parseColor("#1A1565C0"));
        fillPaint.setStyle(Paint.Style.FILL);
    }

    public void addReading(double db) {
        readings.add(db);

        if (readings.size() > MAX_POINTS) {
            readings.remove(0);
        }

        postInvalidate();
    }

    public void clear() {
        readings.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width  = getWidth();
        int height = getHeight();

        float paddingLeft   = 50f;
        float paddingRight  = 12f;
        float paddingTop    = 12f;
        float paddingBottom = 30f;

        float chartLeft   = paddingLeft;
        float chartRight  = width  - paddingRight;
        float chartTop    = paddingTop;
        float chartBottom = height - paddingBottom;
        float chartWidth  = chartRight  - chartLeft;
        float chartHeight = chartBottom - chartTop;

        canvas.drawRect(0, 0, width, height, backgroundPaint);

        int[] gridLevels = {0, 25, 50, 70, 100};
        for (int level : gridLevels) {
            float y = dbToY(level, chartTop, chartHeight);
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint);
            canvas.drawText(level + "", 2f, y + 9f, labelPaint);
        }

        float thresholdY = dbToY(ALERT_DB, chartTop, chartHeight);
        canvas.drawLine(chartLeft, thresholdY, chartRight, thresholdY, thresholdPaint);

        if (readings.size() < 2) return;

        float xStep = chartWidth / (MAX_POINTS - 1);

        Path linePath = new Path();
        Path fillPath = new Path();

        for (int i = 0; i < readings.size(); i++) {
            int indexFromRight = (readings.size() - 1) - i;
            float x = chartRight - (indexFromRight * xStep);
            float y = dbToY(readings.get(i), chartTop, chartHeight);

            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, chartBottom);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        float lastX = chartRight;
        fillPath.lineTo(lastX, chartBottom);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }

    private float dbToY(double db, float chartTop, float chartHeight) {
        double clamped = Math.max(DB_MIN, Math.min(DB_MAX, db));
        double normalized = (clamped - DB_MIN) / (DB_MAX - DB_MIN);
        return (float) (chartTop + chartHeight * (1.0 - normalized));
    }
}