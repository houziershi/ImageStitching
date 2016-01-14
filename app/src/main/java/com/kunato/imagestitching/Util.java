package com.kunato.imagestitching;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;
import android.util.Size;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Comparator;

import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.StrictMath.cos;

/**
 * Created by kunato on 12/14/15 AD.
 */
public class Util {
    /**
     * Compares two {@code Size}s based on their areas.
     */
    public static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
    private static final float NS2S = 1.0f / 1000000000.0f;
    public static float[] getRotationFromGyro(double timedelta,float[] mRotVec,float[] mRotationMatrix){
        // This timestep's delta rotation to be multiplied by the current rotation
        // after computing it from the gyro sample data.
        float[] deltaRotationVector = new float[4];
        if (timedelta != 0) {
            final double dT = timedelta * NS2S;
            // Axis of the rotation sample, not normalized yet.
            float axisX = mRotVec[0];
            float axisY = mRotVec[1];
            float axisZ = mRotVec[2];

            // Calculate the angular speed of the sample

            float omegaMagnitude = (float) sqrt(axisX*axisX + axisY*axisY + axisZ*axisZ);

            // Normalize the rotation vector if it's big enough to get the axis
            // (that is, EPSILON should represent your maximum allowable margin of error)
            if (omegaMagnitude > 5555.0) {
                axisX /= omegaMagnitude;
                axisY /= omegaMagnitude;
                axisZ /= omegaMagnitude;
            }

            // Integrate around this axis with the angular speed by the timestep
            // in order to get a delta rotation from this sample over the timestep
            // We will convert this axis-angle representation of the delta rotation
            // into a quaternion before turning it into the rotation matrix.

            double thetaOverTwo = omegaMagnitude * dT / 2.0f;
//                Log.d("thetaOverTwo",""+thetaOverTwo);
            float sinThetaOverTwo = (float) sin(thetaOverTwo);
            float cosThetaOverTwo = (float) cos(thetaOverTwo);
            deltaRotationVector[0] = sinThetaOverTwo * axisX;
            deltaRotationVector[1] = sinThetaOverTwo * axisY;
            deltaRotationVector[2] = sinThetaOverTwo * axisZ;
            deltaRotationVector[3] = cosThetaOverTwo;
//                Log.d("RotaionVector","["+deltaRotationVector[0]+","+deltaRotationVector[1]+","+deltaRotationVector[2]+","+deltaRotationVector[3]+"]");
        }
        float[] deltaRotationMatrix = new float[16];
        SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, deltaRotationVector);
        // User code should concatenate the delta rotation we computed with the current rotation
        // in order to get the updated rotation.
        // rotationCurrent = rotationCurrent * deltaRotationMatrix;

        return Util.naivMatrixMultiply(mRotationMatrix,deltaRotationMatrix);
    }
    /**
     * Performs naiv n^3 matrix multiplication and returns C = A * B
     *
     * @param A Matrix in the array form (e.g. 3x3 => 9 values)
     * @param B Matrix in the array form (e.g. 3x3 => 9 values)
     * @return A * B
     */
    public static float[] naivMatrixMultiply(float[] B, float[] A) {
        int mA, nA, mB, nB;
        mA = nA = (int) Math.sqrt(A.length);
        mB = nB = (int) Math.sqrt(B.length);
        if (nA != mB)
            throw new RuntimeException("Illegal matrix dimensions.");

        float[] C = new float[mA * nB];

        for (int i = 0; i < mA; i++)
            for (int j = 0; j < nB; j++)
                for (int k = 0; k < nA; k++)
                    C[i + nA * j] += (A[i + nA * k] * B[k + nB * j]);
        return C;
    }
    public static int getJpegOrientation(CameraCharacteristics c, int deviceOrientation) {
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
//    public static void printMatrix(opencv_core.Mat print,String code){
//        Indexer indexer = print.createIndexer();
//
//        Log.d("camera matrix"+code, "[" + indexer.getDouble(0, 0) + "," + indexer.getDouble(0, 1) + "," + indexer.getDouble(0, 2) + "]");
//        Log.d("camera matrix"+code,"["+indexer.getDouble(1,0)+","+indexer.getDouble(1,1)+","+indexer.getDouble(1,2)+"]");
//        Log.d("camera matrix"+code,"["+indexer.getDouble(2,0)+","+indexer.getDouble(2,1)+","+indexer.getDouble(2,2)+"]");
//        Log.d("camera matrix"+code,"######################################");
//    }
    public static void writeBitMap(Bitmap bmp){
        FileOutputStream out = null;
        try {
            out = new FileOutputStream("/sdcard/test.png");
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
            // PNG is a lossless format, the compression factor (100) is ignored
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
