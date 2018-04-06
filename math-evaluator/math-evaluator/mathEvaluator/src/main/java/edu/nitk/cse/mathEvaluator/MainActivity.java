package edu.nitk.cse.mathEvaluator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Rect;

import java.io.File;
import java.util.List;

import edu.nitk.cse.mathEvaluator.camera.CameraConfigurationManager;

import static edu.nitk.cse.mathEvaluator.imageProc.ImageProccessingService.NV21BytesToGrayScaleBitmap;
import static edu.nitk.cse.mathEvaluator.imageProc.ImageProccessingService.locallyAdaptiveThreshold;

public class MainActivity extends Activity {

    private String TAG = "MainActivity";


    static final String DOWNLOAD_BASE = "http://tesseract-ocr.googlecode.com/files/";


    static final String OSD_FILENAME = "tesseract-ocr-3.01.osd.tar";

    static final String OSD_FILENAME_BASE = "osd.traineddata";


    boolean isCameraPreviewing = true;


    private CameraPreviewCallback cameraPreviewCB;

    private class CameraPreviewCallback implements Camera.PreviewCallback {

        public CameraPreviewCallback() {}

        @Override
        public void onPreviewFrame(byte[] bytes, Camera camera) {
            handlePreviewFrame(bytes, camera);
        }
    }

    private static Camera mCamera;
    private CameraConfigurationManager configManager;
    private final String focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;


    private int previewFormat = ImageFormat.NV21; //


    private FrameLayout previewFrame;
    private CameraPreview mPreview;
    private Button captureButton;
    private ImageView pictureView;


    private Bitmap currentFrame;
    private Bitmap currentFrameRaw;
    private Bitmap currentFrameBW;
    int currentFrameWidth;
    int currentFrameHeight;


    private List<Rect> equationRectangles;

    private ProgressDialog dialog;
    private ProgressDialog indeterminateDialog;

    private TessBaseAPI baseApi;


    private int ocrEngineMode = TessBaseAPI.OEM_TESSERACT_ONLY;

    private int pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE; //

    private String characterBlacklist;
    private String characterWhitelist;


