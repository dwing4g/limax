
CC = gcc
CFLAGS = -Wall -DLUA_COMPAT_ALL -DLUA_USE_LINUX -Wno-maybe-uninitialized 
LDFLAGS = 
LIBS = -lm -Wl,-E -ldl -lreadline -lpthread

ifeq ($(debug), true)
CFLAGS += -g3 -ggdb
else
CFLAGS += -O3
endif

ifeq ($(shared), true)
CFLAGS += -fPIC
LDLIB = g++
LDLIBFLAGS = -shared $(LIBS) -o
DESTLUALIB = liblua.so
else
LDLIB = ar 
LDLIBFLAGS = -ru -o
DESTLUALIB = liblua.a
endif

LUA_SRC = ../src

CORE_OBJ = lapi.o lcode.o lctype.o ldebug.o ldo.o ldump.o lfunc.o lgc.o llex.o \
	lmem.o lobject.o lopcodes.o lparser.o lstate.o lstring.o ltable.o \
	ltm.o lundump.o lvm.o lzio.o
LIB_OBJ =	lauxlib.o lbaselib.o lbitlib.o lcorolib.o ldblib.o liolib.o \
	lmathlib.o loslib.o lstrlib.o ltablib.o loadlib.o linit.o lutf8lib.o ljsonlib.o
BASE_OBJ = $(CORE_OBJ) $(LIB_OBJ)

LUA_APP =	lua
LUA_OBJ =	lua.o

LUAC_APP = luac
LUAC_OBJ = luac.o

default : $(DESTLUALIB)

all : clean $(DESTLUALIB)

luas : $(DESTLUALIB) lua luac 

$(DESTLUALIB) : $(BASE_OBJ)
	$(LDLIB) $(LDLIBFLAGS) $@ $^

$(LUA_APP) : $(LUA_OBJ) $(DESTLUALIB)
	$(CC) $(LIBS) -o $@ $^

$(LUAC_APP) : $(LUAC_OBJ) $(BASE_OBJ)
	$(CC) $(LIBS) -o $@ $^

clean:
	$(RM) $(DESTLUALIB) $(LUAC_APP) $(LUA_APP) $(BASE_OBJ) $(LUA_OBJ) $(LUAC_OBJ)

%.o : $(LUA_SRC)/%.c
	$(CC) $(CFLAGS) -c -o $@ $^
	
