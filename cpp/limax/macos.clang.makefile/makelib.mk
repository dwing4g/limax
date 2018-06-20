
CXX = clang++
CCC = clang
CXXFLAGS = -std=c++11 -stdlib=libc++
CCCFLAGS = 
CCOMMONFLAGS = -W -Wall -Wno-unused-parameter -Wno-unused-function -Wno-unknown-pragmas -arch $(ARCHFLAG)

ifeq ($(debug), true)
CCOMMONFLAGS += -g3 -ggdb -DDEBUG -DLIMAX_DEBUG
else
CCOMMONFLAGS += -O3 -DNDEBUG
endif

INC_LIMAX = ../include
SRC_LIMAX = ../source
CXXFLAGS += -I $(INC_LIMAX)
FRAMEWORKS = -isysroot $(SDKPATH)

ifeq ($(ARCHFLAG), x86_64)
CCOMMONFLAGS += -maes
endif

ifeq ($(ARCHFLAG), i386)
CCOMMONFLAGS += -maes
endif

ifeq ($(PLATFLAG), macos)
LD_VERSION_MIN = -macosx_version_min 10.6
else
LD_VERSION_MIN = -ios_version_min 6.0 
CCOMMONFLAGS += -miphoneos-version-min=6.0
endif

ifeq ($(dynamic), true)
LIBFLAGS = -dylib $(LD_VERSION_MIN) -lc -lc++ -syslibroot $(SDKPATH) -arch $(ARCHFLAG)
CCOMMONFLAGS += -dynamic
else
LIBFLAGS = -static -arch_only $(ARCHFLAG)
endif

CXXFLAGS += $(CCOMMONFLAGS)
CCCFLAGS += $(CCOMMONFLAGS)

DESTLIB = $(DESTLIMAXLIB).$(PLATFLAG).$(ARCHFLAG).$(LIB_EXT)
SRCCPPS = $(wildcard $(SRC_LIMAX)/*.cpp)
SRCCCCS = $(wildcard $(SRC_LIMAX)/*.c)
OBJS = $(patsubst %.cpp,%.$(PLATFLAG).$(ARCHFLAG).o, $(SRCCPPS)) $(patsubst %.c,%.$(PLATFLAG).$(ARCHFLAG).o, $(SRCCCCS))

$(DESTLIB) : $(OBJS)
	$(LIBTOOL) $(LIBFLAGS) -o $@ $^

%.$(PLATFLAG).$(ARCHFLAG).o : %.cpp
	$(CXX) $(CXXFLAGS) $(FRAMEWORKS) -c -o $@ $<

%.$(PLATFLAG).$(ARCHFLAG).o : %.c
	$(CCC) $(CCCFLAGS) $(FRAMEWORKS) -c -o $@ $<

clean : 
	$(RM) $(OBJS) $(DESTLIB)


