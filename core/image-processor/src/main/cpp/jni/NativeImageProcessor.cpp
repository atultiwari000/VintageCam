     1|#include "jni/NativeImageProcessor.h"
     2|#include "core/YuvConverter.h"
     3|#include "core/ColorScience.h"
     4|#include "core/GrainGenerator.h"
     5|#include "core/FilterPipeline.h"
     6|#include "core/BurstProcessor.h"
     7|#include "utils/AndroidBitmapLock.h"
     8|#include "utils/MemoryPool.h"
     9|
    10|#include <opencv2/core.hpp>
    11|#include <opencv2/imgproc.hpp>
    12|#include <android/log.h>
    13|#include <cstring>
    14|
    15|#define LOG_TAG "NativeImageProcessor"
    16|#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
    17|#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
    18|
    19|using namespace vintagecam;
    20|
    21|// ── Helper: JNI byte array -> uint8_t* (RAII release) ─────────────────
    22|
    23|struct JByteArrayGuard {
    24|    JNIEnv* env;
    25|    jbyteArray arr;
    26|    jbyte* data;
    27|    jboolean isCopy;
    28|
    29|    JByteArrayGuard(JNIEnv* e, jbyteArray a) : env(e), arr(a) {
    30|        data = env->GetByteArrayElements(arr, &isCopy);
    31|    }
    32|    ~JByteArrayGuard() {
    33|        env->ReleaseByteArrayElements(arr, data, JNI_ABORT); // JNI_ABORT = don't copy back
    34|    }
    35|    const uint8_t* bytes() const { return reinterpret_cast<const uint8_t*>(data); }
    36|};
    37|
    38|// ── Helper: jstring -> std::string ─────────────────────────────────────
    39|
    40|static std::string jstring2str(JNIEnv* env, jstring jstr) {
    41|    if (!jstr) return "";
    42|    const char* cstr = env->GetStringUTFChars(jstr, nullptr);
    43|    std::string result(cstr);
    44|    env->ReleaseStringUTFChars(jstr, cstr);
    45|    return result;
    46|}
    47|
    48|// ── processYuvFrame ────────────────────────────────────────────────────
    49|
    50|JNIEXPORT jboolean JNICALL
    51|Java_com_vintagecam_imageprocessor_NativeImageProcessor_processYuvFrame(
    52|    JNIEnv* env, jobject /*thiz*/,
    53|    jbyteArray y, jbyteArray u, jbyteArray v,
    54|    jint width, jint height,
    55|    jint yStride, jint uStride, jint vStride,
    56|    jint uvPixelStride,
    57|    jstring presetId, jlong timestamp,
    58|    jobject outBitmap)
    59|{
    60|    try {
    61|        // 1. Lock YUV byte arrays
    62|        JByteArrayGuard yGuard(env, y);
    63|        JByteArrayGuard uGuard(env, u);
    64|        JByteArrayGuard vGuard(env, v);
    65|
    66|        // 2. Convert YUV to RGBA
    67|        cv::Mat rgba = YuvConverter::yuv420ToRgba(
    68|            yGuard.bytes(), uGuard.bytes(), vGuard.bytes(),
    69|            width, height,
    70|            yStride, uStride, vStride, uvPixelStride
    71|        );
    72|
    73|        if (rgba.empty()) {
    74|            LOGE("processYuvFrame: YuvConverter returned empty Mat");
    75|            return JNI_FALSE;
    76|        }
    77|
    78|        // 3. Apply filter pipeline
    79|        std::string preset = jstring2str(env, presetId);
    80|        FilterParams params = FilterPipeline::paramsForPreset(preset);
    81|
    82|        cv::Mat outputRgba;
    83|        bool success = FilterPipeline::process(rgba, outputRgba, params, timestamp, preset);
    84|        if (!success) {
    85|            LOGE("processYuvFrame: FilterPipeline::process failed for preset=%s", preset.c_str());
    86|            return JNI_FALSE;
    87|        }
    88|
    89|        // 4. Lock Bitmap and write pixels (zero-copy)
    90|        AndroidBitmapLock bitmapLock(env, outBitmap);
    91|        if (!bitmapLock.isLocked()) {
    92|            LOGE("processYuvFrame: Failed to lock output Bitmap");
    93|            return JNI_FALSE;
    94|        }
    95|
    96|        YuvConverter::rgbaToBitmap(outputRgba, bitmapLock.pixels(),
    97|                                   bitmapLock.width(), bitmapLock.height());
    98|
    99|        LOGD("processYuvFrame: success for preset=%s, %dx%d", preset.c_str(), width, height);
   100|        return JNI_TRUE;
   101|
   102|    } catch (const std::exception& e) {
   103|        LOGE("processYuvFrame: exception: %s", e.what());
   104|        return JNI_FALSE;
   105|    } catch (...) {
   106|        LOGE("processYuvFrame: unknown exception");
   107|        return JNI_FALSE;
   108|    }
   109|}
   110|
   111|// ── processBitmap ──────────────────────────────────────────────────────
   112|
   113|JNIEXPORT jboolean JNICALL
   114|Java_com_vintagecam_imageprocessor_NativeImageProcessor_processBitmap(
   115|    JNIEnv* env, jobject /*thiz*/,
   116|    jobject bitmap, jstring presetId, jlong timestamp)
   117|{
   118|    try {
   119|        AndroidBitmapLock lock(env, bitmap);
   120|        if (!lock.isLocked()) {
   121|            LOGE("processBitmap: Failed to lock input Bitmap");
   122|            return JNI_FALSE;
   123|        }
   124|
   125|        // Wrap Bitmap pixels as cv::Mat (no copy)
   126|        cv::Mat rgba(lock.height(), lock.width(), CV_8UC4, lock.pixels(), lock.stride());
   127|
   128|        // Convert BGRA (Android Bitmap byte order) to RGBA
   129|        cv::Mat rgbaCorrected;
   130|        cv::cvtColor(rgba, rgbaCorrected, cv::COLOR_BGRA2RGBA);
   131|
   132|        // Apply filter pipeline
   133|        std::string preset = jstring2str(env, presetId);
   134|        FilterParams params = FilterPipeline::paramsForPreset(preset);
   135|
   136|        cv::Mat outputRgba;
   137|        bool success = FilterPipeline::process(rgbaCorrected, outputRgba, params, timestamp, preset);
   138|        if (!success) {
   139|            LOGE("processBitmap: FilterPipeline::process failed");
   140|            return JNI_FALSE;
   141|        }
   142|
   143|        // Convert back to BGRA and write to Bitmap
   144|        cv::Mat bgra;
   145|        cv::cvtColor(outputRgba, bgra, cv::COLOR_RGBA2BGRA);
   146|
   147|        // Copy back to locked Bitmap (stride may differ)
   148|        if (bgra.step == lock.stride() && bgra.cols == lock.width() && bgra.rows == lock.height()) {
   149|            std::memcpy(lock.pixels(), bgra.data, bgra.total() * bgra.elemSize());
   150|        } else {
   151|            // Row-by-row copy to handle stride mismatch
   152|            for (int r = 0; r < lock.height(); ++r) {
   153|                uint8_t* dstRow = static_cast<uint8_t*>(lock.pixels()) + r * lock.stride();
   154|                const uint8_t* srcRow = bgra.ptr<uint8_t>(r);
   155|                std::memcpy(dstRow, srcRow, lock.width() * 4);
   156|            }
   157|        }
   158|
   159|        LOGD("processBitmap: success for preset=%s", preset.c_str());
   160|        return JNI_TRUE;
   161|
   162|    } catch (const std::exception& e) {
   163|        LOGE("processBitmap: exception: %s", e.what());
   164|        return JNI_FALSE;
   165|    } catch (...) {
   166|        LOGE("processBitmap: unknown exception");
   167|        return JNI_FALSE;
   168|    }
   169|}
   170|
   171|// ── mergeBurst ─────────────────────────────────────────────────────────
   172|
   173|JNIEXPORT jlong JNICALL
   174|Java_com_vintagecam_imageprocessor_NativeImageProcessor_mergeBurst(
   175|    JNIEnv* env, jobject /*thiz*/,
   176|    jlongArray matAddrs, jint count, jint alignmentMode)
   177|{
   178|    try {
   179|        if (count <= 0) return 0;
   180|
   181|        jlong* addrs = env->GetLongArrayElements(matAddrs, nullptr);
   182|        if (!addrs) return 0;
   183|
   184|        std::vector<cv::Mat> frames;
   185|        frames.reserve(count);
   186|        for (int i = 0; i < count; ++i) {
   187|            cv::Mat* mat = reinterpret_cast<cv::Mat*>(addrs[i]);
   188|            if (mat && !mat->empty()) {
   189|                frames.push_back(mat->clone());
   190|            }
   191|        }
   192|        env->ReleaseLongArrayElements(matAddrs, addrs, JNI_ABORT);
   193|
   194|        if (frames.empty()) return 0;
   195|
   196|        BurstProcessor::AlignmentMode mode =
   197|            static_cast<BurstProcessor::AlignmentMode>(alignmentMode);
   198|
   199|        cv::Mat result = BurstProcessor::mergeBurst(frames, mode);
   200|        if (result.empty()) return 0;
   201|
   202|        // Return a heap-allocated Mat; caller must releaseNativeMat
   203|        cv::Mat* heapMat = new cv::Mat(result);
   204|        return reinterpret_cast<jlong>(heapMat);
   205|
   206|    } catch (const std::exception& e) {
   207|        LOGE("mergeBurst: exception: %s", e.what());
   208|        return 0;
   209|    } catch (...) {
   210|        LOGE("mergeBurst: unknown exception");
   211|        return 0;
   212|    }
   213|}
   214|
   215|// ── generateGrainTexture ───────────────────────────────────────────────
   216|
   217|JNIEXPORT jlong JNICALL
   218|Java_com_vintagecam_imageprocessor_NativeImageProcessor_generateGrainTexture(
   219|    JNIEnv* /*env*/, jobject /*thiz*/,
   220|    jint width, jint height, jfloat intensity,
   221|    jint seed, jint grainSize)
   222|{
   223|    try {
   224|        GrainSize size = static_cast<GrainSize>(grainSize);
   225|        cv::Mat texture = GrainGenerator::generateTexture(width, height, intensity, seed, size);
   226|
   227|        cv::Mat* heapMat = new cv::Mat(texture);
   228|        return reinterpret_cast<jlong>(heapMat);
   229|
   230|    } catch (const std::exception& e) {
   231|        LOGE("generateGrainTexture: exception: %s", e.what());
   232|        return 0;
   233|    } catch (...) {
   234|        LOGE("generateGrainTexture: unknown exception");
   235|        return 0;
   236|    }
   237|}
   238|
   239|// ── releaseNativeMat ───────────────────────────────────────────────────
   240|
   241|JNIEXPORT void JNICALL
   242|Java_com_vintagecam_imageprocessor_NativeImageProcessor_releaseNativeMat(
   243|    JNIEnv* /*env*/, jobject /*thiz*/, jlong matAddr)
   244|{
   245|    if (matAddr != 0) {
   246|        cv::Mat* mat = reinterpret_cast<cv::Mat*>(matAddr);
   247|        delete mat;
   248|    }
   249|}
   250|