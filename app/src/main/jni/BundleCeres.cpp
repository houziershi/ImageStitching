//
// Created by Kunat Pipatanakul on 1/26/16 AD.
//

#include "BundleCeres.h"
/*
 * BundleCeres.cpp
 *
 *  Created on: Nov 18, 2558 BE
 *      Author: kunato
 */
#include <android/log.h>
#include "BundleCeres.h"
#include "ceres/ceres.h"
#include "ceres/rotation.h"
#include "Eigen/Dense"
#include "Eigen/Geometry"

using namespace cv;
using namespace std;
using namespace ceres;
typedef Eigen::Matrix<double,3,3> Matrix3d;
typedef Eigen::Matrix<double,3,1> Vector3d;
void printMatrix(Mat mat,string text){
    __android_log_print(ANDROID_LOG_VERBOSE, "Bundle", "Matrix %s############################", text.c_str());
    __android_log_print(ANDROID_LOG_VERBOSE, "Bundle", "Matrix [%f %f %f]", mat.at<float>(0,0),mat.at<float>(0,1),mat.at<float>(0,2));
    __android_log_print(ANDROID_LOG_VERBOSE, "Bundle", "Matrix [%f %f %f]", mat.at<float>(1,0),mat.at<float>(1,1),mat.at<float>(1,2));
    __android_log_print(ANDROID_LOG_VERBOSE, "Bundle", "Matrix [%f %f %f]", mat.at<float>(2,0),mat.at<float>(2,1),mat.at<float>(2,2));
    __android_log_print(ANDROID_LOG_VERBOSE, "Bundle", "Matrix ##############################");
}
struct ReprojectionErrorData
{
    vector<Point2f> points;
    vector<int> matches_image_idx;
};
struct CostFunctor {
    template <typename T>
    bool operator()(const T* const x, T* residual) const {
        residual[0] = T(10.0) - x[0];
        return true;
    }
};
struct ReprojectionError {
    ReprojectionError(double x1,double y1 , double x2, double y2,double *K1,double *K2)
            : x1(x1), y1(y1) , x2(x2) , y2(y2) , K1(K1), K2(K2){}
    bool operator()(const double* rvec1,const double* rvec2,const double *f1 ,const double *f2,double* residuals) const {
        double R1[9];
        double R2[9];
        Matrix3d eigen_R1;
        Matrix3d eigen_R2;
        Matrix3d eigen_H1;
        Matrix3d eigen_H2;
        Matrix3d eigen_KMat1;
        Matrix3d eigen_KMat2;
        Matrix3d eigen_KInv1;
        Matrix3d eigen_KInv2;
        eigen_KMat1 << *f1, 0.0, *(K1+2),
                0.0, *f1, *(K1+5),
                0.0,  0.0, 1.0;
        eigen_KMat2 << *f2, 0.0, *(K2+2),
                0.0, *f2, *(K2+5),
                0.0,  0.0, 1.0;
        ceres::AngleAxisToRotationMatrix(rvec1,R1);
        ceres::AngleAxisToRotationMatrix(rvec2,R2);
        //order of matrix R
        // [ 0 3 6
        // 	 1 4 7
        //	 2 5 8 ]
        eigen_R1 << R1[0],R1[3],R1[6],R1[1],R1[4],R1[7],R1[2],R1[5],R1[8];
        eigen_R2 << R2[0],R2[3],R2[6],R2[1],R2[4],R2[7],R2[2],R2[5],R2[8];


        eigen_KInv1 = eigen_KMat1.inverse();
        eigen_KInv2 = eigen_KMat2.inverse();
        //R * K^t(K^-1) = H
        //H*[x,y,1]
        //Then find difference between x,y,z
        eigen_H1 = eigen_R1 * eigen_KInv1;
        eigen_H2 = eigen_R2 * eigen_KInv2;
        // THIS IS FOR not using eigen lib.
        //		for(int i = 0 ; i < 3 ; i ++){
        //			for(int j = 0 ; j < 3 ;j ++){
        //				double sum1(0);
        //				double sum2(0);
        //				for(int k = 0 ; k < 3 ; k++){
        //					//					sum1+=(R1[i+k*3]*K1[k*3+j]);
        //					//					sum2+=(R2[i+k*3]*K2[k*3+j]);
        //					sum1+=(R1[i+k*3]*K_inv1(k,j));
        //					sum2+=(R2[i+k*3]*K_inv2(k,j));
        //				}
        //				H2[i*3+j] = sum2;
        //				H1[i*3+j] = sum1;
        //			}
        //		}
        //		double dx1 = H1[0]*x1 + H1[1]*y1 + H1[2];
        //		double dy1 = H1[3]*x1 + H1[4]*y1 + H1[5];
        //		double dz1 = H1[6]*x1 + H1[7]*y1 + H1[8];
        //		double dx2 = H2[0]*x2 + H2[1]*y2 + H2[2];
        //		double dy2 = H2[3]*x2 + H2[4]*y2 + H2[5];
        //		double dz2 = H2[6]*x2 + H2[7]*y2 + H2[8];
        double dx1 = eigen_H1(0,0) * x1 + eigen_H1(0,1)*y1 + eigen_H1(0,2);
        double dy1 = eigen_H1(1,0) * x1 + eigen_H1(1,1)*y1 + eigen_H1(1,2);
        double dz1 = eigen_H1(2,0) * x1 + eigen_H1(2,1)*y1 + eigen_H1(2,2);
        double len1 = std::sqrt(dx1*dx1 + dy1*dy1 + dz1*dz1);
        dx1/=len1; dy1/=len1; dz1/=len1;
        double dx2 = eigen_H2(0,0) * x2 + eigen_H2(0,1)*y2 + eigen_H2(0,2);
        double dy2 = eigen_H2(1,0) * x2 + eigen_H2(1,1)*y2 + eigen_H2(1,2);
        double dz2 = eigen_H2(2,0) * x2 + eigen_H2(2,1)*y2 + eigen_H2(2,2);
        double len2 = std::sqrt(dx2*dx2 + dy2*dy2 + dz2*dz2);
        dx2/=len2; dy2/=len2; dz2/=len2;
        double multiply = 1;
        double ddx = multiply * (dx1-dx2);
        double ddy = multiply * (dy1-dy2);
        double ddz = multiply * (dz1-dz2);
        residuals[0] = ddx;
        residuals[1] = ddy;
        residuals[2] = ddz;
        return true;
    }
    // Factory to hide the construction of the CostFunction object from
    // the client code.
    static ceres::CostFunction* Create(
            const double x1,
            const double y1,
            const double x2,
            const double y2,
            double *K1,
            double *K2) {
        return (new ceres::NumericDiffCostFunction<ReprojectionError,ceres::CENTRAL, 3, 3, 3, 1, 1>(
                new ReprojectionError(x1,y1,x2,y2,K1,K2)));
    }

