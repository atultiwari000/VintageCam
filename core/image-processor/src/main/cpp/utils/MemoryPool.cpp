#include "utils/MemoryPool.h"

namespace vintagecam {

std::vector<MemoryPool::PooledMat> MemoryPool::pool_;
std::mutex MemoryPool::mutex_;
bool MemoryPool::initialized_ = false;

void MemoryPool::init(int width, int height, int count) {
    std::lock_guard<std::mutex> lock(mutex_);

    // Shutdown any existing pool
    pool_.clear();

    for (int i = 0; i < count; ++i) {
        PooledMat pm;
        pm.mat = cv::Mat(height, width, CV_8UC4);
        pm.inUse = false;
        pool_.push_back(pm);
    }

    initialized_ = true;
}

cv::Mat MemoryPool::acquire(int width, int height) {
    if (!initialized_) {
        // Return an empty Mat — caller must check
        return cv::Mat();
    }

    std::lock_guard<std::mutex> lock(mutex_);

    for (auto& pm : pool_) {
        if (!pm.inUse && pm.mat.cols >= width && pm.mat.rows >= height) {
            pm.inUse = true;
            // Return ROI if larger than needed
            if (pm.mat.cols == width && pm.mat.rows == height) {
                return pm.mat;
            }
            return pm.mat(cv::Rect(0, 0, width, height));
        }
    }

    // No buffer available — allocate a temporary one
    return cv::Mat(height, width, CV_8UC4);
}

void MemoryPool::release(cv::Mat& mat) {
    if (!initialized_ || mat.empty()) return;

    std::lock_guard<std::mutex> lock(mutex_);

    // Find the parent Mat by data pointer
    for (auto& pm : pool_) {
        if (pm.mat.data <= mat.data
            && mat.data < pm.mat.data + pm.mat.total() * pm.mat.elemSize()) {
            pm.inUse = false;
            break;
        }
    }

    // Release the header
    mat.release();
}

void MemoryPool::shutdown() {
    std::lock_guard<std::mutex> lock(mutex_);
    pool_.clear();
    initialized_ = false;
}

} // namespace vintagecam
