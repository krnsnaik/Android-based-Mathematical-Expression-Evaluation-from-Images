package edu.nitk.cse.mathEvaluator;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;

import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.tesseract.android.TessBaseAPI;

import edu.nitk.cse.mathEvaluator.math.ExpressionParser;


/**
 * An async task for performing OCR on a mathematical equation.
 */
public class OcrEquationAsyncTask extends AsyncTask {

    // The activity we need to communicate with
    MainActivity activity;

    // Bitmap of the equation in black and white
    Bitmap bitmapBW;

    // Equation number (used to identify the equation)
    private int equationNumber;

    private int width;
    private int height;

    // Tesseract API for OCR
    private TessBaseAPI baseApi;

    private long timeRequired;

    public OcrEquationAsyncTask(MainActivity activity, TessBaseAPI baseApi,
                                int equationNumber, Bitmap bitmapBW, int width, int height) {
        this.activity = activity;
        this.baseApi = baseApi;
        this.bitmapBW = bitmapBW;
        this.width = width;
        this.height = height;
        this.equationNumber = equationNumber;
    }

    @Override
    protected Object doInBackground(Object[] objects) {

        long start = System.currentTimeMillis();



        String ocrText;

        try {
            baseApi.setImage(ReadFile.readBitmap(bitmapBW));
            ocrText = baseApi.getUTF8Text();
            timeRequired = System.currentTimeMillis() - start;

            // Check for failure to recognize text
            if (ocrText == null || ocrText.equals("")) {
                return false;
            }
            Log.d("OcrEquationAsyncTask", "OCR text: " + ocrText);

            Double result = null;
            EquationResult eqnResult;
            try {
                result = ExpressionParser.parse(ocrText);
            } catch (Exception e) {
                eqnResult = new EquationResult(equationNumber, ocrText, null, false, "Unable to parse expression: " + ocrText);
                sendEquationResult(eqnResult);
                return false;
            }

            eqnResult = new EquationResult(equationNumber, ocrText, result, true, null);
            sendEquationResult(eqnResult);
            return true;

        } catch (RuntimeException e) {
            Log.e("OcrRecognizeAsyncTask", "Caught RuntimeException in request to Tesseract. Setting state to CONTINUOUS_STOPPED.");
            e.printStackTrace();
            try {
                baseApi.clear();
            } catch (NullPointerException e1) {
                // Continue
            }
            return false;
        }

    }

    private void sendEquationResult(final EquationResult equationResult) {

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                // update UI with the new result
                activity.handleEquationResult(equationResult);

            }
        });

    }
    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private SurfaceHolder mHolder;
    private Camera mCamera;

    private String TAG = "CameraPreview";

    public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;

        initHolder();

    }

    // Install a SurfaceHolder. Callback so we get notified when the
    // underlying surface is created and destroyed.
    public void initHolder() {
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
        } catch (IOException e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.

    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

        } catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

}
}

