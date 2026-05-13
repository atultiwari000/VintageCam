#ifndef VINTAGECAM_GRAIN_GENERATOR_H
#define VINTAGECAM_GRAIN_GENERATOR_H

#include <opencv2/core.hpp>
#include <random>

namespace vintagecam {

enum class GrainSize {
    FINE = 0,
    MEDIUM = 1,
    COARSE = 2,
};

/**
 * Procedural grain generation with luminance-weighted application.
 *
 * Grain is applied in YUV space to preserve perceived brightness —
 * noise is added to the Y (luma) channel only, keeping colors stable.
 */
class GrainGenerator {
public:
    /**
     * Overlay luminance-weighted grain on an RGBA image.
     * Internally converts to YUV, applies grain, converts back.
     *
     * @param image      Input/output RGBA image
     * @param intensity  0.0-1.0 grain strength
     * @param seed       Random seed for reproducibility/temporal variation
     * @param size       Grain coarseness
     */
    static void overlayGrain(
        cv::Mat& image,
        float intensity,
        int seed,
        GrainSize size
    );

    /**
     * Generate a standalone grain texture (RGBA) for GPU/preview use.
     */
    static cv::Mat generateTexture(
        int width,
        int height,
        float intensity,
        int seed,
        GrainSize size
    );

    // Profile-specific grain types
    static void apply35mmFine(cv::Mat& yuv, float intensity);
    static void apply110Chunky(cv::Mat& yuv, float intensity);
    static void applyVhsBanding(cv::Mat& yuv, float intensity);
    static void applyDigitalChromaNoise(cv::Mat& yuv, float intensity);

private:
    static void addGrainToChannel(
        cv::Mat& channel,
        float intensity,
        std::mt19937& rng,
        float grainScale
    );

    static float canonicalGrainScale(GrainSize size);
};

} // namespace vintagecam

#endif // VINTAGECAM_GRAIN_GENERATOR_H
