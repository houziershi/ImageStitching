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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.location.Location;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class ARObject {

    private final String vertexShaderCode =
                    "uniform mat4 uViewMatrix;" +
                    "uniform mat4 uScaleMatrix;" +
                    "uniform mat4 uRotationMatrix;" +
                    "uniform mat4 uProjectionMatrix;" +
                    "uniform vec4 uTranslationVec;" +
                    "uniform mat4 uAdjustMatrix;" +
                    "attribute vec4 vPosition;" +
                    "attribute vec4 vColor;"+
                    "attribute vec2 a_TexCoordinate;"+
                    "varying vec4 vPosition2;" +
                    "varying vec4 fragmentColor;"+
                    "varying vec2 v_TexCoordinate;"+
                    "void main() {" +
                    "   vPosition2 = (uAdjustMatrix  * uScaleMatrix * vec4 ( vPosition.x , vPosition.z , vPosition.y , 1 )) + uTranslationVec;"+
                    "   gl_Position = uProjectionMatrix * uViewMatrix * vPosition2;" +
                    "   v_TexCoordinate = vec2(1,a_TexCoordinate.y) - vec2(a_TexCoordinate.x,0);" +
                    "   fragmentColor = vec4(1,0,1,1);" +
                    "   return;"+
                    "}";

    private final String fragmentShaderCode =
            "precision highp float;" +
                    "uniform sampler2D sTexture;"+
                    "varying vec2 v_TexCoordinate;"+
                    "varying vec4 fragmentColor;" +
                    "void main() {"+
                    "   gl_FragColor = texture2D(sTexture,v_TexCoordinate);" +
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
    private float[] mTranslationVector = {0,0,0,0};
    private float[] mScaleRotation = {1f,0,0,0
            ,0,1f,0,0f
            ,0,0,1f,0f
            ,0,0,0,1f};
    private float[] mCameraRotation = {0.51611745f, -0.09066901f, 0.8517054f, 0.0f,
            0.8552484f, 0.10867332f, -0.50669557f, 0.0f,
            -0.046616063f, 0.9899339f, 0.1336327f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f};
    private float[] mAdjustRotation = {1f,0,0,0
            ,0,1f,0,0f
            ,0,0,1f,0f
            ,0,0,0,1f};
    private final int TEXTSIZE_HEIGHT = 196;
    private final int TEXTSIZE_WIDTH = 256;
    private Location mLocalLocation;
    private final FloatBuffer mVertexBuffer;
    private final FloatBuffer mTextureBuffer;
    private final int mProgram;
    private int mPositionHandle;
    private int mTextureHandle;
    private int mViewMatrixHandle;
    private int mTranslationVectorHandle;
    private int mRotationMatrixHandle;
    private int mScaleMatrixHandle;

    private int mAdjustMatrixHandle;
    // number of coordinates per vertex in this array
    private float mVertexCoords[];
    private float mTextureCoords[];
    private final int vertexCount;
    private final int VERTEX_STRIDE = ObjReader.COORD_PER_VERTEX * 4; // 4 bytes per float
    private final int textureCount;
    private final int TEXTURE_STRIDE = ObjReader.COORD_PER_TEXTURE * 4;
    //Only one texture
    private int[] mTextures = new int[2];
    private int mTextureCoordinateHandle;
    private int mProjectionMatrixHandle;
    private GLRenderer glRenderer;
    private boolean mCameraPositionSet = false;
    private int mNumber;
    private String mName;

    public ARObject(GLRenderer renderer,int number, String name,double latitude, double longitude) {
        mName = name;
    
        //mock data
        mLocalLocation = new Location("");
        mLocalLocation.setLatitude(latitude);
        mLocalLocation.setLongitude(longitude);
        glRenderer = renderer;
        mNumber = number;
        Context context = renderer.mView.getActivity();
        ObjReader.readAll(context,"sign2");
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
    public void setCameraRotation(float[] cameraRotation,Location deviceLocation){
        Log.d("ARObject","SetCameraRotation");
        mCameraRotation = cameraRotation.clone();
        double bearing = deviceLocation.bearingTo(mLocalLocation);
        float[] mOrientation = new float[3];
        SensorManager.getOrientation(cameraRotation, mOrientation);
        Log.d("ARObject","Location : ("+deviceLocation.getLatitude()+","+deviceLocation.getLongitude()+")");
        Log.d("ARObject","Object : "+mNumber +" , Bearing degree ; "+bearing + " , Plus devices degree ; "+ mOrientation[0] * 180.0 / Math.PI);
        bearing *= Math.PI / 180.0;
        mTranslationVector[0] = (float) Math.sin(-mOrientation[0]+ bearing) * 3;
        mTranslationVector[2] = (float) Math.cos(-mOrientation[0]+ bearing) * -3;
        Log.d("ARObject","Real Heading "+(-mOrientation[0]+ bearing)*180.0/Math.PI);
        mCameraPositionSet = true;
        double DiffAngle = (-mOrientation[0] + bearing) - Math.PI/2.0;
        mAdjustRotation[0] = (float) Math.sin(DiffAngle);
        mAdjustRotation[2] = (float) Math.cos(DiffAngle);
        mAdjustRotation[8] = (float) -Math.cos(DiffAngle);
        mAdjustRotation[10] = (float) Math.sin(DiffAngle);
    }
    public void loadGLTexture(final Context context, final int texture) {
        GLES20.glGenTextures(1, this.mTextures, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0+mNumber);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this.mTextures[0]);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
//        mockTexImage2D(context,texture);
        genTextureFromText(context,mName);
    }
    public void genTextureFromText(Context context,String text){
        // Create an empty, mutable bitmap
        Bitmap bitmap = Bitmap.createBitmap(TEXTSIZE_WIDTH, TEXTSIZE_HEIGHT, Bitmap.Config.ARGB_4444);
        // get a canvas to paint over the bitmap
        Canvas canvas = new Canvas(bitmap);
        Drawable bg = context.getResources().getDrawable(R.drawable.ic_tab_black_48dp);
        bg.setBounds(0,0,TEXTSIZE_WIDTH,TEXTSIZE_HEIGHT);
//        canvas.drawARGB(0xff ,0xff ,0xff ,0xff);
        bg.draw(canvas);
        // Draw the text
        Typeface tf = Typeface.createFromAsset(context.getAssets(),"fonts/comicsanms.ttf");
        Paint textPaint = new Paint();
        textPaint.setTextSize(32);
        textPaint.setTypeface(tf);
        textPaint.setAntiAlias(true);
        textPaint.setARGB(0xff, 0xff, 0xcd, 0x55);
        // draw the text centered

        Rect rect = new Rect(0,0,TEXTSIZE_WIDTH,TEXTSIZE_HEIGHT);
        RectF bounds = new RectF();
        bounds.right = textPaint.measureText(text, 0, text.length());
        bounds.bottom = textPaint.descent() - textPaint.ascent();
        bounds.left += (rect.width() - bounds.right) / 2.0f;
        bounds.top += (rect.height() - bounds.bottom) / 2.0f;
        canvas.drawText(text, bounds.left, bounds.top - textPaint.ascent(), textPaint);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        bitmap.recycle();
    }


    //TODO IMPLEMENTS THIS
    public void draw(float[] viewMatrix,float[] projectionMatrix) {
        if(!mCameraPositionSet)
            return;
        GLES20.glUseProgram(mProgram);

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");

        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgram, "a_TexCoordinate");

        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, ObjReader.COORD_PER_VERTEX, GLES20.GL_FLOAT, false, VERTEX_STRIDE, mVertexBuffer);
        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, ObjReader.COORD_PER_TEXTURE, GLES20.GL_FLOAT, false, TEXTURE_STRIDE, mTextureBuffer);

        // Set the active texture unit to texture unit 0.
        mTextureHandle = GLES20.glGetUniformLocation(mProgram, "sTexture");
        GLES20.glUniform1i(mTextureHandle, mNumber);
        // get handle to shape's transformation matrix
        mViewMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uViewMatrix");
        mProjectionMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uProjectionMatrix");
        mAdjustMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uAdjustMatrix");
        mTranslationVectorHandle = GLES20.glGetUniformLocation(mProgram, "uTranslationVec");
        mRotationMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uRotationMatrix");
        mScaleMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uScaleMatrix");
        // Apply the projection and view transformation
        GLES20.glUniformMatrix4fv(mViewMatrixHandle, 1, false, viewMatrix, 0);
        GLES20.glUniform4fv(mTranslationVectorHandle, 1 , mTranslationVector,0);
        GLES20.glUniformMatrix4fv(mProjectionMatrixHandle, 1 ,false, projectionMatrix,0);
        GLES20.glUniformMatrix4fv(mRotationMatrixHandle, 1, false, mCameraRotation , 0);
        GLES20.glUniformMatrix4fv(mScaleMatrixHandle, 1, true, mScaleRotation, 0);
        GLES20.glUniformMatrix4fv(mAdjustMatrixHandle,1, true, mAdjustRotation,0);

        // Draw the triangle
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
    }


    public void setModelRotation(float[] modelRotation) {
        mTranslationVector = modelRotation;

    }
}
