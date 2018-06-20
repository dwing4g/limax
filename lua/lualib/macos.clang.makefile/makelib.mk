
CXX = clang++
CXXFLAGS = -std=c++11 -stdlib=libc++ -W -Wall -Wno-unused-parameter -Wno-unused-function -Wno-unknown-pragmas -Wno-tautological-compare -arch $(ARCHFLAG)

ifeq ($(debug), true)
CXXFLAGS += -g3 -ggdb -DDEBUG -DLIMAX_DEBUG
else
CXXFLAGS += -O3 -DNDEBUG
endif

ROOT_PATH_LIMAX = ../../../cpp/limax
INC_LIMAX = $(ROOT_PATH_LIMAX)/include
SRC_LIMAXLUA = ../clibs
CXXFLAGS += -I $(INC_LIMAX)
FRAMEWORKS = -isysroot $(SDKPATH)

ifeq ($(PLATFLAG), macos)
LD_VERSION_MIN = -macosx_version_min 10.6
else
LD_VERSION_MIN = -ios_version_min 6.0
CXXFLAGS += -miphoneos-version-min=6.0
endif

ifeq ($(dynamic), true)
LIBFLAGS = -dylib $(LD_VERSION_MIN) -lc -lc++ -lcurses -syslibroot $(SDKPATH) -arch $(ARCHFLAG) $(DESTLUALIB).$(PLATFLAG).$(ARCHFLAG).$(LIB_EXT) $(ROOT_PATH_LIMAX)/macos.clang.makefile/liblimax.$(PLATFLAG).$(ARCHFLAG).$(LIB_EXT)
CXXFLAGS += -dynamic
else
LIBFLAGS = -static -arch_only $(ARCHFLAG)
endif

DESTLIB = $(DESTLIMAXLUALIB).$(PLATFLAG).$(ARCHFLAG).$(LIB_EXT)
SRCCPPS = $(wildcard $(SRC_LIMAXLUA)/*.cpp)
OBJS = $(patsubst %.cpp,%.$(PLATFLAG).$(ARCHFLAG).o, $(SRCCPPS)) 

$(DESTLIB) : $(OBJS)
	$(LIBTOOL) $(LIBFLAGS) -o $@ $^

%.$(PLATFLAG).$(ARCHFLAG).o : %.cpp
	$(CXX) $(CXXFLAGS) $(FRAMEWORKS) -c -o $@ $<

clean : 
	$(RM) $(OBJS) $(DESTLIB)


