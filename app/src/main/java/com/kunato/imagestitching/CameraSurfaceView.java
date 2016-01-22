package com.kunato.imagestitching;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static android.hardware.camera2.CameraCharacteristics.*;
import static android.hardware.camera2.CameraCharacteristics.LENS_FACING;
import static android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_OFF;
import static android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_OFF;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE;
import static android.hardware.camera2.CaptureRequest.SENSOR_EXPOSURE_TIME;

public class CameraSurfaceView extends GLSurfaceView {
    private Activity activity;
    static {
        System.loadLibrary("nonfree_stitching");
    }

    private static final String TAG = CameraSurfaceView.class.getName();
    private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {

        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session,CaptureRequest request,
                                        CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
            process(result);
        }

    };
    //Using in OnImageAvailableListener
    private boolean asyncRunning = false;
    private boolean doingRuning = false;
    private boolean firstTime = true;
    private float[] mQuaternion = new float[4];
    private float[] mCameraQuaternion = new float[4];
    public int mNumPicture = 1;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image img = reader.acquireLatestImage();
            if (!asyncRunning) {
                if (!doingRuning) {
                    if(img != null)
                        img.close();
                    return;
                }
                Log.e("INPUT","Image In");
                AsyncTask<Mat, Integer, Mat> imageStitchingTask = new ImageStitchingTask();
                ByteBuffer jpegBuffer = img.getPlanes()[0].getBuffer();
                byte[] jpegData = new byte[jpegBuffer.remaining()];
                jpegBuffer.get(jpegData);

                Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
                Mat rgbaMat = new Mat();
                Mat imageMat = new Mat();
                Utils.bitmapToMat(bitmap, rgbaMat);
                Imgproc.cvtColor(rgbaMat, imageMat, Imgproc.COLOR_RGBA2BGR);
                if (firstTime) {
                    firstTime = false;
                    mQuaternion[0] = 0f;
                    mQuaternion[1] = 0f;
                    mQuaternion[2] = 0f;
                    mQuaternion[3] = 1f;
                    mCameraQuaternion[0] = 0f;
                    mCameraQuaternion[1] = 0f;
                    mCameraQuaternion[2] = 0f;
                    mCameraQuaternion[3] = 1f;
                    mSensorManager.registerListener(runningSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME);
                }
                float[] cameraRotationMatrix = new float[16];
                SensorManager.getRotationMatrixFromVector(cameraRotationMatrix,mCameraQuaternion);
                Mat rotationMat = new Mat();
                rotationMat.create(3, 3, CvType.CV_32F);
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++)
                        rotationMat.put(i, j, cameraRotationMatrix[i * 4 + j]);
                }
                imageStitchingTask.execute(imageMat, rotationMat);
                img.close();
                doingRuning = false;
            } else {
                if (img == null)
                    return;
                img.close();
            }

        }

    };


    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            activity.onBackPressed();
        }

    };

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private SensorListener runningSensorListener;
    private SensorManager mSensorManager;
    private CameraCharacteristics mCharacteristics;
    private GLRenderer glRenderer;
    private String mCameraId;

    /**
     * GLSurface nessecery
     * @param context
     */
    public CameraSurfaceView(Context context) {

        super(context);

        activity = (Activity) context;
        glRenderer = new GLRenderer(this);
        setEGLContextClientVersion(2);
        setRenderer(glRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

    }

    public void surfaceCreated ( SurfaceHolder holder ) {
        super.surfaceCreated(holder);
        Resume();
    }

    public void surfaceDestroyed ( SurfaceHolder holder ) {
        glRenderer.close();
        super.surfaceDestroyed(holder);
    }

    /**
     * UI method
     * @param progress
     */
    public void ESeekBarChanged(int progress) {
        Range<Integer> range = mCharacteristics.get(SENSOR_INFO_SENSITIVITY_RANGE);
        assert range != null;
        int max1 = range.getUpper();//10000
        int min1 = range.getLower();//100
        int iso = ((progress * (max1 - min1)) / 100 + min1);
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
        updatePreview();
    }

    public void FSeekBarChanged(float progress) {
        float minimumLens = mCharacteristics.get(LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        float num = (progress * minimumLens / 100);
        mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, num);
        updatePreview();
    }

    public void runProcess(){
        doingRuning = true;
    }

    public void Resume() {
        permissionRequest();
        if (mSensorManager == null)
            mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        if (runningSensorListener == null) {
            runningSensorListener = new SensorListener();
        }
        startBackgroundThread();
        openCamera();

    }

    public void Pause() {
        Log.e(TAG, "onPause");
        mSensorManager.unregisterListener(runningSensorListener);
        closeCamera();
        stopBackgroundThread();
    }

    private void openCamera() {
        Log.d("Debug","openCamera");
//        configureTransform(width, height);
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                if (characteristics.get(LENS_FACING) == LENS_FACING_FRONT) continue;

                StreamConfigurationMap map = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP);
                assert map != null;
                List<Size> outputSizes = Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));
                Size largest = Collections.max(outputSizes, new Util.CompareSizesByArea());

                mImageReader = ImageReader.newInstance(largest.getWidth()/5, largest.getHeight()/5, ImageFormat.JPEG, 2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                mCharacteristics = characteristics;
                mCameraId = cameraId;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }


            if (getActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                permissionRequest();
                return;
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);

        } catch (Exception e) {
            Log.e("Error",e.getLocalizedMessage());
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        try {
            mBackgroundThread.quitSafely();
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException | NullPointerException e) {
            e.printStackTrace();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }
    }

    private void createCameraPreviewSession() {
        try {
            Log.d("Debug","createCameraPreviewSession");
            SurfaceTexture texture = glRenderer.getSurfaceTexture();
            if (texture == null){
                try {
                    Thread.sleep(1000);
                    Log.d("GLSurface-Camera-connector","Texture not ready yet try again in 1 sec");
                    createCameraPreviewSession();

                    return;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            texture.setDefaultBufferSize(800, 1280);
            Surface surface = new Surface(texture);
            Surface mImageSurface = mImageReader.getSurface();
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mImageSurface);
            mPreviewRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Arrays.asList(mImageSurface, surface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            if (mCameraDevice == null) return;

                            mCaptureSession = cameraCaptureSession;

                            Range<Long> range = mCharacteristics.get(SENSOR_INFO_EXPOSURE_TIME_RANGE);
                            assert range != null;
                            Long minExpT = range.getLower();
                            Long maxExpT = range.getUpper();
                            mPreviewRequestBuilder.set(SENSOR_EXPOSURE_TIME, ((minExpT + maxExpT) / 128));
                            mPreviewRequestBuilder.set(CONTROL_AF_MODE, CONTROL_AF_MODE_OFF);
                            mPreviewRequestBuilder.set(CONTROL_AE_MODE, CONTROL_AE_MODE_OFF);
                            mPreviewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation(mCharacteristics, getActivity().getWindowManager().getDefaultDisplay().getRotation()));
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(getActivity(), "Failed", Toast.LENGTH_SHORT).show();
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method
     */
    private void updatePreview(){
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("updatePreview", "ExceptionExceptionException");
        }
    }

    public void permissionRequest() {
        if (getActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                getActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                getActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            getActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA},
                    1);
        }
    }

    private int getJpegOrientation(CameraCharacteristics c, int deviceOrientation) {
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return 0;
        int sensorOrientation = c.get(SENSOR_ORIENTATION);
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;
        boolean facingFront = c.get(LENS_FACING) == LENS_FACING_FRONT;
        if (facingFront) deviceOrientation = -deviceOrientation;
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    public Activity getActivity(){
        return activity;
    }

    /**
     * private Class SensorListener + Async
     */
    private class SensorListener implements SensorEventListener {
        private float lastTimeStamp = 0f;
        private static final float NS2S = 1.0f / 1000000000.0f;


        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                mQuaternion = Util.getQuadFromGyro(event.values,lastTimeStamp,event.timestamp,mQuaternion,false,false,false);
                mCameraQuaternion = Util.getQuadFromGyro(event.values,lastTimeStamp,event.timestamp,mCameraQuaternion,false,true,false);
                lastTimeStamp = event.timestamp;
                float[] rotMat = new float[16];
                SensorManager.getRotationMatrixFromVector(rotMat,mQuaternion);
                glRenderer.setRotationMatrix(rotMat);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }
    private class ImageStitchingTask extends AsyncTask<Mat, Integer, Mat> {
        protected Mat doInBackground(Mat... mat) {
            asyncRunning = true;
            ImageStitchingNative is = new ImageStitchingNative();
            Mat ret = is.addToPano(mat[0], mat[1]);
            mNumPicture++;
            return ret;
        }

        protected void onProgressUpdate(Integer... progress) {
        }
        //TODO dummy vertices
        protected void onPostExecute(Mat result) {

            asyncRunning = false;
            Log.d("mNumPicture",mNumPicture+"");
            if(mNumPicture < 3)
                return;
            Bitmap bmp = Bitmap.createBitmap(result.cols(), result.rows(), Bitmap.Config.ARGB_8888);
            Mat test = new Mat(result.height(),result.width(),CvType.CV_8UC3);
            Imgproc.cvtColor(result,test,Imgproc.COLOR_BGR2RGBA);
            Utils.matToBitmap(test, bmp);
            //Bitmap finish send to texture
            Log.d("Post","Finished, Size :"+result.size().width+","+result.size().height);
        }
    }
}