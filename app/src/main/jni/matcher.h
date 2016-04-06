//
// Created by Kunat Pipatanakul on 4/5/16.
//

#ifndef IMAGESTITCHING_MATCHER_H
#define IMAGESTITCHING_MATCHER_H
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
#include "opencv2/core/core.hpp"
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
#include "opencv2/calib3d/calib3d.hpp"
using namespace std;
using namespace cv;
using namespace cv::detail;
namespace matcher {

    void create(float match_conf, int num_matches_thresh1, int num_matches_thresh2);
    void match(const vector<ImageFeatures> &features, vector<MatchesInfo> &pairwise_matches);
    void match(const vector<ImageFeatures> &features, vector<MatchesInfo> &pairwise_matches,int newIndex);
    void match(const ImageFeatures &features1, const ImageFeatures &features2,
               MatchesInfo &matches_info);
    void collectGarbage();
    cv::Mat findHomography( InputArray _points1, InputArray _points2,
                            int method, double ransacReprojThreshold, OutputArray _mask );
    cv::Mat findHomography( InputArray _points1, InputArray _points2,int method);
}
#endif //IMAGESTITCHING_MATCHER_H
