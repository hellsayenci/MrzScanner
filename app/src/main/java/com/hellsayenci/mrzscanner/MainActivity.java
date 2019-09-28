package com.hellsayenci.mrzscanner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.controls.Audio;
import com.otaliastudios.cameraview.controls.Flash;
import com.otaliastudios.cameraview.controls.Mode;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static String TAG = "MainActivity";

    private CameraView camera;

    private FrameOverlay viewFinder;

    private TextRecognitionHelper textRecognitionHelper;

    private AtomicBoolean processing = new AtomicBoolean(false);

    private ProcessOCR processOCR;

    private Bitmap originalBitmap = null;
    private Bitmap scannable = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        camera = findViewById(R.id.camera);
        camera.setLifecycleOwner(this);


        camera.addCameraListener(new CameraListener() {
            @Override
            public void onCameraOpened(@NonNull CameraOptions options) {
                viewFinder = new FrameOverlay(MainActivity.this);
                camera.addView(viewFinder);
                camera.addFrameProcessor(frameProcessor);
            }
        });

        textRecognitionHelper = new TextRecognitionHelper(this, new TextRecognitionHelper.OnMRZScanned() {
            @Override
            public void onScanned(String mrzText) {
                try {
                    FileOutputStream fos = new FileOutputStream(getFilesDir().getAbsolutePath() + "/" + "mrzimage.png");
                    originalBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }


                try {
                    FileOutputStream fos = new FileOutputStream(getFilesDir().getAbsolutePath() + "/" + "scannable.png");
                    scannable.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Log.e("MRZ", mrzText);
                camera.removeFrameProcessor(frameProcessor);

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Scanned MRZ")
                        .setMessage(mrzText)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // Continue with delete operation
                            }
                        })
                        .show();
            }
        });
    }

    private FrameProcessor frameProcessor = new FrameProcessor() {
        @Override
        public void process(@NonNull Frame frame) {
            if (frame.getData() != null && !processing.get()) {
                processing.set(true);

                YuvImage yuvImage = new YuvImage(frame.getData(), ImageFormat.NV21, frame.getSize().getWidth(), frame.getSize().getHeight(), null);
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, frame.getSize().getWidth(), frame.getSize().getHeight()), 100, os);
                byte[] jpegByteArray = os.toByteArray();

                Bitmap bitmap = BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.length);

                if(bitmap != null) {
                    bitmap = rotateImage(bitmap, frame.getRotation());

                    bitmap = getViewFinderArea(bitmap);

                    originalBitmap = bitmap;

                    scannable = getScannableArea(bitmap);

                    processOCR = new ProcessOCR();
                    processOCR.setBitmap(scannable);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            processOCR.execute();
                        }
                    });
                }
            }
        }
    };

    private Bitmap getViewFinderArea(Bitmap bitmap) {
        int sizeInPixel = getResources().getDimensionPixelSize(R.dimen.frame_margin);
        int center = bitmap.getHeight() / 2;

        int left = sizeInPixel;
        int right = bitmap.getWidth() - sizeInPixel;
        int width = right - left;
        int frameHeight = (int) (width / 1.42f); // Passport's size (ISO/IEC 7810 ID-3) is 125mm × 88mm

        int top = center - (frameHeight / 2);

        bitmap = Bitmap.createBitmap(bitmap, left, top,
                width, frameHeight);

        return bitmap;
    }

    private Bitmap getScannableArea(Bitmap bitmap){
        int top = bitmap.getHeight() * 6 / 10;

        bitmap = Bitmap.createBitmap(bitmap, 0, top,
                bitmap.getWidth(), bitmap.getHeight() - top);

        return bitmap;
    }

    private Bitmap rotateImage(Bitmap bitmap, int rotate){
        Log.v(TAG, "Rotation: " + rotate);

        if (rotate != 0) {

            // Getting width & height of the given image.
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();

            // Setting pre rotate
            Matrix mtx = new Matrix();
            mtx.preRotate(rotate);

            // Rotating Bitmap
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
        }

        // Convert to ARGB_8888, required by tess
        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        return bitmap;
    }

    /**
     * reduces the size of the image
     * @param image
     * @param maxSize
     * @return
     */
    public Bitmap getResizedBitmap(Bitmap image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        float bitmapRatio = (float)width / (float) height;
        if (bitmapRatio > 1) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(image, width, height, true);
    }

    private class ProcessOCR extends AsyncTask {

        Bitmap bitmap = null;

        @Override
        protected Object doInBackground(Object[] objects) {
            if (bitmap != null) {

                textRecognitionHelper.setBitmap(bitmap);

                textRecognitionHelper.doOCR();

                textRecognitionHelper.stop();

            }
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            processing.set(false);
        }

        public void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.flash_menu:
                if(camera.getFlash() == Flash.OFF){
                    camera.setFlash(Flash.TORCH);
                    item.setIcon(R.drawable.ic_flash_off_black_24dp);
                } else{
                    camera.setFlash(Flash.OFF);
                    item.setIcon(R.drawable.ic_flash_on_black_24dp);
                }
                return true;
        }
        return false;
    }
}
