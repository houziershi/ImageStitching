package com.kunato.imagestitching;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import android.media.ImageReader;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.RenderScript;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static android.hardware.camera2.CameraCharacteristics.*;
import static android.hardware.camera2.CameraCharacteristics.LENS_FACING;
import static android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.hardware.camera2.CaptureRequest.CONTROL_AE_LOCK;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_TRIGGER;
import static android.hardware.camera2.CaptureRequest.CONTROL_AWB_LOCK;
import static android.hardware.camera2.CaptureRequest.SENSOR_EXPOSURE_TIME;

public class MainController extends GLSurfaceView {
    private Activity mActivity;
    static {
        System.loadLibrary("nonfree_stitching");
    }

    private static final String TAG = MainController.class.getName();
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
    public byte[] mFrameByte = new byte[1920*1080*4];
    public boolean mAsyncRunning = false;
    public boolean mRunning = false;
    private boolean mFirstRun = true;
    public float[] mQuaternion = new float[4];
    public int mNumPicture = 1;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
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
            mActivity.onBackPressed();
        }

    };

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private RSProcessor mProcessor;
    private SensorListener mSensorListener;
    private SensorManager mSensorManager;
    private CameraCharacteristics mCharacteristics;
    public GLRenderer mGLRenderer;
    private String mCameraId;
    private Surface mProcessingHdrSurface;
    private RenderScript mRS;
    private float TARGET_ASPECT = 3.f / 4.f;
    private float ASPECT_TOLERANCE = 0.1f;
    private Factory mFactory;


    public MainController(Context context) {
        super(context);
        mFactory = Factory.getFactory(this);
        mActivity = (Activity) context;
        mRS = RenderScript.create(context);
        mGLRenderer = mFactory.getGlRenderer();
        setEGLContextClientVersion(2);
        setRenderer(mGLRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

    }

    public void surfaceCreated ( SurfaceHolder holder ) {
        super.surfaceCreated(holder);
        Resume();
    }

    public void surfaceDestroyed ( SurfaceHolder holder ) {
        mGLRenderer.close();
        super.surfaceDestroyed(holder);
    }

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
    public float[] mRotmat = new float[16];

    public void doStitching(){

        SensorManager.getRotationMatrixFromVector(mRotmat,mQuaternion);
        AsyncTask<Object, Integer, Boolean> imageStitchingTask = new ImageStitchingTask();
        if (mFirstRun) {
            mFirstRun = false;
            mQuaternion[0] = 0f;
            mQuaternion[1] = 0f;
            mQuaternion[2] = 0f;
            mQuaternion[3] = 1f;
            mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME);
        }
        Mat rotationMat = new Mat();
        rotationMat.create(3, 3, CvType.CV_32F);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++)
                rotationMat.put(i, j, mRotmat[i * 4 + j]);
        }

        Mat mat = new Mat(1080, 1920, CvType.CV_8UC4);
        mat.put(0, 0, mFrameByte);
        Mat image = new Mat();
        Imgproc.cvtColor(mat, image, Imgproc.COLOR_RGBA2BGR);
        //TODO create another thread for convert yuv, tracking
        Log.i("Rot",Arrays.toString(mRotmat));
        imageStitchingTask.execute(image, rotationMat);
    }

    public void runProcess(boolean firstTime){
        if(firstTime){
            Log.d("Surface","Setup");
            mPreviewRequestBuilder.set(CONTROL_AF_TRIGGER,CONTROL_AF_TRIGGER_START);
            mPreviewRequestBuilder.set(CONTROL_AWB_LOCK, Boolean.TRUE);
            mPreviewRequestBuilder.set(CONTROL_AE_LOCK, Boolean.TRUE);
            updatePreview();
        }
        else {

            Log.d("Surface","Running=true");
            if(!mAsyncRunning)
                mRunning = true;
        }
    }

    public void Resume() {
        permissionRequest();
        if (mSensorManager == null)
            mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        if (mSensorListener == null) {
            mSensorListener = new SensorListener();
        }
        startBackgroundThread();
        openCamera();

    }

    public void Pause() {
        Log.e(TAG, "onPause");
        mSensorManager.unregisterListener(mSensorListener);
        closeCamera();
        stopBackgroundThread();
    }

    private void openCamera() {
        Log.d("Debug", "openCamera");
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                if (characteristics.get(LENS_FACING) == LENS_FACING_FRONT) continue;

                StreamConfigurationMap map = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP);
                assert map != null;
                List<Size> outputSizes = Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));
                Size largest = Collections.max(outputSizes, new Util.CompareSizesByArea());
