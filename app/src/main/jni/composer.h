//
// Created by Kunat Pipatanakul on 3/24/16.
//

#ifndef IMAGESTITCHING_COMPOSER_H
#define IMAGESTITCHING_COMPOSER_H


#include <android/log.h>
#include <jni.h>
#include <vector>
#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include <math.h>
#include <float.h>
#include <ctime>
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


using namespace std;
using namespace cv;
using namespace cv::detail;
namespace composer{

    void prepare(Rect dst_roi);
    void feed(Mat img,Mat mask,Point tl);
    void process(Mat &dst,Mat &dst_mask);

}
#endif //IMAGESTITCHING_COMPOSER_H
