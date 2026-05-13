#include "core/YuvConverter.h"
#include <opencv2/imgproc.hpp>
#include <algorithm>

namespace vintagecam {

cv::Mat YuvConverter::yuv420ToRgba(
    const uint8_t* y,
    const uint8_t* u,
    const uint8_t* v,
    int width, int height,
    int yStride, int uStride, int vStride,
    int uvPixelStride
) {
    cv::Mat rgba(height, width, CV_8UC4);

    for (int row = 0; row < height; ++row) {
        uint8_t* dstRow = rgba.ptr<uint8_t>(row);
        for (int col = 0; col < width; ++col) {
            int yIdx = row * yStride + col;
            int uvRow = row / 2;
            int uvCol = col / 2;
            int uvIdx = uvRow * uStride + uvCol * uvPixelStride;

            int yVal = y[yIdx];
            int uVal = u[uvIdx] - 128;
            int vVal = v[uvIdx] - 128;

            uint8_t r, g, b;
            yuvToRgb(yVal, uVal, vVal, r, g, b);

            int dstIdx = col * 4;
            dstRow[dstIdx + 0] = r;
            dstRow[dstIdx + 1] = g;
            dstRow[dstIdx + 2] = b;
            dstRow[dstIdx + 3] = 255;
        }
    }

    return rgba;
}

void YuvConverter::rgbaToBitmap(
    const cv::Mat& rgba,
    void* dstPixels,
    int dstWidth,
    int dstHeight
) {
    // OpenCV stores RGBA; Android Bitmap stores ARGB (little-endian: BGRA in memory).
    // We need to swap R<->B channels for correct display.
    cv::Mat bgra;
    cv::cvtColor(rgba, bgra, cv::COLOR_RGBA2BGRA);

    // Direct copy if dimensions match
    if (bgra.cols == dstWidth && bgra.rows == dstHeight && bgra.isContinuous()) {
        std::memcpy(dstPixels, bgra.data, bgra.total() * bgra.elemSize());
    } else {
        // Resize to fit destination
        cv::Mat resized;
        cv::resize(bgra, resized, cv::Size(dstWidth, dstHeight), 0, 0, cv::INTER_LINEAR);
        std::memcpy(dstPixels, resized.data, resized.total() * resized.elemSize());
    }
}

inline void YuvConverter::yuvToRgb(
    int yVal, int uVal, int vVal,
    uint8_t& r, uint8_t& g, uint8_t& b
) {
    // ITU-R BT.601 (SD) coefficients — appropriate for vintage camera emulation
    int rVal = yVal + ((359 * vVal) >> 8);
    int gVal = yVal - ((88 * uVal + 183 * vVal) >> 8);
    int bVal = yVal + ((454 * uVal) >> 8);

    r = static_cast<uint8_t>(std::clamp(rVal, 0, 255));
    g = static_cast<uint8_t>(std::clamp(gVal, 0, 255));
    b = static_cast<uint8_t>(std::clamp(bVal, 0, 255));
}

} // namespace vintagecam
