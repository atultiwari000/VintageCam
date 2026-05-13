#include "core/FilterPipeline.h"
#include "core/GrainGenerator.h"
#include <opencv2/imgproc.hpp>
#include <cmath>
#include <algorithm>
#include <cstring>

namespace vintagecam {

// ── Preset Definitions ─────────────────────────────────────────────────

FilterParams FilterPipeline::paramsForPreset(const std::string& presetId) {
    FilterParams p;

    if (presetId == "vhs_1985") {
        p.grain = 0.40f;
        p.vignette = 0.50f;
        p.vignetteMid = 0.40f;
        p.vignetteOuter = 1.0f;
        p.fade = 0.08f;
        p.saturation = 0.85f;
        p.contrast = 1.15f;
        p.halation = 0.15f;
        p.chromaticAberration = 0.015f;
        p.barrel = 0.08f;
        p.tintShadow[0] = 0.02f; p.tintShadow[1] = 0.05f; p.tintShadow[2] = -0.03f;
        p.tintHighlight[0] = -0.03f; p.tintHighlight[1] = 0.08f; p.tintHighlight[2] = -0.05f;
        p.filmType = FilmType::SONY_CCD_EARLY;
        p.shadowNoiseIntensity = 0.12f;
        p.crushedBlacks = 0.10f;
        p.applyDateStamp = true;
        p.dateStampStyle = DateStampStyle::RED_LED;

    } else if (presetId == "disposable_1998") {
        p.grain = 0.35f;
        p.vignette = 0.45f;
        p.vignetteMid = 0.50f;
        p.vignetteOuter = 1.0f;
        p.fade = 0.06f;
        p.saturation = 1.25f;
        p.contrast = 1.10f;
        p.halation = 0.08f;
        p.chromaticAberration = 0.005f;
        p.barrel = 0.03f;
        p.tintShadow[0] = 0.01f; p.tintShadow[1] = 0.02f; p.tintShadow[2] = 0.01f;
        p.tintHighlight[0] = 0.04f; p.tintHighlight[1] = 0.02f; p.tintHighlight[2] = -0.02f;
        p.filmType = FilmType::KODAK_GOLD_200;
        p.shadowNoiseIntensity = 0.08f;
        p.crushedBlacks = 0.05f;
        p.applyDateStamp = true;
        p.dateStampStyle = DateStampStyle::YELLOW_CLASSIC;

    } else if (presetId == "digicam_2003") {
        p.grain = 0.15f;
        p.vignette = 0.25f;
        p.vignetteMid = 0.55f;
        p.vignetteOuter = 1.0f;
        p.fade = 0.03f;
        p.saturation = 1.15f;
        p.contrast = 1.05f;
        p.halation = 0.0f;
        p.chromaticAberration = 0.002f;
        p.barrel = 0.0f;
        p.tintShadow[0] = 0.0f; p.tintShadow[1] = 0.0f; p.tintShadow[2] = 0.02f;
        p.tintHighlight[0] = 0.0f; p.tintHighlight[1] = 0.0f; p.tintHighlight[2] = 0.01f;
        p.filmType = FilmType::SONY_CCD_EARLY;
        p.shadowNoiseIntensity = 0.15f;
        p.crushedBlacks = 0.0f;
        p.applyDateStamp = true;
        p.dateStampStyle = DateStampStyle::WHITE_LCD;
    }

    return p;
}

// ── Main Pipeline ──────────────────────────────────────────────────────

bool FilterPipeline::process(
    const cv::Mat& inputRgba,
    cv::Mat& outputRgba,
    const FilterParams& params,
    long timestamp,
    const std::string& presetId
) {
    try {
        // Merge preset-specific params with caller-provided overrides
        FilterParams effective = params;
        if (!presetId.empty() && params.filmType == FilmType::NONE
            && params.grain == 0.0f && params.vignette == 0.0f) {
            // Caller passed zeroes — fill from preset
            effective = paramsForPreset(presetId);
        }

        // Copy input to output
        if (&inputRgba != &outputRgba) {
            inputRgba.copyTo(outputRgba);
        }

        // 1. Barrel distortion
        if (std::abs(effective.barrel) > 0.001f) {
            applyBarrelDistortion(outputRgba, effective.barrel);
        }

        // 2. Chromatic aberration
        if (effective.chromaticAberration > 0.0001f) {
            applyChromaticAberration(outputRgba, effective.chromaticAberration);
        }

        // 3. Film response curve
        if (effective.filmType != FilmType::NONE) {
            ColorScience::applyFilmResponse(outputRgba, effective.filmType);
        }

        // 4. Tint split (shadow/highlight)
        bool hasTintShadow = effective.tintShadow[0] != 0.0f
                          || effective.tintShadow[1] != 0.0f
                          || effective.tintShadow[2] != 0.0f;
        bool hasTintHighlight = effective.tintHighlight[0] != 0.0f
                             || effective.tintHighlight[1] != 0.0f
                             || effective.tintHighlight[2] != 0.0f;
        if (hasTintShadow || hasTintHighlight) {
            ColorScience::applyTintSplit(
                outputRgba, effective.tintShadow, effective.tintHighlight);
        }

        // 5. Saturation & contrast
        ColorScience::adjustSaturation(outputRgba, effective.saturation);
        ColorScience::adjustContrast(outputRgba, effective.contrast);

        // 6. Vignette
        if (effective.vignette > 0.001f) {
            applyVignette(outputRgba, effective.vignette,
                          effective.vignetteMid, effective.vignetteOuter);
        }

        // 7. Halation (two-pass)
        if (effective.halation > 0.001f) {
            applyHalation(outputRgba, effective.halation);
        }

        // 8. Grain
        if (effective.grain > 0.001f) {
            int seed = static_cast<int>(timestamp % 100000);
            GrainGenerator::overlayGrain(
                outputRgba, effective.grain, seed, GrainSize::MEDIUM);
        }

        // 9. Fade
        if (effective.fade > 0.001f) {
            ColorScience::applyFade(outputRgba, effective.fade);
        }

        // 10. Crushed blacks
        if (effective.crushedBlacks > 0.001f) {
            ColorScience::crushBlacks(outputRgba, effective.crushedBlacks);
        }

        // 11. Date stamp
        if (effective.applyDateStamp && effective.dateStampStyle != DateStampStyle::NONE) {
            applyDateStamp(outputRgba, timestamp, effective.dateStampStyle);
        }

        return true;
    } catch (const std::exception& e) {
        (void)e;
        return false;
    }
}

// ── Vignette ───────────────────────────────────────────────────────────

void FilterPipeline::applyVignette(
    cv::Mat& img, float strength, float mid, float outer
) {
    int w = img.cols;
    int h = img.rows;
    float cx = w / 2.0f;
    float cy = h / 2.0f;
    float maxDist = std::sqrt(cx * cx + cy * cy);

    // Create vignette mask (grayscale gradient)
    cv::Mat mask(h, w, CV_32FC1);
    for (int r = 0; r < h; ++r) {
        float* row = mask.ptr<float>(r);
        for (int c = 0; c < w; ++c) {
            float dx = c - cx;
            float dy = r - cy;
            float dist = std::sqrt(dx * dx + dy * dy) / maxDist;

            // Smoothstep between mid and outer
            float t = std::clamp((dist - mid) / (outer - mid), 0.0f, 1.0f);
            // Smoothstep for softer falloff
            row[c] = 1.0f - strength * (t * t * (3.0f - 2.0f * t));
        }
    }

    // Apply mask to each channel
    std::vector<cv::Mat> channels(3);
    cv::split(img, channels);
    for (int c = 0; c < 3; ++c) {
        cv::Mat chFloat;
        channels[c].convertTo(chFloat, CV_32F);
        cv::multiply(chFloat, mask, chFloat);
        chFloat.convertTo(channels[c], CV_8U);
    }
    cv::merge(channels, img);
}

// ── Halation ───────────────────────────────────────────────────────────

void FilterPipeline::applyHalation(cv::Mat& img, float strength) {
    // Two-pass halation:
    //   1. Extract bright pixels (above threshold)
    //   2. Blur them
    //   3. Add back to original with strength blend

    cv::Mat gray;
    cv::cvtColor(img, gray, cv::COLOR_RGB2GRAY);

    // Threshold to extract highlights
    cv::Mat highlights;
    cv::threshold(gray, highlights, 200, 255, cv::THRESH_BINARY);
    highlights.convertTo(highlights, CV_32F, 1.0 / 255.0);

    // Gaussian blur the highlight mask
    cv::Mat blurred;
    int blurSize = static_cast<int>(20.0f * strength + 1.0f);
    blurSize = (blurSize % 2 == 0) ? blurSize + 1 : blurSize;
    cv::GaussianBlur(highlights, blurred, cv::Size(blurSize, blurSize), 0);

    // Add halation to each channel (warm — more red, slightly less green)
    std::vector<cv::Mat> channels(3);
    cv::split(img, channels);

    float halationRGB[3] = {
        strength * 0.6f,  // Red — strongest (warm halation)
        strength * 0.3f,  // Green
        strength * 0.1f,  // Blue — minimal
    };

    for (int c = 0; c < 3; ++c) {
        cv::Mat chFloat;
        channels[c].convertTo(chFloat, CV_32F);
        chFloat += blurred * (halationRGB[c] * 100.0f);
        chFloat.convertTo(channels[c], CV_8U);
    }
    cv::merge(channels, img);
}

// ── Chromatic Aberration ───────────────────────────────────────────────

void FilterPipeline::applyChromaticAberration(cv::Mat& img, float amount) {
    if (amount < 0.0001f) return;

    int shiftPx = static_cast<int>(amount * img.cols);
    if (shiftPx < 1) shiftPx = 1;

    std::vector<cv::Mat> channels(3);
    cv::split(img, channels);

    // Shift red channel right, blue channel left
    cv::Mat rShifted, bShifted;

    cv::Mat rT = (cv::Mat_<float>(2, 3) << 1, 0, shiftPx, 0, 1, 0);
    cv::warpAffine(channels[0], rShifted, rT, cv::Size(img.cols, img.rows),
                   cv::INTER_LINEAR, cv::BORDER_REPLICATE);

    cv::Mat bT = (cv::Mat_<float>(2, 3) << 1, 0, -shiftPx, 0, 1, 0);
    cv::warpAffine(channels[2], bShifted, bT, cv::Size(img.cols, img.rows),
                   cv::INTER_LINEAR, cv::BORDER_REPLICATE);

    channels[0] = rShifted;
    channels[2] = bShifted;

    cv::merge(channels, img);
}

// ── Barrel Distortion ──────────────────────────────────────────────────

void FilterPipeline::applyBarrelDistortion(cv::Mat& img, float k) {
    if (std::abs(k) < 0.001f) return;

    float cx = img.cols / 2.0f;
    float cy = img.rows / 2.0f;

    cv::Mat cameraMatrix = (cv::Mat_<double>(3, 3) <<
        img.cols, 0, cx,
        0, img.cols, cy,
        0, 0, 1);

    cv::Mat distCoeffs = (cv::Mat_<double>(1, 4) << k, 0, 0, 0);

    cv::Mat undistorted;
    cv::undistort(img, undistorted, cameraMatrix, distCoeffs);
    undistorted.copyTo(img);
}

// ── Date Stamp ─────────────────────────────────────────────────────────

void FilterPipeline::applyDateStamp(
    cv::Mat& img, long timestamp, DateStampStyle style
) {
    // Convert timestamp to date string
    time_t t = timestamp / 1000;
    struct tm* tm = localtime(&t);
    char buf[32];
    strftime(buf, sizeof(buf), "%Y.%m.%d", tm);

    cv::Scalar color;
    int thickness;

    switch (style) {
        case DateStampStyle::RED_LED:
            color = cv::Scalar(255, 0, 0);  // BGRA: blue=0, green=0, red=255
            thickness = 1;
            break;
        case DateStampStyle::YELLOW_CLASSIC:
            color = cv::Scalar(0, 255, 255); // Yellow
            thickness = 2;
            break;
        case DateStampStyle::WHITE_LCD:
            color = cv::Scalar(255, 255, 255); // White
            thickness = 2;
            break;
        default:
            return;
    }

    // Position: bottom-left with margin
    int x = static_cast<int>(img.cols * 0.04);
    int y = static_cast<int>(img.rows * 0.93);
    double fontScale = img.rows * 0.0012;
    int fontFace = cv::FONT_HERSHEY_SIMPLEX;

    // Draw shadow
    cv::putText(img, buf, cv::Point(x + 1, y + 1), fontFace,
                fontScale, cv::Scalar(0, 0, 0), thickness); // Black shadow
    // Draw text
    cv::putText(img, buf, cv::Point(x, y), fontFace,
                fontScale, color, thickness);
}

} // namespace vintagecam
