LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := limax
include $(LOCAL_PATH)/limax.base.mk
include $(BUILD_STATIC_LIBRARY)
