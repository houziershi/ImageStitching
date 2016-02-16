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


    //TODO implement (input image, rot) then output the homography matrix
    public native void nativeHomography(long imgAddr,long rotAddr,long retMatAddr);
    public native void nativeStitch(long retAddr);
    public native void nativeAddStitch(long imgAddr,long rotAddr);
    public Mat addToPano(Mat imageMat, Mat rotMat){
        mPictureSize++;
        Log.d("Input Size",imageMat.size().width+"*"+imageMat.size().height);
        Mat ret = new Mat();

        Log.d("Rotin",rotMat.dump());
        nativeAddStitch(imageMat.getNativeObjAddr(), rotMat.getNativeObjAddr());
        nativeStitch(ret.getNativeObjAddr());

//        Highgui.imwrite("/sdcard/stitch/resultjava"+mPictureSize+".jpg",ret);
        return ret;
    }
    public static ImageStitchingNative getNativeInstance(){
        if(instance == null){
            instance = new ImageStitchingNative();
        }
        return instance;
    }
    public void tracking(Mat input,Mat rot){
        Mat ret = new Mat();
        Log.d("Tracking","rotIn"+ rot.toString());
        nativeHomography(input.getNativeObjAddr(), rot.getNativeObjAddr(), ret.getNativeObjAddr());
        Mat one = new Mat(1,1,CvType.CV_32F);
        float[] f = {1f};
        one.put(0,0,f);
        ret.push_back(one);
        Log.d("Homography mat", "Ret Homography" + ret.toString());
        for(int i = 0; i < ret.rows() ;i+=3){
            Log.d("Homography mat",String.format("%f %f %f",ret.get(i,0)[0],ret.get(i+1,0)[0],ret.get(i+2,0)[0]));
        }
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
