package com.jhia.s16.pennapps.carey;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_face.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.*;

import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Jason on 1/22/2016.
 */
public class ImageRecognition {

    private FaceRecognizer recognizer;
    private HashMap<Integer, String> map;
    private boolean inited;

    public String dir;

    // multiple of 16
    public static final int TRAINING_IMAGE_WIDTH = 320;
    public static final int TRAINING_IMAGE_HEIGHT = 320;

    public ImageRecognition (String dir) {
        recognizer = createFisherFaceRecognizer();
        this.dir = dir;
        map = new HashMap<>();
        inited = false;
    }

    public void init() {
        inited = true;
        Log.d("ImageRecognition", "initstart");
        List<File> images = getListFiles(new File(dir));
        MatVector mv = new MatVector(images.size());

        Mat labels = new Mat(images.size(), 1, CV_32SC1);
        boolean empty = true;
        if (labels != null && images.size() > 0) {
            IntBuffer buf = labels.getIntBuffer();
            int count = 0;
            for (File image : images) {
                Log.d("ImageReco", image.getAbsolutePath());
                /*if (image == null || !image.getName().endsWith(".png")) {
                    continue;
                }
                Log.d("ImageReco", image.getAbsolutePath());
                Bitmap bmp = BitmapFactory.decodeFile(image.getAbsolutePath());
                if (bmp == null) {
                    continue;
                }
                bmp = MainActivity.getResizedBitmap(bmp, TRAINING_IMAGE_WIDTH, TRAINING_IMAGE_HEIGHT);
                try {
                    image.delete();
                    image.createNewFile();
                    FileOutputStream fout = new FileOutputStream(image);
                    bmp.copy(Bitmap.Config.RGB_565, true).compress(Bitmap.CompressFormat.PNG, 100, fout);
                    fout.flush();
                    fout.close();
                } catch (IOException e) {

                }
                */
                if(image.getName().contains("random.png")){
                    continue;
                }

                Mat img = imread(image.getAbsolutePath(), CV_LOAD_IMAGE_GRAYSCALE);
                if (img == null || img.total() == 0 || img.empty()) {
                    continue;
                }

                mv.put(count, img);
                Log.d("ImageRec", image.getName());
                empty = false;
                int label = Integer.parseInt(image.getName().split("\\-")[0]);
                if (!map.containsKey(label)) {
                    map.put(label, image.getName().split("\\-")[1]);
                }
                buf.put(count, label);
                count++;
            }
            Log.d("ImageRecognition", "pretrain");

            if (!empty) {
                recognizer.train(mv, labels);
            }
        }
        Log.d("ImageRecognition", "initend");
    }

    protected List<File> getListFiles(File parentDir) {
        ArrayList<File> inFiles = new ArrayList<>();
        File[] files = parentDir.listFiles();
        if (files == null) {
            return inFiles;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                inFiles.addAll(getListFiles(file));
            } else {
                if(file.getName().endsWith(".png")){
                    inFiles.add(file);
                }
            }
        }
        return inFiles;
    }

    /**
     *
     * @param s takes in file.getAbsolutePath()
     * @return name of the person
     */
    public String findPersonFromPhoto(String s) {
        try {
            if (!inited) {
                return null;
            }
            Log.d("ImageRecognition", "findstart");
            Mat img = imread(s, CV_LOAD_IMAGE_GRAYSCALE);
            int buffer = recognizer.predict(img);
            if (buffer <= 0) {
                return null;
            }
            Log.d("ImageRecognition", "findend");
            return map.get(buffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "random";
    }

    public String reparse(String input) {
        //split input
        String[] split = input.split(" ");
        if (split.length < 3) {
            return "";
        }
        //reco
        if(split[0].equals("reco") || split[0].equals("reko") ||
                split[0].equals("rico") || split[0].equals("riko")) {

            split[0] = "reco";
        }

        //spell
        if(split[1].equals("spell") || split[1].equals("spill") ||
                split[1].equals("spall") || split[1].equals("bell") ||
                split[1].equals("elle") || split[1].equals("sell")) {

            split[1] = "spell";
        }

        //note
        if(split[1].equals("note") || split[1].equals("no") ||
                split[1].equals("node") || split[1].equals("low") ||
                split[1].equals("not") || split[1].equals("know")){

            split[1] = "note";

        }

        //change
        else if(split[1].equals("change") || split[1].equals("charge") ||
                split[1].equals("James") || split[1].equals("chain")){

            split[1] = "change";

        }

        else if(split[1].equals("delete") || split[1].equals("silly") ||
                split[1].equals("leet") || split[1].equals("leak") ||
                split[1].equals("leap") || split[1].equals("lead") ||
                (split[1].length() > 3 && split[1].charAt(0) == 'd')){

            split[1] = "delete";

        }

        else if(split[1].equals("person") || split[1].equals("purse") ||
                split[1].equals("pearson") || split[1].equals("persay") ||
                split[1].equals("parson")) {
            split[1] = "person";
        }


        String answer = "";

        for (String s: split) {
            answer += s + " ";
        }

        return answer;

    }
}
