#ifndef VINTAGECAM_ANDROID_BITMAP_LOCK_H
#define VINTAGECAM_ANDROID_BITMAP_LOCK_H

#include <jni.h>
#include <android/bitmap.h>
#include <cstring>

namespace vintagecam {

/**
 * RAII wrapper for AndroidBitmap_lockPixels / unlockPixels.
 *
 * Usage:
 *   AndroidBitmapLock lock(env, bitmap);
 *   if (lock.isLocked()) {
 *       void* pixels = lock.pixels();
 *       // read/write pixels
 *   } // auto-unlocks
 */
class AndroidBitmapLock {
public:
    AndroidBitmapLock(JNIEnv* env, jobject bitmap)
        : env_(env), bitmap_(bitmap), pixels_(nullptr), locked_(false) {
        memset(&info_, 0, sizeof(info_));
        if (AndroidBitmap_getInfo(env, bitmap, &info_) == ANDROID_BITMAP_RESULT_SUCCESS) {
            if (AndroidBitmap_lockPixels(env, bitmap, &pixels_) == ANDROID_BITMAP_RESULT_SUCCESS) {
                locked_ = true;
            }
        }
    }

    ~AndroidBitmapLock() {
        if (locked_) {
            AndroidBitmap_unlockPixels(env_, bitmap_);
        }
    }

    // Non-copyable
    AndroidBitmapLock(const AndroidBitmapLock&) = delete;
    AndroidBitmapLock& operator=(const AndroidBitmapLock&) = delete;

    // Movable
    AndroidBitmapLock(AndroidBitmapLock&& other) noexcept
        : env_(other.env_), bitmap_(other.bitmap_),
          pixels_(other.pixels_), locked_(other.locked_), info_(other.info_) {
        other.locked_ = false;
        other.env_ = nullptr;
        other.bitmap_ = nullptr;
        other.pixels_ = nullptr;
    }

    AndroidBitmapLock& operator=(AndroidBitmapLock&& other) noexcept {
        if (this != &other) {
            if (locked_) AndroidBitmap_unlockPixels(env_, bitmap_);
            env_ = other.env_;
            bitmap_ = other.bitmap_;
            pixels_ = other.pixels_;
            locked_ = other.locked_;
            info_ = other.info_;
            other.locked_ = false;
            other.env_ = nullptr;
            other.bitmap_ = nullptr;
            other.pixels_ = nullptr;
        }
        return *this;
    }

    bool isLocked() const { return locked_; }
    void* pixels() const { return pixels_; }
    int width() const { return info_.width; }
    int height() const { return info_.height; }
    int stride() const { return info_.stride; }
    AndroidBitmapFormat format() const { return (AndroidBitmapFormat)info_.format; }

private:
    JNIEnv* env_;
    jobject bitmap_;
    void* pixels_;
    bool locked_;
    AndroidBitmapInfo info_;
};

} // namespace vintagecam

#endif // VINTAGECAM_ANDROID_BITMAP_LOCK_H
