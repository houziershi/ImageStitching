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
# define M_PI 3.14159265358979323846

double work_scale = 0.4, seam_scale = 0.2, compose_scale = 1.0;
double tracking_scale = 0.2;
string result_name = "/mnt/sdcard/result.png";
int blend_type = Blender::NO;
#define TAG "NATIVE_DEBUG"

extern "C" {

JNIEXPORT void JNICALL Java_com_kunato_imagestitching_ImageStitchingNative_nativeHomography(JNIEnv*, jobject, jlong imgaddr,jlong rotaddr,jlong retaddr);
JNIEXPORT void JNICALL Java_com_kunato_imagestitching_ImageStitchingNative_nativeAddStitch(JNIEnv*, jobject, jlong imgaddr,jlong rotaddr);
JNIEXPORT int JNICALL Java_com_kunato_imagestitching_ImageStitchingNative_nativeStitch(JNIEnv*, jobject,jlong retAddr);



void printMatrix(Mat mat,string text);


struct ImagePackage{
	String name;
	Mat image;
	Mat full_image;
	Mat rotation;
	Size size;
	Size warped_size;
	Size full_size;
	Point corner;
	Point compose_corner;
	Mat mask_warped;
	Mat compose_mask_warped;
	Mat image_warped;
	Mat compose_image_warped;
	bool done = false;
	ImageFeatures feature;
};
vector<ImagePackage> images;
Mat stitching_descriptor;
std::vector<KeyPoint> stitiching_keypoint;
//No need to re-done
Ptr<Feature2D> detector = Algorithm::create<Feature2D>("Feature2D.SURF");
Ptr<DescriptorMatcher> matcher = DescriptorMatcher::create("BruteForce");
int detector_setup = 1;
void findDescriptor(Mat img,std::vector<KeyPoint> &keypoints ,Mat &descriptor){
	if(detector_setup){
		//for surf
		detector->set("hessianThreshold", 300);
		detector->set("nOctaves", 3);
		detector->set("nOctaveLayers", 4);
		detector_setup = 0;
	}
	Mat grayImg;
	cvtColor(img,grayImg,CV_BGR2GRAY);
	(*detector)(grayImg , Mat(), keypoints, descriptor, false);
	descriptor = descriptor.reshape(1, (int)keypoints.size());
}


JNIEXPORT void JNICALL Java_com_kunato_imagestitching_ImageStitchingNative_nativeHomography(JNIEnv*, jobject, jlong imgaddr,jlong rotaddr,jlong retaddr){
	__android_log_print(ANDROID_LOG_DEBUG,"Native","Native homography");
	Mat& full_img  = *(Mat*)imgaddr;
    Mat img;

	imwrite("/sdcard/stitch/tracking2.jpg",full_img);
    resize(full_img,img,Size(),tracking_scale,tracking_scale);
    Point2f center(img.cols/2.0F,img.rows/2.0F);
    Mat rot_mat = getRotationMatrix2D(center, 90, 1.0);
    Mat dst;
    warpAffine(img, dst, rot_mat, img.size());
    Mat& rot = *(Mat*)rotaddr;
	std::vector<KeyPoint> input_keypoint;
	Mat input_descriptor;
	findDescriptor(dst, input_keypoint, input_descriptor);
	vector<DMatch> matches;
	__android_log_print(ANDROID_LOG_ERROR,"Native","Descriptor cols %d,%d",stitching_descriptor.cols,input_descriptor.cols );
	__android_log_print(ANDROID_LOG_ERROR,"Native","Descriptor type %d,%d",stitching_descriptor.type(),input_descriptor.type() );
	(*matcher).match(input_descriptor, stitching_descriptor, matches);
	__android_log_print(ANDROID_LOG_DEBUG,"Native","Tracking,%d",matches.size());
	Mat& R = *(Mat*)retaddr;
	vector<Point2f> in_point;
	vector<Point2f> in2_point;
	__android_log_print(ANDROID_LOG_DEBUG,"Match_count","%d",matches.size());
	for(int i = 0 ; i < matches.size() ;i++){
		Point2f xy1 = input_keypoint[matches[i].queryIdx].pt;
		Point2f xy2 = stitiching_keypoint[matches[i].trainIdx].pt;
		__android_log_print(ANDROID_LOG_DEBUG,"HomoPointBF","(%f,%f) (%f,%f)",xy1.x,xy1.y,xy2.x,xy2.y);
//		xy1*=5.f;
//		xy2*=5.f;
		in_point.push_back(xy1);
		in2_point.push_back(xy2);
		__android_log_print(ANDROID_LOG_DEBUG,"HomoPointAT","(%f,%f) (%f,%f)",xy1.x,xy1.y,xy2.x,xy2.y);
	}
	Mat H = findHomography(in_point,in2_point,CV_RANSAC);
	CameraParams camera;
	camera.ppx = dst.size().width/2.0;
	camera.ppy = dst.size().height/2.0;
	camera.aspect = 1;//??? change to 1(1920/1080??=1.77)
	camera.focal = (dst.size().height * 4.7 / 4.8) * work_scale/seam_scale;
	R = camera.K().inv() * H.inv() * camera.K();
	printMatrix(H,"H_MAT");
	printMatrix(R,"R_MAT");



}





void findWarpForSeam(float warped_image_scale,float seam_scale,float work_scale, vector<ImagePackage> &p_img,vector<CameraParams> cameras){

	double seam_work_aspect = seam_scale/work_scale;
	Ptr<WarperCreator> warper_creator = new cv::SphericalWarper();
//    Ptr<WarperCreator> warper_creator = new cv::CylindricalWarper();
	Ptr<RotationWarper> warper = warper_creator->create(warped_image_scale*seam_work_aspect);

	for(int i = 0; i < p_img.size(); i++){
		if(p_img[i].done == true){
			continue;
		}
		Mat_<float> K;
		Mat image_warped;
		Mat mask;
		mask.create(p_img[i].image.size(), CV_8U);
		mask.setTo(Scalar::all(255));

		cameras[i].K().convertTo(K, CV_32F);
		float swa = (float)seam_work_aspect;
		K(0,0) *= swa; K(0,2) *= swa; K(1,1) *= swa; K(1,2) *= swa;
		p_img[i].corner = warper->warp(p_img[i].image, K, cameras[i].R, INTER_LINEAR, BORDER_REFLECT, image_warped);
		p_img[i].corner;
		warper->warp(mask, K, cameras[i].R, INTER_NEAREST, BORDER_CONSTANT, p_img[i].mask_warped);

		image_warped.convertTo(p_img[i].image_warped, CV_32F);
		mask.release();
	}
}
//need to re-done in some part
void doComposition(float warped_image_scale,vector<CameraParams> cameras,vector<ImagePackage> p_img,Ptr<ExposureCompensator> compensator,float work_scale,float compose_scale,int blend_type,Mat &result){
	double compose_work_aspect = compose_scale / work_scale;
	Mat img_warped;
	Mat dilated_mask, seam_mask, mask, mask_warped;
	Ptr<Blender> blender;
	warped_image_scale =  warped_image_scale * compose_work_aspect;
//    Ptr<WarperCreator> warper_creator = new cv::CylindricalWarper();
	Ptr<WarperCreator> warper_creator = new cv::SphericalWarper();
	Ptr<RotationWarper> warper = warper_creator->create(warped_image_scale);
	for (int i = 0; i < p_img.size(); ++i)
	{

		cameras[i].focal *= compose_work_aspect;
		cameras[i].ppx *= compose_work_aspect;
		cameras[i].ppy *= compose_work_aspect;
		if(p_img[i].done == true){
			continue;
		}
		Size sz = p_img[i].full_size;
		if (std::abs(compose_scale - 1) > 1e-1)
		{
			sz.width = cvRound(p_img[i].full_size.width * compose_scale);
			sz.height = cvRound(p_img[i].full_size.height * compose_scale);
		}

		Mat K;
		cameras[i].K().convertTo(K, CV_32F);
		Rect roi = warper->warpRoi(sz, K, cameras[i].R);
		p_img[i].compose_corner = roi.tl();
		p_img[i].warped_size = roi.size();
	}

	for (int i = 0; i < p_img.size(); i++)
	{
		if(p_img[i].done == true){

		}
		else {
			Mat img;
			cout << "Compositing image #" << p_img[i].name << endl;
			if (compose_scale - 1 < 0)
				resize(p_img[i].full_image, img, Size(), compose_scale, compose_scale);
			else
				img = p_img[i].full_image;

			Size img_size = img.size();
			Mat K;
			mask.create(img_size, CV_8U);
			mask.setTo(Scalar::all(255));
			//change K to cv32f ?
			cameras[i].K().convertTo(K, CV_32F);
			warper->warp(img, K, cameras[i].R, INTER_LINEAR, BORDER_REFLECT, img_warped);
			warper->warp(mask, K, cameras[i].R, INTER_NEAREST, BORDER_CONSTANT, mask_warped);
			img_warped.convertTo(p_img[i].compose_image_warped, CV_16S);
			img_warped.release();
			img.release();
			mask.release();

			dilate(p_img[i].mask_warped, dilated_mask, Mat());
			resize(dilated_mask, seam_mask, mask_warped.size());
			mask_warped = seam_mask & mask_warped;
			p_img[i].compose_mask_warped = mask_warped;
			p_img[i].done = true;
		}
		if (!blender)
		{
			blender = Blender::createDefault(blend_type, false);
			int width = warped_image_scale * M_PI * 2;
			int offset = (warped_image_scale / cameras[0].aspect)/2;//??1.18 at 1.73 aspect???
			int height = ((warped_image_scale / cameras[0].aspect) * M_PI);//??? 1280
			Rect full(-(width/2),0,width,height);//Sphere(scale=0.2)
			__android_log_print(ANDROID_LOG_DEBUG,"TAG","Rect full size (%d,%d) (%d,%d)",full.x,full.y,full.width,full.height);
			blender->prepare(full);
		}
		blender->feed(p_img[i].compose_image_warped, mask_warped, p_img[i].compose_corner);

	}
	Mat result_mask;
	blender->blend(result, result_mask);


}

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
	__android_log_print(ANDROID_LOG_DEBUG,"Native","Full Image Size: %d %d",dst.size().width,dst.size().height);
	resize(full_img, img, Size(), work_scale, work_scale);
//	detector->set("hessianThreshold", 300);
//	detector->set("nOctaves", 3);
//	detector->set("nOctaveLayers", 4);
	findDescriptor(img,feature.keypoints,feature.descriptors);
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
	imagePackage.image = img;
	imagePackage.size = img.size();
	imagePackage.feature = feature;
//    finder->collectGarbage();
	images.push_back(imagePackage);
//    __android_log_print(ANDROID_LOG_VERBOSE, TAG, "Number of Image %d", images.size());

}


