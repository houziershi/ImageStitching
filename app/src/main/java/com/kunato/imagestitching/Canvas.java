package com.kunato.imagestitching;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by kunato on 1/6/16 AD.
 */
public class Canvas {
    private final String vss =
            "uniform mat4 uMVPMatrix;\n" +
                    "attribute vec3 vPosition;\n" +
                    "attribute vec2 vTexCoord;\n" +
                    "varying vec2 texCoord;\n" +
                    "void main() {\n" +
                    "  texCoord = vTexCoord;\n" +
                    "  gl_Position = vec4 ( vPosition.x, vPosition.y, vPosition.z, 1.0 );\n" +
                    "}";
    private final String fss_ext =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "uniform mat3 homography;" +
                    "uniform float width;" +
                    "uniform float height;\n" +
                    "varying vec2 texCoord;\n" +
                    "vec2 convertToTexCoord(vec3 pixelCoords){" +
                    "pixelCoords /= pixelCoords.z;" +
                    "pixelCoords /= vec3(width,height,1.0);" +
                    "" +
                    "return pixelCoords.xy;}" +
                    "void main() {\n" +
                    "vec3 coord = vec3(texCoord.x,texCoord.y,1.0);" +
                    "gl_FragColor = texture2D(sTexture,convertToTexCoord(coord*homography));\n" +
                    "}";
    private final String fss_int =
            "precision mediump float;\n" +
                    "uniform sampler2D sTexture;\n"+
                    "varying vec2 texCoord;\n" +
                    "void main() {\n" +
                    "gl_FragColor = texture2D(sTexture,texCoord);" +
                    "gl_FragColor.a = 1.0;" +
                    "}";
//        public float[] mHomography = {1,0,0,0,1,0,0,0,1};
//    public float[] mHomography = {0.70710678118f,-0.70710678118f,0, 0.70710678118f,0.70710678118f,0f, 0,0,1};
//    public float[] mHomography = {9.284416f,0.329349f,-589.334961f, 1.089121f,10.698853f,-309.189026f, 0.001062f,0.000253f,1.000000f};
//    public float[] mHomography = {1.455513f,-0.037048f,-506.760040f,0.061768f,1.757028f,-136.568909f,0.000019f,0.000011f,1.000000f};
public float[] mHomography = {0.824879f, -0.02256f, 0f, -0.013021f,0.979792f,0f ,-0.000028f ,-0.000031f, 1.000000f};
    private FloatBuffer mVertexCoord;
    private FloatBuffer mTextureCoord;
    private int[] mTexture;
    private int mProgram;
    public static final int COORD_PER_VERTEX = 3;
    public static final int COORD_PER_TEXTURE = 2;
    public Canvas(float[] vertices,float[] textures,Context context) {
        this.mTexture = new int[1];
        mVertexCoord = ByteBuffer.allocateDirect(4 * vertices.length).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertexCoord.put(vertices);
        mVertexCoord.position(0);
        mTextureCoord = ByteBuffer.allocateDirect(4 * textures.length).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTextureCoord.put(textures);
        mTextureCoord.position(0);
        mProgram = Util.loadShader(vss, fss_ext);
        GLES20.glGenTextures(1, mTexture, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTexture[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
    }


    public void draw(float[] mMVPMatrix){
        GLES20.glUseProgram(mProgram);
        int ph = GLES20.glGetAttribLocation(mProgram, "vPosition");
        int tch = GLES20.glGetAttribLocation (mProgram, "vTexCoord" );
        int th = GLES20.glGetUniformLocation(mProgram, "sTexture");
        int homoh = GLES20.glGetUniformLocation(mProgram,"homography");
        int widthh = GLES20.glGetUniformLocation(mProgram,"width");
        int heighth = GLES20.glGetUniformLocation(mProgram,"height");
        int mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,mTexture[0]);
        GLES20.glUniform1i(th, 0);
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false,mMVPMatrix,0);
        GLES20.glVertexAttribPointer(ph, COORD_PER_VERTEX, GLES20.GL_FLOAT, false, 4 * COORD_PER_VERTEX, mVertexCoord);
        GLES20.glVertexAttribPointer(tch, COORD_PER_TEXTURE, GLES20.GL_FLOAT,false,4*COORD_PER_TEXTURE,mTextureCoord);
        GLES20.glUniformMatrix3fv(homoh, 1, false, mHomography,0);
        GLES20.glUniform1f(heighth, 1);
        GLES20.glUniform1f(widthh, 1);
        GLES20.glEnableVertexAttribArray(ph);
        GLES20.glEnableVertexAttribArray(tch);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(ph);
        GLES20.glDisableVertexAttribArray(tch);
        GLES20.glDisableVertexAttribArray(th);
    }
    public void deleteTex() {
        GLES20.glDeleteTextures(1, mTexture, 0);
    }
    public int[] getTexturePos(){
        return mTexture;
    }

}
