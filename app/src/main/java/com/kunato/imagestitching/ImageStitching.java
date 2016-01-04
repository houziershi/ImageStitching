//package com.kunato.imagestitching;
//import android.util.Log;
//
//import org.bytedeco.javacpp.indexer.Indexer;
//import org.bytedeco.javacpp.opencv_core;
//import org.bytedeco.javacpp.opencv_stitching;
//import org.bytedeco.javacpp.opencv_xfeatures2d;
//
//import java.util.ArrayList;
//
///**
// * Created by kunato on 12/14/15 AD.
// */
//public class ImageStitching {
//    public static ImageStitching instance;
//    public int image_number;
//    private int blendType = opencv_stitching.Blender.NO;
//    private opencv_core.UMatVector masksWarped;
//    private opencv_core.UMatVector imagesWarped;
//    private opencv_core.PointVector points;
//    private opencv_core.SizeVector sizes;
//    private float work_scale = 1.0f;
//    private float compose_scale = 1.0f;
//    private float seam_scale = 1.0f;
//    private float focalMedian;
//    public static ImageStitching getInstance(){
//        if(instance == null){
//            instance = new ImageStitching();
//        }
//        return instance;
//    }
//    opencv_core.KeyPointVector keyPointVector;
//    opencv_core.Mat descriptor;
//    ArrayList<opencv_core.Mat> images = new ArrayList<opencv_core.Mat>();
//    ArrayList<opencv_stitching.ImageFeatures> imgFeatures = new ArrayList<opencv_stitching.ImageFeatures>();
//    ArrayList<opencv_stitching.MatchesInfo> matchesInfos = new ArrayList<opencv_stitching.MatchesInfo>();
//    ArrayList<opencv_stitching.CameraParams> cameras = new ArrayList<opencv_stitching.CameraParams>();
//    ArrayList<opencv_core.Mat> rotsMatrix = new ArrayList<opencv_core.Mat>();
//    public void findFeature(opencv_core.Mat image,opencv_core.Mat rotationMat){
//        keyPointVector = new opencv_core.KeyPointVector();
//        rotsMatrix.add(rotationMat);
//        opencv_xfeatures2d.SURF finder = opencv_xfeatures2d.SURF.create();
//        finder.detect(image, keyPointVector);
//        descriptor = new opencv_core.Mat();
//        opencv_core.MatExpr ones  = opencv_core.Mat.ones(image.rows(),image.cols(),opencv_core.CV_8U);
//        finder.detectAndCompute(image, ones.asMat(), keyPointVector, descriptor);
//        Log.d("ImgStitich", keyPointVector.size() + "");
//        opencv_stitching.ImageFeatures imageFeatures = new opencv_stitching.ImageFeatures();
//        imageFeatures.descriptors(descriptor.getUMat(opencv_core.ACCESS_READ));
//        imageFeatures.img_idx(image_number);
//        imageFeatures.keypoints(keyPointVector);
//        imgFeatures.add(imageFeatures);
//        image_number++;
//        images.add(image);
//    }
//    public int matchImage(){
//        if(imgFeatures.size() > 2){
//            Log.d("addToPano","matchImage");
//            opencv_stitching.BestOf2NearestRangeMatcher matcher = new opencv_stitching.BestOf2NearestRangeMatcher(5);
//            opencv_stitching.MatchesInfo matchesInfo = new opencv_stitching.MatchesInfo();
//            matcher.apply(imgFeatures.get(imgFeatures.size()-2),imgFeatures.get(imgFeatures.size()-1),matchesInfo);
//            matchesInfo.src_img_idx(imgFeatures.size()-2);
//            matchesInfo.dst_img_idx(imgFeatures.size()-1);
//        matchesInfos.add(matchesInfo);
//        }
//        return imgFeatures.size();
//    }
//    public void cameraEstimation(){
//        //cameras = new ArrayList<opencv_stitching.CameraParams>();
//        Log.d("addToPano","Matchinfo "+matchesInfos.size());
//        boolean first_pair = true;
//        //get from sensor(R) k from param(f-length)
////        for(int i = 0 ; i < matchesInfos.size() ;i++) {
////            if (matchesInfos.get(i).src_img_idx() != matchesInfos.get(i).dst_img_idx() - 1) {
////                Log.d("addToPano", matchesInfos.get(i).src_img_idx() + "," + matchesInfos.get(i).dst_img_idx());
////                continue;
////
////            }
////            double focal_from = imgFeatures.get(matchesInfos.get(i).src_img_idx()).img_size().width() * 4.7 / 5.2;
////            double focal_to = imgFeatures.get(matchesInfos.get(i).dst_img_idx()).img_size().width() * 4.7 / 5.2;
////            opencv_stitching.CameraParams camera = new opencv_stitching.CameraParams();
////            camera.ppx(imgFeatures.get(matchesInfos.get(i).dst_img_idx()).img_size().width() / 2.0);
////            camera.ppy(imgFeatures.get(matchesInfos.get(i).dst_img_idx()).img_size().height() / 2.0);
////            camera.aspect(1);
////            camera.focal(focal_to);
////            camera.t(opencv_core.Mat.zeros(3, 1, opencv_core.CV_64F).asMat());
////            opencv_core.Mat K_from = opencv_core.Mat.eye(3, 3, opencv_core.CV_64F).asMat();
////            opencv_core.Mat K_to = opencv_core.Mat.eye(3, 3, opencv_core.CV_64F).asMat();
////            Indexer K_fromIndexer = K_from.createIndexer();
////            Indexer K_toIndexer = K_to.createIndexer();
////            int[] kIndex = {0, 0};
////            K_fromIndexer.putDouble(kIndex, focal_from);
////            K_toIndexer.putDouble(kIndex, focal_to);
////            kIndex[0] = 1;
////            kIndex[1] = 1;
////            K_fromIndexer.putDouble(kIndex, focal_from);
////            K_toIndexer.putDouble(kIndex, focal_to);
////            opencv_core.Mat H = new opencv_core.Mat();
////            matchesInfos.get(i).H().convertTo(H, opencv_core.CV_64F);
//////            opencv_core.Mat temp = opencv_core.multiply(K_from.inv(),H.inv()).asMat();
//////            opencv_core.Mat R = opencv_core.multiply(temp,K_to).asMat();
////            //init matrix
////        }
//            int i = images.size()-1;
//            double focal = images.get(i).size().width()*4.7/5.2;
//            //edit this to Real R
//            opencv_core.Mat R = rotsMatrix.get(i);
//            Util.printMatrix(R,"loadR");
//            opencv_core.Mat rTemp = new opencv_core.Mat();
//            R.convertTo(rTemp, opencv_core.CV_32F);
//            opencv_stitching.CameraParams camera = new opencv_stitching.CameraParams();
//            camera.ppx(images.get(i).size().width() / 2.0);
//            camera.ppy(images.get(i).size().height() / 2.0);
//            camera.aspect(1);
//
//            camera.focal(focal);
//            camera.t(opencv_core.Mat.zeros(3, 1, opencv_core.CV_32F).asMat());
//            if(i == 0) {
//                R = opencv_core.Mat.eye(3, 3, opencv_core.CV_32F).asMat();
//            }
//            else
//                R = opencv_core.multiply(rTemp,cameras.get(i-1).R().inv()).asMat();
//            camera.R(R);
//            cameras.add(camera);
//
//
//
//    }
//    public void wrapForSeam(){
//
//        Log.d("addToPano","camera "+cameras.size()+"");
//        for(int i = 0 ; i < cameras.size(); i++){
//            Util.printMatrix(cameras.get(i).R(),"SeamR");
//        }
//        float seamWorkAspect = seam_scale/work_scale;
//        masksWarped = new opencv_core.UMatVector(images.size());
//        imagesWarped = new opencv_core.UMatVector(images.size());
//        points = new opencv_core.PointVector(images.size());
//        sizes = new opencv_core.SizeVector(images.size());
//        opencv_stitching.SphericalWarper sphericalWarper = new opencv_stitching.SphericalWarper();
////        return static_cast<float>(focals[focals.size() / 2]);
//        //edit here
//        double[] focals = new double[cameras.size()];
//        for(int i = 0; i < cameras.size() ;i++){
//            focals[i] = cameras.get(i).focal();
//        }
//        java.util.Arrays.sort(focals);
//        if (focals.length % 2 == 1)
//            focalMedian = (float) focals[focals.length / 2];
//        else
//            focalMedian = (float) (focals[focals.length / 2 - 1] + focals[focals.length / 2]) * 0.5f;
//        //ok
//        opencv_stitching.RotationWarper rotationWarper = sphericalWarper.create(focalMedian * seamWorkAspect);
//        opencv_core.Mat K = new opencv_core.Mat();
////        opencv_core.Mat K = opencv_core.Mat.eye(3,3,opencv_core.CV_32F).asMat();
////        opencv_core.Mat R = opencv_core.Mat.eye(3,3,opencv_core.CV_32F).asMat();
//        opencv_core.Mat imageWrapedTemp = new opencv_core.Mat();
//        for(int i = 0; i < images.size() ;i++){
//            opencv_core.Mat mask = new opencv_core.Mat(images.get(i).size(),opencv_core.CV_8U, opencv_core.Scalar.all(255));
//            opencv_core.Mat imageWarped = new opencv_core.Mat();
//            opencv_core.Mat maskWarped = new opencv_core.Mat();
//            //ok
//            cameras.get(i).K().convertTo(K, opencv_core.CV_32F);
//            Indexer indexer = K.createIndexer();
//            int[] matIndex = new int[2];
//            matIndex[0] = matIndex[1] = 0;
//            indexer.putDouble(matIndex,indexer.getDouble(matIndex)*seamWorkAspect);
//            matIndex[0] = 0; matIndex[1] = 2;
//            indexer.putDouble(matIndex,indexer.getDouble(matIndex)*seamWorkAspect);
//            matIndex[0] = 1; matIndex[1] = 1;
//            indexer.putDouble(matIndex,indexer.getDouble(matIndex)*seamWorkAspect);
//            matIndex[0] = 1; matIndex[1] = 2;
//            indexer.putDouble(matIndex, indexer.getDouble(matIndex) * seamWorkAspect);
//            for(int j =0 ; j < images.size();j++){
//                Util.printMatrix(cameras.get(i).K(),"K");
//            }
//            opencv_core.Mat addToPano = new opencv_core.Mat(images.get(i).size(),opencv_core.CV_8UC3, opencv_core.Scalar.all(255));
//            opencv_core.Point point = rotationWarper.warp(addToPano,K,cameras.get(i).R(),1,2,imageWarped);
//
//            opencv_core.Size wrapped_size = imageWarped.size();
//
//            Log.d("testp", point.x() + "," + point.y());
//            Log.d("testmasks", mask.size().width() + "," + mask.size().height());
//            rotationWarper.warp(mask, K, cameras.get(i).R(), 0, 0, maskWarped);
//
//            imageWarped.convertTo(imageWrapedTemp, opencv_core.CV_32F);
//            imageWarped = imageWrapedTemp.clone();
//            opencv_core.UMat UMaskWarped = new opencv_core.UMat();
//            UMaskWarped.setTo(maskWarped);
//            opencv_core.UMat UImageWarped = new opencv_core.UMat();
//            UImageWarped.setTo(imageWarped);
//            masksWarped.put(i, UMaskWarped);
//            imagesWarped.put(i, UImageWarped);
//            points.put(i, point);
//            sizes.put(i, imageWarped.size());
//            mask.release();
//        }
//        opencv_stitching.VoronoiSeamFinder seamFinder = new opencv_stitching.VoronoiSeamFinder();
//        seamFinder.find(imagesWarped, points, masksWarped);
//
//    }
//    public void doCompositing(){
//        Log.d("step","Composition");
//        float composeWorkAspect = compose_scale / work_scale;
//        opencv_core.Mat imageWarped = new opencv_core.Mat(),imgWarpedS = new opencv_core.Mat(),dilatedMask = new opencv_core.Mat(),
//                seamMask = new opencv_core.Mat(),mask,maskWarped = new opencv_core.Mat();
//        opencv_stitching.Blender blender = null;
//        opencv_stitching.WarperCreator warperCreator = new opencv_stitching.SphericalWarper();
//        float wrapImageScale =  focalMedian * composeWorkAspect;
////        float wrapImageScale = 1.0F;
//        opencv_stitching.RotationWarper rotationWarper = warperCreator.create(wrapImageScale);
//        for(int i = 0; i < images.size() ;i++){
//            opencv_stitching.CameraParams temp = cameras.get(i);
//
//            temp.focal(temp.focal() * composeWorkAspect);
//            temp.ppx(temp.ppx() * composeWorkAspect);
//            temp.ppy(temp.ppy() * composeWorkAspect);
//            opencv_core.Size szTemp = images.get(i).size();
//            if(Math.abs(compose_scale -1) > 1e-1){
//                szTemp.width(Math.round(images.get(i).size().width()*compose_scale));
//                szTemp.height(Math.round(images.get(i).size().height()*compose_scale));
//
//            }
//            opencv_core.Mat K = new opencv_core.Mat();
//            cameras.get(i).K().convertTo(K,opencv_core.CV_32F);
//            opencv_core.Rect roiTemp = rotationWarper.warpRoi(szTemp,K,cameras.get(i).R());
//            points.put(i,roiTemp.tl());
//            sizes.put(i,roiTemp.size());
//        }
//
//        for(int i = 0; i < images.size() ;i++){
//            Log.d("addToPano","We are on "+i);
//            opencv_core.Mat img = images.get(i);
//            opencv_core.Size imgSize = img.size();
//            opencv_core.Mat K = new opencv_core.Mat();
//            mask = new opencv_core.Mat(imgSize,opencv_core.CV_8U, opencv_core.Scalar.all(255.0));
//            cameras.get(i).K().convertTo(K, opencv_core.CV_32F);
//            rotationWarper.warp(img, K, cameras.get(i).R(), 1, 2, imageWarped);
//            rotationWarper.warp(mask, K, cameras.get(i).R(), 0, 0, maskWarped);
//            imageWarped.convertTo(imgWarpedS, opencv_core.CV_16S);
//            //opencv_imgproc.dilate(masksWarped.get(i).getMat(opencv_core.ACCESS_FAST), dilatedMask, new opencv_core.Mat());
//            opencv_core.Mat maskWarpedF = new opencv_core.Mat();
//
////            opencv_core.bitwise_and(dilatedMask, maskWarped, maskWarpedF);
////            maskWarped = maskWarpedF;
//            //??
//            Log.d("step","com2");
//            if(blender == null){
//                opencv_core.SizeVector sizes = new opencv_core.SizeVector(images.size());
////                get corners and size from seam step
//                blender = opencv_stitching.Blender.createDefault(opencv_stitching.Blender.NO);
//                opencv_core.Size dstSz = opencv_stitching.resultRoi(points,sizes).size();
//                float blendWidth = (float) Math.sqrt(dstSz.area()*5/100.0);
//                if(blendWidth < 1.f){
//                    blender = opencv_stitching.Blender.createDefault(opencv_stitching.Blender.NO);
//                }
////                else if(blendType == opencv_stitching.Blender.MULTI_BAND){
////                    ((opencv_stitching.MultiBandBlender)blender).setNumBands((int) Math.ceil(Math.log(blendWidth)/Math.log(2.0))-1);
////
////
////                }
//                blender.prepare(points,sizes);
//            }
//            Log.d("addToPano","feedingStart");
//            blender.feed(imgWarpedS,maskWarped,points.get(i));
//            Log.d("addToPano","feedingEnd");
//        }
//        opencv_core.Mat result = new opencv_core.Mat(),result_mask = new opencv_core.Mat();
//        blender.blend(result,result_mask);
//
//    }
//
//
//}
//
