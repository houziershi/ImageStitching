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
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class ARObject {

    private final String vertexShaderCode =
            "uniform mat4 uViewMatrix;" +
                    "uniform mat4 uProjectionMatrix;" +
                    "uniform mat4 uModelMatrix;" +
                    "attribute vec4 vPosition;" +
                    "attribute vec4 vColor;"+
                    "attribute vec2 a_TexCoordinate;"+
                    "varying vec4 vPosition2;" +
                    "varying vec4 fragmentColor;"+
                    "varying vec2 v_TexCoordinate;"+
                    "void main() {" +
                    "   vPosition2 = vec4 ( vPosition.x, vPosition.y, vPosition.z, 1 );"+
                    "   gl_Position =   uProjectionMatrix * uViewMatrix * uModelMatrix * vPosition2;" +
                    "   v_TexCoordinate = a_TexCoordinate;" +
                    "   fragmentColor = vec4(1,0,1,1);" +
                    "   return;"+
                    "}";

    private final String fragmentShaderCode =
            "precision highp float;" +
                    "uniform sampler2D sTexture;"+
                    "varying vec2 v_TexCoordinate;"+
                    "varying vec4 fragmentColor;" +
                    "void main() {"+
                    "" +
                    "   gl_FragColor = texture2D(sTexture,v_TexCoordinate);" +
                    "   " +
                    "   return;" +
                    "}";
    //OpenGL = cols wise???
    //If transpose is GL_FALSE, each matrix is assumed to be supplied in column major order.
    //If transpose is GL_TRUE, each matrix is assumed to be supplied in row major order.
    //in glUniformMatrix4fv
    //[0 4 8 12]
    //[1 5 9 13]
    //[2 6 10 14]
    //[3 7 11 15]
    private float[] mModelRotation = {1f,0,0,0
            ,0,1f,0,0f
            ,0,0,1f,-3f
            ,0,0,0,1f};

    private final FloatBuffer mVertexBuffer;
    private final FloatBuffer mTextureBuffer;
    private final int mProgram;
    private int mPositionHandle;
    private int mTextureHandle;
    private int mViewMatrixHandle;
    private int mModelMatrixHandle;
    // number of coordinates per vertex in this array
    private float mVertexCoords[];
    private float mTextureCoords[];
    private final int vertexCount;
    private final int VERTEX_STRIDE = ObjReader.COORD_PER_VERTEX * 4; // 4 bytes per float
    private final int textureCount;
    private final int TEXTURE_STRIDE = ObjReader.COORD_PER_TEXTURE * 4;
    //Only one texture
    private int[] mTextures = new int[1];
    private int mTextureCoordinateHandle;
    private int mProjectionMatrixHandle;
    private GLRenderer glRenderer;

    public ARObject(GLRenderer renderer) {
        glRenderer = renderer;
        Context context = renderer.mView.getActivity();
        ObjReader.readAll(context,"sign");
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

        vertexCount = mVertexCoords.length / ObjReader.COORD_PER_VERTEX;
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
        GLES20.glGenTextures(2, this.mTextures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this.mTextures[0]);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        mockTexImage2D(bitmap);
    }


    public void mockTexImage2D(Bitmap bitmap){
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 1, bitmap, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        bitmap.recycle();
    }

    //TODO IMPLEMENTS THIS
    public void draw(float[] viewMatrix,float[] projectionMatrix) {
        GLES20.glUseProgram(mProgram);

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");

        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgram, "a_TexCoordinate");

        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, ObjReader.COORD_PER_VERTEX, GLES20.GL_FLOAT, false, VERTEX_STRIDE, mVertexBuffer);
        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, ObjReader.COORD_PER_TEXTURE, GLES20.GL_FLOAT, false, TEXTURE_STRIDE, mTextureBuffer);

        // Set the active texture unit to texture unit 0.
        mTextureHandle = GLES20.glGetUniformLocation(mProgram, "sTexture");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // Bind the texture to this unit.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);
        GLES20.glUniform1i(mTextureHandle, 0);
        // get handle to shape's transformation matrix
        mViewMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uViewMatrix");
        mProjectionMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uProjectionMatrix");
        mModelMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uModelMatrix");
        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(mViewMatrixHandle, 1, false, viewMatrix, 0);
        GLES20.glUniformMatrix4fv(mProjectionMatrixHandle, 1 ,false, projectionMatrix,0);
        GLES20.glUniformMatrix4fv(mModelMatrixHandle, 1, true, mModelRotation , 0);

        // Draw the triangle
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
    }


    public void setModelRotation(float[] modelRotation) {
        mModelRotation = modelRotation;

    }
}
