LOCAL_PATH  := $(call my-dir)
OPENCV_PATH := /Users/kunato/android-opencv/sdk/native/jni/
CERES_PATH  := /Users/kunato/ceres-solver/
EIGEN_PATH  := /usr/local/include/eigen3/
INCLUDE_PATH:= /usr/local/include/
GLOG_PATH   := /Users/kunato/ceres-solver/internal/ceres/miniglog/

include $(CLEAR_VARS)
LOCAL_MODULE    := ceres
LOCAL_SRC_FILES := lib/libceres.a
include $(PREBUILT_STATIC_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE    := nonfree
LOCAL_SRC_FILES := lib/libnonfree.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
OPENCV_INSTALL_MODULES := on
OPENCV_CAMERA_MODULES  := off

include $(OPENCV_PATH)/OpenCV.mk

LOCAL_C_INCLUDES :=				\
	$(LOCAL_PATH)				\
	$(OPENCV_PATH)/include      \
	$(CERES_PATH)/include       \
	$(GLOG_PATH)                \
	$(EIGEN_PATH)

LOCAL_SRC_FILES :=				\
	stitching.h					\
	stitching.cpp               \
	BundleCeres.h               \
	BundleCeres.cpp				\
	composer.h					\
	composer.cpp

LOCAL_MODULE := nonfree_stitching
LOCAL_SHARED_LIBRARIES += nonfree
LOCAL_STATIC_LIBRARIES += ceres
LOCAL_CFLAGS := -O3 -ffast-math
LOCAL_LDLIBS := -llog -ldl
LOCAL_LDLIBS += -latomic

include $(BUILD_SHARED_LIBRARY)
