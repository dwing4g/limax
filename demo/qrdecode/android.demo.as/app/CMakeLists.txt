# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/native-lib.cpp
             ../../../../cpp/limax/source/QRCODE.c )

set(OpenCV_DIR "D:/Android/opencv-3.4.1-android-sdk/OpenCV-android-sdk/sdk/")

include_directories(${OpenCV_DIR}/native/jni/include )
include_directories(../../../../cpp/limax/include)

add_library(libopencv_core STATIC IMPORTED )
set_target_properties(libopencv_core PROPERTIES
    IMPORTED_LOCATION "${OpenCV_DIR}/native/staticlibs/${ANDROID_ABI}/libopencv_core.a")
add_library(libopencv_imgproc STATIC IMPORTED )
set_target_properties(libopencv_imgproc PROPERTIES
    IMPORTED_LOCATION "${OpenCV_DIR}/native/staticlibs/${ANDROID_ABI}/libopencv_imgproc.a")
add_library(libopencv_features2d STATIC IMPORTED )
set_target_properties(libopencv_features2d PROPERTIES
    IMPORTED_LOCATION "${OpenCV_DIR}/native/staticlibs/${ANDROID_ABI}/libopencv_features2d.a")
add_library(libopencv_dnn STATIC IMPORTED )
set_target_properties(libopencv_dnn PROPERTIES
    IMPORTED_LOCATION "${OpenCV_DIR}/native/staticlibs/${ANDROID_ABI}/libopencv_dnn.a")

add_library(libcpufeatures STATIC IMPORTED )
set_target_properties(libcpufeatures PROPERTIES
    IMPORTED_LOCATION "${OpenCV_DIR}/native/3rdparty/libs/${ANDROID_ABI}/libcpufeatures.a")
add_library(libtegra_hal STATIC IMPORTED )
set_target_properties(libtegra_hal PROPERTIES
    IMPORTED_LOCATION "${OpenCV_DIR}/native/3rdparty/libs/${ANDROID_ABI}/libtegra_hal.a")
add_library(libIlmImf STATIC IMPORTED )
set_target_properties(libIlmImf PROPERTIES
    IMPORTED_LOCATION "${OpenCV_DIR}/native/3rdparty/libs/${ANDROID_ABI}/libIlmImf.a")
add_library(libtbb STATIC IMPORTED )
set_target_properties(libtbb PROPERTIES
    IMPORTED_LOCATION "${OpenCV_DIR}/native/3rdparty/libs/${ANDROID_ABI}/libtbb.a")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11 -fexceptions -frtti -lz")

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       native-lib
                       libopencv_imgproc libopencv_dnn libopencv_core
                       libcpufeatures libtegra_hal libtbb
                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )