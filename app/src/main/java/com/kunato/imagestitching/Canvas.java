package com.kunato.imagestitching;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
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
                    "  gl_Position = uMVPMatrix * vec4 ( vPosition.x, vPosition.y, 0.0, 1.0 );\n" +
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
                    "  gl_FragColor = texture2D(sTexture,texCoord);\n" +
                    "}";

    private FloatBuffer pVertex;
    private FloatBuffer pTexCoord;
    private int[] hTex;
    private int hProgram;
    public static int CAMERA = 0;
    public static int NON_CAMERA = 1;
    private Context context;
    public static int counter = 0;
    public int id;
    public int FLAG;
    private Bitmap queueBmp;
    private boolean requireUpdate = false;
    public Canvas(float[] vertices, float[] textures, int FLAG, int resourceId, Context context,int[] hTex) {
        this.hTex = hTex;
        this.FLAG = FLAG;
        this.context = context;
        id = counter;
        counter++;
        pVertex = ByteBuffer.allocateDirect(4 * vertices.length).order(ByteOrder.nativeOrder()).asFloatBuffer();
        pVertex.put(vertices);
        pVertex.position(0);
        pTexCoord = ByteBuffer.allocateDirect(4 * textures.length).order(ByteOrder.nativeOrder()).asFloatBuffer();
        pTexCoord.put(textures);
        pTexCoord.position(0);
        initTex(resourceId);
        if(FLAG == CAMERA)
            hProgram = loadShader(vss,fss_ext);
        else
            hProgram = loadShader(vss,fss_int);


    }
    private void initTex(int resourceId) {
        GLES20.glGenTextures(1, hTex, id);
        if(FLAG == NON_CAMERA) {
            // Bind to the texture in OpenGL
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, hTex[id]);
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;   // No pre-scaling
            // Set filtering
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options);
            // Load the bitmap into the bound texture.
            if (bitmap != null){
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            // Recycle the bitmap, since its data has been loaded into OpenGL.
            bitmap.recycle();
        }
        }
        else {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[id]);

            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;   // No pre-scaling

            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        }
    }
    private static int loadShader ( String vss, String fss ) {
        int vshader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vshader, vss);
        GLES20.glCompileShader(vshader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Shader", "Could not compile vshader");
            Log.v("Shader", "Could not compile vshader:"+GLES20.glGetShaderInfoLog(vshader));
            GLES20.glDeleteShader(vshader);
            vshader = 0;
        }

        int fshader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fshader, fss);
        GLES20.glCompileShader(fshader);
        GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Shader", "Could not compile fshader");
            Log.v("Shader", "Could not compile fshader:"+GLES20.glGetShaderInfoLog(fshader));
            GLES20.glDeleteShader(fshader);
            fshader = 0;
        }

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vshader);
        GLES20.glAttachShader(program, fshader);
        GLES20.glLinkProgram(program);

        return program;
    }
    //TODO add PVertex change
    public void setTexture(Bitmap bitmap,float[] vertices){

        if(FLAG == NON_CAMERA){
            pVertex.put(vertices);
            pVertex.position(0);
            Log.d("Set Texture", "bitmap");
            queueBmp = bitmap;
            requireUpdate = true;
    }
    }

    public void draw(float[] mMVPMatrix){
        if(requireUpdate == true){
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, hTex[id]);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, queueBmp, 0);
            queueBmp.recycle();
            requireUpdate = false;
        }
        GLES20.glUseProgram(hProgram);
//        Log.d("GL","thread");
        int ph = GLES20.glGetAttribLocation(hProgram, "vPosition");
        int tch = GLES20.glGetAttribLocation ( hProgram, "vTexCoord" );
        int th = GLES20.glGetUniformLocation(hProgram, "sTexture");
        int mMVPMatrixHandle = GLES20.glGetUniformLocation(hProgram, "uMVPMatrix");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + id);
        if(FLAG == CAMERA)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex[id]);
        else
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, hTex[id]);
        GLES20.glUniform1i(th, 0);
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        GLES20.glVertexAttribPointer(ph, 2, GLES20.GL_FLOAT, false, 4 * 3, pVertex);
        GLES20.glVertexAttribPointer(tch, 2, GLES20.GL_FLOAT, false, 4 * 2, pTexCoord);
        GLES20.glEnableVertexAttribArray(ph);
        GLES20.glEnableVertexAttribArray(tch);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(ph);
        GLES20.glDisableVertexAttribArray(tch);
        GLES20.glDisableVertexAttribArray(th);
    }
    public void deleteTex() {
        GLES20.glDeleteTextures(1, hTex, 0);
    }
    public int[] getTexturePos(){
        return hTex;
    }

}
