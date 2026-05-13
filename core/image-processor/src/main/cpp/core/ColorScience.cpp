#include "core/ColorScience.h"
#include <opencv2/imgproc.hpp>
#include <cmath>
#include <algorithm>
#include <string>
#include <ctime>

namespace vintagecam {

// ── Film Response ──────────────────────────────────────────────────────

static std::vector<uint8_t> buildSLut(float shadow, float mid, float highlight) {
    std::vector<uint8_t> lut(256);
    for (int i = 0; i < 256; ++i) {
        float t = i / 255.0f;

        // Shoulder-toe S-curve using rational function
        float numerator = t * (shadow + t * (mid + t * highlight));
        float denominator = shadow + t * (mid + t * highlight);
        float val = numerator / std::max(denominator, 0.001f);

        lut[i] = static_cast<uint8_t>(std::clamp(static_cast<int>(val * 255.0f), 0, 255));
    }
    return lut;
}

void ColorScience::applyFilmResponse(cv::Mat& rgb, FilmType type) {
    auto lut = filmResponseLut(type);
    std::vector<cv::Mat> channels(3);
    cv::split(rgb, channels);

    // Apply to R, G, B (first 3 channels)
    for (int c = 0; c < 3; ++c) {
        cv::LUT(channels[c], lut, channels[c]);
    }

    cv::merge(channels, rgb);
}

std::vector<uint8_t> ColorScience::filmResponseLut(FilmType type) {
    switch (type) {
        case FilmType::KODAK_GOLD_200:
            // Warm, slightly lifted shadows, moderate contrast
            return buildSLut(0.08f, 1.1f, -0.15f);
        case FilmType::KODAK_PORTRA_400:
            // Flat, low contrast, lifted blacks (pastel)
            return buildSLut(0.15f, 0.9f, -0.05f);
        case FilmType::KODAK_TRI_X_400:
            // High contrast B&W curve (applied via luminance)
            return buildSLut(0.02f, 1.4f, -0.3f);
        case FilmType::KODAK_EKTACHROME_E100:
            // Cool shadows, vivid saturation, high contrast
            return buildSLut(0.05f, 1.2f, -0.2f);
        case FilmType::FUJI_VELVIA_50:
            // Extremely saturated, deep shadows, punchy
            return buildSLut(0.02f, 1.5f, -0.3f);
        case FilmType::FUJI_PRO_400H:
            // Cool, pastel, cyan-leaning shadows
            return buildSLut(0.12f, 0.85f, 0.0f);
        case FilmType::FUJI_SUPERIA_XTRA_400:
            // Consumer film: boosted saturation, moderate contrast
            return buildSLut(0.06f, 1.15f, -0.1f);
        case FilmType::ILFORD_HP5:
            // Classic B&W: rich midtones
            return buildSLut(0.04f, 1.3f, -0.25f);
        case FilmType::POLAROID_600:
            // Faded, low contrast, green-leaning
            return buildSLut(0.2f, 0.7f, 0.05f);
        case FilmType::SONY_CCD_EARLY:
            // Early digital: clipped highlights, color shifts
            return buildSLut(0.01f, 1.0f, -0.4f);
        case FilmType::MODERN_CMOS:
            // Almost linear, subtle S-curve
            return buildSLut(0.05f, 1.05f, -0.08f);
        case FilmType::NONE:
        default:
            // Identity
            return buildSLut(0.0f, 1.0f, 0.0f);
    }
}

// ── White Balance ──────────────────────────────────────────────────────

void ColorScience::applyWhiteBalance(cv::Mat& rgb, float tempK, float tint) {
    // Simplified: convert Kelvin to RGB multipliers
    float rMul, gMul, bMul;
    float t = (tempK - 2000.0f) / 8000.0f; // 0.0 at 2000K, 1.0 at 10000K
    t = std::clamp(t, 0.0f, 1.0f);

    rMul = lerp(0.6f, 1.4f, t);
    gMul = 1.0f + tint * 0.3f;
    bMul = lerp(1.6f, 0.7f, t);

    std::vector<cv::Mat> channels(3);
    cv::split(rgb, channels);

    // Multiply each channel
    channels[0] = channels[0] * rMul;
    channels[1] = channels[1] * gMul;
    channels[2] = channels[2] * bMul;

    cv::merge(channels, rgb);
}

// ── Color Matrix ───────────────────────────────────────────────────────

void ColorScience::applyColorMatrix(cv::Mat& rgb, const float* matrix3x3) {
    // 3x3 matrix: new_R = m00*R + m01*G + m02*B, etc.
    cv::Mat m(3, 3, CV_32F);
    for (int i = 0; i < 9; ++i) {
        ((float*)m.data)[i] = matrix3x3[i];
    }
    cv::transform(rgb, rgb, m);
}

// ── Per-channel LUT ────────────────────────────────────────────────────

void ColorScience::applyCurve(cv::Mat& channel, const std::vector<uint8_t>& lut) {
    cv::LUT(channel, lut, channel);
}

// ── Crush Blacks ───────────────────────────────────────────────────────

void ColorScience::crushBlacks(cv::Mat& rgb, float strength) {
    if (strength <= 0.0f) return;

    std::vector<uint8_t> lut(256);
    float threshold = strength * 64.0f; // 0-64 pixel value range
    for (int i = 0; i < 256; ++i) {
        if (i < threshold) {
            lut[i] = 0;
        } else if (i < threshold * 2) {
            float t = (i - threshold) / threshold;
            lut[i] = static_cast<uint8_t>(t * i);
        } else {
            lut[i] = i;
        }
    }

    std::vector<cv::Mat> channels(3);
    cv::split(rgb, channels);
    for (int c = 0; c < 3; ++c) {
        cv::LUT(channels[c], lut, channels[c]);
    }
    cv::merge(channels, rgb);
}

// ── Saturation ─────────────────────────────────────────────────────────

void ColorScience::adjustSaturation(cv::Mat& rgb, float factor) {
    if (std::abs(factor - 1.0f) < 0.001f) return;

    cv::Mat hsv;
    cv::cvtColor(rgb, hsv, cv::COLOR_RGB2HSV);

    std::vector<cv::Mat> hsvChannels(3);
    cv::split(hsv, hsvChannels);

    // Multiply saturation channel
    hsvChannels[1] = hsvChannels[1] * factor;

    cv::merge(hsvChannels, hsv);
    cv::cvtColor(hsv, rgb, cv::COLOR_HSV2RGB);
}

// ── Contrast ───────────────────────────────────────────────────────────

void ColorScience::adjustContrast(cv::Mat& rgb, float factor) {
    if (std::abs(factor - 1.0f) < 0.001f) return;

    // Contrast = (pixel - 128) * factor + 128
    rgb.convertTo(rgb, -1, factor, 128.0f * (1.0f - factor));
}

// ── Tint Split ─────────────────────────────────────────────────────────

void ColorScience::applyTintSplit(
    cv::Mat& rgb,
    const float tintShadow[3],
    const float tintHighlight[3]
) {
    // Compute luminance to mask shadow vs highlight regions
    cv::Mat gray;
    cv::cvtColor(rgb, gray, cv::COLOR_RGB2GRAY);
    cv::Mat luminance;
    gray.convertTo(luminance, CV_32F, 1.0 / 255.0);

    std::vector<cv::Mat> channels(3);
    cv::split(rgb, channels);

    for (int c = 0; c < 3; ++c) {
        cv::Mat chFloat;
        channels[c].convertTo(chFloat, CV_32F);

        // shadowMask = 1 - luminance (dark areas get shadow tint)
        // highlightMask = luminance (bright areas get highlight tint)
        cv::Mat shadowMask = 1.0f - luminance;
        cv::Mat result = chFloat + shadowMask * (tintShadow[c] * 255.0f)
                                + luminance * (tintHighlight[c] * 255.0f);

        result.convertTo(channels[c], CV_8U);
    }

    cv::merge(channels, rgb);
}

// ── Fade (Black Lift) ──────────────────────────────────────────────────

void ColorScience::applyFade(cv::Mat& rgb, float strength) {
    if (strength <= 0.0f) return;

    // Add constant offset to all channels (lifts blacks toward gray)
    int offset = static_cast<int>(strength * 40.0f);
    rgb += cv::Scalar(offset, offset, offset, 0);
}

// ── Utility ────────────────────────────────────────────────────────────

float ColorScience::lerp(float a, float b, float t) {
    return a + t * (b - a);
}

} // namespace vintagecam
