/*M///////////////////////////////////////////////////////////////////////////////////////
//
//  IMPORTANT: READ BEFORE DOWNLOADING, COPYING, INSTALLING OR USING.
//
//  By downloading, copying, installing or using the software you agree to this license.
//  If you do not agree to this license, do not download, install,
//  copy or use the software.
//
//
//                          License Agreement
//                For Open Source Computer Vision Library
//
// Copyright (C) 2000-2008, Intel Corporation, all rights reserved.
// Copyright (C) 2009, Willow Garage Inc., all rights reserved.
// Third party copyrights are property of their respective owners.
//
// Redistribution and use in source and binary forms, with or without modification,
// are permitted provided that the following conditions are met:
//
//   * Redistribution's of source code must retain the above copyright notice,
//     this list of conditions and the following disclaimer.
//
//   * Redistribution's in binary form must reproduce the above copyright notice,
//     this list of conditions and the following disclaimer in the documentation
//     and/or other materials provided with the distribution.
//
//   * The name of the copyright holders may not be used to endorse or promote products
//     derived from this software without specific prior written permission.
//
// This software is provided by the copyright holders and contributors "as is" and
// any express or implied warranties, including, but not limited to, the implied
// warranties of merchantability and fitness for a particular purpose are disclaimed.
// In no event shall the Intel Corporation or contributors be liable for any direct,
// indirect, incidental, special, exemplary, or consequential damages
// (including, but not limited to, procurement of substitute goods or services;
// loss of use, data, or profits; or business interruption) however caused
// and on any theory of liability, whether in contract, strict liability,
// or tort (including negligence or otherwise) arising in any way out of
// the use of this software, even if advised of the possibility of such damage.
//
//
//M*/
#include <android/log.h>
#include <jni.h>
#include <vector>
#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include "opencv2/opencv_modules.hpp"
#include "opencv2/highgui/highgui.hpp"
#include "opencv2/stitching/detail/autocalib.hpp"
#include "opencv2/stitching/detail/blenders.hpp"
#include "opencv2/stitching/detail/camera.hpp"
#include "opencv2/stitching/detail/exposure_compensate.hpp"
#include "opencv2/stitching/detail/matchers.hpp"
#include "opencv2/stitching/detail/motion_estimators.hpp"
#include "opencv2/stitching/detail/seam_finders.hpp"
#include "opencv2/stitching/detail/util.hpp"
#include "opencv2/stitching/detail/warpers.hpp"
#include "opencv2/stitching/warpers.hpp"
#include "opencv2/nonfree/features2d.hpp"
#include "BundleCeres.h"

using namespace std;
using namespace cv;
using namespace cv::detail;
# define M_PI           3.14159265358979323846

double work_scale = 0.4, seam_scale = 0.2, compose_scale = 1.0;
// Default command line args

float conf_thresh = 1.f;
bool save_graph = false;
std::string save_graph_to;
string warp_type = "spherical";
int expos_comp_type = ExposureCompensator::GAIN_BLOCKS;
float match_conf = 0.3f;
string seam_find_type = "gc_color";
float blend_strength = 5;
string result_name = "/mnt/sdcard/result.png";
int blend_type = Blender::NO;
#define TAG "NATIVE_DEBUG"

