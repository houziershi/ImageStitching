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
import java.util.Arrays;

public class Sphere {

    private final String vertexShaderCode =
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "attribute vec4 vColor;"+
                    "attribute vec2 a_TexCoordinate;"+
                    "varying vec4 vPosition2;" +
                    "varying vec4 fragmentColor;"+
                    "varying vec2 v_TexCoordinate;"+
                    "void main() {" +
                    "  vPosition2 = vec4 ( vPosition.x, vPosition.y, vPosition.z, 1 );"+
                    "  gl_Position = uMVPMatrix * vPosition2;" +
                    "  fragmentColor = vColor;"+
                    "  v_TexCoordinate = a_TexCoordinate;"+
                    "}";

    private final String fragmentShaderCode =
            "precision mediump float;" +
            "uniform sampler2D sTexture;"+
            "varying vec2 v_TexCoordinate;"+
            "varying vec4 fragmentColor;" +
                    "vec2 coord;" +
                    "float width_ratio = 1.0/9242.0;" +
                    "float height_ratio = 1.0/4620.0;" +
                    "uniform float img_x;" +
                    "uniform float img_y;" +
                    "uniform float img_width;" +
                    "uniform float img_height;" +
                    "void main() {" +
                    "vec4 color;" +
                    "if(v_TexCoordinate.x < img_x*width_ratio || v_TexCoordinate.x > (img_x+img_width)*width_ratio || " +
                    "v_TexCoordinate.y < img_y*height_ratio || v_TexCoordinate.y > (img_y+img_height)*height_ratio){" +
                    "coord = vec2(0.0,0.0);" +
                    "color = texture2D(sTexture,coord);" +
                    "}" +
                    "else{" +
                    "float diff_x = (v_TexCoordinate.x - (img_x*width_ratio))/(img_width*width_ratio);" +
                    "float diff_y = (v_TexCoordinate.y - (img_y*height_ratio))/(img_height*height_ratio);" +
                    "coord = vec2(diff_x,diff_y);" +
                    "color = texture2D(sTexture,coord);" +
                    "}" +
                    ""+
                    "gl_FragColor = color;" +
                    "" +
            "}";