JNIEXPORT int JNICALL Java_com_kunato_imagestitching_ImageStitchingNative_nativeStitch(JNIEnv*, jobject,jlong retAddr){
	Mat& result = *(Mat*)retAddr;
	int num_images = static_cast<int>(images.size());
	if(num_images < 2){
		return 0;
	}

	//do matcher
	vector<MatchesInfo> pairwise_matches;
	BestOf2NearestMatcher matcher(false, 0.3f);
	vector<ImageFeatures> features(num_images);
	for(int i = 0; i < num_images; i++){
		features[i] = images[i].feature;
	}
	matcher(features, pairwise_matches);
	matcher.collectGarbage();


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
		//camera.focal = (images[i].size.height * 4.7 / 5.2) * work_scale/seam_scale; 5.2 - > 10 = bigger
		//4.8 maybe better
		camera.focal = (images[i].size.height * 4.7 / 4.8) * work_scale/seam_scale;
//        camera.focal = 981;
		camera.R = images[i].rotation;
		camera.t = Mat::zeros(3,1,CV_32F);
		cameras.push_back(camera);
		__android_log_print(ANDROID_LOG_VERBOSE,"CameraParam","focal %lf , ppx %lf , ppy %lf",camera.focal,camera.ppx,camera.ppy);
	}


	//Implement BundleAdjustment
