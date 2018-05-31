LOCAL_PATH := $(call my-dir)

###########################
#
# SDL shared library
#
###########################

include $(CLEAR_VARS)

LOCAL_MODULE := SDL2

LOCAL_ARM_MODE=arm

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include \

LOCAL_EXPORT_C_INCLUDES := $(LOCAL_C_INCLUDES)

LOCAL_SRC_FILES := \
	$(subst $(LOCAL_PATH)/,, \
	$(wildcard $(LOCAL_PATH)/src/*.c) \
	$(wildcard $(LOCAL_PATH)/src/audio/*.c) \
	$(wildcard $(LOCAL_PATH)/src/audio/android/*.c) \
	$(wildcard $(LOCAL_PATH)/src/audio/dummy/*.c) \
	$(LOCAL_PATH)/src/atomic/SDL_atomic.c \
	$(LOCAL_PATH)/src/atomic/SDL_spinlock.c.arm \
	$(wildcard $(LOCAL_PATH)/src/core/android/*.c) \
	$(wildcard $(LOCAL_PATH)/src/cpuinfo/*.c) \
	$(wildcard $(LOCAL_PATH)/src/dynapi/*.c) \
	$(wildcard $(LOCAL_PATH)/src/events/*.c) \
	$(wildcard $(LOCAL_PATH)/src/file/*.c) \
	$(wildcard $(LOCAL_PATH)/src/haptic/*.c) \
	$(wildcard $(LOCAL_PATH)/src/haptic/dummy/*.c) \
	$(wildcard $(LOCAL_PATH)/src/joystick/*.c) \
	$(wildcard $(LOCAL_PATH)/src/joystick/android/*.c) \
	$(wildcard $(LOCAL_PATH)/src/loadso/dlopen/*.c) \
	$(wildcard $(LOCAL_PATH)/src/power/*.c) \
	$(wildcard $(LOCAL_PATH)/src/power/android/*.c) \
	$(wildcard $(LOCAL_PATH)/src/filesystem/android/*.c) \
	$(wildcard $(LOCAL_PATH)/src/render/*.c) \
	$(wildcard $(LOCAL_PATH)/src/render/*/*.c) \
	$(wildcard $(LOCAL_PATH)/src/stdlib/*.c) \
	$(wildcard $(LOCAL_PATH)/src/thread/*.c) \
	$(wildcard $(LOCAL_PATH)/src/thread/pthread/*.c) \
	$(wildcard $(LOCAL_PATH)/src/timer/*.c) \
	$(wildcard $(LOCAL_PATH)/src/timer/unix/*.c) \
	$(wildcard $(LOCAL_PATH)/src/video/*.c) \
	$(wildcard $(LOCAL_PATH)/src/video/android/*.c) \
	$(wildcard $(LOCAL_PATH)/src/test/*.c))

LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES
LOCAL_LDLIBS := -ldl -lGLESv1_CM -lGLESv2 -llog -landroid

include $(BUILD_SHARED_LIBRARY)

###########################
#
# SDL static library
#
###########################
LOCAL_MODULE := SDL2_static
LOCAL_MODULE_FILENAME := libSDL2
LOCAL_SRC_FILES += $(LOCAL_PATH)/src/main/android/SDL_android_main.c
LOCAL_LDLIBS :=
LOCAL_EXPORT_LDLIBS := -Wl,--undefined=Java_com_example_ffmpegsdlplayer_activity_SDLActivity_nativeInit -ldl -lGLESv1_CM -lGLESv2 -llog -landroid
include $(BUILD_STATIC_LIBRARY)

###########################
#
# FFmpeg shared library
#
###########################
# FFmpeg library
include $(CLEAR_VARS)
LOCAL_MODULE := ffmpeg
LOCAL_SRC_FILES := libffmpeg.so
include $(PREBUILT_SHARED_LIBRARY)

###########################
#
# mediacodec ndk shared library
#
###########################

include $(CLEAR_VARS)
LOCAL_MODULE:= libnative_codec16
LOCAL_SRC_FILES:= libnative_codec16.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE:= libnative_codec17
LOCAL_SRC_FILES:= libnative_codec17.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE:= libnative_codec18
LOCAL_SRC_FILES:= libnative_codec18.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE:= libnative_codec19
LOCAL_SRC_FILES:= libnative_codec19.so
include $(PREBUILT_SHARED_LIBRARY)

###########################
#
# native_video
#
###########################

include $(CLEAR_VARS)
LOCAL_MODULE := native_video
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES := native_video.c
LOCAL_SHARED_LIBRARIES := ffmpeg
LOCAL_LDLIBS := -lGLESv1_CM -lGLESv2 -llog
include $(BUILD_SHARED_LIBRARY)

#libSDL2main=======================================
include $(CLEAR_VARS)
LOCAL_MODULE := SDL2main
SDL_PATH := ./
LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(SDL_PATH)/include
# Add your application source files here...
LOCAL_SRC_FILES := $(SDL_PATH)/src/main/android/SDL_android_main.c \
	$(SDL_PATH)/simplest_ffmpeg_sdl_player.c \
	$(SDL_PATH)/mediacodec_decoder.c \
	$(SDL_PATH)/mediacodec_utils.c \
	$(SDL_PATH)/NativeCodec.cpp
LOCAL_SHARED_LIBRARIES := ffmpeg SDL2
LOCAL_LDLIBS := -lGLESv1_CM -lGLESv2 -llog
include $(BUILD_SHARED_LIBRARY)