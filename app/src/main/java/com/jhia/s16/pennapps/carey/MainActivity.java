package com.jhia.s16.pennapps.carey;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.logging.Filter;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends AppCompatActivity {
    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    protected static final int REQUEST_OK = 100;

    private ImageRecognition rec;

    private boolean takingPictures = true, init = true;
    public static String basePictureDir = null;
    private int whoIsCurrent = 0;

    private static final int PICTURE_DELAY = 20000;
    private static final int IMAGE_CAPTURE_ID = 1;
    private final Handler cameraHandler = new Handler();

    private Camera mCamera = null;
    private CameraView mCameraView = null;
    private CameraHandler mCameraHandler = null;
    private Camera.PreviewCallback previewCallback = null;
    private Activity mActivity = this;

    private Bitmap mostRecentImage = null;
    private Semaphore semaphore = new Semaphore(1);
    private boolean imageFresh = false;
    private boolean forceImage = false;

    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mCameraView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };


    private final Runnable mTakePicturesOfPeople = new Runnable() {
        @Override
        public void run() {
            if (init) {
                rec.init();
                init = false;
            }
            while (takingPictures) {
                takePicture("random");
                cameraHandler.postDelayed(this, PICTURE_DELAY);
            }
        }
    };

    private final void savePicture(String fileName, Bitmap img) {
        try {
            String directoryName = basePictureDir;
            File directory = new File(directoryName);
            directory.mkdirs();
            File imgFile = new File(directoryName + "/" + fileName + ".png");
            imgFile.createNewFile();
            FileOutputStream fout = new FileOutputStream(imgFile);
            img.compress(Bitmap.CompressFormat.PNG, 85, fout);
            fout.flush();
            fout.close();
        } catch (IOException io) {
            io.printStackTrace();
        }
    }


    private final void takePicture(String fileName) {
        String directoryName = basePictureDir;
        File directory = new File(directoryName);
        directory.mkdirs();
        // File pictureFile = new File(directoryName + "/" + fileName + ".jpg");
        if (mCameraView == null) {
            System.out.println("ERROR mCameraView null");
            return;
        }
        mCameraView.captureImage(directoryName + "/" + fileName + ".jpg");
        //try {
            //pictureFile.createNewFile();
        //} catch (IOException ioException) {
        //    ioException.printStackTrace();
        //}
        // Uri outputFileUri = Uri.fromFile(pictureFile);
        // Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
        // startActivityForResult(cameraIntent, IMAGE_CAPTURE_ID);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        basePictureDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        .getAbsolutePath() + "/reco/";
        File dir = new File(basePictureDir);
        dir.mkdirs();

        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(mActivity,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(mActivity,
                    Manifest.permission.CAMERA)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(mActivity,
                        new String[]{Manifest.permission.CAMERA},
                        2);



                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
        mCamera = CameraView.getCameraInstance();
        if (mCamera == null) {

            mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            if (mCamera == null) {
                mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
            }
        }
        mCamera.setDisplayOrientation(90);
        Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
        previewCallback = new Camera.PreviewCallback() {

            long time = 0;
            long counter = 0;

            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                execute(data);
            }

            private void execute(byte[] data) {
                if (time == 0) {
                    time = System.currentTimeMillis();
                }
                Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                YuvImage yuvImg =
                        new YuvImage(data, ImageFormat.NV21, previewSize.width, previewSize.height, null);
                yuvImg.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 50, out);
                byte[] imageBytes = out.toByteArray();
                Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                if (image == null) {
                    System.out.println("NULL IMAGE PREVIEW");
                } else if (System.currentTimeMillis() - time > PICTURE_DELAY) {
                    savePicture("random", image);
                }
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {

                }
                if (++counter > 300 || forceImage) {
                    counter = 0;
                    mostRecentImage = image;
                    imageFresh = true;
                    forceImage = false;
                }
                semaphore.release();
            }
        };
        CameraHandler handler = new CameraHandler();
        mCameraView = new CameraView(this, this, mCamera, handler);
        FrameLayout preview = (FrameLayout) findViewById(R.id.framelayout);
        preview.addView(mCameraView);
        mCamera.setPreviewCallback(previewCallback);

        // Set up the user interaction to manually show or hide the system UI.
        mCameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        rec = new ImageRecognition(basePictureDir);
    }

    public Bitmap getMostRecentImage() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        imageFresh = false;
        Bitmap mostRecent = mostRecentImage;
        semaphore.release();
        return mostRecent;
    }

    public Bitmap sudoGetMostRecentImage() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {

        }
        forceImage = true;
        semaphore.release();
        return getMostRecentImage();
    }

    public boolean isMostRecentImageFresh() {
        return imageFresh;
    }

    private void toggle() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        try {
            startActivityForResult(i, REQUEST_OK);
        } catch (Exception e) {
            Toast.makeText(this, "ERROR", Toast.LENGTH_SHORT).show();
        }
    }
    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mCameraView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (data != null && resultCode == -1) {
            final ArrayList<String> result =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            Log.d("MainActivity", result.get(0));
            String command = rec.reparse(result.get(0));
            String[] words = command.split(" ");
            if (words[0].equals("reco")) {
                switch (words[1]) {
                    case "person":
                        addPerson(words[2]);
                        break;
                    case "change":
                        change(command.substring(12));
                        break;
                    case "delete":
                        delete(command.substring(12));
                        break;
                    case "note":
                        note(command.substring(10));
                        break;
                }
            }
        }
    }

    public void addPerson(String name){
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
        int nextId = sp.getInt("people_count", 0) + 1;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(nextId + "-" + name + "1.png");
            getMostRecentImage().compress(Bitmap.CompressFormat.PNG, 100, out);
            out = new FileOutputStream(nextId + "-" + name + "2.png");
            sudoGetMostRecentImage().compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public void change(String note) {
        if (whoIsCurrent == 0) {
            return;
        }
    }

    public void note(String note) {
        if (whoIsCurrent == 0) {
            return;
        }
    }

    public void delete(String note) {
        if (whoIsCurrent == 0) {
            return;
        }
    }
}
