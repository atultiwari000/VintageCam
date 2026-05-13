#include "core/BurstProcessor.h"
#include <opencv2/imgproc.hpp>
#include <opencv2/photo.hpp>
#include <opencv2/calib3d.hpp>
#include <algorithm>
#include <cmath>

namespace vintagecam {

// ── mergeBurst ─────────────────────────────────────────────────────────

cv::Mat BurstProcessor::mergeBurst(
    const std::vector<cv::Mat>& frames,
    AlignmentMode mode
) {
    if (frames.empty()) return cv::Mat();
    if (frames.size() == 1) return frames[0].clone();

    // Align all frames to the first (reference) frame
    std::vector<cv::Mat> aligned;
    aligned.push_back(frames[0].clone());

    for (size_t i = 1; i < frames.size(); ++i) {
        switch (mode) {
            case AlignmentMode::ECC:
                aligned.push_back(alignECC(frames[0], frames[i]));
                break;
            case AlignmentMode::OPTICAL_FLOW:
                aligned.push_back(alignOpticalFlow(frames[0], frames[i]));
                break;
            case AlignmentMode::NONE:
                aligned.push_back(frames[i].clone());
                break;
        }
    }

    // Convert to 8-bit 3-channel for Mertens (if RGBA, drop alpha)
    std::vector<cv::Mat> alignedBGR;
    for (auto& f : aligned) {
        if (f.channels() == 4) {
            cv::Mat bgr;
            cv::cvtColor(f, bgr, cv::COLOR_RGBA2BGR);
            alignedBGR.push_back(bgr);
        } else {
            alignedBGR.push_back(f);
        }
    }

    // Mertens exposure fusion
    cv::Ptr<cv::MergeMertens> merge = cv::createMergeMertens();
    cv::Mat resultHDR;
    merge->process(alignedBGR, resultHDR);

    // Tone map to 8-bit
    cv::Mat result8u;
    resultHDR.convertTo(result8u, CV_8U, 255.0);

    // Convert back to RGBA
    cv::Mat resultRGBA;
    if (frames[0].channels() == 4) {
        cv::cvtColor(result8u, resultRGBA, cv::COLOR_BGR2RGBA);
    } else {
        cv::cvtColor(result8u, resultRGBA, cv::COLOR_BGR2RGB);
    }

    return resultRGBA;
}

// ── ECC Alignment ──────────────────────────────────────────────────────

cv::Mat BurstProcessor::alignECC(const cv::Mat& ref, const cv::Mat& src) {
    if (ref.size() != src.size()) return src.clone();

    // Convert to grayscale for alignment
    cv::Mat refGray, srcGray;
    if (ref.channels() >= 3) {
        cv::cvtColor(ref, refGray, cv::COLOR_RGB2GRAY);
        cv::cvtColor(src, srcGray, cv::COLOR_RGB2GRAY);
    } else {
        refGray = ref;
        srcGray = src;
    }

    refGray.convertTo(refGray, CV_32F, 1.0 / 255.0);
    srcGray.convertTo(srcGray, CV_32F, 1.0 / 255.0);

    // Start with identity warp
    cv::Mat warpMatrix = cv::Mat::eye(2, 3, CV_32F);
    cv::TermCriteria criteria(
        cv::TermCriteria::COUNT + cv::TermCriteria::EPS, 100, 1e-5);

    try {
        cv::findTransformECC(
            refGray, srcGray, warpMatrix,
            cv::MOTION_EUCLIDEAN,  // Rotation + translation only (more stable)
            criteria
        );

        cv::Mat aligned;
        cv::warpAffine(
            src, aligned, warpMatrix, ref.size(),
            cv::INTER_LINEAR + cv::WARP_INVERSE_MAP,
            cv::BORDER_REPLICATE
        );
        return aligned;
    } catch (const cv::Exception&) {
        // ECC failed — return unaligned frame
        return src.clone();
    }
}

// ── Optical Flow Alignment ─────────────────────────────────────────────

cv::Mat BurstProcessor::alignOpticalFlow(const cv::Mat& ref, const cv::Mat& src) {
    if (ref.size() != src.size()) return src.clone();

    cv::Mat refGray, srcGray;
    if (ref.channels() >= 3) {
        cv::cvtColor(ref, refGray, cv::COLOR_RGB2GRAY);
        cv::cvtColor(src, srcGray, cv::COLOR_RGB2GRAY);
    } else {
        refGray = ref;
        srcGray = src;
    }

    // Feature-based: Shi-Tomasi corners on reference
    std::vector<cv::Point2f> refCorners;
    cv::goodFeaturesToTrack(refGray, refCorners, 200, 0.01, 10);

    if (refCorners.empty()) return src.clone();

    // Track with Lucas-Kanade optical flow
    std::vector<cv::Point2f> srcCorners;
    std::vector<uint8_t> status;
    std::vector<float> err;
    cv::calcOpticalFlowPyrLK(
        refGray, srcGray, refCorners, srcCorners, status, err,
        cv::Size(21, 21), 3
    );

    // Filter valid matches
    std::vector<cv::Point2f> goodRef, goodSrc;
    for (size_t i = 0; i < status.size(); ++i) {
        if (status[i]) {
            goodRef.push_back(refCorners[i]);
            goodSrc.push_back(srcCorners[i]);
        }
    }

    if (goodRef.size() < 4) return src.clone();

    // Estimate affine transform
    cv::Mat affine = cv::estimateAffinePartial2D(goodSrc, goodRef);
    if (affine.empty()) return src.clone();

    cv::Mat aligned;
    cv::warpAffine(src, aligned, affine, ref.size(),
                   cv::INTER_LINEAR, cv::BORDER_REPLICATE);
    return aligned;
}

// ── Tone Mapping ───────────────────────────────────────────────────────

void BurstProcessor::toneMapReinhard(cv::Mat& hdr) {
    cv::Mat gray;
    cv::cvtColor(hdr, gray, cv::COLOR_BGR2GRAY);

    double minVal, maxVal;
    cv::minMaxLoc(gray, &minVal, &maxVal);
    if (maxVal <= 0.0) return;

    float logAvg = 0.0f;
    int count = hdr.rows * hdr.cols;
    for (int r = 0; r < hdr.rows; ++r) {
        const float* row = gray.ptr<float>(r);
        for (int c = 0; c < hdr.cols; ++c) {
            logAvg += std::log(row[c] + 1e-6f);
        }
    }
    logAvg = std::exp(logAvg / count);

    float scale = 0.18f / logAvg;
    hdr = hdr * scale / (cv::Scalar::all(1.0f) + hdr * scale);
}

void BurstProcessor::toneMapFilmic(cv::Mat& hdr) {
    // ACES-style filmic tone mapping
    // f(x) = (x*(a*x + b)) / (x*(c*x + d) + e)
    const float a = 2.51f;
    const float b = 0.03f;
    const float c = 2.43f;
    const float d = 0.59f;
    const float e = 0.14f;

    std::vector<cv::Mat> channels(3);
    cv::split(hdr, channels);

    for (int ch = 0; ch < 3; ++ch) {
        for (int r = 0; r < hdr.rows; ++r) {
            float* row = channels[ch].ptr<float>(r);
            for (int c = 0; c < hdr.cols; ++c) {
                float x = std::max(row[c], 0.0f);
                row[c] = (x * (a * x + b)) / (x * (c * x + d) + e);
                row[c] = std::clamp(row[c], 0.0f, 1.0f);
            }
        }
    }

    cv::merge(channels, hdr);
}

} // namespace vintagecam
