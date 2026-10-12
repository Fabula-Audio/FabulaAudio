cmake_minimum_required(VERSION 3.22.1)

project(fabula_tempo LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(SBSMS "${CMAKE_CURRENT_SOURCE_DIR}/third_party/libsbsms")

if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/tempo.cpp")
    message(FATAL_ERROR "Missing native source: tempo.cpp")
endif()

if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/tempo_jni.cpp")
    message(FATAL_ERROR "Missing native source: tempo_jni.cpp")
endif()

if(NOT EXISTS "${SBSMS}/include/sbsms.h")
    message(FATAL_ERROR "Missing SBSMS header: third_party/libsbsms/include/sbsms.h")
endif()

add_library(fabula_tempo SHARED
    tempo.cpp
    tempo_jni.cpp

    ${SBSMS}/src/buffer.cpp
    ${SBSMS}/src/dBTable.cpp
    ${SBSMS}/src/fft.cpp
    ${SBSMS}/src/grain.cpp
    ${SBSMS}/src/resample.cpp
    ${SBSMS}/src/sbsms.cpp
    ${SBSMS}/src/slide.cpp
    ${SBSMS}/src/sms.cpp
    ${SBSMS}/src/subband.cpp
    ${SBSMS}/src/track.cpp
    ${SBSMS}/src/trackpoint.cpp
)

target_include_directories(fabula_tempo PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"
    "${SBSMS}/include"
)

target_compile_options(fabula_tempo PRIVATE
    -O2
    -Wno-enum-compare
)

target_link_options(fabula_tempo PRIVATE
    "-Wl,-z,max-page-size=16384"
)
