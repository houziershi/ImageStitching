package com.kunato.imagestitching;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

// Renderer
public class GLRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private long mStartTime;
    private int mFrame;
    private SurfaceTexture mSTexture;
    private boolean mUpdateST = false;
    private CameraSurfaceView mView;
    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float SCREEN_RATIO = 0.6239168f;
    private final float ZOOM_RATIO = 1.5f;
    private final float CANVAS_SIZE = 1f * ZOOM_RATIO;
    private final float HEIGHT_WIDTH_RATIO = 1f;
    private float[] mRotationMatrix = {1f,0,0,0
            ,0,1f,0,0
            ,0,0,1f,0
            ,0,0,0,1f};
    private Canvas mCanvas;
    private Sphere mSphere;

    GLRenderer(CameraSurfaceView view) {
        mView = view;
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
        mSphere = new Sphere(mView.getActivity());
        mSTexture = new SurfaceTexture (mCanvas.getTexturePos()[0]);
        mSTexture.setOnFrameAvailableListener(this);
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        //(x (vertical),(horizontal)y,z)
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        Matrix.setLookAtM(mViewMatrix, 0,
                0.0f, 0.0f, -0.2f,
                0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f);
    }
    //Core function
    public void onDrawFrame ( GL10 unused ) {
        mFrame++;
        if(System.nanoTime() - mStartTime >= 1000000000){
            Log.i("FPS","fps : "+mFrame);
            mFrame = 0;
            mStartTime = System.nanoTime();
        }
        float[] sphereMat = new float[16];
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);


        //draws
        synchronized(this) {
            if ( mUpdateST ) {
                mSTexture.updateTexImage();
                mUpdateST = false;
            }
        }

        //multiply MM(retMat, retMatOffset, mat1 * mat2 (includeOffset))
        mCanvas.draw(mMVPMatrix);

        Matrix.multiplyMM(sphereMat, 0, mMVPMatrix, 0, mRotationMatrix, 0);
        mSphere.draw(sphereMat);
        GLES20.glFlush();
    }

    public void onSurfaceChanged ( GL10 unused, int width, int height ) {
        GLES20.glViewport(0, 0, width, height);
//        float ratio =( float ) width / height;
        float ratio = (float) 3/4; //always because camera input as 3/4
        //48=zoom1.5//72=zoom1
//        Log.d("Ratio", ratio + "");
        Matrix.perspectiveM(mProjectionMatrix, 0, 76/ZOOM_RATIO, ratio, 0.1f, 1000f);//48 for 4/3 64 for 1920/1080
//        Log.d("PerspectiveM",Arrays.toString(mProjectionMatrix));
//        Matrix.orthoM(mProjectionMatrix,0,-100,100,-100,100,-0.1f,100f);


        }

    public synchronized void onFrameAvailable ( SurfaceTexture st ) {
        mUpdateST = true;
        mView.requestRender();
    }

    public void close() {
        mUpdateST = false;
        mSTexture.release();
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
    public SurfaceTexture getSurfaceTexture(){
        return mSTexture;
    }

}