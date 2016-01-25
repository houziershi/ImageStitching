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
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.highgui.Highgui;
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
    private Activity mActivity;
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
    private boolean mAsyncRunning = false;
    private boolean mRunning = false;
    private boolean mFirstRun = true;
    private float[] mQuaternion = new float[4];
    private float[] mCameraQuaternion = new float[4];
    public int mNumPicture = 1;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        public Mat imageToMat(Image image) {
            ByteBuffer buffer;
            int rowStride;
            int pixelStride;
            int width = image.getWidth();
            int height = image.getHeight();
            int offset = 0;

            Image.Plane[] planes = image.getPlanes();
            byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
            byte[] rowData = new byte[planes[0].getRowStride()];

            for (int i = 0; i < planes.length; i++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
                buffer = planes[i].getBuffer();
                rowStride = planes[i].getRowStride();
                pixelStride = planes[i].getPixelStride();
                int w = (i == 0) ? width : width / 2;
                int h = (i == 0) ? height : height / 2;
                for (int row = 0; row < h; row++) {
                    if (pixelStride == bytesPerPixel) {
                        Log.d("YUV","1");
                        int length = w * bytesPerPixel;
                        buffer.get(data, offset, length);
                        // Advance buffer the remainder of the row stride, unless on the last row.
                        // Otherwise, this will throw an IllegalArgumentException because the buffer
                        // doesn't include the last padding.
                        if (h - row != 1) {
                            buffer.position(buffer.position() + rowStride - length);
                        }
                        offset += length;
                    } else {
                        Log.d("YUV","2");
                        // On the last row only read the width of the image minus the pixel stride
                        // plus one. Otherwise, this will throw a BufferUnderflowException because the
                        // buffer doesn't include the last padding.
                        if (h - row == 1) {
                            buffer.get(rowData, 0, width - pixelStride + 1);
                        } else {
                            buffer.get(rowData, 0, rowStride);
                        }

                        for (int col = 0; col < w; col++) {
                            data[offset++] = rowData[col * pixelStride];
                        }
                    }
                }
            }
            // Finally, create the Mat.
            Mat mat = new Mat(height + height / 2, width, CvType.CV_8UC1);
            mat.put(0, 0, data);

            return mat;
        }
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (!mAsyncRunning) {
                if (!mRunning) {
                    if(image != null)
                        image.close();
                    return;
                }
                Log.e("INPUT","Image In");

                AsyncTask<Mat, Integer, Mat> imageStitchingTask = new ImageStitchingTask();
                Mat yuvMat = imageToMat(image);
                Mat imageMat = new Mat(image.getHeight(),image.getWidth(),CvType.CV_8UC3);

                Imgproc.cvtColor(yuvMat,imageMat,Imgproc.COLOR_YUV420p2RGB);
                Highgui.imwrite("/sdcard/yuvtest.jpg", yuvMat);
                Highgui.imwrite("/sdcard/test.jpg", imageMat);
                if (mFirstRun) {
                    mFirstRun = false;
                    mQuaternion[0] = 0f;
                    mQuaternion[1] = 1f;
                    mQuaternion[2] = 0f;
                    mQuaternion[3] = 0f;
                    mCameraQuaternion[0] = 0f;
                    mCameraQuaternion[1] = 0f;
                    mCameraQuaternion[2] = 0f;
                    mCameraQuaternion[3] = 1f;
                    mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME);
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
                image.close();
                mRunning = false;
            } else {
                if (image == null)
                    return;
                image.close();
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
            mActivity.onBackPressed();
        }

    };

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private SensorListener mSensorListener;
    private SensorManager mSensorManager;
    private CameraCharacteristics mCharacteristics;
    private GLRenderer mGLRenderer;
    private String mCameraId;


    public CameraSurfaceView(Context context) {
        super(context);
        mActivity = (Activity) context;
        mGLRenderer = new GLRenderer(this);
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

    public void runProcess(){
        mRunning = true;
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
        Log.d("Debug","openCamera");
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                if (characteristics.get(LENS_FACING) == LENS_FACING_FRONT) continue;

                StreamConfigurationMap map = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP);
                assert map != null;
                List<Size> outputSizes = Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));
                Size largest = Collections.max(outputSizes, new Util.CompareSizesByArea());

                mImageReader = ImageReader.newInstance(largest.getWidth()/5, largest.getHeight()/5, ImageFormat.YUV_420_888, 2);
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
            SurfaceTexture texture = mGLRenderer.getSurfaceTexture();
            if (texture == null){
                try {
                    Thread.sleep(1000);
                    Log.i("GLSurface-Camera-connector","Texture not ready yet try again in 1 sec");
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
                            if (mCameraDevice == null)
                                return;
                            mCaptureSession = cameraCaptureSession;
                            Range<Long> range = mCharacteristics.get(SENSOR_INFO_EXPOSURE_TIME_RANGE);
                            assert range != null;
                            Long minExpT = range.getLower();
                            Long maxExpT = range.getUpper();
                            mPreviewRequestBuilder.set(SENSOR_EXPOSURE_TIME, ((minExpT + maxExpT) / 128));
                            mPreviewRequestBuilder.set(CONTROL_AF_MODE, CONTROL_AF_MODE_OFF);
                            mPreviewRequestBuilder.set(CONTROL_AE_MODE, CONTROL_AE_MODE_OFF);
                            mPreviewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, Util.getJpegOrientation(mCharacteristics, getActivity().getWindowManager().getDefaultDisplay().getRotation()));
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
                mQuaternion = Util.getQuadFromGyro(event.values,lastTimeStamp,event.timestamp,mQuaternion,true,false,true,true);
                mCameraQuaternion = Util.getQuadFromGyro(event.values,lastTimeStamp,event.timestamp,mCameraQuaternion,false,true,false,true);
                lastTimeStamp = event.timestamp;
                float[] rotMat = new float[16];
                SensorManager.getRotationMatrixFromVector(rotMat,mQuaternion);
                mGLRenderer.setRotationMatrix(rotMat);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }
    private class ImageStitchingTask extends AsyncTask<Mat, Integer, Mat> {
        protected Mat doInBackground(Mat... mat) {
            mAsyncRunning = true;
            ImageStitchingNative is = new ImageStitchingNative();
            Mat ret = is.addToPano(mat[0], mat[1]);
            mNumPicture++;
            return ret;
        }

        protected void onProgressUpdate(Integer... progress) {

        }

        protected void onPostExecute(Mat result) {

            mAsyncRunning = false;
            Log.i("mNumPicture",mNumPicture+"");
            if(mNumPicture < 3)
                return;
            Bitmap bitmap = Bitmap.createBitmap(result.cols(), result.rows(), Bitmap.Config.ARGB_8888);
            Mat test = new Mat(result.height(),result.width(),CvType.CV_8UC3);
            Imgproc.cvtColor(result, test, Imgproc.COLOR_BGR2RGBA);
            Utils.matToBitmap(test, bitmap);
            Log.d("Post","Finished, Size :"+result.size().width+","+result.size().height);
            mGLRenderer.getSphere().updateBitmap(bitmap);
        }
    }
}