extern "C" {

void printMatrix(Mat mat,string text);


struct ImagePackage{
    String name;
    Mat image;
    Mat full_image;
    Mat rotation;
    Size size;
    Size full_size;
    Point corner;
    Point seam_corner;
    Mat mask_warped;
    Mat img_warped;
    bool newfile;
    ImageFeatures feature;
};
vector<ImagePackage> images;

//No need to re-done
void findWarpForSeam(float warped_image_scale,float seam_scale,float work_scale, vector<ImagePackage> &p_img,vector<Mat> &images_warped,vector<Mat> &masks_warped,vector<Point> &corners,vector<CameraParams> cameras){

    double seam_work_aspect = seam_scale/work_scale;
    Ptr<WarperCreator> warper_creator = new cv::SphericalWarper();
//    Ptr<WarperCreator> warper_creator = new cv::CylindricalWarper();
    Ptr<RotationWarper> warper = warper_creator->create(warped_image_scale*seam_work_aspect);

    for(int i = 0; i < p_img.size(); i++){
        Mat_<float> K;
        Mat images_warped_f;
        Mat masks;
        masks.create(p_img[i].image.size(), CV_8U);
        masks.setTo(Scalar::all(255));

        cameras[i].K().convertTo(K, CV_32F);
        float swa = (float)seam_work_aspect;
        K(0,0) *= swa; K(0,2) *= swa; K(1,1) *= swa; K(1,2) *= swa;
        p_img[i].corner = warper->warp(p_img[i].image, K, cameras[i].R, INTER_LINEAR, BORDER_REFLECT, images_warped[i]);
        p_img[i].size = images_warped[i].size();
        corners[i] = p_img[i].corner;
        p_img[i].seam_corner = p_img[i].corner;
        warper->warp(masks, K, cameras[i].R, INTER_NEAREST, BORDER_CONSTANT, masks_warped[i]);
        images_warped[i].convertTo(images_warped_f, CV_32F);
        images_warped[i] = images_warped_f.clone();
        p_img[i].mask_warped = masks_warped[i];
        p_img[i].img_warped = images_warped[i];
        masks.release();
    }
}
//need to re-done in some part
void doComposition(float warped_image_scale,vector<CameraParams> cameras,vector<ImagePackage> p_img,Ptr<ExposureCompensator> compensator,float work_scale,float compose_scale,int blend_type,Mat &result){
    double compose_work_aspect = compose_scale / work_scale;
    Mat img_warped, img_warped_s;
    Mat dilated_mask, seam_mask, mask, mask_warped;
    vector<Mat> masks_warped(p_img.size());
    vector<Point> corners(p_img.size());
    Ptr<Blender> blender;
    warped_image_scale =  warped_image_scale * compose_work_aspect;
//    Ptr<WarperCreator> warper_creator = new cv::CylindricalWarper();
    Ptr<WarperCreator> warper_creator = new cv::SphericalWarper();
    Ptr<RotationWarper> warper = warper_creator->create(warped_image_scale);
    for (int i = 0; i < p_img.size(); ++i)
    {
        masks_warped[i] = p_img[i].mask_warped;
        cameras[i].focal *= compose_work_aspect;
        cameras[i].ppx *= compose_work_aspect;
        cameras[i].ppy *= compose_work_aspect;
        Size sz = p_img[i].full_size;
        if (std::abs(compose_scale - 1) > 1e-1)
        {
            sz.width = cvRound(p_img[i].full_size.width * compose_scale);
            sz.height = cvRound(p_img[i].full_size.height * compose_scale);
        }

        Mat K;
        cameras[i].K().convertTo(K, CV_32F);
        Rect roi = warper->warpRoi(sz, K, cameras[i].R);
        p_img[i].corner = roi.tl();
        p_img[i].size = roi.size();
    }

    for (int img_idx = 0; img_idx < p_img.size(); img_idx++)
    {
        Mat img,full_img;
        cout <<"Compositing image #" << p_img[img_idx].name << endl;
        full_img = p_img[img_idx].full_image;
        if (compose_scale - 1 < 0)
            resize(full_img, img, Size(), compose_scale, compose_scale);
        else
            img = full_img;
        Size img_size = img.size();
        __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Size of Image %d %d", img_size.width,img_size.height);
        Mat K;

        mask.create(img_size, CV_8U);
        mask.setTo(Scalar::all(255));
        cameras[img_idx].K().convertTo(K, CV_32F);
        warper->warp(img, K, cameras[img_idx].R, INTER_LINEAR, BORDER_REFLECT, img_warped);
        warper->warp(mask, K, cameras[img_idx].R, INTER_NEAREST, BORDER_CONSTANT, mask_warped);
        img_warped.convertTo(img_warped_s, CV_16S);
        img_warped.release();
        img.release();
        mask.release();
        dilate(masks_warped[img_idx], dilated_mask, Mat());
        resize(dilated_mask, seam_mask, mask_warped.size());
        mask_warped = seam_mask & mask_warped;

        if (!blender)
        {
            vector<Size> sizes(p_img.size());

            for(int j = 0; j < p_img.size();j++){
                corners[j] = p_img[j].corner;
                sizes[j] = p_img[j].size;
            }
            blender = Blender::createDefault(blend_type, false);

            //(w,h),(w,h)???//final 1 before bundle
//            Rect full(-(3406/2),-(3406/4),3406,3406/2);//Cylinder
            int width = cameras[0].focal * M_PI * 2;
            int offset = (cameras[0].focal / cameras[0].aspect)/2;//??1.18 at 1.73 aspect???
            int height = ((cameras[0].focal / cameras[0].aspect) * M_PI);//??? 1280
            Rect full(-(width/2),0,width,height);//Sphere(scale=0.2)
//            blender->prepare(corners, sizes);

            Rect r = resultRoi(corners,sizes);
            __android_log_print(ANDROID_LOG_DEBUG,"TAG","Rect r size (%d,%d) (%d,%d)",r.x,r.y,r.width,r.height);
            __android_log_print(ANDROID_LOG_DEBUG,"TAG","Rect full size (%d,%d) (%d,%d)",full.x,full.y,full.width,full.height);
//            blender->prepare(r);
            blender->prepare(full);
        }
        blender->feed(img_warped_s, mask_warped, corners[img_idx]);

    }
    Mat result_mask;
    blender->blend(result, result_mask);




}

JNIEXPORT void JNICALL Java_com_kunato_imagestitching_ImageStitchingNative_nativeAddStitch(JNIEnv*, jobject, jlong imgaddr,jlong rotaddr);
JNIEXPORT void JNICALL Java_com_kunato_imagestitching_ImageStitchingNative_nativeAddStitch(JNIEnv*, jobject, jlong imgaddr,jlong rotaddr){
//    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Add Images");
    ImagePackage imagePackage;
    Mat& full_img  = *(Mat*)imgaddr;
    Point2f center(full_img.cols/2.0F,full_img.rows/2.0F);
    Mat rot_mat = getRotationMatrix2D(center, 90, 1.0);
    Mat dst;
    warpAffine(full_img, dst, rot_mat, full_img.size());
    full_img = dst;
    Mat& rot = *(Mat*)rotaddr;
    imagePackage.rotation = rot;
    imagePackage.full_size = dst.size();
    imagePackage.full_image = dst;
    ImageFeatures feature;
    Mat img;
    resize(full_img, img, Size(), work_scale, work_scale);

    Ptr<Feature2D> surf = Algorithm::create<Feature2D>("Feature2D.SURF");
    surf->set("hessianThreshold", 300);
    surf->set("nOctaves", 3);
    surf->set("nOctaveLayers", 4);

    std::vector<KeyPoint> keypoints;
    Mat descriptors;
    Mat grayImg;
    cvtColor(img,grayImg,CV_BGR2GRAY);
    (*surf)(grayImg , Mat(), feature.keypoints, descriptors, false);
    feature.descriptors = descriptors.reshape(1, (int)keypoints.size());

    feature.img_idx = images.size();
    resize(full_img, img, Size(), seam_scale, seam_scale);
    feature.img_size = img.size();

//    __android_log_print(ANDROID_LOG_VERBOSE,"Feature","SURF Keypoints Size %d",feature.keypoints.size());
//    __android_log_print(ANDROID_LOG_VERBOSE,"Feature","SURF Size (%d %d)",feature.descriptors.cols,feature.descriptors.rows);
//    Ptr<FeaturesFinder> finder = new OrbFeaturesFinder();
//    (*finder)(img, feature);
    __android_log_print(ANDROID_LOG_VERBOSE,"Feature","Keypoints Size %d",feature.keypoints.size());
    __android_log_print(ANDROID_LOG_VERBOSE,"Feature","Size (%d %d)",feature.descriptors.cols,feature.descriptors.rows);
//    printMatrix(feature.descriptors,"feature");
    imagePackage.image = img.clone();
    imagePackage.size = img.size();
    imagePackage.feature = feature;
//    finder->collectGarbage();
    images.push_back(imagePackage);
//    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Number of Image %d", images.size());

}


JNIEXPORT int JNICALL Java_com_kunato_imagestitching_ImageStitchingNative_nativeStitch(JNIEnv*, jobject,jlong retAddr);
JNIEXPORT int JNICALL Java_com_kunato_imagestitching_ImageStitchingNative_nativeStitch(JNIEnv*, jobject,jlong retAddr){
    Mat& result = *(Mat*)retAddr;
    int num_images = static_cast<int>(images.size());
    if(num_images < 2){
        return 0;
    }

    bool is_work_scale_set = false, is_seam_scale_set = false, is_compose_scale_set = false;


//    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Pairwise matches %d", 5);
    vector<MatchesInfo> pairwise_matches;
    BestOf2NearestMatcher matcher(false, match_conf);
    vector<ImageFeatures> features(num_images);
    for(int i = 0; i < num_images; i++){
        features[i] = images[i].feature;
    }
//    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Run matcher %d", 5);
    matcher(features, pairwise_matches);
    matcher.collectGarbage();
//    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Run estimator %d", 5);
    vector<CameraParams> cameras;
    for(int i = 0; i < num_images;i++){

        __android_log_print(ANDROID_LOG_VERBOSE,"Native Size","Input Image Size : %d,%d ",images[i].size.height,images[i].size.width);
        CameraParams camera;
        camera.ppx = images[i].size.width/2.0 * work_scale/seam_scale;
        camera.ppy = images[i].size.height/2.0 * work_scale/seam_scale;
//        camera.ppx = 268;
//        camera.ppy = 5.16;
//        camera.aspect = 4/3.0;
        camera.aspect = 1;//??? change to 1(1920/1080??=1.77)
        camera.focal = (images[i].size.height * 4.7 / 5.2) * work_scale/seam_scale;
//        camera.focal = 981;
        camera.R = images[i].rotation;
        camera.t = Mat::zeros(3,1,CV_32F);
        cameras.push_back(camera);
        __android_log_print(ANDROID_LOG_VERBOSE,"CameraParam","focal %lf , ppx %lf , ppy %lf",camera.focal,camera.ppx,camera.ppy);
    }
    //Implement BundleAdjustment
//    Ptr<detail::BundleAdjusterBase> adjuster = new detail::BundleAdjusterRay();
//    (*adjuster)(features, pairwise_matches, cameras);
//    doingBundle(features,pairwise_matches,cameras);
    vector<double> focals;
    for (size_t i = 0; i < cameras.size(); ++i)
    {
        printMatrix(cameras[i].R,"after");
        focals.push_back(cameras[i].focal);
    }

    sort(focals.begin(), focals.end());
    float warped_image_scale;
    if (focals.size() % 2 == 1)
        warped_image_scale = static_cast<float>(focals[focals.size() / 2]);
    else
        warped_image_scale = static_cast<float>(focals[focals.size() / 2 - 1] + focals[focals.size() / 2]) * 0.5f;
    __android_log_print(ANDROID_LOG_VERBOSE,"Warped_image_scale","(%lf)",warped_image_scale);
//    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Prepare Warp Image %d", 5);

    vector<Mat> masks_warped(num_images);
    vector<Mat> images_warped(num_images);
    vector<Point> corners(num_images);
//    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Do Warp %d", 0);
    findWarpForSeam(warped_image_scale,seam_scale,work_scale,images,images_warped,masks_warped,corners,cameras);

    Ptr<SeamFinder> seam_finder =  new detail::GraphCutSeamFinder(GraphCutSeamFinderBase::COST_COLOR);
//    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Do SeamFinder %d", 0);
    seam_finder->find(images_warped, corners, masks_warped);
//    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Do Composition %d", 0);
    Mat out;
    doComposition(warped_image_scale,cameras,images,nullptr,work_scale,compose_scale,blend_type,out);
    __android_log_print(ANDROID_LOG_ERROR,TAG,"Compositioned %d Images",num_images);
    out.convertTo(result,CV_8UC3);
    return 0;
}
void printMatrix(Mat mat,string text){
    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Matrix %s############################", text.c_str());
    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Matrix [%f %f %f]", mat.at<float>(0,0),mat.at<float>(0,1),mat.at<float>(0,2));
    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Matrix [%f %f %f]", mat.at<float>(1,0),mat.at<float>(1,1),mat.at<float>(1,2));
    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Matrix [%f %f %f]", mat.at<float>(2,0),mat.at<float>(2,1),mat.at<float>(2,2));
    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Matrix ##############################");
}
}
