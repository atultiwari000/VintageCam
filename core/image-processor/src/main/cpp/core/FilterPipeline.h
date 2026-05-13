#ifndef VINTAGECAM_FILTER_PIPELINE_H
#define VINTAGECAM_FILTER_PIPELINE_H

#include <opencv2/core.hpp>
#include <string>
#include "ColorScience.h"
#include "GrainGenerator.h"

struct AAssetManager;

namespace vintagecam {

/**
 * Parameter pack matching FilterPreset — all filter settings in one struct.
 */
struct FilterParams {
    float grain = 0.0f;
    float vignette = 0.0f;
    float vignetteMid = 0.55f;
    float vignetteOuter = 1.0f;
    float fade = 0.0f;
    float saturation = 1.0f;
    float contrast = 1.0f;
    float halation = 0.0f;
    float chromaticAberration = 0.0f;
    float barrel = 0.0f;
    float tintShadow[3] = {0.0f, 0.0f, 0.0f};
    float tintHighlight[3] = {0.0f, 0.0f, 0.0f};
    float lutStrength = 0.0f;
    FilmType filmType = FilmType::NONE;
    float shadowNoiseIntensity = 0.0f;
    float crushedBlacks = 0.0f;
    bool applyDateStamp = false;
    DateStampStyle dateStampStyle = DateStampStyle::NONE;
};

/**
 * Orchestrates the full filter chain for a single frame.
 *
 * Applies effects in a deterministic order:
 *   1. Barrel distortion
 *   2. Chromatic aberration
 *   3. Film response curve
 *   4. Color matrix / tint split
 *   5. Saturation & contrast
 *   6. Vignette
 *   7. Halation (two-pass)
 *   8. Grain
 *   9. Fade (black lift)
 *  10. Crushed blacks
 *  11. Date stamp
 *  12. LUT application
 */
class FilterPipeline {
public:
    /**
     * Main entry point.
     *
     * @param inputRgba   Source image (RGBA, 8UC4)
     * @param outputRgba  Destination image (RGBA, 8UC4) — may be same as input
     * @param params      Filter parameter pack
     * @param timestamp   Unix epoch millis for date stamp
     * @param presetId    Filter preset ID for profile-specific tweaks
     * @return true on success
     */
    static bool process(
        const cv::Mat& inputRgba,
        cv::Mat& outputRgba,
        const FilterParams& params,
        long timestamp,
        const std::string& presetId
    );

private:
    static void applyVignette(
        cv::Mat& img,
        float strength,
        float mid,
        float outer
    );

    static void applyHalation(cv::Mat& img, float strength);

    static void applyChromaticAberration(cv::Mat& img, float amount);

    static void applyBarrelDistortion(cv::Mat& img, float k);

    static void applyDateStamp(
        cv::Mat& img,
        long timestamp,
        DateStampStyle style
    );

    /**
     * Map preset ID string to FilterParams with profile-specific defaults.
     */
    static FilterParams paramsForPreset(const std::string& presetId);
};

} // namespace vintagecam

#endif // VINTAGECAM_FILTER_PIPELINE_H
