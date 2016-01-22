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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * A two-dimensional triangle for use as a drawn object in OpenGL ES 2.0.
 */
public class Sphere {

    private static final int VERTEX_MAGIC_NUMBER = 50;
    private static final int NUM_FLOATS_PER_VERTEX = 3;
    private static final int NUM_FLOATS_PER_TEXTURE = 2;
    private final String vertexShaderCode =
            // This matrix member variable provides a hook to manipulate
            // the coordinates of the objects that use this vertex shader
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "attribute vec4 vColor;"+
                    "attribute vec2 a_TexCoordinate;"+
                    "varying vec4 vPosition2;" +
                    "varying vec4 fragmentColor;"+
                    "varying vec2 v_TexCoordinate;"+
                    "void main() {" +
                    // The matrix must be included as a modifier of gl_Position.
                    // Note that the uMVPMatrix factor *must be first* in order
                    // for the matrix multiplication product to be correct.
                    "  vPosition2 = vec4 ( vPosition.x * 2.0, vPosition.y * 2.0, vPosition.z * 2.0, 1.0 );"+
                    "  gl_Position = uMVPMatrix * vPosition2;" +
                    "  fragmentColor = vColor;"+
                    "v_TexCoordinate = a_TexCoordinate;"+
                    "}";

    private final String fragmentShaderCode =
            "precision mediump float;" +
            "uniform sampler2D sTexture;"+
            "varying vec2 v_TexCoordinate;"+
            "varying vec4 fragmentColor;" +
            "void main() {" +
            "  gl_FragColor = texture2D(sTexture,v_TexCoordinate);\n" +
            "}";

    private final FloatBuffer vertexBuffer;
    private final FloatBuffer textureBuffer;
    private final int mProgram;
    private int mPositionHandle;
    private int mColorHandle;
    private int mTextureHandle;
    private int mMVPMatrixHandle;

    // number of coordinates per vertex in this array

    private float vertCoords[];
    private float textureCoords[];
    private final int vertexCount;
    private final int vertexStride = ObjReader.COORD_PER_VERTEX * 4; // 4 bytes per vertex
    private final int textureCount;
    private final int textureStride = ObjReader.COORD_PER_TEXTURE * 4;
    float color[] = { 0.63671875f, 0.76953125f, 0.22265625f, 0.0f };
    //Only one texture
    private int[] mTextures = new int[1];
    private int mTextureCoordinateHandle;


    public Sphere(Context context) {

        ObjReader.readAll(context);
        vertCoords = new float[ObjReader.verts.size()* ObjReader.COORD_PER_VERTEX];
        textureCoords = new float[ObjReader.textures.size()* ObjReader.COORD_PER_TEXTURE];
        for(int i = 0 ; i < ObjReader.verts.size() ;i++){
            for(int j = 0; j < ObjReader.COORD_PER_VERTEX;j++){
                vertCoords[i* ObjReader.COORD_PER_VERTEX+j] = ObjReader.verts.get(i)[j];

            }
            for(int j = 0 ; j < ObjReader.COORD_PER_TEXTURE ;j++){
                textureCoords[i* ObjReader.COORD_PER_TEXTURE+j] = ObjReader.textures.get(i)[j];
            }
        }
        vertexCount = vertCoords.length / ObjReader.COORD_PER_VERTEX;
        textureCount = textureCoords.length / ObjReader.COORD_PER_TEXTURE;
        //End of DataLoading

        // initialize vertex byte buffer for shape coordinates
        ByteBuffer bb = ByteBuffer.allocateDirect(vertCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(vertCoords);
        vertexBuffer.position(0);

        ByteBuffer tbb = ByteBuffer.allocateDirect(textureCoords.length * 4);
        tbb.order(ByteOrder.nativeOrder());
        textureBuffer = tbb.asFloatBuffer();
        textureBuffer.put(textureCoords);
        textureBuffer.position(0);

        // prepare shaders and OpenGL program
        int vertexShader = Util.loadShader(
                GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = Util.loadShader(
                GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        mProgram = GLES20.glCreateProgram();
        //Load IC_launcher
        loadGLTexture(context,R.drawable.pano);
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);
        GLES20.glLinkProgram(mProgram);

    }

    public void loadGLTexture(final Context context, final int texture) {
        // Generate one texture pointer, and bind it to the texture array.
        GLES20.glGenTextures(1, this.mTextures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this.mTextures[0]);

        // Create nearest filtered texture.
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;
        // Use Android GLUtils to specify a two-dimensional texture image from our bitmap.
        final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), texture, options);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();
    }

    public void draw(float[] mvpMatrix) {
        GLES20.glUseProgram(mProgram);

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        mColorHandle = GLES20.glGetAttribLocation(mProgram, "vColor");

        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgram, "a_TexCoordinate");

        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, ObjReader.COORD_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);
        GLES20.glEnableVertexAttribArray(mColorHandle);
        GLES20.glVertexAttribPointer(mColorHandle, ObjReader.COORD_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);
        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, ObjReader.COORD_PER_TEXTURE, GLES20.GL_FLOAT, false, textureStride, textureBuffer);

        // Set the active texture unit to texture unit 0.
        mTextureHandle = GLES20.glGetUniformLocation(mProgram, "sTexture");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        // Bind the texture to this unit.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);
        GLES20.glUniform1i(mTextureHandle, 0);
        // get handle to shape's transformation matrix
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");

        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);
        // Draw the triangle
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
    }
}
