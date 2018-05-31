/*
    SDL_android_main.c, placed in the public domain by Sam Lantinga  3/13/14
*/
#include "../../SDL_internal.h"

#ifdef __ANDROID__

/* Include the SDL main definition header */
#include "SDL_main.h"

/*******************************************************************************
                 Functions called by JNI
*******************************************************************************/
#include <jni.h>

/* Called before SDL_main() to initialize JNI bindings in SDL library */
extern void SDL_Android_Init(JNIEnv* env, jclass cls);

/* Start up the SDL app */
jint Java_com_example_pcapdecoder_activity_SDLActivity_nativeInitYUV(JNIEnv* env, jclass cls,
                                            jstring input_jstr,jint pixel_w,jint pixel_h,jint pixel_type,jint fps)
{
    /* This interface could expand with ABI negotiation, calbacks, etc. */
    SDL_Android_Init(env, cls);

    SDL_SetMainReady();

    /* Run the application code! */
    int status;
    char *argv[5];
    char input_str[500]={0};
    char str_pixel_w[20]={0};
    char str_pixel_h[20]={0};
    char str_pixel_type[20]={0};
    char str_fps[20]={0};
    sprintf(input_str,"%s",(*env)->GetStringUTFChars(env,input_jstr, NULL));
    sprintf(str_pixel_w,"%d",pixel_w);
    sprintf(str_pixel_h,"%d",pixel_h);
    sprintf(str_pixel_type,"%d",pixel_type);
    sprintf(str_fps,"%d",fps);
    argv[0] = SDL_strdup(input_str);
    argv[1] = SDL_strdup(str_pixel_w);
    argv[2] = SDL_strdup(str_pixel_h);
    argv[3] = SDL_strdup(str_pixel_type);
    argv[4] = SDL_strdup(str_fps);
    return SDL_main(env, 5, argv);

    /* Do not issue an exit or the whole application will terminate instead of just the SDL thread */
    /* exit(status); */
}

jint Java_com_example_pcapdecoder_activity_SDLActivity_nativeInit(JNIEnv* env, jclass cls, jstring input_jstr)
{
    /* This interface could expand with ABI negotiation, calbacks, etc. */
    SDL_Android_Init(env, cls);

    SDL_SetMainReady();

    /* Run the application code! */
    int status;
    char *argv[1];
    char input_str[500]={0};
    sprintf(input_str,"%s",(*env)->GetStringUTFChars(env,input_jstr, NULL));
    argv[0] = SDL_strdup(input_str);
    return SDL_main(env, 1, argv);

    /* Do not issue an exit or the whole application will terminate instead of just the SDL thread */
    /* exit(status); */
}
#endif /* __ANDROID__ */

/* vi: set ts=4 sw=4 expandtab: */
