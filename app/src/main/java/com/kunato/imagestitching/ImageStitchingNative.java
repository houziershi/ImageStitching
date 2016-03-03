package com.kunato.imagestitching;
import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.RenderScript;
import android.renderscript.ScriptC;
import android.util.Log;

import org.opencv.*;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    public native void nativeStitch(long retAddr,long areaAddr);
    public native void nativeAddStitch(long imgAddr,long rotAddr);
    public void addToPano(Mat imageMat, Mat rotMat){
        mPictureSize++;
        Log.d("Input Size",imageMat.size().width+"*"+imageMat.size().height);
        Mat ret = new Mat();
        Mat area = new Mat(1,4,CvType.CV_32F);
        Log.d("Rotin", rotMat.dump());
        nativeAddStitch(imageMat.getNativeObjAddr(), rotMat.getNativeObjAddr());
        nativeStitch(ret.getNativeObjAddr(), area.getNativeObjAddr());
        float[] areaFloat = new float[4];
        area.get(0,0,areaFloat);
        Log.d("Rect Java", Arrays.toString(areaFloat));
        Highgui.imwrite("/sdcard/stitch/resultjava" + mPictureSize + ".jpg", ret);
        if(ret.empty()) {
            return;
        }
        Log.d("Pano type",ret.toString());
        Bitmap bitmap = Bitmap.createBitmap(ret.cols(), ret.rows(), Bitmap.Config.ARGB_8888);
//        Mat test = new Mat(ret.height(),ret.width(),CvType.CV_8UC4);
//        Imgproc.cvtColor(ret, test, Imgproc.COLOR_BGR2RGBA);
        Utils.matToBitmap(ret, bitmap);
        //create a file to write bitmap data
        File f = new File("/sdcard/stitch/", "test.jpg");
        try {
            f.createNewFile();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 0 /*ignored for PNG*/, bos);
            byte[] bitmapdata = bos.toByteArray();
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(bitmapdata);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("Post", "Finished, Size :" + ret.size().width + "," + ret.size().height);

        Factory.getFactory(null).getGlRenderer().getSphere().updateBitmap(bitmap,areaFloat);
//        mGLRenderer.getSphere().updateBitmap(bitmap);
        Factory.getFactory(null).getRSProcessor(null,null).requestHomography();;
//        mProcessor.requestHomography();
        Factory.getFactory(null).getGlRenderer().captureScreen();
//        mGLRenderer.captureScreen();
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
        GLRenderer glRenderer = Factory.getFactory(null).getGlRenderer();
        glRenderer.setHomography(data);

    }

    public void setContext(Context context){

        this.context = context;
    }

    static {
        System.loadLibrary("nonfree_stitching");
    }
}
