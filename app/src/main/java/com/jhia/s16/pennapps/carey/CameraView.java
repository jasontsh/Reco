package com.jhia.s16.pennapps.carey;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Camera;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

/**
 * Created by He on 9/5/2015.
 */
public class CameraView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private SurfaceHolder mHolder = null;
    private Camera mCamera = null;
    private boolean on = false;
    private CameraHandler handler = null;
    private MainActivity mainActivity = null;

    public CameraView(MainActivity main, Context context, Camera camera, CameraHandler handler) {
        super(context);
        mainActivity = main;
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        this.handler = handler;
        mCamera = camera;
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void run() {

    }

    public static Camera getCameraInstance() {
        Camera camera = null;
        try {
            camera = Camera.open();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return camera;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
            on = true;
            setWillNotDraw(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDraw(Canvas cv) {
        super.onDraw(cv);
        synchronized (handler) {
            // handler.drawTargetbox(cv);
        }
        Paint textPaint = new Paint();
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(40);
        // cv.drawText(ia.getStateString(), 50, 50, textPaint);
        invalidate();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mHolder.getSurface() == null) {
            return;
        }

        //handler.drawTargetbox(holder);

        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        on = false;
    }
}
