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

/**
 * Created by kunato on 12/21/15 AD.
 */
public class ImageStitchingNative {
    private static ImageStitchingNative instance = null;
    public static int mPictureSize = 0;
    private Context context;

    private ImageStitchingNative(){

    }
    //TODO implement (input image, rot) then output the homography matrix
    public native void nativeImageToMat(ByteBuffer buf1,ByteBuffer buf2,ByteBuffer buf3,int width, int height, int bitPerPixel, int[] rowStride,int[] pixelStride, long retMatAddr);
    public native void nativeFeatureTracking(long imgAddr,long rotAddr,long retMatAddr);
    public native void nativeStitch(long retAddr);
    public native void nativeAddStitch(long imgAddr,long rotAddr);
    public Mat addToPano(Mat imageMat, Mat rotMat){
        mPictureSize++;
        Log.d("Input Size",imageMat.size().width+"*"+imageMat.size().height);
        Mat ret = new Mat();

        Log.d("Rotin",rotMat.dump());
        nativeAddStitch(imageMat.getNativeObjAddr(), rotMat.getNativeObjAddr());
        nativeStitch(ret.getNativeObjAddr());

        Highgui.imwrite("/sdcard/stitch/resultjava"+mPictureSize+".jpg",ret);
        return ret;
    }
    public static ImageStitchingNative getNativeInstance(){
        if(instance == null){
            instance = new ImageStitchingNative();
        }
        return instance;
    }
    public void setContext(Context context){

        this.context = context;
    }
//    public void testRenderScript(){
//        RenderScript rs = RenderScript.create(context);
//        ScriptC script = new
//    }

    static {
        System.loadLibrary("nonfree_stitching");
    }
}