//    Ptr<detail::BundleAdjusterBase> adjuster = new detail::BundleAdjusterRay();
//    (*adjuster)(features, pairwise_matches, cameras);
	doingBundle(features,pairwise_matches,cameras);

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


	findWarpForSeam(warped_image_scale,seam_scale,work_scale,images,cameras);

	//Create vector of var because need to call seam_finder
	vector<Mat> masks_warped(num_images);
	vector<Mat> images_warped(num_images);
	vector<Point> corners(num_images);
	for(int i = 0; i < images.size();i++){
		corners[i] = images[i].corner;
		images_warped[i] = images[i].image_warped;
		masks_warped[i] = images[i].mask_warped;
	}
	Ptr<SeamFinder> seam_finder =  new detail::GraphCutSeamFinder(GraphCutSeamFinderBase::COST_COLOR);
	seam_finder->find(images_warped, corners, masks_warped);

	Mat out;
	doComposition(warped_image_scale,cameras,images,nullptr,work_scale,compose_scale,blend_type,out);
	__android_log_print(ANDROID_LOG_ERROR,TAG,"Compositioned %d Images",num_images);
	out.convertTo(result,CV_8UC3);
	Mat small;
	resize(result,small,Size(),tracking_scale,tracking_scale);
	findDescriptor(small, stitiching_keypoint, stitching_descriptor);
	__android_log_print(ANDROID_LOG_DEBUG,"Native","Save descriptor %d",stitching_descriptor.cols);
	return 0;
}
void printMatrix(Mat tmp,string text){
	Mat mat;
	if(mat.type() != CV_32F){
		tmp.convertTo(mat,CV_32F);
	}
	__android_log_print(ANDROID_LOG_VERBOSE, TAG, "Matrix %s############################", text.c_str());
	__android_log_print(ANDROID_LOG_VERBOSE, TAG, "Matrix [%f %f %f]", mat.at<float>(0,0),mat.at<float>(0,1),mat.at<float>(0,2));
	__android_log_print(ANDROID_LOG_VERBOSE, TAG, "Matrix [%f %f %f]", mat.at<float>(1,0),mat.at<float>(1,1),mat.at<float>(1,2));
	__android_log_print(ANDROID_LOG_VERBOSE, TAG, "Matrix [%f %f %f]", mat.at<float>(2,0),mat.at<float>(2,1),mat.at<float>(2,2));
	__android_log_print(ANDROID_LOG_VERBOSE, TAG, "Matrix ##############################");
}
}
