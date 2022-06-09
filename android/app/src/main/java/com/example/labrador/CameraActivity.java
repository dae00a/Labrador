package com.example.labrador;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.util.Size;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

public abstract class CameraActivity extends AppCompatActivity
        implements ImageReader.OnImageAvailableListener,
        View.OnClickListener {

    private LocationManager locManager;
    private GPSListener gpsListener;

    private PermissionSupport permission;

    protected int previewWidth = 0;
    protected int previewHeight = 0;
    private boolean debug = false;

    private Handler handler;
    private HandlerThread handlerThread;

    private boolean isProcessingFrame = false;
    private final byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;

    private Runnable postInferenceCallback;
    private Runnable imageConverter;

    protected static SoundPool soundPool;
    protected static int barkSound;
    protected static int taxiSound;
    protected static int gpsSound;

    private MediaPlayer mediaPlayer;

    protected Vibrator vibrator;
    protected int amplitude;

    private boolean flashOn;
    private String cameraId;
    private CameraManager cameraManager;

    protected CameraActivity() {
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(null);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        soundPool = new SoundPool.Builder()
                .setAudioAttributes(attributes)
                .build();
        barkSound = soundPool.load(this, R.raw.dog_sound, 1);
        taxiSound = soundPool.load(this, R.raw.taxi_not_support, 1);
        gpsSound = soundPool.load(this, R.raw.gps_support, 1);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);

        permission = new PermissionSupport(this, this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (permission.checkPermission()) {
                setFragment();
            } else {
                permission.requestPermission();
            }
        }
        else {
            setFragment();
        }

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        final Button lightButton = findViewById(R.id.light_button);
        lightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /** TODO
                 *  Now on, Camera2 API does not allow set flash light with capturing from same camera
                 */
                //flashlight();
            }
        });

        final Button manualButton = findViewById(R.id.manual_button);
        manualButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(CameraActivity.this);
                builder.setTitle("도움말");
                builder.setMessage(R.string.manual_content);
                builder.setIcon(null);
                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mediaPlayer.stop();
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();

                mediaPlayer = MediaPlayer.create(CameraActivity.this, R.raw.manual_support);
                mediaPlayer.start();
            }
        });

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        final SeekBar vibrationSeekbar = findViewById(R.id.vibration_seekbar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrationSeekbar.setMin(1);
        }
        vibrationSeekbar.setMax(200);
        vibrationSeekbar.setProgress(100);
        amplitude = 100;

        vibrationSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (i % 10 != 0) {
                    if (i / 10 <= 0)
                        amplitude = 1;
                    else
                        amplitude = (i / 10) * 10;
                }
                else {
                    amplitude = i;
                }
                vibrationSeekbar.setProgress(amplitude);
                vibrate();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        final LinearLayout locationLayout = (LinearLayout) findViewById(R.id.location_layout);

        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.OnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public void onShowPress(MotionEvent e) {

            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {

            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                Point size = new Point();
                getWindowManager().getDefaultDisplay().getSize(size);
                if (e1.getX() - e2.getX() > size.x /2.0)
                    startLocationService();
                return true;
            }
        });

        locationLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return true;
            }
        });

        while (soundPool.play(barkSound, 1, 1, 1, 0, 1f) == 0);
    }

    @Override
    public synchronized void onStart() {
        super.onStart();
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        }
        catch (final InterruptedException e) {
            e.printStackTrace();
        }

        super.onPause();
    }

    @Override
    public synchronized void onStop() {
        super.onStop();
    }

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();

        soundPool.release();
        mediaPlayer.release();
        mediaPlayer = null;
    }

    /** Request Permissions for using Camera */
    @Override
    public void onRequestPermissionsResult(
            final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!permission.permissionResult(requestCode, permissions, grantResults))
            permission.requestPermission();
        else
            setFragment();
    }

    /** Callback for Camera2 API */
    @Override
    public void onImageAvailable(ImageReader reader) {
        // Waiting until have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (isProcessingFrame) {
                image.close();
                return;
            }
            isProcessingFrame = true;
            Trace.beginSection("imageAvailable");
            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };

            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            image.close();
                            isProcessingFrame = false;
                        }
                    };

            processImage();
        }
        catch (final Exception e) {
            Log.e(String.valueOf(e), "Exception!");
            Trace.endSection();
            return;
        }
        Trace.endSection();
    }

    // Find a proper Camera
    private String chooseCamera() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : cameraManager.getCameraIdList()) {
                final CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);

                // Exclude front facing camera
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                final StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // Fallback to camera1 API for internal cameras that don't have full support.
                // This should help with legacy situations where using the camera2 API causes
                // distorted or otherwise broken previews
                return cameraId;
            }
        }
        catch (CameraAccessException e) {
            Log.e(String.valueOf(e), "Not allowed to access camera");
        }

        return null;
    }

    protected void setFragment() {
        cameraId = chooseCamera();

        Fragment fragment;
        CameraConnectionFragment camera2Fragment =
                CameraConnectionFragment.newInstance(
                        new CameraConnectionFragment.ConnectionCallback() {
                            @Override
                            public void onPreviewSizeChosen(final Size size, final int rotation) {
                                previewHeight = size.getHeight();
                                previewWidth = size.getWidth();
                                CameraActivity.this.onPreviewSizeChosen(size, rotation);
                            }
                        },
                        this,
                        getLayoutId(),
                        getDesiredPreviewFrameSize());

        camera2Fragment.setCamera(cameraId);
        fragment = camera2Fragment;

        getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    protected void vibrate() {
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, amplitude));
        }
        else {
            vibrator.vibrate(100);
        }
    }

    public void startLocationService() {
        locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            gpsListener = new GPSListener();

            if (locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, gpsListener);
            else
                locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsListener);
        } catch(SecurityException e) {
            e.printStackTrace();
        }
    }

    class GPSListener implements LocationListener {

        public void onLocationChanged(@NonNull Location location) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();

            GeoThread thread = new GeoThread(latitude, longitude);
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            soundPool.play(gpsSound, 1, 1, 1, 0, 1f);
            vibrate();
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
        }
    }

    class GeoThread extends Thread {
        String parseName = null;
        double latitude;
        double longitude;

        public GeoThread(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        @Override
        public void run() {
            Geocoder gCoder = new Geocoder(getApplicationContext(), Locale.KOREA);
            List<Address> address = null;

            try {
                address = gCoder.getFromLocation(latitude,longitude,2);
                Address a = address.get(0);
                String localName = a.getAddressLine(a.getMaxAddressLineIndex());
                StringTokenizer tokenizer = new StringTokenizer(localName);
                String temp = tokenizer.nextToken();
                while (temp != null) {
                    if (temp.charAt(temp.length() - 1) == '시'
                            || temp.charAt(temp.length() - 1) == '군') {
                        parseName = temp;
                        break;
                    }
                    temp = tokenizer.nextToken();
                }
                Log.d("location", parseName);

                boolean containCheck = false;
                String phoneNum = null;

                InputStreamReader is = new InputStreamReader(getResources().openRawResource(R.raw.taxi_support_open_data), "EUC-KR");
                BufferedReader reader = new BufferedReader(is);
                CSVReader read = new CSVReader(reader);
                String[] record = null;
                while ((record = read.readNext()) != null) {
                    if (Arrays.deepToString(record).contains(parseName)) {
                        containCheck = true;
                        phoneNum = record[9];
                        break;
                    }
                }
                if (containCheck) {
                    Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNum));
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
                else {
                    soundPool.play(taxiSound, 1, 1, 1, 0, 1f);
                }
            } catch (IOException | CsvValidationException e) {
                e.printStackTrace();
            }
            locManager.removeUpdates(gpsListener);
        }
    }

    /**
    private void flashlight() {
        flashOn = !flashOn;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraId, flashOn);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    */

    public boolean isDebug() { return debug; }

    protected abstract void processImage();

    protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

    protected abstract int getLayoutId();

    protected abstract Size getDesiredPreviewFrameSize();
}
