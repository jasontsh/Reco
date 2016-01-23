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

    private static enum DRAW_MODE {
        SQUARE_LEFT, // when the face square is to the right
        SQUARE_RIGHT, // when the face square is to the left
        SQUARE_INNER // when the face square is too big
    }

    public void drawInformation(Canvas cv, List<String> list, float x, float y) {
        if (cv == null) {
            return;
        }
        Paint paint = new Paint();
        Paint.FontMetrics fm = paint.getFontMetrics();
        paint.setTextSize(25);
        paint.setColor(0xFFFFFFFF);
        float maxWidth = 0;
        float maxHeight = fm.descent - fm.ascent + fm.leading;
        float totalHeight = 0;
        for (String s : list) {
            maxWidth = Math.max(maxWidth, paint.measureText(s));
            totalHeight += maxHeight;
        }
        totalHeight += TEXT_Y_MARGIN;
        // dimensions of the draw text should now be
        for (int i = 0; i < list.size(); ++i) {
            cv.drawText(list.get(i), x + TEXT_X_MARGIN, y + LINE_SPACING + TEXT_HEIGHT, paint);
        }
        cv.drawRect(x, y, x + maxWidth + 2 * TEXT_X_MARGIN, y + totalHeight + 2 * TEXT_Y_MARGIN, paint);
    }


}
