package edu.nitk.cse.mathEvaluator.imageProc;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.KeyPoint;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class ImageProccessingService {

    private final String TAG = "ImageProccessingService";

    // How much to pad an equation rectangle (as a percentage of the rectangle size)
    private double paddingHorizPct = 0.05;
    private double paddingVertPct = 0.2;

    private static ImageProccessingService instance;

    public static ImageProccessingService getInstance() {
        if (instance == null) {
            instance = new ImageProccessingService();
        }
        return instance;
    }

    private ImageProccessingService() {
    }

    public Bitmap convertToGrayScle(Bitmap bitmap) {

        Mat mat = Mat.zeros(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC1);
        Utils.bitmapToMat(bitmap, mat);
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);
        Utils.matToBitmap(mat, bitmap);
        return bitmap;
    }

    public List<Rect> detectObjects(Bitmap bitmap) {
        Mat img = Mat.zeros(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC1);
        Mat mask = Mat.zeros(img.size(), CvType.CV_8UC1);

        Scalar CONTOUR_COLOR = new Scalar(255);
        Utils.bitmapToMat(bitmap, img);


        Imgproc.cvtColor(img, img, Imgproc.COLOR_RGB2GRAY);

        MatOfKeyPoint mokp = new MatOfKeyPoint();
        FeatureDetector fd = FeatureDetector.create(FeatureDetector.MSER);
        fd.detect(img, mokp);

        Log.i(TAG, "Mat of key points = " + mokp.rows() + "x" + mokp.cols());

        List<KeyPoint> keypointsList = mokp.toList();

        for (KeyPoint keyPoint : keypointsList) {
            int rectanx1 = (int) (keyPoint.pt.x - 0.5 * keyPoint.size);
            int rectany1 = (int) (keyPoint.pt.y - 0.5 * keyPoint.size);

            if (rectanx1 <= 0)
                rectanx1 = 1;
            if (rectany1 <= 0)
                rectany1 = 1;
            int rectanw = (int) keyPoint.size;
            int rectanh = (int) keyPoint.size;

            if ((rectanx1 + rectanw) > img.width())
                rectanw = img.width() - rectanx1;
            if ((rectany1 + rectanh) > img.height())
                rectanh = img.height() - rectany1;

            Rect rectant = new Rect(rectanx1, rectany1, rectanw, rectanh);
            Mat roi = new Mat(mask, rectant);
            roi.setTo(CONTOUR_COLOR);
            Log.i(TAG, "Keypoint at x = " + rectanx1 + ", width = " + rectanw + ", y = " +
                    rectany1 + ", height = " + rectanh);
        }


        List<MatOfPoint> contour2 = new ArrayList<MatOfPoint>();
        List<Rect> rectList = new ArrayList<Rect>();

        Mat morbyte = new Mat();
        Mat hierarchy = new Mat();


        Mat se = new Mat(1, 50, CvType.CV_8UC1, Scalar.all(255));
        Imgproc.morphologyEx(mask, morbyte, Imgproc.MORPH_DILATE, se);


        Imgproc.findContours(morbyte, contour2, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);

        for (MatOfPoint matOfPoint : contour2) {
            rectList.add(padRectangle(Imgproc.boundingRect(matOfPoint)));
        }

        return rectList;
    }

    private Rect padRectangle(Rect rect) {

        int ulx = (int) rect.tl().x;
        int uly = (int) rect.tl().y;
        int brx = (int) rect.br().x;
        int bry = (int) rect.br().y;

        Integer paddingHoriz = (int) ((brx - ulx) * paddingHorizPct);
        Integer paddingVert = (int) ((bry - uly) * paddingVertPct);

        return new Rect(Math.max(0, ulx - paddingHoriz), Math.max(0, uly - paddingVert),
                2 * paddingHoriz + (brx - ulx), 2 * paddingVert + (bry - uly));

    }

    public static Bitmap locallyAdaptiveThreshold(Bitmap gray) {
        Bitmap newBitmap = Bitmap.createBitmap(gray.getWidth(), gray.getHeight(), Bitmap.Config.ARGB_8888);

        Mat mat = new Mat();
        Utils.bitmapToMat(gray, mat);
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY);

        Mat matBW = new Mat();


        int blockSize = 55;
        Imgproc.adaptiveThreshold(mat, matBW, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, blockSize, 15);
        Utils.matToBitmap(matBW, newBitmap);

        return newBitmap;

    }


    public static Bitmap NV21BytesToGrayScaleBitmap(byte[] data, int width, int height) {
        PlanarYUVLuminanceSource lum = new PlanarYUVLuminanceSource(data, width, height,
                0, 0, width, height, false);
        return lum.renderCroppedGreyscaleBitmap();
    }
}
