//
// Created by Kunat Pipatanakul on 3/31/16.
//

#include "util.h"
Mat multiply(Mat a,Mat b){
    int m = a.cols;
    int y = b.cols;
    int x = b.rows;
    Mat out(m,y,a.type());
    for(int i = 0; i < y ; i++) {
        for (int j = 0; j < x ; j++) {
            float temp = 0;
            for (int k = 0; k < m; k++) {
                temp = temp + (a.at<float>(i, k) * b.at<float>(k, j));
            }
            out.at<float>(i, j) = temp;
        }
    }
    return out;
}
void remap(Mat src,Mat &dst,Mat xmap,Mat ymap){
    dst = Mat(xmap.size(),CV_8UC3);
    for(int i = 0 ; i < xmap.rows;i++){
        for(int j = 0 ; j < xmap.cols; j++){
            dst.at<Vec3b>(i,j) = src.at<Vec3b>(ymap.at<float>(i,j),xmap.at<float>(i,j));
        }
    }
    __android_log_print(ANDROID_LOG_INFO,"C++ Remap","First %f %f",ymap.at<float>(0,0),xmap.at<float>(0,0));

}


