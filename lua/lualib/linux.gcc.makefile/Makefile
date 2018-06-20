
LIBLIMAX_PATH = ../../../cpp/limax/linux.gcc.makefile

MKFLAGS = 
LUALIBFLAGS = LIBLIMAX_PATH=$(LIBLIMAX_PATH)

ifeq ($(debug), true)
MKFLAGS += debug=true
endif

ifeq ($(shared), true)
MKFLAGS += shared=true
endif

default :
	make -C $(LIBLIMAX_PATH) $(MKFLAGS)
	make -f makelua.mk $(MKFLAGS)
	make -f makelib.mk $(MKFLAGS) $(LUALIBFLAGS)

clean :
	make -C $(LIBLIMAX_PATH) $(MKFLAGS) clean
	make -f makelua.mk $(MKFLAGS) clean
	make -f makelib.mk $(MKFLAGS) clean

all :
	make -C $(LIBLIMAX_PATH) $(MKFLAGS) all
	make -f makelua.mk $(MKFLAGS) all
	make -f makelib.mk $(MKFLAGS) $(LUALIBFLAGS) all
	
luas :
	make -f makelua.mk $(MKFLAGS) luas
	
