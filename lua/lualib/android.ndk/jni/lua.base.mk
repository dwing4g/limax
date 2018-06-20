LOCAL_MODULE := lua
SRC_LUA_PATH := ../../src

CORE_FILE_LIST := lapi.c lcode.c lctype.c ldebug.c ldo.c ldump.c lfunc.c lgc.c llex.c \
	lmem.c lobject.c lopcodes.c lparser.c lstate.c lstring.c ltable.c \
	ltm.c lundump.c lvm.c lzio.c
LIB_FILE_LIST := lauxlib.c lbaselib.c lbitlib.c lcorolib.c ldblib.c liolib.c \
	lmathlib.c loslib.c lstrlib.c ltablib.c loadlib.c linit.c lutf8lib.c ljsonlib.c
FILE_LIST := $(CORE_FILE_LIST) $(LIB_FILE_LIST)

$(foreach FILENAME, $(FILE_LIST), $(eval LOCAL_SRC_FILES += $$(SRC_LUA_PATH)/$$(FILENAME)))

LOCAL_CFLAGS := -Wall -Dlua_getlocaledecpoint\(\)=46 -Wno-maybe-uninitialized
