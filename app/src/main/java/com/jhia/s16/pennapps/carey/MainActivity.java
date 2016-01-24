package com.jhia.s16.pennapps.carey;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.FaceDetector;
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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 * l'chairmao mao
 */
public class MainActivity extends AppCompatActivity {
    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private String person = null;
    private ArrayList<String> notes = null;
    private boolean pressed;
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

    //private Bitmap mostRecentImage = null;

    private ConcurrentHashMap<String, Bitmap> damnGarbageCollector = new ConcurrentHashMap<>();
    private static final String IMAGE_KEY_RECENT = "recent";

    private Semaphore semaphore = new Semaphore(1);
    private Semaphore reclock = new Semaphore(1);
    private boolean imageFresh = false;
    private boolean forceImage = false;
    private boolean faceDetectionSwitch = false;

    private ConcurrentHashMap<String, Rect> faceMap = new ConcurrentHashMap<>();
    private AtomicLong faceTimer = new AtomicLong(0);

    private Semaphore photolock = new Semaphore(1);


    private final void savePicture(String fileName, Bitmap img) {
        try {
            img = getResizedBitmap(img, ImageRecognition.TRAINING_IMAGE_WIDTH, ImageRecognition.TRAINING_IMAGE_HEIGHT);
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
                //if (System.currentTimeMillis() - faceTimer.get() > 500) {
                //faceMap.clear();
                // } else {
                Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                YuvImage yuvImg =
                        new YuvImage(data, ImageFormat.NV21, previewSize.width, previewSize.height, null);
                yuvImg.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 50, out);
                byte[] imageBytes = out.toByteArray();
                //if (mostRecentImage != null && !mostRecentImage.isRecycled())
                //    mostRecentImage.recycle();
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                final String imageKey = IMAGE_KEY_RECENT;
                if (damnGarbageCollector.get(imageKey) != null) {
                    damnGarbageCollector.get(imageKey).recycle();
                }
                Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                //Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (image != null) {
                    image = image.copy(Bitmap.Config.RGB_565, true);
                    damnGarbageCollector.put(IMAGE_KEY_RECENT, image);
                    photolock.release();
                }
                if (damnGarbageCollector.get(IMAGE_KEY_RECENT) == null) {
                    System.out.println("NULL IMAGE PREVIEW");
                } else {
                    if (System.currentTimeMillis() - time > PICTURE_DELAY) {
                        // savePicture("random", damnGarbageCollector.get(IMAGE_KEY_RECENT));
                        time = System.currentTimeMillis();
                    }
                    //Bitmap.createBitmap(mCameraView.getDrawingCache());
                    //mostRecentImage = image.copy(Bitmap.Config.RGB_565, true);

                    int maxFaces = 4;

                    FaceDetector.Face[] faces = new FaceDetector.Face[maxFaces];
                    FaceDetector fd = new FaceDetector(damnGarbageCollector.get(imageKey).getWidth(), damnGarbageCollector.get(imageKey).getHeight(), maxFaces);

                    int faceCount = fd.findFaces(damnGarbageCollector.get(imageKey), faces);
                    if (faceCount > 0) {
                        faceTimer.set(System.currentTimeMillis());
                    }
                    Log.d("FACE DELETEC", "detectedface " + faceCount);
                    for (FaceDetector.Face f : faces) {
                        if (f == null) {
                            continue;
                        }
                        PointF midPoint = new PointF();
                        f.getMidPoint(midPoint);

                        float eyesDistance = f.eyesDistance();
                        float x = Math.max(midPoint.x - eyesDistance, 0);
                        float y = Math.max(midPoint.y - eyesDistance, 0);
                        float xpw = midPoint.x + eyesDistance;
                        float yph = midPoint.y + eyesDistance;
                        Log.d("MIDPOINT", midPoint.toString() + " " + eyesDistance);
                        Rect rect = new Rect((int) x, (int) y, (int) (xpw - x), (int) (yph - y));
                        Log.d("DRAWING", rect.toString());
                        Bitmap sub = Bitmap.createBitmap(damnGarbageCollector.get(IMAGE_KEY_RECENT), (int) x, (int) y, (int) (xpw - x), (int) (yph - y));
                        savePicture("random", sub);
                        try {
                            reclock.acquire();
                        } catch (InterruptedException e) {

                        }
                        String name = rec.findPersonFromPhoto("random");
                        reclock.release();
                        faceMap.put(name, rect);

                    }
                    if (System.currentTimeMillis() - faceTimer.get() > 500) {
                        faceMap.clear();
                    }
                    try {
                        reclock.acquire();
                    } catch (InterruptedException e) {

                    }
                    person = rec.findPersonFromPhoto("random");
                    reclock.release();
                    if (person != null) {
                        notes = new ArrayList<>(PreferenceManager.getDefaultSharedPreferences(mActivity).getStringSet(person, new HashSet<String>()));
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

        pressed = false;
    }

    /**
     * Credit goes to http://stackoverflow.com/users/884674/jeet-chanchawat
     *
     * @param bm
     * @param newWidth
     * @param newHeight
     * @return
     */
    public static Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        return resizedBitmap;
    }

