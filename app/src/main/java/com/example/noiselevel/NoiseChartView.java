package com.example.noiselevel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;

public class NoiseChartView extends View {
    private ArrayList<Double> data = new ArrayList<>();
    private Paint paint = new Paint();

    public NoiseChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(Color.BLUE);
        paint.setStrokeWidth(2);
    }

    public void add(double db) {
        data.add(db);
        if (data.size() > 100) data.remove(0);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (data.size() < 2) return;
        
        float step = (float) w / 100;
        for (int i = 1; i < data.size(); i++) {
            float x1 = (data.size() - i) * step;
            float y1 = h - (float)(data.get(i-1) * h / 100);
            float x2 = (data.size() - i + 1) * step;
            float y2 = h - (float)(data.get(i) * h / 100);
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
    }
}