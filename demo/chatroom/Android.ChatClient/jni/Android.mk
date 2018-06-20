SAVED_LOCAL_PATH := $(call my-dir)

include $(SAVED_LOCAL_PATH)/../../../../cpp/limax/android.ndk/jni/limax.static.mk

include $(CLEAR_VARS)

LOCAL_PATH := $(SAVED_LOCAL_PATH)

LOCAL_MODULE    := ndkimpl
LOCAL_SRC_FILES := ndkimpl.cpp

LOCAL_CPP_FEATURES := exceptions
LOCAL_STATIC_LIBRARIES := limax
LOCAL_LDLIBS += -llog 

include $(BUILD_SHARED_LIBRARY)
