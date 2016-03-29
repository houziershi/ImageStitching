//
// Created by Kunat Pipatanakul on 3/24/16.
//

#include "composer.h"
using namespace std;
using namespace cv;
using namespace cv::detail;


namespace composer{
    Mat dst_, dst_mask_,dst_dt_;
    Rect dst_roi_;
    void prepare(Rect dst_roi){
        __android_log_print(ANDROID_LOG_DEBUG,"Composer","Prepare");
        dst_.create(dst_roi.size(), CV_8UC3);
        dst_.setTo(Scalar::all(0));
        dst_mask_.create(dst_roi.size(), CV_8U);
        dst_mask_.setTo(Scalar::all(0));
        dst_roi_ = dst_roi;
        dst_dt_.create(dst_roi.size(),CV_32F);
        __android_log_print(ANDROID_LOG_DEBUG,"Composer","Prepared");
    }

    void feed(Mat img,Mat mask,Point tl){
        CV_Assert(img.type() == CV_8UC3);
        CV_Assert(mask.type() == CV_8U);
        int dx = tl.x - dst_roi_.x;
        int dy = tl.y - dst_roi_.y;

        for (int y = 0; y < img.rows; ++y)
        {
            const Vec3b *src_row = img.ptr<Vec3b>(y);
            Vec3b *dst_row = dst_.ptr<Vec3b>(dy + y);
            const uchar *mask_row = mask.ptr<uchar>(y);
            uchar *dst_mask_row = dst_mask_.ptr<uchar>(dy + y);

            for (int x = 0; x < img.cols; ++x)
            {
                if (mask_row[x])
                    dst_row[dx + x] = src_row[x];
                dst_mask_row[dx + x] |= mask_row[x];

            }
        }

        __android_log_print(ANDROID_LOG_DEBUG,"Composer","Feed");
    }

    void process(Mat &dst,Mat &dst_mask){

        __android_log_print(ANDROID_LOG_DEBUG,"Composer","Process");
        dst_.setTo(Scalar::all(0), dst_mask_ == 0);
        int channels_setting[] = {2,0, 1,1, 0,2, 3,3};
        dst.create(dst_.size(),CV_8UC4);
        Mat out[] = {dst_,dst_mask_};
        __android_log_print(ANDROID_LOG_DEBUG,"Depth","%d %d %d",dst.depth(),dst_.depth(),dst_mask_.depth());
        mixChannels(out,2,&dst,1,channels_setting,4);

        for(int x = 0 ; x < dst_roi_.size().height; x++){

            for(int y = 0 ; y < dst_roi_.size().width ; y++){
//                calc distance transform (replace 0.1)
                dst_dt_.at<float>(x,y) = 0.1;
            }
        }
//        dst = dst_;

        dst_mask = dst_mask_;
        dst_dt_.release();
        dst_.release();
        dst_mask_.release();
    }
}


