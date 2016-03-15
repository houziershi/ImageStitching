#ifndef IMAGESTITCHING_STITCHING_H
#define IMAGESTITCHING_STITCHING_H

#include <android/log.h>
#include <jni.h>
#include <vector>
#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include <math.h>
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

#define M_PI 3.14159265358979323846

double work_scale = 0.4, seam_scale = 0.2, compose_scale = 1.0;
double tracking_scale = 0.2;
string result_name = "/mnt/sdcard/result.png";
int blend_type = Blender::NO;
#define TAG "NATIVE_DEBUG"
#define GL_HEIGHT 1731
#define GL_WIDTH 1080

struct ImagePackage{
    String name;
    Mat image;
    Mat full_image;
    Mat rotation;
    Size size;
    Size compose_size;
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
float focal_divider = 3.45;
int work_width = 0;
int work_height = 0;
vector<ImagePackage> images;
Mat stitching_descriptor;
std::vector<KeyPoint> stitiching_keypoint;
vector<vector<Point2f>> p2d;
vector<vector<Point3f>> p3d;
//No need to re-done
Ptr<Feature2D> detector = Algorithm::create<Feature2D>("Feature2D.SURF");
Ptr<DescriptorMatcher> matcher = DescriptorMatcher::create("BruteForce");
int detector_setup = 1;
extern "C" {

JNIEXPORT void JNICALL Java_com_kunato_imagestitching_ImageStitchingNative_nativeHomography(JNIEnv*, jobject, jlong imgaddr,jlong glRotAddr,jlong glProjAddr,jlong retaddr);
JNIEXPORT void JNICALL Java_com_kunato_imagestitching_ImageStitchingNative_nativeAddStitch(JNIEnv*, jobject, jlong imgaddr,jlong rotaddr);
JNIEXPORT int JNICALL Java_com_kunato_imagestitching_ImageStitchingNative_nativeStitch(JNIEnv*, jobject,jlong retAddr,jlong areaAddr);


void tracking(jlong imgaddr,jlong glrotaddr,jlong glprojaddr,jlong retaddr);

void findDescriptor(Mat img,std::vector<KeyPoint> &keypoints ,Mat &descriptor);
inline Point3f calc3DPosition(Point2f keyPoint,float multiply_aspect);
inline int glhProjectf(float objx, float objy, float objz, float *modelview, float *projection, int *viewport, float *windowCoordinate);


void printMatrix(Mat mat,string text);
}

using namespace std;
using namespace cv;
using namespace cv::detail;

#endif