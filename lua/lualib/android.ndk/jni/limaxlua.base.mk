
LOCAL_MODULE := limaxlua
SRC_LIMAXLUA_PATH := ../../clibs

FILE_LIST := $(notdir $(wildcard $(LOCAL_PATH)/$(SRC_LIMAXLUA_PATH)/*.cpp))
$(foreach FILENAME, $(FILE_LIST), $(eval LOCAL_SRC_FILES += $$(SRC_LIMAXLUA_PATH)/$$(FILENAME)))

LOCAL_C_INCLUDES := $(LOCAL_PATH)/../../../../cpp/limax/include
LOCAL_CPPFLAGS := -Wall -Wno-unknown-pragmas
LOCAL_CPP_FEATURES := exceptions

ifeq ($(shared), true) 
	LOCAL_SHARED_LIBRARIES := limax lua
endif
