cmake_minimum_required(VERSION 3.22.1)

project("vintagecam_processor")

# ── OpenCV ──────────────────────────────────────────────────────────────
# Set OPENCV_SDK to the root of OpenCV-android-sdk (e.g.
# OPENCV_SDK=~/OpenCV-android-sdk/sdk/native/jni)
# The prebuilt .so files must also be placed in
# src/main/jniLibs/<abi>/ for APK packaging.
if(NOT DEFINED OPENCV_SDK)
    # Allow OPENCV_SDK to be provided via environment or CMake cache.
    if(DEFINED ENV{OPENCV_SDK})
        set(OPENCV_SDK "$ENV{OPENCV_SDK}" CACHE PATH "Path to OpenCV Android SDK jni directory")
    else()
        set(OPENCV_SDK "${CMAKE_SOURCE_DIR}/../../../../../../OpenCV-android-sdk/sdk/native/jni"
            CACHE PATH "Path to OpenCV Android SDK jni directory")
    endif()
endif()

if(EXISTS "${OPENCV_SDK}")
    include_directories("${OPENCV_SDK}/include")
    set(OpenCV_DIR "${OPENCV_SDK}")
endif()

# Prefer an explicit OpenCV_DIR if provided in the environment (OpenCVConfig.cmake path)
if(DEFINED ENV{OpenCV_DIR})
    set(OpenCV_DIR "$ENV{OpenCV_DIR}")
endif()

message(STATUS "OpenCV_DIR=${OpenCV_DIR}")

find_package(OpenCV REQUIRED COMPONENTS core imgproc photo calib3d)

# ── Native source files ─────────────────────────────────────────────────

# JNI bridge
add_library(vintagecam_processor SHARED
    jni/NativeImageProcessor.cpp
    core/YuvConverter.cpp
    core/ColorScience.cpp
    core/GrainGenerator.cpp
    core/FilterPipeline.cpp
    core/BurstProcessor.cpp
    utils/MemoryPool.cpp
)

# ── Include paths ──────────────────────────────────────────────────────
target_include_directories(vintagecam_processor PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"
)

# ── Compiler flags ─────────────────────────────────────────────────────
target_compile_options(vintagecam_processor PRIVATE
    -std=c++17
    -O3
    -fexceptions
    -frtti
    -Wno-deprecated-declarations
)

# ── Link libraries ─────────────────────────────────────────────────────
target_link_libraries(vintagecam_processor
    ${OpenCV_LIBS}
    android
    log
    jnigraphics
)
