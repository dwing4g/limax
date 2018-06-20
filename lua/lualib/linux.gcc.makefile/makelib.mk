
CXX = g++
CXXFLAGS = -std=c++0x -W -Wall -Wno-unused-parameter -Wno-unknown-pragmas

ifeq ($(debug), true)
CXXFLAGS += -g3 -ggdb -DDEBUG -DLIMAX_DEBUG
else
CXXFLAGS += -O3 -DNDEBUG
endif

ifeq ($(shared), true)
CXXFLAGS += -fPIC
LDLIB = g++
LDFLAGS = -shared -lpthread -lcurses -L$(LIBLIMAX_PATH) -llimax -L. -llua -o
DESTLIB = liblimaxlua.so
else
LDLIB = ar
LDFLAGS = -ru -o
DESTLIB = liblimaxlua.a
endif

INC_LIMAX = ../../../cpp/limax/include
SRC_CLIBS = ../clibs

CXXFLAGS += -I $(INC_LIMAX)

CPPS_CLIBS = $(wildcard $(SRC_CLIBS)/*.cpp)
OBJS_CLIBS = $(patsubst %.cpp,%.o,$(CPPS_CLIBS))
	
$(DESTLIB) : $(OBJS_CLIBS)
	$(LDLIB) $(LDFLAGS) $@ $^

all: clean $(DESTLIB)

%.o : %.cpp
	$(CXX) $(CXXFLAGS) -c -o $@ $<

clean:
	$(RM) $(OBJS_CLIBS) $(DESTLIB)
