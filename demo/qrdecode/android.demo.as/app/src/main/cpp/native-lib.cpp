#include <jni.h>
#include <string>
#include <cstring>
#include <malloc.h>
#include <opencv2/core.hpp>
#include <opencv2/imgcodecs.hpp>
#include <opencv2/imgproc.hpp>
#include "QRCODE.h"

extern "C" JNIEXPORT void
JNICALL
Java_limax_android_demo_MainActivity_qrInitJNI(
        JNIEnv *env,
        jobject /* this */) {
    qr_initialize();
    return;
}

extern "C" JNIEXPORT jobject
JNICALL
Java_limax_android_demo_MainActivity_qrDecodeJNI(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray data,
        jint width,
        jint height) {
    jbyte *arr;
    jint length;
    arr = env->GetByteArrayElements(data, 0);
    length = env->GetArrayLength(data);

    int sample_granularity = 1;
    int bwthreshold = 128;
    int meanfilter = 5;

    cv::Mat grey, mean, thre;
    cv::Mat image = cv::Mat(height + height / 2, width, CV_8UC1, arr);
    cv::cvtColor(image, grey, cv::COLOR_YUV420sp2GRAY);
    cv::filter2D(grey, mean, -1, cv::Mat::ones(meanfilter, meanfilter, CV_32F) / (meanfilter * meanfilter));
    cv::threshold(mean, thre, bwthreshold, 255, cv::THRESH_BINARY);

    QrCodeInfo info = qr_decode((char *) thre.data, thre.cols, thre.rows, sample_granularity);

    jobject retobj = NULL;
    if (info->status == SCAN_OK) {
        jclass cls = env->FindClass("limax/android/demo/QRInfo");
        jmethodID constructorMID = env->GetMethodID(cls, "<init>","(IIIIII[B)V");
        jbyteArray jRetBytes = env->NewByteArray(info->length + 1);
        env->SetByteArrayRegion(jRetBytes, 0, info->length, (jbyte*)info->data);
        retobj = env->NewObject(cls, constructorMID, info->status, info->reverse,
                                     info->mirror, info->version, info->ecl, info->mask, jRetBytes);
        env->DeleteLocalRef(jRetBytes);
    }
    info->release(info);

    return retobj == NULL ? env->NewGlobalRef(NULL) : retobj;
}
