#ifndef VINTAGECAM_YUV_CONVERTER_H
#define VINTAGECAM_YUV_CONVERTER_H

#include <opencv2/core.hpp>
#include <cstdint>

namespace vintagecam {

/**
 * Converts YUV_420_888 (planar or semi-planar) to RGBA without JPEG
 * compression. Handles arbitrary stride and pixel stride values from
 * Android CameraX ImageProxy planes.
 *
 * Output: cv::Mat with 4 channels (RGBA, 8UC4).
 */
class YuvConverter {
public:
    /**
     * Convert YUV_420_888 planar data to RGBA.
     *
     * @param y              Luma plane bytes
     * @param u              U chroma plane bytes
     * @param v              V chroma plane bytes
     * @param width          Frame width in pixels
     * @param height         Frame height in pixels
     * @param yStride        Row stride of Y plane
     * @param uStride        Row stride of U plane
     * @param vStride        Row stride of V plane
     * @param uvPixelStride  Pixel stride of UV planes (1=planar, 2=semi-planar)
     * @return               RGBA cv::Mat (8UC4)
     */
    static cv::Mat yuv420ToRgba(
        const uint8_t* y,
        const uint8_t* u,
        const uint8_t* v,
        int width,
        int height,
        int yStride,
        int uStride,
        int vStride,
        int uvPixelStride
    );

    /**
     * Write RGBA pixels directly to a locked Android Bitmap buffer.
     * The Bitmap must be ARGB_8888 and already locked via
     * AndroidBitmap_lockPixels.
     *
     * @param rgba        Source RGBA image
     * @param dstPixels   Bitmap pixel buffer (locked)
     * @param dstWidth    Bitmap width
     * @param dstHeight   Bitmap height
     */
    static void rgbaToBitmap(
        const cv::Mat& rgba,
        void* dstPixels,
        int dstWidth,
        int dstHeight
    );

private:
    // YUV -> RGB conversion for a single pixel
    static inline void yuvToRgb(
        int yVal, int uVal, int vVal,
        uint8_t& r, uint8_t& g, uint8_t& b
    );
};

} // namespace vintagecam

#endif // VINTAGECAM_YUV_CONVERTER_H
