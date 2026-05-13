#include "jni/NativeImageProcessor.h"
#include "core/YuvConverter.h"
#include "core/ColorScience.h"
#include "core/GrainGenerator.h"
#include "core/FilterPipeline.h"
#include "core/BurstProcessor.h"
#include "utils/AndroidBitmapLock.h"
#include "utils/MemoryPool.h"

#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <android/log.h>
#include <cstring>

#define LOG_TAG "NativeImageProcessor"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace vintagecam;

// ── Helper: JNI byte array -> uint8_t* (RAII release) ─────────────────

struct JByteArrayGuard {
    JNIEnv* env;
    jbyteArray arr;
    jbyte* data;
    jboolean isCopy;

    JByteArrayGuard(JNIEnv* e, jbyteArray a) : env(e), arr(a) {
        data = env->GetByteArrayElements(arr, &isCopy);
    }
    ~JByteArrayGuard() {
        env->ReleaseByteArrayElements(arr, data, JNI_ABORT); // JNI_ABORT = don't copy back
    }
    const uint8_t* bytes() const { return reinterpret_cast<const uint8_t*>(data); }
};

// ── Helper: jstring -> std::string ─────────────────────────────────────

static std::string jstring2str(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* cstr = env->GetStringUTFChars(jstr, nullptr);
    std::string result(cstr);
    env->ReleaseStringUTFChars(jstr, cstr);
    return result;
}

