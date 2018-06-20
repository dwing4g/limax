
ROOT_LIMAX_PATH := ../..
SRC_LIMAX_PATH := $(ROOT_LIMAX_PATH)/source

FILE_LIST := $(notdir $(wildcard $(LOCAL_PATH)/$(SRC_LIMAX_PATH)/*.cpp))
FILE_LIST += $(notdir $(wildcard $(LOCAL_PATH)/$(SRC_LIMAX_PATH)/*.c))
$(foreach FILENAME, $(FILE_LIST), $(eval LOCAL_SRC_FILES += $$(SRC_LIMAX_PATH)/$$(FILENAME)))

LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(ROOT_LIMAX_PATH)/include
LOCAL_CPPFLAGS := -Wall -Wno-unknown-pragmas
LOCAL_CPP_FEATURES := exceptions


