package com.kunato.imagestitching;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by kunato on 1/14/16 AD.
 */
public class ObjReader {
    public static final int COORD_PER_VERTEX = 3;
    public static final int COORD_PER_TEXTURE = 2;
    public static List<float[]> mVertices = new ArrayList<float[]>();
    public static List<float[]> mTextures = new ArrayList<float[]>();
    public static void readAll(Context context){
        readVertices(context, "iso_sphere_vertices.txt");
        readTextures(context, "iso_sphere_texture.txt");
    }

    public static void readVertices(Context context, String filename){
        AssetManager assetManager = context.getAssets();
        try {
            InputStream inputStream = assetManager.open(filename);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String strLine;
            while ((strLine = bufferedReader.readLine()) != null) {
                if(strLine.contains("//") || strLine.equals("")){
                    continue;
                }
                String[] strSet = strLine.split(",");
                float[] vertex = new float[3];
                for(int i = 0; i < 3 ;i++){
                    vertex[i] = Float.parseFloat(strSet[i]);
                }
                mVertices.add(vertex);
            }
        } catch (IOException e) {
            Log.e("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }
    public static void readTextures(Context context, String filename){
        AssetManager assetManager = context.getAssets();
        try {
            InputStream inputStream = assetManager.open(filename);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String strLine;
            while ((strLine = bufferedReader.readLine()) != null) {
                if(strLine.contains("//") || strLine.equals("")){
                    continue;
                }
                String[] strSet = strLine.split(",");
                float[] texture = new float[2];
                //x
                texture[0] = 1f - Float.parseFloat(strSet[0]);
                //y
                texture[1] = Float.parseFloat(strSet[1]);
                mTextures.add(texture);
            }
        } catch (IOException e) {
            Log.e("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

}
