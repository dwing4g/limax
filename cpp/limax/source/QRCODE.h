#pragma once

enum { ECL_M, ECL_L, ECL_H, ECL_Q };

struct tagQrCode;
typedef struct tagQrCode* QrCode;

struct tagQrCode {
	void *modules;
	int size;
	void(*release)(QrCode THIS);
	char*(*toSvgXML)(QrCode THIS);
	void*(*toByteArray)(QrCode THIS, int *size);
};
#ifdef __cplusplus
extern "C"
{
#endif

QrCode encode(void *data, int size, int ecl);
QrCode fromByteArray(void *array, int array_length);

#ifdef __cplusplus
}
#endif
