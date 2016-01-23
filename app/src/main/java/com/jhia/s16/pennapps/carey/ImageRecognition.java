package com.jhia.s16.pennapps.carey;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_contrib.*;
import static org.bytedeco.javacpp.opencv_highgui.*;

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

    public String dir;

    public ImageRecognition (String dir) {
        recognizer = createFisherFaceRecognizer();
        this.dir = dir;
        map = new HashMap<>();
    }

    public void init() {
        List<File> images = getListFiles(new File(dir));
        MatVector mv = new MatVector(images.size());
        Mat labels = new Mat(images.size(), 1, CV_32SC1);
        IntBuffer buf = labels.getIntBuffer();
        int count = 0;
        for (File image: images) {
            Mat img = imread(image.getAbsolutePath(), CV_LOAD_IMAGE_GRAYSCALE);
            mv.put(count, img);
            int label = Integer.parseInt(image.getName().split("\\-")[0]);
            if (!map.containsKey(label)) {
                map.put(label, image.getName().split("\\-")[1]);
            }
            buf.put(count, label);
            count++;
        }
        recognizer.train(mv, labels);
    }

    protected List<File> getListFiles(File parentDir) {
        ArrayList<File> inFiles = new ArrayList<>();
        File[] files = parentDir.listFiles();
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
        Mat img = imread(s, CV_LOAD_IMAGE_GRAYSCALE);
        return map.get(recognizer.predict(img));
    }
}
