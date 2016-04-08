package com.kunato.imagestitching;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.opengl.Matrix;
import android.util.Log;

import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

// Renderer
public class GLRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private long mStartTime;
    private int mFrame;
    private SurfaceTexture mTextureNormal;
    private SurfaceTexture mTextureProcessed;
    private boolean mUpdateST = false;
    protected MainController mView;
    private final float[] mMVPMatrix = new float[16];
    public final float[] mProjectionMatrix = new float[16];
    private final float[] mViewCanvasMatrix = new float[16];
    private final float SCREEN_RATIO = 0.6239168f;
    private final float ZOOM_RATIO = 1f;
    private final float CANVAS_SIZE = 1f * ZOOM_RATIO;
    private final float HEIGHT_WIDTH_RATIO = 1f;
    public float[] mRotationMatrix = {1f,0,0,0
            ,0,1f,0,0
            ,0,0,1f,0
            ,0,0,0,1f};
    private float[] mSpericalModelMatrix = {1f,0,0,0
            ,0,1f,0,0
            ,0,0,1f,0
            ,0,0,0,1f};
    private float[] mWorldModelMatrix = {1f,0,0,0
            ,0,1f,0,0
            ,0,0,1f,0
            ,0,0,0,1f};

    private float[] mModelViewMatrix = new float[16];
    private Canvas mCanvas;
    private Sphere mSphere;
    private Canvas mCanvasProcessed;
    public int mWidth;
    public int mHeight;
    public float[] mHomography = {1,0,0,0,1,0,0,0,1};
    private boolean readInProgress = false;
    GLRenderer(MainController view) {
        mView = view;
    }
    public void captureScreen(){
        mSphere.readPixel = true;
        readInProgress = true;
    }
    public void onSurfaceCreated ( GL10 unused, EGLConfig config ) {
        mStartTime = System.nanoTime();

        float[] vertices = {-CANVAS_SIZE, -CANVAS_SIZE * HEIGHT_WIDTH_RATIO, -1.0f,
                CANVAS_SIZE, -CANVAS_SIZE * HEIGHT_WIDTH_RATIO, -1.0f,
                -CANVAS_SIZE, CANVAS_SIZE * HEIGHT_WIDTH_RATIO, -1.0f,
                CANVAS_SIZE, CANVAS_SIZE * HEIGHT_WIDTH_RATIO, -1.0f};
        float[] textures = {0.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 0.0f,
                1.0f, 1.0f};
        mCanvas = new Canvas(vertices,textures, mView.getActivity());
        mCanvasProcessed = new Canvas(vertices,textures,mView.getActivity());
        mSphere = new Sphere(this);
        mTextureNormal = new SurfaceTexture (mCanvas.getTexturePos()[0]);
        mTextureProcessed = new SurfaceTexture(mCanvasProcessed.getTexturePos()[0]);
        mTextureNormal.setOnFrameAvailableListener(this);
        mTextureProcessed.setOnFrameAvailableListener(this);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        //(x (vertical),(horizontal)y,z)
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    }
    //Core function
    public void onDrawFrame ( GL10 unused ) {
        mFrame++;
        if(System.nanoTime() - mStartTime >= 1000000000){
            Log.v("FPS","fps : "+mFrame);
            mFrame = 0;
            mStartTime = System.nanoTime();
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);

        Matrix.multiplyMM(mModelViewMatrix,0, mRotationMatrix,0, mSpericalModelMatrix,0);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix,0, mModelViewMatrix,0);


//        float[] out = new float[3];
//        int[] viewport = {0,0,mWidth,mHeight};
//        GLU.gluProject(123.4349f, 0, -169.89357f, mRotationMatrix, 0, mProjectionMatrix, 0, viewport,0,out,0);
//        Log.d("gluProject", "" + Arrays.toString(out));
        //draws
        synchronized(this) {
            if ( mUpdateST ) {
                //choose whice texture to update
                //maybe no need for mTextureNormal
                mTextureProcessed.updateTexImage();
                mUpdateST = false;
            }
        }

        //multiply MM(retMat, retMatOffset, mat1 * mat2 (includeOffset))
//        mCanvas.draw(mMVPMatrix);
        if(readInProgress){
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            mSphere.draw(mMVPMatrix);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);
            mSphere.readPixel = false;
            readInProgress = false;
        }

        mCanvasProcessed.draw(mViewCanvasMatrix,mHomography);
        mSphere.draw(mMVPMatrix);
        GLES20.glFlush();
    }

    public void onSurfaceChanged ( GL10 unused, int width, int height ) {
        mWidth = width;
        mHeight = height;

        GLES20.glViewport(0, 0, mWidth, mHeight);
        Log.v("GL", "Screen" + String.format("(Width:Height)[%d,%d]", mWidth,mHeight));
//        float ratio =( float ) width / height;
        float ratio = 9f/16f; //always because camera input as 3/4
        //48=zoom1.5//72=zoom1
//        Log.d("Ratio", ratio + "");
        //52 for height
        //65 default
        Matrix.perspectiveM(mProjectionMatrix, 0, 67 / ZOOM_RATIO, ratio, 0.1f, 1000f);//48 for 4/3 64 for 1920/1080
//        Log.d("PerspectiveM",Arrays.toString(mProjectionMatrix));
//        Matrix.orthoM(mProjectionMatrix,0,-100,100,-100,100,-0.1f,100f);


    }
    public void setHomography(float[] input){
        mHomography = input;
        Log.v("GL","SetHomography" + Arrays.toString(input));
    }
    public synchronized void onFrameAvailable ( SurfaceTexture st ) {
        mUpdateST = true;
        mView.requestRender();
    }

    public void close() {
        mUpdateST = false;
        mTextureNormal.release();
        mCanvas.deleteTex();
    }
    //TODO change to quat?
    public void setRotationMatrix(float[] rot){
//        Log.i("RotationMat", Arrays.toString(rot));
        mRotationMatrix = rot;
    }
    public Sphere getSphere(){
        return mSphere;
    }
    public Canvas getCanvas() { return mCanvas; }
    public SurfaceTexture getSurfaceTexture(){
        return mTextureNormal;
    }
    public SurfaceTexture getProcessSurfaceTexture() {
        return mTextureProcessed;
    }

}