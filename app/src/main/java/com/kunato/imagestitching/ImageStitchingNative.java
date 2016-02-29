package com.kunato.imagestitching;
import android.content.Context;
import android.renderscript.RenderScript;
import android.renderscript.ScriptC;
import android.util.Log;

import org.opencv.*;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.*;
import org.opencv.highgui.Highgui;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by kunato on 12/21/15 AD.
 */
public class ImageStitchingNative {
    private static ImageStitchingNative instance = null;
    public static int mPictureSize = 0;
    private Context context;

    private ImageStitchingNative(){

    }
    public native void nativeHomography(long imgAddr,long glRotAddr,long glProjAddr,long retMatAddr);
    public native void nativeStitch(long retAddr);
    public native void nativeAddStitch(long imgAddr,long rotAddr);
    public Mat addToPano(Mat imageMat, Mat rotMat){
        mPictureSize++;
        Log.d("Input Size",imageMat.size().width+"*"+imageMat.size().height);
        Mat ret = new Mat();

        Log.d("Rotin",rotMat.dump());
        nativeAddStitch(imageMat.getNativeObjAddr(), rotMat.getNativeObjAddr());
        nativeStitch(ret.getNativeObjAddr());

        Highgui.imwrite("/sdcard/stitch/resultjava" + mPictureSize + ".jpg", ret);
        return ret;
    }
    public static ImageStitchingNative getNativeInstance(){
        if(instance == null){
            instance = new ImageStitchingNative();
        }
        return instance;
    }
    public void tracking(Mat input,float[] glRot,float[] glProj){
        Mat glRotMat = new Mat(4,4,CvType.CV_32F);
        glRotMat.put(0, 0, glRot);
        Mat glProjMat = new Mat(4,4,CvType.CV_32F);
        glProjMat.put(0, 0, glProj);

        Mat ret = new Mat();
        nativeHomography(input.getNativeObjAddr(), glRotMat.getNativeObjAddr(), glProjMat.getNativeObjAddr(), ret.getNativeObjAddr());
        Log.d("Homography mat", "Ret Homography" + ret.toString());
        float[] data = new float[9];
        ret.get(0,0,data);
        data[2]/=1080f;
        data[5]/=1920f;

        for(int i = 0; i < 3 ;i++){
            Log.d("HomoMat",String.format("[%f %f %f]",data[i*3],data[i*3+1],data[i*3+2]));
        }
        GLRenderer glRenderer = Factory.getGlRenderer(null);
        glRenderer.setHomography(data);

    }

    public void setContext(Context context){

        this.context = context;
    }

    static {
        System.loadLibrary("nonfree_stitching");
    }
}