    public Bitmap getMostRecentImage() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        imageFresh = false;
        Bitmap mostRecent = damnGarbageCollector.get(IMAGE_KEY_RECENT);
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
        if (pressed) {
            return;
        }
        pressed = true;
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
                    if (words == null || words.length <= 2) {
                        continue;
                    }
                    if (words[0].equals("reco")) {
                        commandCondition.setCommand(0, "reco");
                    }
                    if (commandCondition.getCommands()[1] == null || commandCondition.getCommands()[1].equals(""))
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
        pressed = false;
    }

    public void addPerson(String name) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
        int highestID = 1;

        for (File f : new File(basePictureDir).listFiles()) {
            if (f.getName().contains(name)) {
                highestID++;
            }
        }


        int nextId = sp.getInt("people_count", 0) + 1;
        try {
            photolock.acquire();
        } catch (InterruptedException e) {

        }
        Bitmap p1 = getMostRecentImage();
        int bufferId = nextId + (nextId % 2);
        if (p1 != null) {
            savePicture(bufferId + "-" + name + "-" + highestID, p1);
        }
        /*try {
            photolock.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        p1 = sudoGetMostRecentImage();
        if (p1 != null) {
            p1.setPixel(0, 0, 0);
            savePicture(nextId + "-" + name + "-" + (highestID + 1), p1);
        }*/
        // photolock.release();
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt("people_count", nextId).apply();
        person = name;
        notes = null;
        if (highestID > 2) {
            recInit();
        }
    }

    public void change(String note) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
        if (person == null) {
            savePicture("random", getMostRecentImage());
            try {
                reclock.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            person = rec.findPersonFromPhoto(basePictureDir + "/random");
            reclock.release();
        }
        if (person != null) {
            if (notes == null) {
                notes = new ArrayList<>(sp.getStringSet(person, new HashSet<String>()));
            }
            int index = parseNumber(note.split("\\s")[0]) - 1;
            if (index < notes.size() && index >= 0) {
                notes.set(index, note);
                sp.edit().putStringSet(person, new HashSet<>(notes)).apply();
            }
        }
    }

    public void note(String note) {
        if (person == null) {
            savePicture("random", getMostRecentImage());
            try {
                reclock.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            person = rec.findPersonFromPhoto(basePictureDir + "/random");
            reclock.release();
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
            savePicture("random", getMostRecentImage());
            try {
                reclock.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            person = rec.findPersonFromPhoto(basePictureDir + "/random");
            reclock.release();
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
        /*
        IplImage image = IplImage.create(bm.getWidth(), bm.getHeight(), IPL_DEPTH_8U, 4);
        bm.copyPixelsToBuffer(image.getByteBuffer());
        ArrayList<Rect> answer = new ArrayList<>();
        CvHaarClassifierCascade cascade = new CvHaarClassifierCascade(cvLoad("haarcascade_frontalface_default.xml"));
        CvMemStorage storage = CvMemStorage.create();
        CvSeq sign = cvHaarDetectObjects(image, cascade, storage, 1.5, 3, CV_HAAR_DO_CANNY_PRUNING);

        cvClearMemStorage(storage);

        int total_Faces = sign.total();

        for (int i = 0; i < total_Faces; i++) {
            CvRect r = new CvRect(cvGetSeqElem(sign, i));
            answer.add(new Rect(r.x(), r.y(), r.width() + r.x(), r.y() + r.height()));
        }
        return answer;*/
        return new ArrayList<Rect>();
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
