/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kunato.imagestitching;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class SphereObject {

    private final String vertexShaderCode =
            "uniform mat4 uViewMatrix;" +
                    "uniform mat4 uProjectionMatrix;" +
                    "attribute vec4 vPosition;" +
                    "attribute vec4 vColor;"+
                    "attribute vec2 a_TexCoordinate;"+
                    "varying vec4 vPosition2;" +
                    "varying vec4 fragmentColor;"+
                    "varying vec2 v_TexCoordinate;"+
                    "void main() {" +
                    "  vPosition2 = vec4 ( vPosition.x, vPosition.y, vPosition.z, 1 );"+
                    "  gl_Position = uProjectionMatrix * uViewMatrix * vPosition2;" +
                    "  fragmentColor = vColor;"+
                    "  v_TexCoordinate = a_TexCoordinate;"+
                    "}";

    private final String fragmentShaderCode =
            "precision highp float;" +
            "uniform sampler2D sTexture;"+
            "varying vec2 v_TexCoordinate;"+
            "varying vec4 fragmentColor;" +
                    "float width_ratio = 9242.0;" +
                    "float height_ratio = 4620.0;" +
                    "uniform float img_x;" +
                    "uniform float img_y;" +
                    "uniform float img_width;" +
                    "uniform float img_height;" +
                    "void main() {" +
                    "if(img_x == 0.0 && img_y == 0.0 && img_width == 0.0 && img_height == 0.0){" +
                    "   gl_FragColor = vec4(0,0,0,0);" +
                    "   return;" +
                    "}" +
                    "float diff_x = (((v_TexCoordinate.x*width_ratio) - (img_x))/(img_width));" +
                    "float diff_y = (((v_TexCoordinate.y*height_ratio) - (img_y))/(img_height));" +
                    "gl_FragColor = texture2D(sTexture,vec2(diff_x,diff_y));" +
            "}";

    private final int mProgram;
    private int mPositionHandle;
    private int mTextureHandle;
    private int mViewMatrixHandle;
    private int mProjectionMatrixHandle;
    private SphereShape mSphereShape;
    private FloatBuffer mSphereBuffer;
    private ShortBuffer mIndexBuffer;
    float color[] = { 0.63671875f, 0.76953125f, 0.22265625f, 0.0f };
    //Only one texture
    private int[] mTextures = new int[1];
    private int mTextureCoordinateHandle;
    private boolean mTexRequireUpdate = false;
    private Bitmap mQueueBitmap;
    public boolean readPixel = false;
    private ByteBuffer mScreenBuffer;
    private GLRenderer glRenderer;
    public float[] mArea = {0,0,0,0};
    public SphereObject(GLRenderer renderer) {
        glRenderer = renderer;
                Context context = renderer.mView.getActivity();
        mSphereShape = new SphereShape(20,210,1);
        mSphereBuffer = mSphereShape.getVertices();
        mSphereBuffer.position(0);
        mIndexBuffer = mSphereShape.getIndices()[0];
        mIndexBuffer.position(0);

        mProgram = Util.loadShader(vertexShaderCode, fragmentShaderCode);

        loadGLTexture(context, R.drawable.pano, false);


    }

    public void loadGLTexture(final Context context, final int texture, boolean show) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;
        final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), texture, options);

        GLES20.glGenTextures(1, this.mTextures, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this.mTextures[0]);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST_MIPMAP_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        if(show)
        mockTexImage2D(bitmap);
    }


    public void mockTexImage2D(Bitmap bitmap){
        mArea[0] = mArea[1] = 0;
        mArea[2] = 9242;
        mArea[3] = 4620;
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
//        mArea[0] = 3257f;
//        mArea[1] = 1460f;
//        mArea[2] = 1881.0f;
//        mArea[3] = 1707.0f;
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        bitmap.recycle();
    }

    public void updateBitmap(Bitmap bitmap,float[] area){
        this.mArea = area;
        mTexRequireUpdate = true;
        mQueueBitmap = bitmap;
        Log.i("GLSphere", "Bitmap waiting for updated");
    }

    public void draw(float[] viewMatrix,float[] projectionMatrix) {
        int xh = GLES20.glGetUniformLocation(mProgram,"img_x");
        int yh = GLES20.glGetUniformLocation(mProgram,"img_y");
        int widthh = GLES20.glGetUniformLocation(mProgram,"img_width");
        int heighth = GLES20.glGetUniformLocation(mProgram,"img_height");

        if(mTexRequireUpdate){
            Log.i("GLSphere", "Bitmap updated,Return to normal activity.");
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this.mTextures[0]);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mQueueBitmap, 0);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            mQueueBitmap.recycle();
            mTexRequireUpdate = false;
        }
        GLES20.glUseProgram(mProgram);
        //Attrib
        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgram, "a_TexCoordinate");
        mSphereBuffer.position(0);
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, 3, GLES20.GL_FLOAT, false, mSphereShape.getVeticesStride(), mSphereBuffer);

        mSphereBuffer.position(3);
        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, 2, GLES20.GL_FLOAT, false, mSphereShape.getVeticesStride(), mSphereBuffer);
        //Uniform
        mTextureHandle = GLES20.glGetUniformLocation(mProgram, "sTexture");
        GLES20.glUniform1i(mTextureHandle, 0);
        //Area
        GLES20.glUniform1f(xh,mArea[0]);
        GLES20.glUniform1f(yh,mArea[1]);
        GLES20.glUniform1f(widthh,mArea[2]);
        GLES20.glUniform1f(heighth,mArea[3]);

        mViewMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uViewMatrix");
        mProjectionMatrixHandle = GLES20.glGetUniformLocation(mProgram,"uProjectionMatrix");
        GLES20.glUniformMatrix4fv(mViewMatrixHandle, 1, false, viewMatrix, 0);
        GLES20.glUniformMatrix4fv(mProjectionMatrixHandle, 1, false, projectionMatrix, 0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mSphereShape.getNumIndices()[0], GLES20.GL_UNSIGNED_SHORT, mIndexBuffer);
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTextureCoordinateHandle);


        if(readPixel) {
            Log.d("GL","ReadPixel");
            mScreenBuffer = ByteBuffer.allocateDirect(glRenderer.mHeight * glRenderer.mWidth * 4);
            mScreenBuffer.order(ByteOrder.nativeOrder());
            GLES20.glReadPixels(0, 0, glRenderer.mWidth, glRenderer.mHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mScreenBuffer);
            Log.d("mScreenBuffer", "Remaining " + mScreenBuffer.remaining());
            mScreenBuffer.rewind();
            byte pixelsBuffer[] = new byte[4*glRenderer.mHeight*glRenderer.mWidth];
            mScreenBuffer.get(pixelsBuffer);
            Mat mat = new Mat(glRenderer.mHeight, glRenderer.mWidth, CvType.CV_8UC4);
            mat.put(0, 0, pixelsBuffer);
            Mat m = new Mat();
            Imgproc.cvtColor(mat, m, Imgproc.COLOR_RGBA2BGR);
            Core.flip(m, mat, 0);
            Highgui.imwrite("/sdcard/stitch/readpixel.jpg",mat);

        }
    }


}
