
CCC = clang
CCCFLAGS = -W -Wall -Wno-unused-parameter -Wno-unused-function -Wno-deprecated-declarations -arch $(ARCHFLAG) -DLUA_COMPAT_ALL -DLUA_USE_MACOSX
LDAPP = clang
LDAPPFLAGS =

ifeq ($(debug), true)
CCCFLAGS += -g3 -ggdb -DDEBUG -DNETP_DEBUG
else
CCCFLAGS += -O3 -DNDEBUG
endif

FRAMEWORKS = -isysroot $(SDKPATH)

ifeq ($(PLATFLAG), macos)
LD_VERSION_MIN = -macosx_version_min 10.6
else
LD_VERSION_MIN = -ios_version_min 6.0
CCCFLAGS += -miphoneos-version-min=6.0
endif

ifeq ($(dynamic), true)
LIBFLAGS = -dylib $(LD_VERSION_MIN) -lc -lc++ -syslibroot $(SDKPATH) -arch $(ARCHFLAG)
CCCFLAGS += -dynamic
else
LIBFLAGS = -static -arch_only $(ARCHFLAG)
endif

DESTLIB = $(DESTLUALIB).$(PLATFLAG).$(ARCHFLAG).$(LIB_EXT)
LUA_SRC = ../src
CORE_SRC = lapi.c lcode.c lctype.c ldebug.c ldo.c ldump.c lfunc.c lgc.c llex.c \
        lmem.c lobject.c lopcodes.c lparser.c lstate.c lstring.c ltable.c \
        ltm.c lundump.c lvm.c lzio.c
LIB_SRC = lauxlib.c lbaselib.c lbitlib.c lcorolib.c ldblib.c liolib.c \
        lmathlib.c loslib.c lstrlib.c ltablib.c loadlib.c linit.c lutf8lib.c ljsonlib.c
BASE_SRC = $(CORE_SRC) $(LIB_SRC)

LUA_APP = lua
LUA_OBJ = $(LUA_SRC)/lua.o
LUAC_APP = luac
LUAC_OBJ = $(LUA_SRC)/luac.o
LUAS_ITEMS = $(LUA_APP) $(LUA_OBJ) $(LUAC_APP) $(LUAC_OBJ)

OBJS_NAMES = $(patsubst %.c,%.$(PLATFLAG).$(ARCHFLAG).o, $(BASE_SRC))
OBJS = 
$(foreach FILENAME, $(OBJS_NAMES), $(eval OBJS += $(LUA_SRC)/$(FILENAME)))

$(DESTLIB) : $(OBJS)
	$(LIBTOOL) $(LIBFLAGS) -o $@ $^

%.$(PLATFLAG).$(ARCHFLAG).o : %.c
	$(CCC) $(CCCFLAGS) $(FRAMEWORKS) -c -o $@ $<

$(LUA_APP) : $(LUA_OBJ) $(DESTLIB)
	$(LDAPP) $(LDAPPFLAGS) -o $@ $^

$(LUAC_APP) : $(LUAC_OBJ) $(OBJS)
	$(LDAPP) $(LDAPPFLAGS) -o $@ $^
        
luas : $(LUA_APP) $(LUAC_APP)

clean : 
	$(RM) $(OBJS) $(DESTLIB) $(LUAS_ITEMS)


