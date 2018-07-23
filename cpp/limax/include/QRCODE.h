#pragma once

enum { ECL_M, ECL_L, ECL_H, ECL_Q };
enum scan_status { SCAN_OK, ERR_POSITIONING, ERR_VERSION_INFO, ERR_ALIGNMENTS, ERR_FORMAT_INFO, ERR_UNRECOVERABLE_CODEWORDS, UNSUPPORTED_ENCODE_MODE };

struct tagQrCode;
typedef struct tagQrCode* QrCode;

struct tagQrCode {
	void *modules;
	int size;
	void(*release)(QrCode _THIS);
	char*(*toSvgXML)(QrCode _THIS);
};

struct tagQrCodeInfo;
typedef struct tagQrCodeInfo *QrCodeInfo;

struct tagQrCodeInfo {
	enum scan_status status;
	int reverse;
	int mirror;
	int version;
	int ecl;
	int mask;
	void *data;
	int length;
	void(*release)(QrCodeInfo _THIS);
};
#ifdef __cplusplus
extern "C"
{
#endif

void qr_initialize();
QrCode qr_encode(void *data, int size, int ecl);
QrCodeInfo qr_decode(char *image_1bit, int width, int height, int sample_granularity);

#ifdef __cplusplus
}
#endif
