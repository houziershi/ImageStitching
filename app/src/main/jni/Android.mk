LOCAL_PATH  := $(call my-dir)
OPENCV_PATH := /Users/kunato/Downloads/OpenCV-2.4.10-android-sdk/sdk/native/jni/

include $(CLEAR_VARS)
LOCAL_MODULE    := stitching
LOCAL_SRC_FILES := ../lib/libnonfree.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
OPENCV_INSTALL_MODULES := on
OPENCV_CAMERA_MODULES  := off
include $(OPENCV_PATH)/OpenCV.mk

LOCAL_C_INCLUDES :=				\
	$(LOCAL_PATH)				\
	$(OPENCV_PATH)/include

LOCAL_SRC_FILES :=				\
	stitching.cpp
	
LOCAL_MODULE := nonfree_stitching
LOCAL_CFLAGS := -Werror -O3 -ffast-math
LOCAL_LDLIBS := -llog -ldl
LOCAL_SHARED_LIBRARIES += nonfree

include $(BUILD_SHARED_LIBRARY)