    private String sourceLanguageCodeOcr = "eng";
    private String sourceLanguageReadable = "English";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);


        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_11, this, mOvenCVLoaderCallback)) {
            Toast toast = Toast.makeText(getApplicationContext(), "Failed to load OpenCV! Check that you have OpenCV Manager.", Toast.LENGTH_LONG);
            toast.show();
        }

        initCharacterBlacklistAndWhitelist();

        // Initialize the OCR engine
        File storageDirectory = getStorageDirectory();
        if (storageDirectory != null) {
            initOcrEngine(storageDirectory, sourceLanguageCodeOcr, sourceLanguageReadable);
        }

        setupCameraPreview();

    }

    private void initCharacterBlacklistAndWhitelist() {
        characterBlacklist = "";
        characterWhitelist = "d0123456789()-+x*/∫";
    }


    private File getStorageDirectory() {

        String state = null;
        try {
            state = Environment.getExternalStorageState();
        } catch (RuntimeException e) {
            Log.e(TAG, "Is the SD card visible?", e);
            showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable.");
        }

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {



            try {
                return getExternalFilesDir(Environment.MEDIA_MOUNTED);
            } catch (NullPointerException e) {

                Log.e(TAG, "External storage is unavailable");
                showErrorMessage("Error", "Required external storage (such as an SD card) is full or unavailable.");
            }

        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {

            Log.e(TAG, "External storage is read-only");
            showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable for data storage.");
        } else {

            Log.e(TAG, "External storage is unavailable");
            showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable or corrupted.");
        }
        return null;
    }


    void showErrorMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setOnCancelListener(new FinishListener(this))
                .setPositiveButton( "Done", new FinishListener(this))
                .show();
    }


    private BaseLoaderCallback mOvenCVLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV loaded successfully");
                    break;
                default:

                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    private void setupCameraPreview() {


        configManager = new CameraConfigurationManager(this);
        mCamera = configManager.getCameraInstance(focusMode, previewFormat);
        cameraPreviewCB = new CameraPreviewCallback();


        mPreview = new CameraPreview(this, mCamera);
        previewFrame = (FrameLayout) findViewById(R.id.camera_preview);
        previewFrame.addView(mPreview);

        pictureView = (ImageView) findViewById(R.id.picture_view);


        captureButton = (Button) findViewById(R.id.button_capture);
        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // get an image from the camera
                        //mCamera.takePicture(null, null, mPictureCB);

                        // Request a frame from the camera
                        mCamera.setOneShotPreviewCallback(cameraPreviewCB);

                    }
                }
        );
    }


    public void handlePreviewFrame(byte[] data, Camera camera) {

        hideCameraPreview();

        Point cameraResolution = configManager.getCameraResolution();

        currentFrameWidth = cameraResolution.x;
        currentFrameHeight = cameraResolution.y;

        currentFrameRaw = NV21BytesToGrayScaleBitmap(data, currentFrameWidth, currentFrameHeight);

        currentFrame = currentFrameRaw;
        currentFrameBW = locallyAdaptiveThreshold(currentFrame);


        pictureView.setImageBitmap(currentFrame);
        pictureView.setVisibility(View.VISIBLE);


        processImage(currentFrame, currentFrameBW, currentFrameWidth, currentFrameHeight);
    }

    private void processImage(Bitmap bitmap, Bitmap bitmapBW, int width, int height) {
        new FindEqnRectsAsyncTask(this, baseApi, bitmap, bitmapBW, width, height)
                .execute();
    }

    public void handleEquationResult(EquationResult equationResult) {


        int rectColor;
        boolean success = equationResult.isSuccess();
        if(success) {
            rectColor = Color.GREEN;
        }
        else {
            rectColor = Color.RED;
        }
        redrawEquationRect(null, equationResult.getEquationNumber(), rectColor);


        drawEquationResult(success, equationResult.getOcrText(),  equationResult.getSolution(), equationResult.getEquationNumber());
    }

    private void hideCameraPreview() {
        previewFrame.setVisibility(View.GONE);
        captureButton.setVisibility(View.GONE);
        isCameraPreviewing = false;
    }

    private void showCameraPreview() {
        previewFrame.setVisibility(View.VISIBLE);
        captureButton.setVisibility(View.VISIBLE);
        isCameraPreviewing = true;
    }

    @Override
    public void onBackPressed() {
        if( isCameraPreviewing ) {
            super.onBackPressed();
        }
        else {

            pictureView.setVisibility(View.GONE);
            mCamera.startPreview();
            showCameraPreview();
        }

    }


    private void initOcrEngine(File storageRoot, String languageCode, String languageName) {


        if (dialog != null) {
            dialog.dismiss();
        }
        dialog = new ProgressDialog(this);


        indeterminateDialog = new ProgressDialog(this);
        indeterminateDialog.setTitle("Please wait");
        String ocrEngineModeName = "Tesseract";
        indeterminateDialog.setMessage("Initializing " + ocrEngineModeName + " OCR engine for " + languageName + "...");
        indeterminateDialog.setCancelable(false);
        indeterminateDialog.show();


        baseApi = new TessBaseAPI();
        new OcrInitAsyncTask(this, baseApi, dialog, indeterminateDialog, languageCode, languageName, ocrEngineMode)
                .execute(storageRoot.toString());
    }

    public void drawEquationResult(boolean success, String ocrEquationStr, Double result, int equationNumber) {


        Bitmap newBitmap = Bitmap.createBitmap(currentFrame.getWidth(), currentFrame.getHeight(), Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(newBitmap);


        canvas.drawBitmap(currentFrame, 0, 0, null);


        Paint paint = new Paint();
        if(success) {
            paint.setColor(Color.GREEN);
        }
        else {
            paint.setColor(Color.RED);
        }

        paint.setTextSize(14);
        paint.setStrokeWidth(12);


        Rect rect = equationRectangles.get(equationNumber);
        String resultStr = ocrEquationStr + " = " + String.valueOf(result);
        Point drawResultAt = locationForResult(rect, resultStr);
        canvas.drawText(resultStr, drawResultAt.x, drawResultAt.y, paint);


        pictureView.setImageDrawable(new BitmapDrawable(getResources(), newBitmap));
        currentFrame = newBitmap;

    }

    private Point locationForResult(Rect rect, String resultStr) {

        int locY;

        if(rect.y > 10) {

            locY = rect.y - 2;
        }
        else if(rect.y + rect.height <= currentFrameHeight - 10) {

            locY = rect.y + rect.height + 16;
        }
        else {

            locY = rect.y + 16;
        }

        return new Point(rect.x, locY);
    }

    public void redrawEquationRect(Rect rect, int equationNumber, int color) {

        if(rect == null) {
            rect = equationRectangles.get(equationNumber);
        }

        Bitmap newBitmap = Bitmap.createBitmap(currentFrame.getWidth(), currentFrame.getHeight(), Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(newBitmap);

        canvas.drawBitmap(currentFrame, 0, 0, null);

        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);

        android.graphics.Rect rectGraphic = new android.graphics.Rect((int) rect.tl().x, (int) rect.tl().y,
                (int) rect.br().x, (int) rect.br().y);
        canvas.drawRect(rectGraphic, paint);

        pictureView.setImageDrawable(new BitmapDrawable(getResources(), newBitmap));
        currentFrame = newBitmap;
    }

    public void drawEquationRects(List<Rect> rectList) {

        equationRectangles = rectList;

        //Create a new image bitmap and attach a brand new canvas to it
        Bitmap newBitmap = Bitmap.createBitmap(currentFrame.getWidth(), currentFrame.getHeight(), Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(newBitmap);

        //Draw the image bitmap into the canvas
        canvas.drawBitmap(currentFrame, 0, 0, null);

        // Set up the paint to be drawn on the canvas
        Paint paint = new Paint();
        paint.setColor(Color.YELLOW);
        paint.setStyle(Paint.Style.STROKE);

        // Draw each rectangle
        for(Rect rect : rectList) {
            android.graphics.Rect rectGraphic = new android.graphics.Rect((int) rect.tl().x, (int) rect.tl().y,
                    (int) rect.br().x, (int) rect.br().y);
            canvas.drawRect(rectGraphic, paint);
        }

        // Display the image with rectangles on it
        pictureView.setImageDrawable(new BitmapDrawable(getResources(), newBitmap));
        currentFrame = newBitmap;
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");


        if(mCamera != null) {
            mCamera.stopPreview();

            previewFrame.removeView(mPreview);
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }

        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();

        if(mCamera == null) {
            Log.d(TAG, "re-initializing camera");
            setupCameraPreview();
        }

        if (baseApi == null) {
            // Initialize the OCR engine
            File storageDirectory = getStorageDirectory();
            if (storageDirectory != null) {
                initOcrEngine(storageDirectory, sourceLanguageCodeOcr, sourceLanguageReadable);
            }
        } else {

            resumeOCR();
        }
    }


    void resumeOCR() {
        Log.d(TAG, "resumeOCR()");

        if (baseApi != null) {
            baseApi.setPageSegMode(pageSegmentationMode);
            baseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, characterBlacklist);
            baseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, characterWhitelist);
        }
    }




}