//                Log.i("Size","Valid Size :"+outputSizes.toString());

                mImageReader = ImageReader.newInstance(1080, 1920, ImageFormat.YUV_420_888, 5);
                Log.d("CameraCharacteristic","Create Camera With Size ("+largest.getWidth()+","+largest.getHeight()+")");
                Log.d("CameraCharacteristic","LENS_INTRINSIC_CALIBRATION : "+Arrays.toString(characteristics.get(LENS_INTRINSIC_CALIBRATION)));
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
            e.printStackTrace();
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
            Log.d("Debug", "createCameraPreviewSession");

            SurfaceTexture glProcessTexture = mGLRenderer.getProcessSurfaceTexture();
            if (glProcessTexture == null){
                try {
                    Thread.sleep(1000);
                    Log.i("GLSurface Connector","Texture not ready yet try again in 1 sec");
                    createCameraPreviewSession();
                    return;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //This is removed because of glProcessTexture via RS can be done the same work.
//            SurfaceTexture glTexture = mGLRenderer.getSurfaceTexture();
//            glTexture.setDefaultBufferSize(1440, 1080);
//            Surface mGLSurface = new Surface(glTexture);


            Surface mProcessSurface = mImageReader.getSurface();
            Surface mGLProcessSurface = new Surface(glProcessTexture);
            mProcessor = mFactory.getRSProcessor(mRS,new Size(1920,1080));

            mProcessingHdrSurface = mProcessor.getInputHdrSurface();
            mProcessor.setOutputSurface(mGLProcessSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(mProcessSurface, mProcessingHdrSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            if (mCameraDevice == null)
                                return;
                            mCaptureSession = cameraCaptureSession;
                            Range<Long> range = mCharacteristics.get(SENSOR_INFO_EXPOSURE_TIME_RANGE);
                            assert range != null;
                            Long minExpT = range.getLower();
                            Long maxExpT = range.getUpper();
                            mPreviewRequestBuilder.set(SENSOR_EXPOSURE_TIME, ((minExpT + maxExpT) / 128));
                            mPreviewRequestBuilder.set(CONTROL_AF_MODE, CONTROL_AF_MODE_AUTO);
//                            mPreviewRequestBuilder.set(CONTROL_AE_MODE, CONTROL_AE_MODE_OFF);
                            mPreviewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, Util.getJpegOrientation(mCharacteristics, getActivity().getWindowManager().getDefaultDisplay().getRotation()));
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(getActivity(), "Failed", Toast.LENGTH_SHORT).show();
                        }
                    }, null
            );
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//            mPreviewRequestBuilder.addTarget(mProcessSurface);
//            mPreviewRequestBuilder.addTarget(mGLSurface);
            mPreviewRequestBuilder.addTarget(mProcessingHdrSurface);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview(){
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
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

    public Activity getActivity(){
        return mActivity;
    }

    /**
     * private Class SensorListener + Async
     */
    private class SensorListener implements SensorEventListener {
        private float lastTimeStamp = 0f;

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                mQuaternion = Util.getQuadFromGyro(event.values,lastTimeStamp,event.timestamp, mQuaternion,false,true,false,true);
                lastTimeStamp = event.timestamp;
                float[] swapMat = new float[16];
                SensorManager.getRotationMatrixFromVector(swapMat,mQuaternion);
                float[] rotMat = new float[16];
                float[] correctedQuat = {mQuaternion[0],-mQuaternion[1], mQuaternion[2], mQuaternion[3]};
                float[] temp = new float[16];
                SensorManager.getRotationMatrixFromVector(rotMat, correctedQuat);
                Matrix.multiplyMM(temp,0,Util.ROTATE_Y_270,0,swapMat,0);
//                Matrix.multiplyMM(temp, 0, Util.SWAP_X, 0, rotMat, 0);
//                Matrix.multiplyMM(rotMat, 0, Util.SWAP_Z, 0, temp, 0);
                mGLRenderer.setRotationMatrix(rotMat);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }

    //Implement this in JNI
    private class ImageStitchingTask extends AsyncTask<Object, Integer, Boolean> {
        protected Boolean doInBackground(Object... objects) {
            mAsyncRunning = true;
            Mat mat = new Mat(1080, 1920, CvType.CV_8UC4);
            mat.put(0, 0, mFrameByte);
            Mat imageMat = new Mat();
            Imgproc.cvtColor(mat, imageMat, Imgproc.COLOR_RGBA2BGR);
            ImageStitchingNative.getNativeInstance().addToPano(imageMat, (Mat) objects[1]);
            mNumPicture++;
            return true;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Boolean bool) {
            mAsyncRunning = false;
            Log.i("mNumPicture",mNumPicture+"");

        }
    }
}