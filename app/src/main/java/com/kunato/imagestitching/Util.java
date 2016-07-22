package com.kunato.imagestitching;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;
import android.util.Size;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;

import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.StrictMath.cos;
import static java.lang.System.currentTimeMillis;

/**
 * Created by kunato on 12/14/15 AD.
 */
public class Util {
    public static float[] ROTATE_X = { 1,0,0,0,
            0,0,-1,0,
            0,1,0,0,
            0,0,0,1
    };
    public static float[] ROTATE_Y_270 = { 0,0,-1,0,
            0,1,0,0,
            1,0,0,0,
            0,0,0,1

    };
    public static float[] ROTATE_Z = {
            0,-1,0,0,
            1,0,0,0,
            0,0,1,0,
            0,0,0,1
    };
    public static float[] SWAP_X = {-1,0,0,0,
            0,1,0,0,
            0,0,1,0,
            0,0,0,1
    };
    public static float[] SWAP_Y = {1,0,0,0,
            0,-1,0,0,
            0,0,1,0,
            0,0,0,1
    };
    public static float[] SWAP_Z = {1,0,0,0,
            0,1,0,0,
            0,0,-1,0,
            0,0,0,1
    };
    public static float[] MAGIC_MAT = {
            -1,0,0,0,
            0,0,-1,0,
            0,1,0,0,
            0,0,0,1

    };
    public static float[] UP180 = {1.f ,         0.f  ,        0.f
            , 0.f     ,   0.90128334f , 0.43323012f
            , 0.f     ,   -0.43323012f, 0.9012833f};
    public static final float NS2S = 1.0f / 1000000000.0f;

    public static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
    //[ 0  1  2 {0}]
    //[ 3  4  5 {0}]
    //[ 6  7  8 {0}]
    //[{0}{0}{0}{1} ]
    public static float[] matrix9to16(float[] matrix){
        float[] out = {matrix[0],matrix[1],matrix[2],0,matrix[3],matrix[4],matrix[5],0,matrix[6],matrix[7],matrix[8],0,0,0,0,1};
        Log.d("Util","Input  : "+Arrays.toString(matrix));
        Log.d("Util","Output :"+Arrays.toString(out));
        return out;
    }

    public static float[] lowPass(float[] input, float[] output){
        if(output == null) return input;
        for(int i = 0 ; i < input.length ; i++){
            output[i] = output[i] + 0.015f * (input[i] - output[i]);
        }
        return output;
    }

    public static float[] matrixToQuad(float[] matrix){
        float[] quad = new float[4];

        //[0 1 2 3]
        //[4 5 6 7]
        //[8 9 10 11]
        //[12 13 14 15]
        //qw= √(1 + m00 + m11 + m22) /2
        quad[3] = (float)Math.sqrt(1f+matrix[0]+matrix[5]+matrix[10])/2.0f;
//        qx = (m21 - m12)/( 4 *qw)
//        qy = (m02 - m20)/( 4 *qw)
//        qz = (m10 - m01)/( 4 *qw)
        quad[0] = (matrix[9]-matrix[6])/(4*quad[3]);
        quad[1] = (matrix[2]-matrix[8])/(4*quad[3]);
        quad[2] = -((matrix[4]-matrix[1])/(4*quad[3]));
        return quad;
    }


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

