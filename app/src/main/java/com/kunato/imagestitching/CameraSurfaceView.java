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
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
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

    private static final int sImageFormat = ImageFormat.JPEG;

    private RelativeLayout layout;
    public static boolean accessGranted = true;

    static {
        System.loadLibrary("nonfree_stitching");
    }

    private static final String TAG = CameraSurfaceView.class.getName();

    private final CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
                private void process(CaptureResult result) {
                }

                @Override
                public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                                CaptureResult partialResult) {
                    process(partialResult);
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                               TotalCaptureResult result) {
                    process(result);
                }

            };

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener =
            new TextureView.SurfaceTextureListener() {

                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
//                    configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {

                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture texture) {

                }

            };
    private String mCameraId;
    private TextureView mTextureView;
    private boolean asyncRunning = false;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {

                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = reader.acquireLatestImage();
                    //then
                    if (count % 10 == 0 && !asyncRunning) {
                        if (!doingRuning) {
                            if(img != null)
                            img.close();
                            return;
                        }

                        AsyncTask<Mat, Integer, org.opencv.core.Size> imageStitchingTask = new ImageStitchingTask();
                        ByteBuffer jpegBuffer = img.getPlanes()[0].getBuffer();
                        if (img == null) {
                            return;
                        }
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
    private int count;
    private float[] mRotationMatrix = new float[16];
    private SensorListener runningSensorListener;
    private SensorManager mSensorManager;
    private SeekBar fSeekBar;
    private SeekBar eSeekBar;
    private CameraCharacteristics mCharacteristics;
    private boolean doingRuning = false;
    private boolean firstTime = false;
    private GLRenderer glRenderer;

    public CameraSurfaceView(Context context) {

        super(context);

        activity = (Activity) context;
        glRenderer = new GLRenderer(this);
        setEGLContextClientVersion ( 2 );
        setRenderer ( glRenderer );
        setRenderMode ( GLSurfaceView.RENDERMODE_WHEN_DIRTY );

        Init();
    }
    public void surfaceCreated ( SurfaceHolder holder ) {
        super.surfaceCreated(holder);
        Resume();
    }

    public void surfaceDestroyed ( SurfaceHolder holder ) {
        glRenderer.close();
        super.surfaceDestroyed(holder);
    }

    public void surfaceChanged (SurfaceHolder holder, int format, int w, int h ) {
        super.surfaceChanged(holder, format, w, h);
    }

    private Activity getActivity(){
        return activity;
    }
    public void Init() {

        Activity rootView = getActivity();
        mTextureView = (TextureView)rootView.findViewById(R.id.texture);

        layout = (RelativeLayout) rootView.findViewById(R.id.fragment_decoder_layout);
        final TextView status = (TextView) activity.findViewById(R.id.status);
        Button start = (Button) rootView.findViewById(R.id.btn_str);
        Button stop = (Button) rootView.findViewById(R.id.btn_stop);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startProcess();
                status.setText("Start");

            }

        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSensorManager.unregisterListener(runningSensorListener);
                status.setText("Stop");
                doingRuning = false;
            }
        });
    }

    public void ESeekBarChanged(int progress) {
        Range<Integer> range = mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        int max1 = range.getUpper();//10000
        int min1 = range.getLower();//100
        int iso = ((progress * (max1 - min1)) / 100 + min1);
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
        updatePreview();
    }

    public void FSeekBarChanged(float progress) {
        float minimumLens = mCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        float num = (progress * minimumLens / 100);
        mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, num);
        updatePreview();
    }

    public void startProcess(){
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
    public void permissionRequest() {
        if (getActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                getActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                getActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            getActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA},
                    1);
        }
    }

    public void Resume() {
        permissionRequest();
        if (mSensorManager == null)
            mSensorManager = (SensorManager) getActivity().getSystemService(getActivity().SENSOR_SERVICE);
        accessGranted = true;
        if (runningSensorListener == null) {
            runningSensorListener = new SensorListener();
        }
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        openCamera(800, 1280);

//        mSensorManager.registerListener((SensorEventListener) runningSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), 10000);

    }

    public void Pause() {
        Log.e(TAG, "onPause");
        mSensorManager.unregisterListener(runningSensorListener);
        closeCamera();

//        stopBackgroundThread();
    }

    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) getActivity().getSystemService(getActivity().CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                if (characteristics.get(LENS_FACING) == LENS_FACING_FRONT) continue;

                StreamConfigurationMap map = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP);

                // For still image captures, we use the largest available size.
                List<Size> outputSizes = Arrays.asList(map.getOutputSizes(sImageFormat));
                Size largest = Collections.max(outputSizes, new Util.CompareSizesByArea());

                mImageReader = ImageReader.newInstance(largest.getWidth() / 16, largest.getHeight() / 16, sImageFormat, 2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                mCharacteristics = characteristics;
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera(int width, int height) {
        setUpCameraOutputs(width, height);
//        configureTransform(width, height);
        CameraManager manager = (CameraManager) getActivity().getSystemService(getActivity().CAMERA_SERVICE);
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
        } catch (InterruptedException e) {
            e.printStackTrace();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (NullPointerException ex) {
            ex.printStackTrace();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }
    }

    private int getJpegOrientation(CameraCharacteristics c, int deviceOrientation) {
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return 0;
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Round device orientation to a multiple of 90
        deviceOrientation = (deviceOrientation + 45) / 90 * 90;

        // Reverse device orientation for front-facing cameras
        boolean facingFront = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
        if (facingFront) deviceOrientation = -deviceOrientation;

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        int jpegOrientation = (sensorOrientation + deviceOrientation + 360) % 360;

        return jpegOrientation;
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = glRenderer.getSurfaceTexture();
            Log.d("test","test2");
            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(800, 1280);

            Surface surface = new Surface(texture);
            Surface mImageSurface = mImageReader.getSurface();
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mImageSurface);
            mPreviewRequestBuilder.addTarget(surface);
            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(mImageSurface, surface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            Log.e(TAG, "onConfigured");
                            if (mCameraDevice == null) return;

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;

                            Range<Long> range = mCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                            Long minExpT = range.getLower();
                            Long maxExpT = range.getUpper();
                            mPreviewRequestBuilder.set(SENSOR_EXPOSURE_TIME, ((minExpT + maxExpT) / 128));
                            // Auto focus should be continuous for camera preview.
                            mPreviewRequestBuilder.set(CONTROL_AF_MODE, CONTROL_AF_MODE_OFF);
                            mPreviewRequestBuilder.set(CONTROL_AE_MODE, CONTROL_AE_MODE_OFF);
                            mPreviewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation(mCharacteristics, getActivity().getWindowManager().getDefaultDisplay().getRotation()));

                            // Finally, we start displaying the camera preview.
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



    class SensorListener implements SensorEventListener {
        public float[] getRotationFromSensor(SensorEvent event){
            float[] rotationMatrix = new float[16];
            SensorManager.getRotationMatrixFromVector(
                    rotationMatrix, event.values);
            return rotationMatrix;
        }
        private static final float NS2S = 1.0f / 1000000000.0f;

        private float timestamp;
        public float[] getRotationFromGyro(SensorEvent event){
            // This timestep's delta rotation to be multiplied by the current rotation
            // after computing it from the gyro sample data.
            float[] deltaRotationVector = new float[4];
            if (timestamp != 0) {
                final float dT = (event.timestamp - timestamp) * NS2S;
                // Axis of the rotation sample, not normalized yet.
                float axisX = event.values[0];
                float axisY = -event.values[1];
                float axisZ = event.values[2];

                // Calculate the angular speed of the sample
                float omegaMagnitude = (float) sqrt(axisX*axisX + axisY*axisY + axisZ*axisZ);

                // Normalize the rotation vector if it's big enough to get the axis
                // (that is, EPSILON should represent your maximum allowable margin of error)
                if (omegaMagnitude > 1.0) {
                    axisX /= omegaMagnitude;
                    axisY /= omegaMagnitude;
                    axisZ /= omegaMagnitude;
                }

                // Integrate around this axis with the angular speed by the timestep
                // in order to get a delta rotation from this sample over the timestep
                // We will convert this axis-angle representation of the delta rotation
                // into a quaternion before turning it into the rotation matrix.
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
            // User code should concatenate the delta rotation we computed with the current rotation
            // in order to get the updated rotation.
            // rotationCurrent = rotationCurrent * deltaRotationMatrix;

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