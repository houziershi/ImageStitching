package com.kunato.imagestitching;
import android.util.Log;

import org.opencv.*;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.*;
import org.opencv.highgui.Highgui;

/**
 * Created by kunato on 12/21/15 AD.
 */
public class ImageStitchingNative {
    public static int mPictureSize = 0;

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

    static {
        System.loadLibrary("nonfree_stitching");
    }
}
