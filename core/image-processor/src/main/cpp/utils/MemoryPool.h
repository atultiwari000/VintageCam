#ifndef VINTAGECAM_MEMORY_POOL_H
#define VINTAGECAM_MEMORY_POOL_H

#include <opencv2/core.hpp>
#include <vector>
#include <mutex>

namespace vintagecam {

/**
 * Pre-allocated buffer pool for cv::Mat frames.
 *
 * Avoids per-frame allocation overhead during burst processing.
 * Pre-allocates N buffers of size (width × height × 4) bytes (RGBA).
 */
class MemoryPool {
public:
    /**
     * Initialize pool with `count` buffers of the given dimensions.
     * Thread-safe: call once at startup from any thread.
     */
    static void init(int width, int height, int count = 5);

    /**
     * Acquire a buffer from the pool. Blocks if none available.
     * Returns an empty Mat if the pool was not initialized.
     */
    static cv::Mat acquire(int width, int height);

    /**
     * Release a buffer back to the pool.
     */
    static void release(cv::Mat& mat);

    /**
     * Destroy all pooled buffers.
     */
    static void shutdown();

private:
    struct PooledMat {
        cv::Mat mat;
        bool inUse = false;
    };

    static std::vector<PooledMat> pool_;
    static std::mutex mutex_;
    static bool initialized_;
};

} // namespace vintagecam

#endif // VINTAGECAM_MEMORY_POOL_H
