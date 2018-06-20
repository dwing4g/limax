
CXX = g++
CCC = gcc
CXXFLAGS = -std=c++11 -W -Wall -Wno-unused-parameter -Wno-unknown-pragmas -maes -Wl,--no-as-needed
CCCFLAGS = -W -Wall -Wno-unused-parameter -Wl,--no-as-needed
LDAPP = g++
LDAPPFLAGS = -lpthread -Wl,--no-as-needed

ifeq ($(debug), true)
CXXFLAGS += -DDEBUG -DLIMAX_DEBUG
CCCFLAGS += -DDEBUG -DLIMAX_DEBUG
DEF_OPT = -g3 -ggdb
else
CXXFLAGS += -DNDEBUG
CCCFLAGS += -DNDEBUG
DEF_OPT = -O3
endif

ifeq ($(arch), 32)
	ifeq ($(debug), true)
	DEF_OPT = -g3 -ggdb 
	else
	DEF_OPT = -O2
	endif
ARCHFLAG = -m32 #-march=i386 -mtune=i686
LDAPPFLAGS += -latomic
endif

ifeq ($(arch), 64)
ARCHFLAG = -m64 #-march=x86-64 -mtune=generic
endif

CXXFLAGS += $(DEF_OPT) $(ARCHFLAG)
CCCFLAGS += $(DEF_OPT) $(ARCHFLAG)
LDAPPFLAGS += $(ARCHFLAG)

ifeq ($(shared), true)
CXXFLAGS += -fPIC
CCCFLAGS += -fPIC
LDLIB = g++
LDLIBFLAGS = -shared -lpthread -Wl,--no-as-needed -o $(ARCHFLAG)
DESTLIMAXLIB = liblimax.so
else
LDLIB = ar
LDLIBFLAGS = -ru -o
DESTLIMAXLIB = liblimax.a
endif

INC_LIMAX = ../include
SRC_LIMAX = ../source

CXXFLAGS += -I $(INC_LIMAX)

CPPS_LIMAX = $(wildcard $(SRC_LIMAX)/*.cpp)
CCCS_LIMAX = $(wildcard $(SRC_LIMAX)/*.c)
OBJS_LIMAX = $(patsubst %.cpp,%.o,$(CPPS_LIMAX)) $(patsubst %.c,%.o,$(CCCS_LIMAX))

$(DESTLIMAXLIB) : $(OBJS_LIMAX)
	$(LDLIB) $(LDLIBFLAGS) $@ $^

all: clean $(DESTLIMAXLIB)

%.o : %.cpp
	$(CXX) $(CXXFLAGS) -c -o $@ $<

%.o : %.c
	$(CCC) $(CCCFLAGS) -c -o $@ $<

clean:
	$(RM) $(OBJS_LIMAX) $(DESTLIMAXLIB)
