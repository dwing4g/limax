#include <opencv2/core.hpp>
#include <opencv2/imgcodecs.hpp>
#include <opencv2/imgproc.hpp>
#include <windows.h>

#include "QRCODE.h"

int main(int argc, char *argv[])
{
	if (argc < 2 || argc > 5)
	{
		fprintf(stderr, "Usage: qrdecode <imgfile> [sample_granularity=1] [bwthreshold=128] [meanfilter=2]\n");
		exit(0);
	}
	int sample_granularity = argc > 2 ? atoi(argv[2]) : 1;
	int bwthreshold = argc > 3 ? atoi(argv[3]) : 128;
	int meanfilter = (argc > 4 ? atoi(argv[4]) : 2) * 2 + 1;
	qr_initialize();
	cv::Mat image = cv::imread(argv[1], cv::IMREAD_COLOR);
	cv::Mat grey, mean, data;
	LARGE_INTEGER f, t0, t1, t2, e0, e1;
	QueryPerformanceCounter(&t0);
	cv::cvtColor(image, grey, cv::COLOR_BGR2GRAY);
	cv::filter2D(grey, mean, -1, cv::Mat::ones(meanfilter, meanfilter, CV_32F) / (meanfilter * meanfilter));
	cv::threshold(mean, data, bwthreshold, 255, cv::THRESH_BINARY);
	QueryPerformanceCounter(&t1);
	QrCodeInfo info = qr_decode((char *)data.data, data.cols, data.rows, sample_granularity);
	QueryPerformanceCounter(&t2);
	QueryPerformanceFrequency(&f);
	e0.QuadPart = (t1.QuadPart - t0.QuadPart) * 1000 / f.QuadPart;
	e1.QuadPart = (t2.QuadPart - t1.QuadPart) * 1000 / f.QuadPart;
	printf("status=%d,reverse=%d,mirror=%d,version=%d,ecl=%d,mask=%d,data=%.*s\n", info->status, info->reverse, info->mirror, info->version, info->ecl, info->mask, info->length, (char*)info->data);
	printf("ImageOpElapsed:%lldms, DecodeOpElapsed: %lldms\n", e0.QuadPart, e1.QuadPart);
	info->release(info);
	return 0;
}

