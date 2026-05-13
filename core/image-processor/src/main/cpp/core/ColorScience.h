#ifndef VINTAGECAM_COLOR_SCIENCE_H
#define VINTAGECAM_COLOR_SCIENCE_H

#include <opencv2/core.hpp>
#include <vector>
#include <cstdint>

namespace vintagecam {

/**
 * Film type enum for characteristic curve presets.
 */
enum class FilmType {
    KODAK_GOLD_200,
    KODAK_PORTRA_400,
    KODAK_TRI_X_400,
    KODAK_EKTACHROME_E100,
    FUJI_VELVIA_50,
    FUJI_PRO_400H,
    FUJI_SUPERIA_XTRA_400,
    ILFORD_HP5,
    POLAROID_600,
    SONY_CCD_EARLY,
    MODERN_CMOS,
    NONE,
};

/**
 * Date stamp style enum.
 */
enum class DateStampStyle {
    NONE,
    YELLOW_CLASSIC,
    RED_LED,
    WHITE_LCD,
};

/**
 * Color science operations — film response curves, white balance,
 * color matrices, and per-channel LUT application.
 */
class ColorScience {
public:
    /**
     * Apply a film characteristic curve (S-curve) per RGB channel.
     * Modelled after real film stock sensitometry.
     */
    static void applyFilmResponse(cv::Mat& rgb, FilmType type);

    /**
     * Apply custom white balance correction.
     *
     * @param rgb    Input/output RGBA image
     * @param tempK  Color temperature in Kelvin (2000-10000)
     * @param tint   Green/magenta tint (-1.0 to 1.0)
     */
    static void applyWhiteBalance(cv::Mat& rgb, float tempK, float tint);

    /**
     * Apply a 3×3 color matrix (row-major, 9 floats).
     */
    static void applyColorMatrix(cv::Mat& rgb, const float* matrix3x3);

    /**
     * Apply a per-channel lookup table to a single-channel image.
     */
    static void applyCurve(cv::Mat& channel, const std::vector<uint8_t>& lut);

    /**
     * Crush blacks by remapping low values toward zero.
     *
     * @param rgb      Input/output RGBA image
     * @param strength 0.0-1.0 (how aggressively to crush shadows)
     */
    static void crushBlacks(cv::Mat& rgb, float strength);

    /**
     * Adjust saturation in HSV space.
     *
     * @param rgb      Input/output RGBA image
     * @param factor   0.5 = desaturated, 1.0 = normal, 1.5 = boosted
     */
    static void adjustSaturation(cv::Mat& rgb, float factor);

    /**
     * Adjust contrast by scaling around mid-gray (128).
     *
     * @param rgb      Input/output RGBA image
     * @param factor   0.5 = flat, 1.0 = normal, 1.5 = punchy
     */
    static void adjustContrast(cv::Mat& rgb, float factor);

    /**
     * Apply additive tint to shadows and highlights separately.
     *
     * @param rgb        Input/output RGBA image
     * @param tintShadow     [R, G, B] additive values for shadows
     * @param tintHighlight  [R, G, B] additive values for highlights
     */
    static void applyTintSplit(
        cv::Mat& rgb,
        const float tintShadow[3],
        const float tintHighlight[3]
    );

    /**
     * Apply black lift (fade/matte) — adds a constant offset to all channels.
     */
    static void applyFade(cv::Mat& rgb, float strength);

private:
    // Generate per-channel LUT for a given film type
    static std::vector<uint8_t> filmResponseLut(FilmType type);

    // RGB -> HSV helpers
    static float lerp(float a, float b, float t);
};

} // namespace vintagecam

#endif // VINTAGECAM_COLOR_SCIENCE_H