    public static float[] getRotationFromGyro(float[] values,float timestamp,float nowTimeStamp,float[] currentRotMatrix,boolean swapX,boolean swapY,boolean swapZ){
        float[] deltaRotationVector = new float[4];
        if (timestamp != 0) {
            final float dT = (nowTimeStamp - timestamp) * NS2S;
            float axisX = swapX? -values[0]: values[0];
            float axisY = swapY? -values[1]: values[1];
            float axisZ = swapZ? -values[2]: values[2];

            float omegaMagnitude = (float) Math.sqrt(axisX*axisX + axisY*axisY + axisZ*axisZ);
            if (omegaMagnitude > 0.1f) {
                axisX /= omegaMagnitude;
                axisY /= omegaMagnitude;
                axisZ /= omegaMagnitude;
            }

            float thetaOverTwo = omegaMagnitude * dT / 2.0f;
            float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
            float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);
            deltaRotationVector[0] = sinThetaOverTwo * axisX;
            deltaRotationVector[1] = sinThetaOverTwo * axisY;
            deltaRotationVector[2] = sinThetaOverTwo * axisZ;
            deltaRotationVector[3] = cosThetaOverTwo;
        }
        float[] deltaRotationMatrix = new float[16];
        SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, deltaRotationVector);
        return naivMatrixMultiply(currentRotMatrix, deltaRotationMatrix);
    }

    public static float[] getQuadFromGyro(float[] values,float timestamp,float nowTimeStamp,float[] mCurrentRot,boolean swapX,boolean swapY,boolean swapZ,boolean usingZ){
        float[] deltaRotationVector = new float[4];
        if (timestamp != 0) {
            final float dT = (nowTimeStamp - timestamp) * NS2S;
            float axisX = swapX? -values[0]: values[0];
            float axisY = swapY? -values[1]: values[1];
            float axisZ = swapZ? -values[2]: values[2];
//            float axisX = 0;
//            float axisZ = 0;
            if(!usingZ){
                axisZ = 0;
            }
            float omegaMagnitude = (float) sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
            if (omegaMagnitude > 0.00001f) {
                axisX /= omegaMagnitude;
                axisY /= omegaMagnitude;
                axisZ /= omegaMagnitude;
            }
            double thetaOverTwo = omegaMagnitude * dT / 2.0f;
            float sinThetaOverTwo = (float) sin(thetaOverTwo);
            float cosThetaOverTwo = (float) cos(thetaOverTwo);
            deltaRotationVector[0] = sinThetaOverTwo * axisX;
            deltaRotationVector[1] = sinThetaOverTwo * axisY;
            deltaRotationVector[2] = sinThetaOverTwo * axisZ;
            deltaRotationVector[3] = cosThetaOverTwo;
//            Log.d("rot", Arrays.toString(mCurrentRot));
            return multiplyByQuat(deltaRotationVector,mCurrentRot);
        }

        return mCurrentRot;
    }
    //TODO improve this, so slow
    public static Mat imageToMat(Image image) {
        long step1 = currentTimeMillis ();
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int width = image.getWidth();
        int height = image.getHeight();
        int[] rowStrides = new int[3];
        int[] pixelStrides = new int[3];

        int offset = 0;
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer[] buffers = new ByteBuffer[3];
        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        Log.d("buffer",String.format("data size %d",data.length));
        Log.d("buffer",String.format("bitformat %d",ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)));
        byte[] rowData = new byte[planes[0].getRowStride()];
        for(int i = 0; i < planes.length; i++){
            buffers[i] = planes[i].getBuffer();
            rowStrides[i] = planes[i].getRowStride();
            pixelStrides[i] = planes[i].getPixelStride();
            Log.d("buffer",String.format("Stride %d : %d %d ",i,rowStrides[i],pixelStrides[i]));
        }
        Mat test = new Mat();
        //move this code to native
        long step1_5 = currentTimeMillis();
        for (int i = 0; i < planes.length; i++) {
            int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int w = (i == 0) ? width : width / 2;
            int h = (i == 0) ? height : height / 2;
            for (int row = 0; row < h; row++) {
                //Y
                if (pixelStride == 1) {
                    int length = w * bytesPerPixel;
                    buffer.get(data, offset, length);;

                    Log.w("buffer", String.format("h %d row %d", h, row));
                    if (h - row != 1) {

                        buffer.position(buffer.position() + rowStride - length);
                        Log.w("buffer",String.format("position %d",buffer.position()));
                    }

                    offset += length;
                    Log.w("buffer",String.format("offset %d",offset));
                }//UV
                else {
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
        long step2 = currentTimeMillis();
        // Finally, create the Mat.
        Mat yuvMat = new Mat(height + height / 2, width, CvType.CV_8UC1);
        yuvMat.put(0, 0, data);
        Mat imageMat = new Mat(image.getHeight(),image.getWidth(),CvType.CV_8UC3);
        Imgproc.cvtColor(yuvMat, imageMat, Imgproc.COLOR_YUV420p2RGB);
        long step3 = currentTimeMillis();
        Log.i("Timer","imageToMat : ("+(step1_5-step1)*NS2S+","+((step2-step1_5)*NS2S)+","+((step3-step2)*NS2S)+")");
        image.close();
        return imageMat;
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

    public static int loadShader ( String vss, String fss ) {
        int vshader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vshader, vss);
        GLES20.glCompileShader(vshader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Shader", "Could not compile vshader");
            Log.v("Shader", "Could not compile vshader:"+GLES20.glGetShaderInfoLog(vshader));
            GLES20.glDeleteShader(vshader);
            vshader = 0;
        }

        int fshader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fshader, fss);
        GLES20.glCompileShader(fshader);
        GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Shader", "Could not compile fshader");
            Log.v("Shader", "Could not compile fshader:"+GLES20.glGetShaderInfoLog(fshader));
            GLES20.glDeleteShader(fshader);
            fshader = 0;
        }

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vshader);
        GLES20.glAttachShader(program, fshader);
        GLES20.glLinkProgram(program);

        return program;
    }

    public static float[] multiplyByQuat(float[] input1,float[] input2) {
        float[] output = new float[4];
        output[3] = (input1[3] * input2[3] - input1[0] * input2[0] - input1[1] * input2[1] - input1[2]
                    * input2[2]); //w = w1w2 - x1x2 - y1y2 - z1z2
        output[0] = (input1[3] * input2[0] + input1[0] * input2[3] + input1[1] * input2[2] - input1[2]
                    * input2[1]); //x = w1x2 + x1w2 + y1z2 - z1y2
        output[1] = (input1[3] * input2[1] + input1[1] * input2[3] + input1[2] * input2[0] - input1[0]
                    * input2[2]); //y = w1y2 + y1w2 + z1x2 - x1z2
        output[2] = (input1[3] * input2[2] + input1[2] * input2[3] + input1[0] * input2[1] - input1[1]
                    * input2[0]); //z = w1z2 + z1w2 + x1y2 - y1x2
        return output;
    }

}
