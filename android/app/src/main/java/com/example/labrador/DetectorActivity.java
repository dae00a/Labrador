package com.example.labrador;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Size;
import android.view.View;

import com.example.labrador.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.detection.tflite.Detector;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An activity that uses a TensorFlowBoundingBoxDetector and ObjectTracker to detect
 * and then track and warn of the objects
 **/

public class DetectorActivity extends CameraActivity {
    // Configuration values for the EfficientDet Lite0 model.
    private static final int TF_OD_API_INPUT_SIZE = 320;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect_model.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;

    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(1280, 720);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Detector detector;

    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            this,
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        }
        catch (final IOException e) {
            e.printStackTrace();
            finish();
        }

        previewHeight = size.getHeight();
        previewWidth = size.getWidth();

        sensorOrientation = rotation - getScreenOrientation();

        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        final List<Detector.Recognition> results = detector.recognizeImage(croppedBitmap);

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;

                        final List<Detector.Recognition> mappedRecognitions =
                                new ArrayList<Detector.Recognition>();

                        final float h = cropCopyBitmap.getHeight();
                        final float w = cropCopyBitmap.getWidth();

                        for (final Detector.Recognition result : results) {
                            final RectF location = result.getLocation();

                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                // Draw BBox located in specific range
                                if (localCheck(location.bottom, location.left, location.right, h, w)) {
                                    canvas.drawRect(location, paint);
                                    // Warn of objects with vibration and sound
                                    if (location.bottom >= 0.9 * h) {
                                        vibrate();
                                        if (location.right < w / 2) {
                                            soundPool.play(barkSound, 1, 0, 1, 0, 1f);
                                        }
                                        else if (location.left > w / 2) {
                                            soundPool.play(barkSound, 0, 1, 1, 0, 1f);
                                        }
                                        else {
                                            soundPool.play(barkSound, 1, 1, 1, 0, 1f);
                                        }
                                    }

                                    cropToFrameTransform.mapRect(location);

                                    result.setLocation(location);
                                    mappedRecognitions.add(result);
                                }
                            }
                        }

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;
                    }
                });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onClick(View view) {
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen checkpoints.
    private enum DetectorMode { TF_OD_API; }

    protected boolean localCheck(float y, float x1, float x2, float h, float w) {
        return (y >= h / w * x1 && y >= -1 * h / w * x1 + h) || (y >= h / w * x2 && y >= -1 * h / w * x2 + h);
    }
}