    double x1;
    double y1;
    double x2;
    double y2;
    double *K1;
    double *K2;
};

void test() {

    // The variable to solve for with its initial value.
    double initial_x = 5.0;
    double x = initial_x;

    // Build the problem.
    Problem problem;

    // Set up the only cost function (also known as residual). This uses
    // auto-differentiation to obtain the derivative (jacobian).
    CostFunction* cost_function =
            new AutoDiffCostFunction<CostFunctor, 1, 1>(new CostFunctor);
    problem.AddResidualBlock(cost_function, NULL, &x);

    // Run the solver!
    Solver::Options options;
    options.linear_solver_type = ceres::DENSE_SCHUR;
//    options.linear_solver_type = ceres::DENSE_QR;
//    options.minimizer_progress_to_stdout = true;
    Solver::Summary summary;

    Solve(options, &problem , &summary);

    std::cout << summary.BriefReport() << "\n";
    std::cout << "x : " << initial_x
    << " -> " << x << "\n";
}
//Need to re-done
//TODO WORK ?
void doingBundle(vector<ImageFeatures> features,vector<MatchesInfo> pairs,vector<CameraParams> &cameras){

    vector<ReprojectionErrorData> rpSet;
    cout << "point set" << endl;
    for(int i = 0 ; i < pairs.size() ;i++){
        if(pairs[i].confidence < 0.9)
            continue;
        for(int j = 0 ; j < pairs[i].matches.size() ; j++){
            if(!pairs[i].inliers_mask[j]){
                continue;
            }
            vector<Point2f> pointSet;
            vector<int> image_idx;
            pointSet.push_back(features[pairs[i].src_img_idx].keypoints[pairs[i].matches[j].queryIdx].pt);
            image_idx.push_back(pairs[i].src_img_idx);
            pointSet.push_back(features[pairs[i].dst_img_idx].keypoints[pairs[i].matches[j].trainIdx].pt);
            image_idx.push_back(pairs[i].dst_img_idx);
            ReprojectionErrorData rpData;
            rpData.points = pointSet;
            rpData.matches_image_idx = image_idx;
            rpSet.push_back(rpData);
        }
    }
    cout << "Ending add rpSet" <<endl;
    ceres::Problem problem;
    double focal_array[cameras.size()];
    double *focal_pointer = focal_array;
    double rotation_array[cameras.size()*3];
    double *rotation_pointer = rotation_array;
    double K_array[cameras.size()*9];
    double *K_pointer = K_array;
    for(int i = 0 ; i < cameras.size() ; i++){
        focal_array[i] = cameras[i].focal;
        Mat R = cameras[i].R;
        Mat rvec;
        Rodrigues(R,rvec);
        for(int j = 0 ; j < 3 ; j++){
            rotation_array[i*3+j] = rvec.at<float>(j);
        }
        Mat_<double> K = Mat::eye(3, 3, CV_64F);
        K(0,0) = focal_array[i]; K(0,2) = features[i].img_size.width * 0.5;
        K(1,1) = focal_array[i]; K(1,2) = features[i].img_size.height * 0.5;
        //		K = K.inv();
        for(int j = 0 ; j < 9 ; j ++){
            K_array[i*9+j] = K.at<double>(j);
        }
    }
    cout << "Ending create rotation array" << endl;
    cout << "Ending create K Matrix" << endl;
    for(int i = rpSet.size() -1 ; i >= 0  ; i--){
        ceres::CostFunction* cost_func = ReprojectionError::Create(rpSet[i].points[0].x,rpSet[i].points[0].y,rpSet[i].points[1].x,rpSet[i].points[1].y,
                                                                   K_pointer + rpSet[i].matches_image_idx[0]*9,
                                                                   K_pointer + rpSet[i].matches_image_idx[1]*9);
        problem.AddResidualBlock(cost_func,NULL,rotation_pointer + rpSet[i].matches_image_idx[0]*3
                ,rotation_pointer + rpSet[i].matches_image_idx[1]*3, focal_pointer+ (rpSet[i].matches_image_idx[0]) ,focal_pointer + (rpSet[i].matches_image_idx[1]));
    }

    ceres::Solver::Options options;
    options.linear_solver_type = ceres::DENSE_SCHUR;
    options.minimizer_progress_to_stdout = true;
    ceres::Solver::Summary summary;
    ceres::Solve(options, &problem, &summary);
    std::cout << summary.FullReport() << "\n";
    for(int i = 0 ; i < cameras.size() ; i ++){
        Mat R;
        Mat rvec = Mat::zeros(3,1,CV_64F);
        for(int j = 0 ; j < 3 ; j++){
            rvec.at<double>(j) = rotation_array[i*3+j];
        }
        Rodrigues(rvec,R);
        R.convertTo(cameras[i].R,CV_32F);
        cameras[i].focal = focal_array[i];
    }
    Mat R_inv = cameras[0].R.inv();
    for(int i = 0 ; i < cameras.size() ;i++){
        cameras[i].R = R_inv * cameras[i].R;
    }

}