    private final FloatBuffer mVertexBuffer;
    private final FloatBuffer mTextureBuffer;
    private final int mProgram;
    private int mPositionHandle;
    private int mTextureHandle;
    private int mMVPMatrixHandle;
    private SphereObject sphereObject;
    // number of coordinates per vertex in this array
    private float mVertexCoords[];
    private float mTextureCoords[];
    private final int VERTEX_COUNT;
    private final int VERTEX_STRIDE = ObjReader.COORD_PER_VERTEX * 4; // 4 bytes per float
    private final int textureCount;
    private final int TEXTURE_STRIDE = ObjReader.COORD_PER_TEXTURE * 4;
    private FloatBuffer mSphereBuffer;
    private ShortBuffer mIndexBuffer;
    float color[] = { 0.63671875f, 0.76953125f, 0.22265625f, 0.0f };
    //Only one texture
    private int[] mTextures = new int[1];
    private int mTextureCoordinateHandle;
    private boolean mTexRequireUpdate = false;
    private Bitmap mQueueBitmap;
    private int[] ibo = new int[1];
    public boolean readPixel = false;
    private ByteBuffer mScreenBuffer;
    private GLRenderer glRenderer;
    public float[] mArea = {0,0,9242,4620};
    public Sphere(GLRenderer renderer) {
        glRenderer = renderer;
        Context context = renderer.mView.getActivity();
        sphereObject = new SphereObject(20,210,1);
        mSphereBuffer = sphereObject.getVertices();

        mSphereBuffer.position(0);
        mIndexBuffer = sphereObject.getIndices()[0];
        mIndexBuffer.position(0);
        ObjReader.readAll(context);
        mVertexCoords = new float[ObjReader.mVertices.size()* ObjReader.COORD_PER_VERTEX];
        mTextureCoords = new float[ObjReader.mTextures.size()* ObjReader.COORD_PER_TEXTURE];
        for(int i = 0 ; i < ObjReader.mVertices.size() ;i++){
            for(int j = 0; j < ObjReader.COORD_PER_VERTEX;j++){
                mVertexCoords[i* ObjReader.COORD_PER_VERTEX+j] = ObjReader.mVertices.get(i)[j];

            }
            for(int j = 0 ; j < ObjReader.COORD_PER_TEXTURE ;j++){
                mTextureCoords[i* ObjReader.COORD_PER_TEXTURE+j] = ObjReader.mTextures.get(i)[j];
            }
        }

        VERTEX_COUNT = mVertexCoords.length / ObjReader.COORD_PER_VERTEX;
        textureCount = mTextureCoords.length / ObjReader.COORD_PER_TEXTURE;
        //End of DataLoading

        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb = ByteBuffer.allocateDirect(mVertexCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        mVertexBuffer = bb.asFloatBuffer();
        mVertexBuffer.put(mVertexCoords);
        mVertexBuffer.position(0);

        ByteBuffer tbb = ByteBuffer.allocateDirect(mTextureCoords.length * 4);
        tbb.order(ByteOrder.nativeOrder());
        mTextureBuffer = tbb.asFloatBuffer();
        mTextureBuffer.put(mTextureCoords);
        mTextureBuffer.position(0);
        mProgram = Util.loadShader(vertexShaderCode, fragmentShaderCode);

        loadGLTexture(context, R.drawable.pano);


    }

    public void loadGLTexture(final Context context, final int texture) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;
        final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), texture, options);
        GLES20.glGenTextures(1, this.mTextures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this.mTextures[0]);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        texImage2D(bitmap);
    }


    public void texImage2D(Bitmap bitmap){
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();
    }

    public void updateBitmap(Bitmap bitmap,float[] area){
        this.mArea = area;
        mTexRequireUpdate = true;
        mQueueBitmap = bitmap;
        Log.i("GLSphere", "Bitmap waiting for updated");
    }

    public void draw(float[] mvpMatrix) {
        int xh = GLES20.glGetUniformLocation(mProgram,"img_x");
        int yh = GLES20.glGetUniformLocation(mProgram,"img_y");
        int widthh = GLES20.glGetUniformLocation(mProgram,"img_width");
        int heighth = GLES20.glGetUniformLocation(mProgram,"img_height");

        if(mTexRequireUpdate){
            Log.i("GLSphere", "Bitmap updated,Return to normal activity.");
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
        GLES20.glVertexAttribPointer(mPositionHandle, 3, GLES20.GL_FLOAT, false, sphereObject.getVeticesStride(), mSphereBuffer);

        mSphereBuffer.position(3);
        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, 2, GLES20.GL_FLOAT, false, sphereObject.getVeticesStride(), mSphereBuffer);
        //Uniform
        mTextureHandle = GLES20.glGetUniformLocation(mProgram, "sTexture");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glUniform1i(mTextureHandle, 0);
        //Area
        GLES20.glUniform1f(xh,mArea[0]);
        GLES20.glUniform1f(yh,mArea[1]);
        GLES20.glUniform1f(widthh,mArea[2]);
        GLES20.glUniform1f(heighth,mArea[3]);

        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereObject.getNumIndices()[0], GLES20.GL_UNSIGNED_SHORT, mIndexBuffer);
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
            Mat mat = new Mat(glRenderer.mHeight,glRenderer.mWidth, CvType.CV_8UC4);
            mat.put(0, 0, pixelsBuffer);
            Mat m = new Mat();
            Imgproc.cvtColor(mat, m, Imgproc.COLOR_RGBA2BGR);
            Core.flip(m, mat, 0);
            Highgui.imwrite("/sdcard/stitch/readpixel.jpg",mat);

        }
    }


}
