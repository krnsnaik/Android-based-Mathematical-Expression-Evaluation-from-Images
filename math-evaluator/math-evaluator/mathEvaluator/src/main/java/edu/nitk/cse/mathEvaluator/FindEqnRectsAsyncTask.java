package edu.nitk.cse.mathEvaluator;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.core.Rect;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import edu.nitk.cse.mathEvaluator.imageProc.ImageProccessingService;

/**
 * An async task for finding rectangles in an image
 */
public class FindEqnRectsAsyncTask extends AsyncTask {

    private String TAG = "FindEqnRectsAsyncTask";

    private MainActivity activity;
    private TessBaseAPI baseApi;
    private Bitmap bitmap;
    private Bitmap bitmapBW;

    FindEqnRectsAsyncTask(MainActivity activity, TessBaseAPI baseApi, Bitmap bitmap, Bitmap bitmapBW, int width, int height) {
        this.activity = activity;
        this.baseApi = baseApi;
        this.bitmap = bitmap;
        this.bitmapBW = bitmapBW;
    }

    @Override
    protected Object doInBackground(Object[] objects) {

        Bitmap gray = ImageProccessingService.getInstance().convertToGrayScle(this.bitmap);
        final List<Rect> rectList = ImageProccessingService.getInstance().detectObjects(gray);

        int numRectangles = rectList.size();
        if (numRectangles == 0) {
            displayNoRectMessage();
            Log.d(TAG, "Failed to detect rectangles");
            return false;
        } else {

            if(numRectangles > 1 && numRectangles <= 7) {
                mergeRectangles(rectList);
            }

            drawEquationRectangles(rectList);
            DoOcrOnEquations(rectList);
        }

        return null;
    }

    private void mergeRectangles(List<Rect> rectList) {
        // Sort rectangles by their horizontal position
        Collections.sort(rectList, new Comparator<Rect>() {
            @Override
            public int compare(Rect r1, Rect r2) {
                return (int) (r1.x - r2.x);
            }
        });

        int numRectangles = rectList.size();
        Rect cur, next;
        for(int i=0; i < numRectangles-1; i++) {
            cur = rectList.get(i);
            next = rectList.get(i+1);
            if(shouldMerge(cur, next)) {
                Log.d(TAG, "Merging rectangles at index: " + String.valueOf(i));
                doMerge(rectList, i, cur, i+1, next);
                i -= 1;
                numRectangles -= 1;
            }
        }
    }

    private boolean shouldMerge(Rect rect1, Rect rect2) {

        int verticalThresh = 40;
        int horizThresh = 40;

        int yMid1 = rect1.y + (int)((0.5) * rect1.height);
        int yMid2 = rect2.y + (int)((0.5) * rect2.height);

        return Math.abs(rect2.x - (rect1.x+rect1.width)) <= horizThresh &&
               Math.abs(yMid2 - yMid1) <= verticalThresh;
    }

    private void doMerge(List<Rect> rectList, int rectIdx1, Rect rect1, int rectIdx2, Rect rect2) {

        int mergeXLeft = Math.min(rect1.x, rect2.x);
        int mergeXRight = Math.max(rect1.x + rect1.width, rect2.x + rect2.width);

        int mergeYTop = Math.min(rect1.y, rect2.y);
        int mergeYBottom = Math.max(rect1.y + rect1.height, rect2.y + rect2.height);

        Rect merge = new Rect(mergeXLeft, mergeYTop, mergeXRight - mergeXLeft, mergeYBottom - mergeYTop);
        rectList.remove(Math.max(rectIdx1, rectIdx2));
        rectList.set(Math.min(rectIdx1, rectIdx2), merge);
    }

    private void displayNoRectMessage() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(activity, "Could not find any equations", Toast.LENGTH_LONG);
                toast.show();
            }
        });
    }

    private void drawEquationRectangles(final List<Rect> rectList) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                // update UI with the rectangles
                activity.drawEquationRects(rectList);

            }
        });
    }

    private void DoOcrOnEquations(List<Rect> rectList) {

        int equationNumber = 0;
        for(Rect rect : rectList) {
            Integer ulx = (int) rect.tl().x;
            Integer uly = (int) rect.tl().y;
            Integer brx = (int) rect.br().x;
            Integer bry = (int) rect.br().y;

            // crop by bounding box, but leave some padding space
            Log.d("Cropping at", ulx.toString() + " " +uly.toString());

            Bitmap cropped = Bitmap.createBitmap(bitmapBW, Math.max(ulx,0), Math.max(uly,0),
                    Math.min(brx , bitmapBW.getWidth())- ulx , Math.min(bry,bitmapBW.getHeight())- uly );

            // Start an async task to recognize this equation
            new OcrEquationAsyncTask(this.activity, this.baseApi, equationNumber, cropped,
                    cropped.getWidth(), cropped.getHeight())
                    .execute();

            equationNumber++;
        }

    }

}
