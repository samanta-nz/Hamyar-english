cmake_minimum_required(VERSION 3.22.1)
project(kokoro_android LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- 16 KB page alignment (Android 15 ready) -------------------------------
add_link_options(
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384")

# --- espeak-ng submodule at repo root --------------------------------------
# jni dir = library/src/main/jni  →  four levels up is the repo root.
set(ESPEAK_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../espeak-ng)

# Build ONLY the espeak-ng library (G2P text→IPA). The espeak-ng-data (phoneme
# dictionaries, ~15 MB) is NOT generated here — that step needs a host-native
# espeak-ng binary, impossible when cross-compiling. It ships prebuilt in the AAR
# assets and is extracted at first launch (see KokoroInitializer). EXCLUDE_FROM_ALL
# keeps espeak-ng's CLI / data / test targets out of the build.
set(USE_ASYNC        OFF CACHE BOOL "" FORCE)
set(USE_MBROLA       OFF CACHE BOOL "" FORCE)
set(USE_LIBSONIC     OFF CACHE BOOL "" FORCE)
set(USE_LIBPCAUDIO   OFF CACHE BOOL "" FORCE)
set(USE_KLATT        OFF CACHE BOOL "" FORCE)
set(USE_SPEECHPLAYER OFF CACHE BOOL "" FORCE)
set(EXTRA_cmn        OFF CACHE BOOL "" FORCE)
set(ESPEAK_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS  ON  CACHE BOOL "" FORCE)
add_subdirectory(${ESPEAK_DIR} ${CMAKE_CURRENT_BINARY_DIR}/espeak-ng EXCLUDE_FROM_ALL)

# --- JNI bridge → libkokoro_jni.so -----------------------------------------
# Native side is espeak-ng only (text → IPA phonemes). ONNX inference runs in
# Kotlin via onnxruntime-android.
add_library(kokoro_jni SHARED kokoro_jni.cpp)
find_library(log-lib log)

target_include_directories(kokoro_jni PRIVATE
    ${ESPEAK_DIR}/src/include
    ${ESPEAK_DIR}/src/include/espeak-ng
    # ucd-tools, needed even though this file uses none of it. Linking espeak-ng puts
    # its src/include/compat on our include path, and that directory holds a wctype.h
    # which SHADOWS the NDK's own: libc++ <string> -> <cwchar> -> <cwctype> lands on
    # espeak-ng's compat header, which includes <ucd/ucd.h>. Without this directory the
    # translation unit fails to compile on a clean checkout.
    ${ESPEAK_DIR}/src/ucd-tools/src/include)

# espeak-ng's shared library target is named "espeak-ng".
target_link_libraries(kokoro_jni
    espeak-ng
    ${log-lib})
