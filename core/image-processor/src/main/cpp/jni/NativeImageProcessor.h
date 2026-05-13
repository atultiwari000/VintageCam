#ifndef VINTAGECAM_NATIVE_IMAGE_PROCESSOR_H
#define VINTAGECAM_NATIVE_IMAGE_PROCESSOR_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * JNI method declarations for com.vintagecam.imageprocessor.NativeImageProcessor.
 */

JNIEXPORT jboolean JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_processYuvFrame(
    JNIEnv* env, jobject thiz,
    jbyteArray y, jbyteArray u, jbyteArray v,
    jint width, jint height,
    jint yStride, jint uStride, jint vStride,
    jint uvPixelStride,
    jstring presetId, jlong timestamp,
    jobject outBitmap);

JNIEXPORT jboolean JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_processBitmap(
    JNIEnv* env, jobject thiz,
    jobject bitmap, jstring presetId, jlong timestamp);

JNIEXPORT jlong JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_mergeBurst(
    JNIEnv* env, jobject thiz,
    jlongArray matAddrs, jint count, jint alignmentMode);

JNIEXPORT jlong JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_generateGrainTexture(
    JNIEnv* env, jobject thiz,
    jint width, jint height, jfloat intensity,
    jint seed, jint grainSize);

JNIEXPORT void JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_releaseNativeMat(
    JNIEnv* env, jobject thiz, jlong matAddr);

#ifdef __cplusplus
}
#endif

#endif // VINTAGECAM_NATIVE_IMAGE_PROCESSOR_H
