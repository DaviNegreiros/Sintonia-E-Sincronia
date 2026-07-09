cmake_minimum_required(VERSION 3.22.1)

project(sincronia_yuv)

set(UNIT_TEST OFF CACHE BOOL "Disable libyuv tests" FORCE)
add_subdirectory(libyuv)

add_library(
    sincronia_yuv
    SHARED
    LibyuvBridge.cpp
)

target_include_directories(
    sincronia_yuv
    PRIVATE
    libyuv/include
)

target_link_libraries(
    sincronia_yuv
    yuv
    log
)
