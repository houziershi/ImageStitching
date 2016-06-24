package com.kunato.imagestitching;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Created by kunato on 12/21/15 AD.
 */
public class ImageStitchingNative {
    private static ImageStitchingNative instance = null;
    private Context context;

    private ImageStitchingNative(){
    }

    public native int nativeKeyFrameSelection(float[] rotMat);
    public native void nativeAligning(long imgAddr,long glRotAddr,long glProjAddr,long retMatAddr);
    public native int nativeStitch(long retAddr,long areaAddr,long rotAddr);
    public native void nativeAddStitch(long imgAddr,long rotAddr);
    public int keyFrameSelection(float[] rotMat) {
        return nativeKeyFrameSelection(rotMat);
    }
    public int addToPano(Mat imageMat, Mat rotMat,int mPictureSize){
        Highgui.imwrite("/sdcard/stitch/input"+mPictureSize+".jpg",imageMat);
        Log.d("JAVA Stitch", "Image Input Size : "+imageMat.size().width + "*" + imageMat.size().height);
        Mat ret = new Mat();
        Mat area = new Mat(1,4,CvType.CV_32F);
        Mat rot = new Mat(3,3,CvType.CV_32F);
        Log.d("JAVA Stitch", "Image Rotation Input : "+rotMat.dump());
        nativeAddStitch(imageMat.getNativeObjAddr(), rotMat.getNativeObjAddr());
        int rtCode = nativeStitch(ret.getNativeObjAddr(), area.getNativeObjAddr(),rot.getNativeObjAddr());
        Log.d("JAVA Stitch", "JNI Return Code : "+rtCode + "");
        float[] areaFloat = new float[4];
        area.get(0, 0, areaFloat);
        Log.d("JAVA Stitch", "Return Area [" + Arrays.toString(areaFloat)+"]");
        if(rtCode != 1) {
            return rtCode;
        }
        Bitmap bitmap = Bitmap.createBitmap(ret.cols(), ret.rows(), Bitmap.Config.ARGB_8888);
        Mat test = new Mat(ret.height(),ret.width(),CvType.CV_8UC3);
        Imgproc.cvtColor(ret, test, Imgproc.COLOR_BGRA2RGB);
        Highgui.imwrite("/sdcard/stitch/pano"+mPictureSize+".jpg",test);

        Utils.matToBitmap(ret, bitmap);
        Log.d("JAVA Stitch", "Add Panorama Finished, Size :" + ret.size().width + "," + ret.size().height);

        Factory.getFactory(null).getGlRenderer().getSphere().updateBitmap(bitmap, areaFloat);
//        mGLRenderer.getSphere().updateBitmap(bitmap);
        Factory.getFactory(null).getRSProcessor(null, null).requestAligning();;
//        mProcessor.requestAligning();
        Factory.getFactory(null).getGlRenderer().captureScreen();
//        mGLRenderer.captureScreen();
        return rtCode;
    }

    public void aligning(Mat input, float[] glRot, float[] glProj){
        Factory.mainController.startRecordQuaternion();
        long cStart = System.nanoTime();
        Mat glRotMat = new Mat(4,4,CvType.CV_32F);
        glRotMat.put(0, 0, glRot);
        Mat glProjMat = new Mat(4,4,CvType.CV_32F);
        glProjMat.put(0, 0, glProj);
        Log.d("JAVA Stitch","Input Rotation");
        for (int i = 0; i < 4; i++) {

            Log.d("JAVA Stitch", String.format("[%f %f %f %f]", glRot[i * 4], glRot[i * 4 + 1], glRot[i * 4 + 2], glRot[i*4 +3]));
        }
        Mat ret = new Mat(4,4,CvType.CV_32F);
        long cBeforeNative = System.nanoTime();

        Factory.mainController.startRecordQuaternion();
        nativeAligning(input.getNativeObjAddr(), glRotMat.getNativeObjAddr(), glProjMat.getNativeObjAddr(), ret.getNativeObjAddr());
        long cEnd = System.nanoTime();
        Log.d("JAVA Stitch", "Time Used: "+((cEnd-cBeforeNative)*Util.NS2S)+","+(cBeforeNative-cStart)*Util.NS2S+" Return Mat" + ret.toString());
        //using return as homo
        float[] data;
        if(false) {
            data = new float[9];
            ret.get(0, 0, data);
            data[2] /= 1080f;
            data[5] /= 1920f;

            Log.d("JAVA Stitch","HomographyMat");
            for (int i = 0; i < 3; i++) {

                Log.d("JAVA Stitch", String.format("[%f %f %f]", data[i * 3], data[i * 3 + 1], data[i * 3 + 2]));
            }
        }
        else{
            data = new float[]{1,0,0,0,1,0,0,0,1};
            float[] rotmat = new float[16];
            ret.get(0, 0, rotmat);
            float[] quad = Util.matrixToQuad(rotmat);
            for (int i = 0; i < 4; i++) {
               Log.d("JAVA Stitch", String.format("[%f %f %f %f]", rotmat[i * 4], rotmat[i * 4 + 1], rotmat[i * 4 + 2], rotmat[i*4 +3]));
            }
            Log.d("JAVA Stitch",Arrays.toString(Factory.mainController.mQuaternion));
//            float[] correctedQuat = {quad[0],-quad[1], quad[2], quad[3]};
            Factory.mainController.updateQuaternion(quad,Factory.mainController.mDeltaQuaternion);
//            Factory.mainController.mQuaternion = quad;
//            Log.d("quad+",Arrays.toString(Factory.mainController.mQuaternion));
//            Factory.mainController.mRotmat = rotmat;
        }

        GLRenderer glRenderer = Factory.getFactory(null).getGlRenderer();
        glRenderer.setHomography(data);

    }

    public void setContext(Context context){

        this.context = context;
    }

    public static ImageStitchingNative getNativeInstance(){
        if(instance == null){
            instance = new ImageStitchingNative();
        }
        return instance;
    }
    static {
        System.loadLibrary("nonfree_stitching");
    }
}
