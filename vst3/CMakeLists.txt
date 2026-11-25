cmake_minimum_required(VERSION 3.25)
project(CbspVocalReduction VERSION 1.0.0 LANGUAGES CXX)

option(CBSP_BUILD_PLUGIN "Build the VST3 bundle" ON)
set(VST3_SDK_DIR "" CACHE PATH "Steinberg VST3 SDK checkout")

find_package(Java 21 REQUIRED COMPONENTS Development)
find_package(JNI REQUIRED COMPONENTS JVM)
find_package(Threads REQUIRED)

set(dsp_source "${CMAKE_CURRENT_SOURCE_DIR}/../src/main/java/cbsp/lab08/Vst3Dsp.java")
set(dsp_classes "${CMAKE_CURRENT_BINARY_DIR}/java")
set(dsp_header "${CMAKE_CURRENT_BINARY_DIR}/Vst3DspClass.h")
add_custom_command(OUTPUT "${dsp_header}"
    COMMAND "${CMAKE_COMMAND}" -E make_directory "${dsp_classes}"
    COMMAND "${Java_JAVAC_EXECUTABLE}" --release 21 -d "${dsp_classes}" "${dsp_source}"
    COMMAND "${CMAKE_COMMAND}"
        "-DCLASS_FILE=${dsp_classes}/cbsp/lab08/Vst3Dsp.class" "-DOUTPUT_FILE=${dsp_header}"
        -P "${CMAKE_CURRENT_SOURCE_DIR}/EmbedClass.cmake"
    DEPENDS "${dsp_source}" "${CMAKE_CURRENT_SOURCE_DIR}/EmbedClass.cmake"
    VERBATIM)

add_library(cbsp_jni STATIC JavaDsp.cpp "${dsp_header}")
set_target_properties(cbsp_jni PROPERTIES POSITION_INDEPENDENT_CODE ON CXX_VISIBILITY_PRESET hidden)
target_compile_features(cbsp_jni PUBLIC cxx_std_17)
target_include_directories(cbsp_jni PRIVATE "${CMAKE_CURRENT_BINARY_DIR}")
target_link_libraries(cbsp_jni PUBLIC JNI::JNI JNI::JVM Threads::Threads)

if(CBSP_BUILD_PLUGIN)
    if(NOT EXISTS "${VST3_SDK_DIR}/public.sdk/source/main/pluginfactory.h")
        message(FATAL_ERROR "Set VST3_SDK_DIR to the Steinberg SDK with its Git submodules.")
    endif()
    set(SMTG_ENABLE_VST3_PLUGIN_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(SMTG_ENABLE_VST3_HOSTING_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(SMTG_ENABLE_VSTGUI_SUPPORT OFF CACHE BOOL "" FORCE)
    set(SMTG_CREATE_PLUGIN_LINK OFF CACHE BOOL "" FORCE)
    set(SMTG_RUN_VST_VALIDATOR OFF CACHE BOOL "" FORCE)
    set(public_sdk_SOURCE_DIR "${VST3_SDK_DIR}/public.sdk")
    add_subdirectory("${VST3_SDK_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/sdk")
    smtg_add_vst3plugin(CbspVocalReduction Plugin.cpp)
    target_link_libraries(CbspVocalReduction PRIVATE sdk cbsp_jni)
    if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
        # The embedded JVM and attached host threads may outlive a plug-in instance.
        target_link_options(CbspVocalReduction PRIVATE "LINKER:-z,nodelete")
    elseif(APPLE)
        smtg_target_set_bundle(CbspVocalReduction
            BUNDLE_IDENTIFIER "org.cbsp.lab08.vocal-reduction" COMPANY_NAME "CBSP")
    endif()
endif()

include(CTest)
if(BUILD_TESTING)
    add_executable(cbsp_jni_check JavaDspCheck.cpp)
    target_link_libraries(cbsp_jni_check PRIVATE cbsp_jni)
    add_test(NAME JNI-DSP COMMAND cbsp_jni_check)
endif()
