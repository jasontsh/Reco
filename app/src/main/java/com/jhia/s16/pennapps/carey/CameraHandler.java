package com.jhia.s16.pennapps.carey;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.List;

/**
 * Created by He on 9/5/2015.
 */
public class CameraHandler {

    private static final int LINE_SPACING = 6;
    private static final int TEXT_X_MARGIN = 5;
    private static final int TEXT_Y_MARGIN = 4;
    private static final int TEXT_HEIGHT = 25;

    public void drawInformation(Canvas cv, List<String> list, float x, float y) {
        if (cv == null) {
            return;
        }
        Paint paint = new Paint();
        Paint.FontMetrics fm = paint.getFontMetrics();
        paint.setTextSize(25);
        paint.setColor(0xFFFFFFFF);
        int maxWidth = 0;
        int maxHeight = 0;
        for (int i = 0; i < list.size(); ++i) {
            cv.drawText(list.get(i), x, y + LINE_SPACING + TEXT_HEIGHT, paint);
        }
    }


}
