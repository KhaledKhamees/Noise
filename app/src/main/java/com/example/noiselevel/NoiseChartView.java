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
    private Paint linePaint = new Paint();
    private Paint gridPaint = new Paint();
    private Paint bgPaint = new Paint();

    public NoiseChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        linePaint.setColor(Color.BLUE);
        linePaint.setStrokeWidth(3);
        linePaint.setAntiAlias(true);
        
        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStrokeWidth(1);
        
        bgPaint.setColor(Color.WHITE);
    }

    public void add(double db) {
        data.add(db);
        if (data.size() > 100) data.remove(0);
        invalidate();
    }

    public void clear() {
        data.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        
        canvas.drawRect(0, 0, w, h, bgPaint);
        
        for (int i = 0; i <= 4; i++) {
            float y = h * i / 4;
            canvas.drawLine(0, y, w, y, gridPaint);
        }
        
        if (data.size() < 2) return;
        
        float step = (float) w / 100;
        for (int i = 1; i < data.size(); i++) {
            float x1 = w - (data.size() - i + 1) * step;
            float x2 = w - (data.size() - i) * step;
            
            float y1 = h - (float)(Math.min(data.get(i-1), 100) * h / 100);
            float y2 = h - (float)(Math.min(data.get(i), 100) * h / 100);
            
            canvas.drawLine(x1, y1, x2, y2, linePaint);
        }
    }
}