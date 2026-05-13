#ifndef VINTAGECAM_BURST_PROCESSOR_H
#define VINTAGECAM_BURST_PROCESSOR_H

#include <opencv2/core.hpp>
#include <vector>

namespace vintagecam {

/**
 * Burst photography: alignment, merge, and tone mapping for HDR.
 */
class BurstProcessor {
public:
    enum class AlignmentMode {
        ECC = 0,           // cv::findTransformECC (rigid/affine)
        OPTICAL_FLOW = 1,  // cv::calcOpticalFlowPyrLK (feature-based)
        NONE = 2,          // No alignment (fast, static scenes)
    };

    /**
     * Align and merge a burst of frames using Mertens fusion.
     *
     * @param frames  RGBA frames (same dimensions)
     * @param mode    Alignment strategy
     * @return        Merged RGBA frame
     */
    static cv::Mat mergeBurst(
        const std::vector<cv::Mat>& frames,
        AlignmentMode mode = AlignmentMode::ECC
    );

    /**
     * Reinhard global tone mapping operator.
     * Converts HDR linear to displayable LDR.
     */
    static void toneMapReinhard(cv::Mat& hdr);

    /**
     * Filmic tone mapping (ACES-style).
     * More contrast than Reinhard with better highlight rolloff.
     */
    static void toneMapFilmic(cv::Mat& hdr);

private:
    static cv::Mat alignECC(const cv::Mat& ref, const cv::Mat& src);
    static cv::Mat alignOpticalFlow(const cv::Mat& ref, const cv::Mat& src);
};

} // namespace vintagecam

#endif // VINTAGECAM_BURST_PROCESSOR_H
