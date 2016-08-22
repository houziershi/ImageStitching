//
// Created by Kunat Pipatanakul on 8/22/16.
//

#ifndef IMAGESTITCHING_WARPER_H
#define IMAGESTITCHING_WARPER_H


#include <math.h>
#include <opencv2/core/types_c.h>

class warper {

    inline
    void mapForward(float x, float y, float &u, float &v)
    {
        float x_ = r_kinv[0] * x + r_kinv[1] * y + r_kinv[2];
        float y_ = r_kinv[3] * x + r_kinv[4] * y + r_kinv[5];
        float z_ = r_kinv[6] * x + r_kinv[7] * y + r_kinv[8];

        u = scale * atan2f(x_, z_);
        float w = y_ / sqrtf(x_ * x_ + y_ * y_ + z_ * z_);
        v = scale * (static_cast<float>(CV_PI) - acosf(w == w ? w : 0));
    }
    inline
    void mapBackward(float u, float v, float &x, float &y)
    {
        u /= scale;
        v /= scale;

        float sinv = sinf(static_cast<float>(CV_PI) - v);
        float x_ = sinv * sinf(u);
        float y_ = cosf(static_cast<float>(CV_PI) - v);
        float z_ = sinv * cosf(u);

        float z;
        x = k_rinv[0] * x_ + k_rinv[1] * y_ + k_rinv[2] * z_;
        y = k_rinv[3] * x_ + k_rinv[4] * y_ + k_rinv[5] * z_;
        z = k_rinv[6] * x_ + k_rinv[7] * y_ + k_rinv[8] * z_;

        if (z > 0) { x /= z; y /= z; }
        else x = y = -1;
    }

    float scale;
    float r_kinv[9];
    float k_rinv[9];
    
};


#endif //IMAGESTITCHING_WARPER_H
