package com.kunato.imagestitching;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.FloatBuffer;
import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

// Renderer
public class GLRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private FloatBuffer pVertex;
    private FloatBuffer pTexCoord;
    private int hProgram;
    private SurfaceTexture mSTexture;
    private boolean mUpdateST = false;
    private CameraSurfaceView mView;
    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private float[] mRotationMatrix = {1f,0,0,0,0,1f,0,0,0,0,1f,0,0,0,0,1f};
    private Canvas canvas;
    private Sphere mSphere;

    GLRenderer(CameraSurfaceView view) {
        mView = view;
    }

    public void onSurfaceCreated ( GL10 unused, EGLConfig config ) {
        float canvasScale = 0.1f;
        float[] vertices = {-canvasScale, -canvasScale, 0,
                canvasScale, -canvasScale, 0,
                -canvasScale, canvasScale, 0f,
                canvasScale, canvasScale, 0f};
        float[] textures = {0.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 0.0f,
                1.0f, 1.0f};
        canvas = new Canvas(vertices,textures, mView.getActivity());
        mSphere = new Sphere(mView.getActivity());
        mSTexture = new SurfaceTexture (canvas.getTexturePos()[0]);
        mSTexture.setOnFrameAvailableListener(this);
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        //(x (vertical),(horizontal)y,z)
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        Matrix.setLookAtM(mViewMatrix, 0,
                0.0f, 0.0f, 0.7f,
                0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f);

    }
    //Core function
    public void onDrawFrame ( GL10 unused ) {
        float[] sphereMat = new float[16];
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);
        Matrix.multiplyMM(sphereMat, 0, mMVPMatrix, 0, mRotationMatrix, 0);


        //draws
        synchronized(this) {
            if ( mUpdateST ) {
                mSTexture.updateTexImage();
                mUpdateST = false;
            }
        }

        //multiply MM(retMat, retMatOffset, mat1 * mat2 (includeOffset))
        canvas.draw(mMVPMatrix);
        mSphere.draw(sphereMat);
        GLES20.glFlush();
    }

    public void onSurfaceChanged ( GL10 unused, int width, int height ) {
        GLES20.glViewport(0, 0, width, height);
        float ratio =( float )width / height;
        Matrix.perspectiveM(mProjectionMatrix,0,45,ratio,0.1f,100f);
        }

    public synchronized void onFrameAvailable ( SurfaceTexture st ) {
        mUpdateST = true;
        mView.requestRender();
    }

    public void close() {
        mUpdateST = false;
        mSTexture.release();
        canvas.deleteTex();
    }
    public void setRotationMatrix(float[] rot){
        mRotationMatrix = rot;
    }
    public SurfaceTexture getSurfaceTexture(){
        return mSTexture;
    }
}