// ── processYuvFrame ────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_processYuvFrame(
    JNIEnv* env, jobject /*thiz*/,
    jbyteArray y, jbyteArray u, jbyteArray v,
    jint width, jint height,
    jint yStride, jint uStride, jint vStride,
    jint uvPixelStride,
    jstring presetId, jlong timestamp,
    jobject outBitmap)
{
    try {
        // 1. Lock YUV byte arrays
        JByteArrayGuard yGuard(env, y);
        JByteArrayGuard uGuard(env, u);
        JByteArrayGuard vGuard(env, v);

        // 2. Convert YUV to RGBA
        cv::Mat rgba = YuvConverter::yuv420ToRgba(
            yGuard.bytes(), uGuard.bytes(), vGuard.bytes(),
            width, height,
            yStride, uStride, vStride, uvPixelStride
        );

        if (rgba.empty()) {
            LOGE("processYuvFrame: YuvConverter returned empty Mat");
            return JNI_FALSE;
        }

        // 3. Apply filter pipeline
        std::string preset = jstring2str(env, presetId);
        FilterParams params = FilterPipeline::paramsForPreset(preset);

        cv::Mat outputRgba;
        bool success = FilterPipeline::process(rgba, outputRgba, params, timestamp, preset);
        if (!success) {
            LOGE("processYuvFrame: FilterPipeline::process failed for preset=%s", preset.c_str());
            return JNI_FALSE;
        }

        // 4. Lock Bitmap and write pixels (zero-copy)
        AndroidBitmapLock bitmapLock(env, outBitmap);
        if (!bitmapLock.isLocked()) {
            LOGE("processYuvFrame: Failed to lock output Bitmap");
            return JNI_FALSE;
        }

        YuvConverter::rgbaToBitmap(outputRgba, bitmapLock.pixels(),
                                   bitmapLock.width(), bitmapLock.height());

        LOGD("processYuvFrame: success for preset=%s, %dx%d", preset.c_str(), width, height);
        return JNI_TRUE;

    } catch (const std::exception& e) {
        LOGE("processYuvFrame: exception: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("processYuvFrame: unknown exception");
        return JNI_FALSE;
    }
}

// ── processBitmap ──────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_processBitmap(
    JNIEnv* env, jobject /*thiz*/,
    jobject bitmap, jstring presetId, jlong timestamp)
{
    try {
        AndroidBitmapLock lock(env, bitmap);
        if (!lock.isLocked()) {
            LOGE("processBitmap: Failed to lock input Bitmap");
            return JNI_FALSE;
        }

        // Wrap Bitmap pixels as cv::Mat (no copy)
        cv::Mat rgba(lock.height(), lock.width(), CV_8UC4, lock.pixels(), lock.stride());

        // Convert BGRA (Android Bitmap byte order) to RGBA
        cv::Mat rgbaCorrected;
        cv::cvtColor(rgba, rgbaCorrected, cv::COLOR_BGRA2RGBA);

        // Apply filter pipeline
        std::string preset = jstring2str(env, presetId);
        FilterParams params = FilterPipeline::paramsForPreset(preset);

        cv::Mat outputRgba;
        bool success = FilterPipeline::process(rgbaCorrected, outputRgba, params, timestamp, preset);
        if (!success) {
            LOGE("processBitmap: FilterPipeline::process failed");
            return JNI_FALSE;
        }

        // Convert back to BGRA and write to Bitmap
        cv::Mat bgra;
        cv::cvtColor(outputRgba, bgra, cv::COLOR_RGBA2BGRA);

        // Copy back to locked Bitmap (stride may differ)
        if (bgra.step == lock.stride() && bgra.cols == lock.width() && bgra.rows == lock.height()) {
            std::memcpy(lock.pixels(), bgra.data, bgra.total() * bgra.elemSize());
        } else {
            // Row-by-row copy to handle stride mismatch
            for (int r = 0; r < lock.height(); ++r) {
                uint8_t* dstRow = static_cast<uint8_t*>(lock.pixels()) + r * lock.stride();
                const uint8_t* srcRow = bgra.ptr<uint8_t>(r);
                std::memcpy(dstRow, srcRow, lock.width() * 4);
            }
        }

        LOGD("processBitmap: success for preset=%s", preset.c_str());
        return JNI_TRUE;

    } catch (const std::exception& e) {
        LOGE("processBitmap: exception: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("processBitmap: unknown exception");
        return JNI_FALSE;
    }
}

// ── mergeBurst ─────────────────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_mergeBurst(
    JNIEnv* env, jobject /*thiz*/,
    jlongArray matAddrs, jint count, jint alignmentMode)
{
    try {
        if (count <= 0) return 0;

        jlong* addrs = env->GetLongArrayElements(matAddrs, nullptr);
        if (!addrs) return 0;

        std::vector<cv::Mat> frames;
        frames.reserve(count);
        for (int i = 0; i < count; ++i) {
            cv::Mat* mat = reinterpret_cast<cv::Mat*>(addrs[i]);
            if (mat && !mat->empty()) {
                frames.push_back(mat->clone());
            }
        }
        env->ReleaseLongArrayElements(matAddrs, addrs, JNI_ABORT);

        if (frames.empty()) return 0;

        BurstProcessor::AlignmentMode mode =
            static_cast<BurstProcessor::AlignmentMode>(alignmentMode);

        cv::Mat result = BurstProcessor::mergeBurst(frames, mode);
        if (result.empty()) return 0;

        // Return a heap-allocated Mat; caller must releaseNativeMat
        cv::Mat* heapMat = new cv::Mat(result);
        return reinterpret_cast<jlong>(heapMat);

    } catch (const std::exception& e) {
        LOGE("mergeBurst: exception: %s", e.what());
        return 0;
    } catch (...) {
        LOGE("mergeBurst: unknown exception");
        return 0;
    }
}

// ── generateGrainTexture ───────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_generateGrainTexture(
    JNIEnv* /*env*/, jobject /*thiz*/,
    jint width, jint height, jfloat intensity,
    jint seed, jint grainSize)
{
    try {
        GrainSize size = static_cast<GrainSize>(grainSize);
        cv::Mat texture = GrainGenerator::generateTexture(width, height, intensity, seed, size);

        cv::Mat* heapMat = new cv::Mat(texture);
        return reinterpret_cast<jlong>(heapMat);

    } catch (const std::exception& e) {
        LOGE("generateGrainTexture: exception: %s", e.what());
        return 0;
    } catch (...) {
        LOGE("generateGrainTexture: unknown exception");
        return 0;
    }
}

// ── releaseNativeMat ───────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_releaseNativeMat(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong matAddr)
{
    if (matAddr != 0) {
        cv::Mat* mat = reinterpret_cast<cv::Mat*>(matAddr);
        delete mat;
    }
}
