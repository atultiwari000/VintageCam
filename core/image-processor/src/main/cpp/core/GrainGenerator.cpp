#include "core/GrainGenerator.h"
#include <opencv2/imgproc.hpp>
#include <cmath>
#include <algorithm>

namespace vintagecam {

// ── Public API ─────────────────────────────────────────────────────────

void GrainGenerator::overlayGrain(
    cv::Mat& image,
    float intensity,
    int seed,
    GrainSize size
) {
    if (intensity <= 0.0f || image.empty()) return;

    std::mt19937 rng(seed);
    float grainScale = canonicalGrainScale(size);

    // Convert to YUV so we add noise only to luma (preserves color accuracy)
    cv::Mat yuv;
    cv::cvtColor(image, yuv, cv::COLOR_RGB2YUV);

    std::vector<cv::Mat> yuvChannels(3);
    cv::split(yuv, yuvChannels);

    addGrainToChannel(yuvChannels[0], intensity, rng, grainScale);

    cv::merge(yuvChannels, yuv);
    cv::cvtColor(yuv, image, cv::COLOR_YUV2RGB);
}

cv::Mat GrainGenerator::generateTexture(
    int width, int height,
    float intensity, int seed,
    GrainSize size
) {
    cv::Mat texture(height, width, CV_8UC4, cv::Scalar(0, 0, 0, 255));
    if (intensity <= 0.0f) return texture;

    std::mt19937 rng(seed);
    float grainScale = canonicalGrainScale(size);

    // Generate grayscale noise, then convert to RGBA
    cv::Mat gray(height, width, CV_8UC1);
    for (int r = 0; r < height; ++r) {
        uint8_t* row = gray.ptr<uint8_t>(r);
        for (int c = 0; c < width; ++c) {
            float noise = std::normal_distribution<float>(0.0f, intensity)(rng);
            int val = static_cast<int>(128.0f + noise * 128.0f);
            row[c] = static_cast<uint8_t>(std::clamp(val, 0, 255));
        }
    }

    // Apply grain scale (resize down then up for coarser grains)
    if (grainScale > 1.0f) {
        int smallW = std::max(1, static_cast<int>(width / grainScale));
        int smallH = std::max(1, static_cast<int>(height / grainScale));
        cv::Mat small;
        cv::resize(gray, small, cv::Size(smallW, smallH), 0, 0, cv::INTER_AREA);
        cv::resize(small, gray, cv::Size(width, height), 0, 0, cv::INTER_LINEAR);
    }

    cv::cvtColor(gray, texture, cv::COLOR_GRAY2RGBA);
    return texture;
}

// ── Profile-Specific Grain Types ───────────────────────────────────────

void GrainGenerator::apply35mmFine(cv::Mat& yuv, float intensity) {
    // Fine, uniform grain typical of 35mm film
    std::mt19937 rng(42); // Fixed seed for reproducible look
    addGrainToChannel(yuv, intensity * 0.7f, rng, 1.0f);
}

void GrainGenerator::apply110Chunky(cv::Mat& yuv, float intensity) {
    // Large, blocky grain typical of 110 cartridge film
    std::mt19937 rng(137);
    addGrainToChannel(yuv, intensity * 1.5f, rng, 4.0f);
}

void GrainGenerator::applyVhsBanding(cv::Mat& yuv, float intensity) {
    // Horizontal banding — correlated noise along rows (VHS tape head switching)
    // For simplicity, we apply stronger grain but only vary per-row
    int rows = yuv.rows;
    int cols = yuv.cols;
    std::mt19937 rng(211);

    for (int r = 0; r < rows; ++r) {
        float rowOffset = std::normal_distribution<float>(0.0f, intensity * 30.0f)(rng);
        uint8_t* row = yuv.ptr<uint8_t>(r);
        for (int c = 0; c < cols; ++c) {
            int val = static_cast<int>(row[c] + rowOffset);
            row[c] = static_cast<uint8_t>(std::clamp(val, 0, 255));
        }
    }
}

void GrainGenerator::applyDigitalChromaNoise(cv::Mat& yuv, float intensity) {
    // Early digital camera noise: chroma noise in UV channels (colored speckles)
    cv::Mat yuvCopy;
    cv::cvtColor(yuv, yuvCopy, cv::COLOR_GRAY2BGR); // placeholder; yuv is actually single channel here
    (void)yuvCopy;
    // This method is called on the full YUV image; apply noise to UV channels
    // For now, apply strong noise (digital cameras had chroma noise, not luma)
    std::mt19937 rng(89);
    addGrainToChannel(yuv, intensity * 0.5f, rng, 1.0f);
}

// ── Private ────────────────────────────────────────────────────────────

void GrainGenerator::addGrainToChannel(
    cv::Mat& channel,
    float intensity,
    std::mt19937& rng,
    float grainScale
) {
    int rows = channel.rows;
    int cols = channel.cols;
    float noiseStdDev = intensity * 60.0f;

    // If grainScale > 1, generate low-res noise then upscale
    if (grainScale > 1.0f) {
        int coarseW = std::max(1, static_cast<int>(cols / grainScale));
        int coarseH = std::max(1, static_cast<int>(rows / grainScale));

        cv::Mat coarse(coarseH, coarseW, CV_8UC1);
        for (int r = 0; r < coarseH; ++r) {
            uint8_t* row = coarse.ptr<uint8_t>(r);
            for (int c = 0; c < coarseW; ++c) {
                float noise = std::normal_distribution<float>(0.0f, 1.0f)(rng);
                row[c] = static_cast<uint8_t>(std::clamp(
                    static_cast<int>(128.0f + noise * 128.0f), 0, 255));
            }
        }

        cv::Mat scaled;
        cv::resize(coarse, scaled, cv::Size(cols, rows), 0, 0, cv::INTER_LINEAR);

        // Blend with original
        for (int r = 0; r < rows; ++r) {
            uint8_t* chRow = channel.ptr<uint8_t>(r);
            const uint8_t* nRow = scaled.ptr<uint8_t>(r);
            for (int c = 0; c < cols; ++c) {
                float n = (nRow[c] - 128.0f) / 128.0f;
                int val = static_cast<int>(chRow[c] + n * noiseStdDev);
                chRow[c] = static_cast<uint8_t>(std::clamp(val, 0, 255));
            }
        }
    } else {
        // Per-pixel noise
        for (int r = 0; r < rows; ++r) {
            uint8_t* row = channel.ptr<uint8_t>(r);
            for (int c = 0; c < cols; ++c) {
                float noise = std::normal_distribution<float>(0.0f, 1.0f)(rng);
                int val = static_cast<int>(row[c] + noise * noiseStdDev);
                row[c] = static_cast<uint8_t>(std::clamp(val, 0, 255));
            }
        }
    }
}

float GrainGenerator::canonicalGrainScale(GrainSize size) {
    switch (size) {
        case GrainSize::FINE:   return 1.0f;
        case GrainSize::MEDIUM: return 3.0f;
        case GrainSize::COARSE: return 6.0f;
        default:                return 1.0f;
    }
}

} // namespace vintagecam
