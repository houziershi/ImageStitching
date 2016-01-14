package com.kunato.imagestitching;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.FloatBuffer;

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
    private int[] hTex = new int[2];
    private Canvas canvas;
    private Canvas canvas2;

    GLRenderer(CameraSurfaceView view) {
        mView = view;
    }

    public void onSurfaceCreated ( GL10 unused, EGLConfig config ) {
        //String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        //Log.i("mr", "Gl extensions: " + extensions);
        //Assert.assertTrue(extensions.contains("OES_EGL_image_external"));
        float[] vertices = {1.0f, -1.0f, 0.0f, -1.0f, -1.0f, 0.0f, 1.0f, 1.0f, 0.0f, -1.0f, 1.0f, 0.0f};
        float[] textures = {0.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 0.0f,
                1.0f, 1.0f};
        //
        float[] vertices2 = {0.0f,  -0.5f, 0.0f,   // top left
                0.0f, 0.0f, 0.0f,   // bottom left
                0.0f, 0.0f, 0.0f,   // bottom right
                0.0f,  0.0f, 0.0f }; // top right
        //bot,left
        //bot,right
        //top,left
        //top,right
        float[] textures2 = {
                0.0f, 1.0f,
                1.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f};
        canvas = new Canvas(vertices,textures,Canvas.CAMERA, -1 , mView.getActivity(),hTex);
        canvas2 = new Canvas(vertices2,textures2,Canvas.NON_CAMERA, -1 , mView.getActivity(),hTex);
        mSTexture = new SurfaceTexture (canvas.getTexturePos()[0]);
        mSTexture.setOnFrameAvailableListener(this);
        GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);

    }
    //Core function
    public void onDrawFrame ( GL10 unused ) {

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        //draw
        synchronized(this) {
            if ( mUpdateST ) {
                mSTexture.updateTexImage();
                mUpdateST = false;
            }
        }
        //setLookAt(returnMat, returnMatStartOffset,(eye3),(center3),(up3))
        //(x (vertical),(horizontal)y,z)
        Matrix.setLookAtM(mViewMatrix, 0,
                0.0f, 0.0f, -3.0f,
                0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f);
        //multiply MM(retMat, retMatOffset, mat1 * mat2 (includeOffset))
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);
        canvas.draw(mMVPMatrix);
        canvas2.draw(mMVPMatrix);
//        GLES20.glUseProgram(hProgram);
//
//        int ph = GLES20.glGetAttribLocation(hProgram, "vPosition");
//        int tch = GLES20.glGetAttribLocation ( hProgram, "vTexCoord" );
//        int th = GLES20.glGetUniformLocation(hProgram, "sTexture");
//        int mMVPMatrixHandle = GLES20.glGetUniformLocation(hProgram, "uMVPMatrix");
//
//        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
//        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[0]);
//        GLES20.glUniform1i(th, 0);
//        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);
//        GLES20.glVertexAttribPointer(ph, 2, GLES20.GL_FLOAT, false, 4 * 3, pVertex);
//        GLES20.glVertexAttribPointer(tch, 2, GLES20.GL_FLOAT, false, 4 * 2, pTexCoord);
//        GLES20.glEnableVertexAttribArray(ph);
//        GLES20.glEnableVertexAttribArray(tch);
//
//        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glFlush();
    }

    public void onSurfaceChanged ( GL10 unused, int width, int height ) {
        GLES20.glViewport(0, 0, width, height);
        float ratio =( float )width / height;
        Matrix.frustumM(mProjectionMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
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
    public Canvas getCanvas2(){
        return canvas2;
    }
    public SurfaceTexture getSurfaceTexture(){
        return mSTexture;
    }
}