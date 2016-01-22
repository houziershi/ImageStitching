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
    public static List<float[]> verts = new ArrayList<float[]>();
    public static List<float[]> textures = new ArrayList<float[]>();
    public static void readAll(Context context){
        readVertices(context, "sphere_vertices.txt");
        readTextures(context, "sphere_texture.txt");
    }
    public static void readVertices(Context context, String filename){
        AssetManager assetManager = context.getAssets();
        try {
            InputStream ims = assetManager.open(filename);
            BufferedReader br = new BufferedReader(new InputStreamReader(ims));
            String strLine;
            while ((strLine = br.readLine()) != null) {
                if(strLine.contains("//") || strLine.equals("")){
                    continue;
                }
                String[] strSet = strLine.split(",");
                float[] vert = new float[3];
                for(int i = 0; i < 3 ;i++){
                    vert[i] = Float.parseFloat(strSet[i]);
                }
                verts.add(vert);
            }
        } catch (IOException e) {
            Log.e("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }
    public static void readTextures(Context context, String filename){
        AssetManager assetManager = context.getAssets();
        try {
            InputStream ims = assetManager.open(filename);
            BufferedReader br = new BufferedReader(new InputStreamReader(ims));
            String strLine;
            while ((strLine = br.readLine()) != null) {
                if(strLine.contains("//") || strLine.equals("")){
                    continue;
                }
                String[] strSet = strLine.split(",");
                float[] cord = new float[2];
                //x
                cord[0] = 1f - Float.parseFloat(strSet[0]);
                //y
                cord[1] = Float.parseFloat(strSet[1]);
                textures.add(cord);
            }
        } catch (IOException e) {
            Log.e("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

}
