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
import android.support.annotation.NonNull;
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
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.StrictMath.cos;


public class CameraSurfaceView extends GLSurfaceView {
    private Activity activity;
    static {
        System.loadLibrary("nonfree_stitching");
    }

    private static final String TAG = CameraSurfaceView.class.getName();
    private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {
            Log.d("mCaptureCallBack",result.getFrameNumber()+"");
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };
    //Using in OnImageAvailableListener
    private boolean asyncRunning = false;
    private static int count = 0;
    private boolean doingRuning = false;
    private boolean firstTime = false;
    private float[] mRotationMatrix = new float[16];
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image img = reader.acquireLatestImage();
            if (count % 10 == 0 && !asyncRunning) {
                if (!doingRuning) {
                    if(img != null)
                        img.close();
                    return;
                }

                AsyncTask<Mat, Integer, org.opencv.core.Size> imageStitchingTask = new ImageStitchingTask();
                ByteBuffer jpegBuffer = img.getPlanes()[0].getBuffer();
                byte[] jpegData = new byte[jpegBuffer.remaining()];
                jpegBuffer.get(jpegData);
                Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
                Mat rgbaMat = new Mat();
                Mat imageMat = new Mat();
                Utils.bitmapToMat(bitmap, rgbaMat);
                Imgproc.cvtColor(rgbaMat, imageMat, Imgproc.COLOR_RGBA2BGR);
                if (firstTime) {
                    for (int i = 0; i < mRotationMatrix.length; i++) {
                        mRotationMatrix[i] = 0;
                    }
                    mRotationMatrix[0] = 1.0f;
                    mRotationMatrix[5] = 1.0f;
                    mRotationMatrix[10] = 1.0f;
                    mRotationMatrix[15] = 1.0f;
                    firstTime = false;
                }
                Mat rotationMat = new Mat();
                rotationMat.create(3, 3, CvType.CV_32F);
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++)
                        rotationMat.put(i, j, mRotationMatrix[i * 4 + j]);
                }
                imageStitchingTask.execute(imageMat, rotationMat);
                img.close();
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
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
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
        setRenderMode ( GLSurfaceView.RENDERMODE_WHEN_DIRTY );

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
        for (int i = 0; i < mRotationMatrix.length; i++) {
            mRotationMatrix[i] = 0;

        }
        mRotationMatrix[0] = 1.0f;
        mRotationMatrix[5] = 1.0f;
        mRotationMatrix[10] = 1.0f;
        mRotationMatrix[15] = 1.0f;
        mSensorManager.registerListener(runningSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), 10000);
        doingRuning = true;
        firstTime = true;
    }

    public void Resume() {
        permissionRequest();
        if (mSensorManager == null)
            mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        if (runningSensorListener == null) {
            runningSensorListener = new SensorListener();
        }
        openCamera();
        startBackgroundThread();
    }

    public void Pause() {
        Log.e(TAG, "onPause");
        mSensorManager.unregisterListener(runningSensorListener);
        closeCamera();
        stopBackgroundThread();
    }

    private void openCamera() {
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

                mImageReader = ImageReader.newInstance(largest.getWidth() / 16, largest.getHeight() / 16, ImageFormat.JPEG, 2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                mCharacteristics = characteristics;
                mCameraId = cameraId;
                return;
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

        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
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
            SurfaceTexture texture = glRenderer.getSurfaceTexture();
            texture.setDefaultBufferSize(800, 1280);

            Surface surface = new Surface(texture);
            Surface mImageSurface = mImageReader.getSurface();
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mImageSurface);
            mPreviewRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Arrays.asList(mImageSurface, surface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
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
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
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

        // Round device orientation to a multiple of 90
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;

        // Reverse device orientation for front-facing cameras
        boolean facingFront = c.get(LENS_FACING) == LENS_FACING_FRONT;
        if (facingFront) deviceOrientation = -deviceOrientation;

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation

        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    public Activity getActivity(){
        return activity;
    }

    /**
     * private Class SensorListener + Async
     */
    private class SensorListener implements SensorEventListener {
        public float[] getRotationFromSensor(SensorEvent event){
            float[] rotationMatrix = new float[16];
            SensorManager.getRotationMatrixFromVector(
                    rotationMatrix, event.values);
            return rotationMatrix;
        }
        private static final float NS2S = 1.0f / 1000000000.0f;

        private float timestamp;
        public float[] getRotationFromGyro(SensorEvent event){
            float[] deltaRotationVector = new float[4];
            if (timestamp != 0) {
                final float dT = (event.timestamp - timestamp) * NS2S;
                float axisX = event.values[0];
                float axisY = -event.values[1];
                float axisZ = event.values[2];

                float omegaMagnitude = (float) sqrt(axisX*axisX + axisY*axisY + axisZ*axisZ);
                if (omegaMagnitude > 1.0) {
                    axisX /= omegaMagnitude;
                    axisY /= omegaMagnitude;
                    axisZ /= omegaMagnitude;
                }

                float thetaOverTwo = omegaMagnitude * dT / 2.0f;
                float sinThetaOverTwo = (float) sin(thetaOverTwo);
                float cosThetaOverTwo = (float) cos(thetaOverTwo);
                deltaRotationVector[0] = sinThetaOverTwo * axisX;
                deltaRotationVector[1] = sinThetaOverTwo * axisY;
                deltaRotationVector[2] = sinThetaOverTwo * axisZ;
                deltaRotationVector[3] = cosThetaOverTwo;
            }
            timestamp = event.timestamp;
            float[] deltaRotationMatrix = new float[16];
            SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, deltaRotationVector);
            return Util.naivMatrixMultiply(mRotationMatrix, deltaRotationMatrix);
        }
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE){

                mRotationMatrix = getRotationFromGyro(event);
//                mRotVec[0] += event.values[0];
//                mRotVec[1] -= event.values[1];
//                mRotVec[2] += event.values[2];
//                timedelta += timestamp;
//                timestamp = event.timestamp;
            }
//            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            // convert the rotation-vector to a 4x4 matrix. the matrix
            // is interpreted by Open GL as the inverse of the
            // rotation-vector, which is what we want.
//                mRotationMatrix = getRotationFromSensor(event);
            // [1,0,0,0]
            // [0,1,0,0]
            // [0,0,1,0]
            // [0,0,0,0]
//            DecimalFormat matrixFormatter = new DecimalFormat("+#,##0.00;-#");
//            for(int i = 0 ; i < 4 ; i++) {
//                StringBuilder sb = new StringBuilder();
//                sb.append('[');
//                for(int j = 0 ; j < 4 ; j++){
//                    sb.append(matrixFormatter.format(mRotationMatrix[i*4+j]));
//                    if(j != 4)
//                        sb.append(',');
//
//                }
//                sb.append(']');
//                Log.d("Matrix",sb.toString());
//            }
//            Log.d("Matrix","############################");

        }


        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }
    private class ImageStitchingTask extends AsyncTask<Mat, Integer, org.opencv.core.Size> {
        protected org.opencv.core.Size doInBackground(Mat... mat) {
            asyncRunning = true;
            ImageStitchingNative is = new ImageStitchingNative();
            Mat ret = is.addToPano(mat[0], mat[1]);
            return ret.size();

        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(org.opencv.core.Size result) {
            asyncRunning = false;
            Log.d("Post","Finished, Size :"+result.width+","+result.height);
        }
    }
}