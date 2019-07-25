
ROOT_LIMAX_PATH := ../..
SRC_LIMAX_PATH := $(ROOT_LIMAX_PATH)/source

FILE_LIST := $(notdir $(wildcard $(LOCAL_PATH)/$(SRC_LIMAX_PATH)/*.cpp))
FILE_LIST += $(notdir $(wildcard $(LOCAL_PATH)/$(SRC_LIMAX_PATH)/*.c))
$(foreach FILENAME, $(FILE_LIST), $(eval LOCAL_SRC_FILES += $$(SRC_LIMAX_PATH)/$$(FILENAME)))

LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(ROOT_LIMAX_PATH)/include
LOCAL_CFLAGS := -Wall -Wno-unknown-pragmas -std=c99
LOCAL_CPPFLAGS := -Wall -Wno-unknown-pragmas -std=c++11
LOCAL_CPP_FEATURES := exceptions
