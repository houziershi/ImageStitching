package com.kunato.imagestitching;

/**
 * Created by kunato on 2/29/16.
 */
public class Factory {
    static GLRenderer mGlRenderer;
    static ImageStitchingNative mImageStitchingNative;

    public static GLRenderer getGlRenderer(MainController mainController) {
        if(mGlRenderer == null)
            mGlRenderer = new GLRenderer(mainController);
        return mGlRenderer;
    }
    public static ImageStitchingNative getStitchingNative(){
        if(mImageStitchingNative == null){
            mImageStitchingNative = ImageStitchingNative.getNativeInstance();
        }
        return mImageStitchingNative;
    }
}
