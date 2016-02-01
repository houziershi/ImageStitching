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
                    "varying vec2 texCoord;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(sTexture,texCoord);\n" +
                    "}";
    private final String fss_int =
            "precision mediump float;\n" +
                    "uniform sampler2D sTexture;\n"+
                    "varying vec2 texCoord;\n" +
                    "void main() {\n" +
                    "gl_FragColor = texture2D(sTexture,texCoord);" +
                    "gl_FragColor.a = 1.0;" +
                    "}";

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
        int mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTexture[0]);
        GLES20.glUniform1i(th, 0);
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

        GLES20.glVertexAttribPointer(ph, COORD_PER_VERTEX, GLES20.GL_FLOAT, false, 4 * COORD_PER_VERTEX, mVertexCoord);
        GLES20.glVertexAttribPointer(tch, COORD_PER_TEXTURE, GLES20.GL_FLOAT, false, 4 * COORD_PER_TEXTURE, mTextureCoord);
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
