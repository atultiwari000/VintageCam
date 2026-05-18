cmake_minimum_required(VERSION 3.22.1)

project("vintagecam_processor")

add_library(vintagecam_processor SHARED
    src/main/cpp/jni/NativeImageProcessor.cpp
)

target_compile_options(vintagecam_processor PRIVATE
    -std=c++17
    -O3
    -fexceptions
    -frtti
)

target_link_libraries(vintagecam_processor
    android
    log
    jnigraphics
)
