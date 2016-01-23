package com.jhia.s16.pennapps.carey;

import android.Manifest;
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
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_objdetect.*;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends AppCompatActivity {
    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private String person = null;
    private ArrayList<String> notes = null;
    protected static final int REQUEST_OK = 100;

    private ImageRecognition rec;

    public static String basePictureDir = null;

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
    private Semaphore reclock = new Semaphore(1);
    private boolean imageFresh = false;
    private boolean forceImage = false;

    private ConcurrentHashMap<String, Rect> faceMap = new ConcurrentHashMap<>();


    private final void savePicture(String fileName, Bitmap img) {
        try {
            String directoryName = basePictureDir;
            File directory = new File(directoryName);
            directory.mkdirs();
            File imgFile = new File(directoryName + "/" + fileName + ".png");
            imgFile.createNewFile();
            FileOutputStream fout = new FileOutputStream(imgFile);
            img.compress(Bitmap.CompressFormat.PNG, 100, fout);
            fout.flush();
            fout.close();
        } catch (IOException io) {
            io.printStackTrace();
        }
    }


    public ConcurrentHashMap<String, Rect> getFaceMap() {
        return faceMap;
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

        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(mActivity, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(mActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED) {

            ActivityCompat
                    .requestPermissions(mActivity, new String[]{Manifest.permission.CAMERA,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
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
        rec = new ImageRecognition(basePictureDir);
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
                } else {
                    if (System.currentTimeMillis() - time > PICTURE_DELAY) {
                        savePicture("random", image);
                    }

                    try {
                        semaphore.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (++counter > 50 || forceImage) {
                        counter = 0;
                        mostRecentImage = image;
                        imageFresh = true;
                        forceImage = false;

                        // savePicture("random", image);
                        // rec.findPersonFromPhoto(basePictureDir + "/random.png");
                        faceMap.clear();
                        ArrayList<Rect> faceBoxes = facepos(image);
                        for (Rect face : faceBoxes) {
                            Bitmap cropped = Bitmap.createBitmap(image, face.left, face.top, face.width(), face.height());
                            savePicture("faceread", image);
                            String name = rec.findPersonFromPhoto(basePictureDir + "/faceread.png");
                            faceMap.put(name, face);
                        }
                    }
                    semaphore.release();
                }
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

    @Override
    protected void onResume() {
        super.onResume();
        recInit();

    }

    private void recInit() {
        try {
            reclock.acquire();
            rec.init();
        } catch (InterruptedException e) {

        } finally {
            reclock.release();
        }
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        final int voiceAcceptanceThreshold = 4;

        if (data != null && resultCode == -1) {
            final ArrayList<String> result =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            CommandCondition commandCondition = new CommandCondition(2) {
                @Override
                public boolean isCommandSatisfied() {
                    String[] cmd = getCommands();
                    if (cmd != null) {
                        for (int i = 0; i < cmd.length; ++i) {
                            if (cmd[i] == null || cmd[i].length() == 0) {
                                return false;
                            }
                        }
                        return true;
                    }
                    return false;
                }
            };

            if (result != null) {
                int commandsFound = 0;
                for (int i = 0; i < Math.min(voiceAcceptanceThreshold, result.size()) &&
                        !commandCondition.isCommandSatisfied(); ++i) {
                    String resulti = result.get(i);
                    Log.d("MainActivity", resulti);
                    String command = rec.reparse(resulti);
                    String[] words = command.split("\\s");

                    if (words[0].equals("reco")) {
                        commandCondition.setCommand(0, "reco");
                    }

                    switch (words[1]) {
                        case "person":
                            addPerson(words[2]);
                            commandCondition.setCommand(1, "person");
                            break;
                        case "change":
                            change(command.substring(12));
                            commandCondition.setCommand(1, "change");
                            break;
                        case "delete":
                            delete(command.substring(12));
                            commandCondition.setCommand(1, "delete");
                            break;
                        case "note":
                            note(command.substring(10));
                            commandCondition.setCommand(1, "note");
                            break;
                    }
                }
            }
        }
    }

    public void addPerson(String name) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
        int nextId = sp.getInt("people_count", 0) + 1;
        savePicture(nextId + "-" + name + "1.png", getMostRecentImage());
        savePicture(nextId + "-" + name + "2.png", sudoGetMostRecentImage());
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt("people_count", nextId).apply();
        person = name;
        notes = null;
        recInit();
    }

    public void change(String note) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
        if (person == null) {
            savePicture("temp.png", getMostRecentImage());
            person = rec.findPersonFromPhoto(basePictureDir + "/temp.png");
        }
        if (person != null) {
            if (notes == null) {
                notes = new ArrayList<>(sp.getStringSet(person, new HashSet<String>()));
            }
            int index = parseNumber(note.split(" ")[0]) - 1;
            if (index < notes.size() && index >= 0) {
                notes.set(index, note);
                sp.edit().putStringSet(person, new HashSet<>(notes)).apply();
            }
        }
    }

    public void note(String note) {
        if (person == null) {
            savePicture("temp.png", getMostRecentImage());
            person = rec.findPersonFromPhoto(basePictureDir + "/temp.png");
        }
        if (person != null) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
            Set<String> set = sp.getStringSet(person, new HashSet<String>());
            set.add(note);
            sp.edit().putStringSet(person, set).apply();
            notes = new ArrayList<>(set);
        }
    }

    public void delete(String note) {
        if (person == null) {
            savePicture("temp.png", getMostRecentImage());
            person = rec.findPersonFromPhoto(basePictureDir + "/temp.png");
        }
        if (person != null) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
            if (notes == null) {
                notes = new ArrayList<>(sp.getStringSet(person, new HashSet<String>()));
            }
            int index = parseNumber(note.split(" ")[0]) - 1;
            if (index < notes.size() && index >= 0) {
                notes.remove(index);
                sp.edit().putStringSet(person, new HashSet<>(notes)).apply();
            }
        }
    }

    public ArrayList<Rect> facepos(Bitmap bm) {
        IplImage image = IplImage.create(bm.getWidth(), bm.getHeight(), IPL_DEPTH_8U, 4);
        bm.copyPixelsToBuffer(image.getByteBuffer());
        ArrayList<Rect> answer = new ArrayList<>();
        CvHaarClassifierCascade cascade = new CvHaarClassifierCascade();
        CvMemStorage storage = CvMemStorage.create();
        CvSeq sign = cvHaarDetectObjects(image, cascade, storage, 1.5, 3, CV_HAAR_DO_CANNY_PRUNING);

        cvClearMemStorage(storage);

        int total_Faces = sign.total();

        for (int i = 0; i < total_Faces; i++) {
            CvRect r = new CvRect(cvGetSeqElem(sign, i));
            answer.add(new Rect(r.x(), r.y(), r.width() + r.x(), r.y() + r.height()));
        }
        return answer;
    }

    /**
     * Parse a string s, such as "five", into 5.  1 <= x <= 10 Default return -1
     *
     * @param s
     * @return
     */
    private int parseNumber(String s) {
        switch (s.toLowerCase()) {
            case "one":
                return 1;
            case "two":
                return 2;
            case "three":
                return 3;
            case "four":
                return 4;
            case "five":
                return 5;
            case "six":
                return 6;
            case "seven":
                return 7;
            case "eight":
                return 8;
            case "nine":
                return 9;
            case "ten":
                return 10;
        }
        return -1;
    }
}
