#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <random>
#include <string>

#define LOG_TAG "NativeImageProcessor"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct BitmapLock {
    JNIEnv* env;
    jobject bitmap;
    AndroidBitmapInfo info{};
    void* pixels = nullptr;
    bool locked = false;

    BitmapLock(JNIEnv* env, jobject bitmap) : env(env), bitmap(bitmap) {
        if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return;
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;
        locked = AndroidBitmap_lockPixels(env, bitmap, &pixels) == ANDROID_BITMAP_RESULT_SUCCESS;
    }

    ~BitmapLock() {
        if (locked) AndroidBitmap_unlockPixels(env, bitmap);
    }
};

struct ByteArrayLock {
    JNIEnv* env;
    jbyteArray array;
    jbyte* bytes = nullptr;

    ByteArrayLock(JNIEnv* env, jbyteArray array) : env(env), array(array) {
        bytes = env->GetByteArrayElements(array, nullptr);
    }

    ~ByteArrayLock() {
        if (bytes) env->ReleaseByteArrayElements(array, bytes, JNI_ABORT);
    }
};

static std::string toString(JNIEnv* env, jstring value) {
    if (!value) return "";
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

static float clamp01(float value) {
    return std::max(0.0f, std::min(1.0f, value));
}

static uint8_t byteClamp(float value) {
    return static_cast<uint8_t>(std::max(0.0f, std::min(255.0f, value)));
}

static float smoothContrast(float value, float contrast, float fade) {
    float lifted = value * (1.0f - fade) + fade * 0.10f;
    return clamp01((lifted - 0.5f) * contrast + 0.5f);
}

struct ProfileLook {
    float rGain;
    float gGain;
    float bGain;
    float saturation;
    float contrast;
    float fade;
    float grain;
    float vignette;
    float crush;
    int grainMode;
};

static ProfileLook lookFor(const std::string& preset) {
    if (preset == "vhs_1985") {
        return {1.06f, 1.17f, 0.94f, 0.78f, 1.16f, 0.08f, 0.24f, 0.34f, 0.10f, 1};
    }
    if (preset == "disposable_1998") {
        return {1.18f, 1.06f, 0.86f, 1.24f, 1.12f, 0.03f, 0.30f, 0.42f, 0.05f, 2};
    }
    return {0.96f, 1.04f, 1.16f, 1.10f, 1.24f, 0.00f, 0.13f, 0.16f, 0.12f, 3};
}

static void applyLookToPixel(
    uint8_t& rByte,
    uint8_t& gByte,
    uint8_t& bByte,
    float x,
    float y,
    const ProfileLook& look,
    uint32_t& rngState
) {
    float r = rByte / 255.0f;
    float g = gByte / 255.0f;
    float b = bByte / 255.0f;

    r = std::pow(clamp01(r), 0.94f) * look.rGain;
    g = std::pow(clamp01(g), 0.98f) * look.gGain;
    b = std::pow(clamp01(b), 1.05f) * look.bGain;

    float luma = r * 0.299f + g * 0.587f + b * 0.114f;
    r = luma + (r - luma) * look.saturation;
    g = luma + (g - luma) * look.saturation;
    b = luma + (b - luma) * look.saturation;

    if (look.crush > 0.0f) {
        float threshold = look.crush * 0.45f;
        r = r < threshold ? r * (r / threshold) : r;
        g = g < threshold ? g * (g / threshold) : g;
        b = b < threshold ? b * (b / threshold) : b;
    }

    r = smoothContrast(r, look.contrast, look.fade);
    g = smoothContrast(g, look.contrast, look.fade);
    b = smoothContrast(b, look.contrast, look.fade);

    float dx = x - 0.5f;
    float dy = y - 0.5f;
    float edge = clamp01(std::sqrt(dx * dx + dy * dy) * 1.45f);
    float vignette = 1.0f - look.vignette * edge * edge;

    rngState = rngState * 1664525u + 1013904223u;
    float noise = (static_cast<float>((rngState >> 16) & 0xFFu) / 255.0f - 0.5f);
    float weightedNoise = noise * look.grain * (1.20f - luma);
    if (look.grainMode == 1) {
        weightedNoise += std::sin(y * 900.0f) * look.grain * 0.03f;
    }

    luma = clamp01((r * 0.299f + g * 0.587f + b * 0.114f) + weightedNoise);
    float chromaR = r - (r * 0.299f + g * 0.587f + b * 0.114f);
    float chromaG = g - (r * 0.299f + g * 0.587f + b * 0.114f);
    float chromaB = b - (r * 0.299f + g * 0.587f + b * 0.114f);

    rByte = byteClamp((luma + chromaR) * vignette * 255.0f);
    gByte = byteClamp((luma + chromaG) * vignette * 255.0f);
    bByte = byteClamp((luma + chromaB) * vignette * 255.0f);
}

static void yuvToRgb(int yValue, int uValue, int vValue, uint8_t& r, uint8_t& g, uint8_t& b) {
    float y = static_cast<float>(yValue);
    float u = static_cast<float>(uValue) - 128.0f;
    float v = static_cast<float>(vValue) - 128.0f;
    r = byteClamp(y + 1.402f * v);
    g = byteClamp(y - 0.344136f * u - 0.714136f * v);
    b = byteClamp(y + 1.772f * u);
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_processYuvFrame(
    JNIEnv* env,
    jobject,
    jbyteArray yArray,
    jbyteArray uArray,
    jbyteArray vArray,
    jint width,
    jint height,
    jint yStride,
    jint uStride,
    jint vStride,
    jint uvPixelStride,
    jstring presetId,
    jlong timestamp,
    jobject outBitmap
) {
    try {
        ByteArrayLock y(env, yArray);
        ByteArrayLock u(env, uArray);
        ByteArrayLock v(env, vArray);
        BitmapLock bitmap(env, outBitmap);
        if (!y.bytes || !u.bytes || !v.bytes || !bitmap.locked) return JNI_FALSE;

        const auto look = lookFor(toString(env, presetId));
        uint32_t rng = static_cast<uint32_t>(timestamp);

        for (int row = 0; row < height; ++row) {
            auto* out = reinterpret_cast<uint8_t*>(
                static_cast<uint8_t*>(bitmap.pixels) + row * bitmap.info.stride
            );
            for (int col = 0; col < width; ++col) {
                int uvIndex = (row / 2) * uStride + (col / 2) * uvPixelStride;
                int yValue = static_cast<uint8_t>(y.bytes[row * yStride + col]);
                int uValue = static_cast<uint8_t>(u.bytes[uvIndex]);
                int vValue = static_cast<uint8_t>(v.bytes[(row / 2) * vStride + (col / 2) * uvPixelStride]);
                uint8_t r;
                uint8_t g;
                uint8_t b;
                yuvToRgb(yValue, uValue, vValue, r, g, b);
                applyLookToPixel(r, g, b, col / static_cast<float>(width), row / static_cast<float>(height), look, rng);
                out[col * 4 + 0] = r;
                out[col * 4 + 1] = g;
                out[col * 4 + 2] = b;
                out[col * 4 + 3] = 255;
            }
        }

        return JNI_TRUE;
    } catch (...) {
        LOGE("processYuvFrame failed");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_processBitmap(
    JNIEnv* env,
    jobject,
    jobject bitmapObject,
    jstring presetId,
    jlong timestamp
) {
    try {
        BitmapLock bitmap(env, bitmapObject);
        if (!bitmap.locked) return JNI_FALSE;

        const auto look = lookFor(toString(env, presetId));
        uint32_t rng = static_cast<uint32_t>(timestamp);

        for (uint32_t row = 0; row < bitmap.info.height; ++row) {
            auto* pixels = reinterpret_cast<uint8_t*>(
                static_cast<uint8_t*>(bitmap.pixels) + row * bitmap.info.stride
            );
            for (uint32_t col = 0; col < bitmap.info.width; ++col) {
                uint8_t& r = pixels[col * 4 + 0];
                uint8_t& g = pixels[col * 4 + 1];
                uint8_t& b = pixels[col * 4 + 2];
                applyLookToPixel(
                    r,
                    g,
                    b,
                    col / static_cast<float>(bitmap.info.width),
                    row / static_cast<float>(bitmap.info.height),
                    look,
                    rng
                );
                pixels[col * 4 + 3] = 255;
            }
        }

        return JNI_TRUE;
    } catch (...) {
        LOGE("processBitmap failed");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_mergeBurst(
    JNIEnv*,
    jobject,
    jlongArray,
    jint,
    jint
) {
    return 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_generateGrainTexture(
    JNIEnv*,
    jobject,
    jint width,
    jint height,
    jfloat intensity,
    jint seed,
    jint grainSize
) {
    auto* texture = new uint8_t[static_cast<size_t>(width) * static_cast<size_t>(height)];
    std::mt19937 rng(seed);
    const float scale = grainSize == 2 ? 1.7f : grainSize == 1 ? 1.2f : 0.8f;
    for (int i = 0; i < width * height; ++i) {
        float n = std::generate_canonical<float, 16>(rng) - 0.5f;
        texture[i] = byteClamp(128.0f + n * 255.0f * intensity * scale);
    }
    return reinterpret_cast<jlong>(texture);
}

extern "C" JNIEXPORT void JNICALL
Java_com_vintagecam_imageprocessor_NativeImageProcessor_releaseNativeMat(
    JNIEnv*,
    jobject,
    jlong matAddr
) {
    delete[] reinterpret_cast<uint8_t*>(matAddr);
}
