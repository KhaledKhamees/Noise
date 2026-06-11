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

    private static final int MAX_DATA_POINTS = 100;
    private static final double THRESHOLD_LEVEL = 70.0;
    private static final double MIN_DB = 0.0;
    private static final double MAX_DB = 100.0;

    private Paint signalPaint;
    private Paint gridLinePaint;
    private Paint alertLinePaint;
    private Paint textPaint;
    private Paint bgPaint;
    private Paint areaPaint;

    private List<Double> dataPoints;

    public NoiseChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dataPoints = new ArrayList<>();
        initializePaints();
    }

    private void initializePaints() {
        signalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        signalPaint.setColor(Color.parseColor("#1565C0"));
        signalPaint.setStrokeWidth(3f);
        signalPaint.setStyle(Paint.Style.STROKE);

        gridLinePaint = new Paint();
        gridLinePaint.setColor(Color.parseColor("#E0E0E0"));
        gridLinePaint.setStrokeWidth(1f);
        gridLinePaint.setStyle(Paint.Style.STROKE);

        alertLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        alertLinePaint.setColor(Color.parseColor("#D32F2F"));
        alertLinePaint.setStrokeWidth(2f);
        alertLinePaint.setStyle(Paint.Style.STROKE);
        alertLinePaint.setPathEffect(
            new android.graphics.DashPathEffect(new float[]{15f, 8f}, 0f));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#757575"));
        textPaint.setTextSize(28f);

        bgPaint = new Paint();
        bgPaint.setColor(Color.WHITE);
        bgPaint.setStyle(Paint.Style.FILL);

        areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        areaPaint.setColor(Color.parseColor("#1A1565C0"));
        areaPaint.setStyle(Paint.Style.FILL);
    }

    public void addReading(double db) {
        dataPoints.add(db);
        if (dataPoints.size() > MAX_DATA_POINTS) {
            dataPoints.remove(0);
        }
        postInvalidate();
    }

    public void clear() {
        dataPoints.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();

        float leftPad = 50f;
        float rightPad = 12f;
        float topPad = 12f;
        float bottomPad = 30f;

        float drawLeft = leftPad;
        float drawRight = w - rightPad;
        float drawTop = topPad;
        float drawBottom = h - bottomPad;
        float drawW = drawRight - drawLeft;
        float drawH = drawBottom - drawTop;

        canvas.drawRect(0, 0, w, h, bgPaint);

        int[] levels = {0, 25, 50, 70, 100};
        for (int level : levels) {
            float yPos = calculateY(level, drawTop, drawH);
            canvas.drawLine(drawLeft, yPos, drawRight, yPos, gridLinePaint);
            canvas.drawText(level + "", 2f, yPos + 9f, textPaint);
        }

        float alertY = calculateY(THRESHOLD_LEVEL, drawTop, drawH);
        canvas.drawLine(drawLeft, alertY, drawRight, alertY, alertLinePaint);

        if (dataPoints.size() < 2) return;

        float stepX = drawW / (MAX_DATA_POINTS - 1);

        Path line = new Path();
        Path area = new Path();

        for (int idx = 0; idx < dataPoints.size(); idx++) {
            int reverseIdx = (dataPoints.size() - 1) - idx;
            float xPos = drawRight - (reverseIdx * stepX);
            float yPos = calculateY(dataPoints.get(idx), drawTop, drawH);

            if (idx == 0) {
                line.moveTo(xPos, yPos);
                area.moveTo(xPos, drawBottom);
                area.lineTo(xPos, yPos);
            } else {
                line.lineTo(xPos, yPos);
                area.lineTo(xPos, yPos);
            }
        }

        float rightEdge = drawRight;
        area.lineTo(rightEdge, drawBottom);
        area.close();

        canvas.drawPath(area, areaPaint);
        canvas.drawPath(line, signalPaint);
    }

    private float calculateY(double dbValue, float top, float height) {
        double clipped = Math.max(MIN_DB, Math.min(MAX_DB, dbValue));
        double norm = (clipped - MIN_DB) / (MAX_DB - MIN_DB);
        return (float) (top + height * (1.0 - norm));
    }
}