// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <SDL.h>
#include <cstring>
#include <cstdlib>
namespace {
void push(JNIEnv* env, SDL_Event& event) {
    if (env->ExceptionCheck()) return;
    int added = event.common.timestamp ? SDL_PeepEvents(&event, 1, SDL_ADDEVENT, SDL_FIRSTEVENT, SDL_LASTEVENT) : SDL_PushEvent(&event);
    if (added != 1) {
        auto type = env->FindClass("java/lang/IllegalStateException");
        if (type) { env->ThrowNew(type, "Cannot enqueue SDL test input"); env->DeleteLocalRef(type); }
    }
}
}
extern "C" JNIEXPORT void JNICALL Java_org_shiraz_fixture_InputFixture_inject(JNIEnv* env, jclass) {
    SDL_Window* window = nullptr;
    for (Uint32 id = 1; id <= 64 && !window; ++id) window = SDL_GetWindowFromID(id);
    if (!window) {
        auto type = env->FindClass("java/lang/IllegalStateException");
        if (type) { env->ThrowNew(type, "No presentation window for input test"); env->DeleteLocalRef(type); }
        return;
    }
    auto mouse = [&](Uint32 type, int x, int y, Uint8 button = SDL_BUTTON_LEFT, Uint32 which = 0, Uint32 timestamp = 0) {
        SDL_Event event{}; event.type = type; event.button.x = x; event.button.y = y; event.button.button = button; event.button.which = which; event.common.timestamp = timestamp; push(env, event);
    };
    Uint32 base = SDL_GetTicks();
    mouse(SDL_MOUSEBUTTONDOWN, 16, 24, SDL_BUTTON_LEFT, 0, base - 8);
    SDL_Event foreign{}; foreign.type = SDL_MOUSEMOTION; foreign.motion.which = 42; foreign.motion.x = 100; foreign.motion.y = 100; push(env, foreign);
    SDL_Event move{}; move.type = SDL_MOUSEMOTION; move.motion.x = 40; move.motion.y = 60; move.motion.state = SDL_BUTTON_LMASK; move.common.timestamp = base - 4; push(env, move);
    mouse(SDL_MOUSEBUTTONUP, 40, 60, SDL_BUTTON_LEFT, 0, base - 1);
    mouse(SDL_MOUSEBUTTONDOWN, 20, 30);
    SDL_Event leave{}; leave.type = SDL_WINDOWEVENT; leave.window.event = SDL_WINDOWEVENT_LEAVE; push(env, leave);
    move.common.timestamp = 0; push(env, move); // Dummy video has no capture: leave must cancel before this move.
    SDL_Event focus{}; focus.type = SDL_WINDOWEVENT; focus.window.event = SDL_WINDOWEVENT_FOCUS_LOST; push(env, focus);
    mouse(SDL_MOUSEBUTTONUP, 20, 30);
    mouse(SDL_MOUSEBUTTONDOWN, -40, 10);
    mouse(SDL_MOUSEBUTTONUP, -40, 10);
    mouse(SDL_MOUSEBUTTONDOWN, 20, 30, SDL_BUTTON_RIGHT);
    mouse(SDL_MOUSEBUTTONDOWN, 20, 30, SDL_BUTTON_LEFT, SDL_TOUCH_MOUSEID);
    auto touch = [&](Uint32 type, SDL_FingerID id, float x, float y, SDL_TouchID device = 1) {
        SDL_Event event{}; event.type = type; event.tfinger.fingerId = id; event.tfinger.touchId = device; event.tfinger.x = x; event.tfinger.y = y; push(env, event);
    };
    touch(SDL_FINGERDOWN, 121, .5f, .5f, SDL_MOUSE_TOUCHID);
    touch(SDL_FINGERUP, 121, .5f, .5f, SDL_MOUSE_TOUCHID);
    touch(SDL_FINGERDOWN, 55, .5f, .5f);
    touch(SDL_FINGERMOTION, 55, .2f, .2f, 2); // A different device must not hijack the stream.
    touch(SDL_FINGERDOWN, 99, .4f, .4f); // The current bridge supports one finger.
    touch(SDL_FINGERUP, 99, .4f, .4f);
    touch(SDL_FINGERMOTION, 55, .7f, .6f);
    touch(SDL_FINGERUP, 55, .7f, .6f);
    auto key = [&](Uint32 type, SDL_Scancode scan, SDL_Keycode symbol, Uint16 meta = 0, Uint8 repeat = 0, Uint32 id = 0) {
        SDL_Event event{}; event.type = type; event.key.windowID = id; event.key.keysym.scancode = scan; event.key.keysym.sym = symbol; event.key.keysym.mod = meta; event.key.repeat = repeat; push(env,event);
    };
    key(SDL_KEYDOWN,SDL_SCANCODE_Z,SDLK_z,0,0,SDL_GetWindowID(window)+100);
    key(SDL_KEYDOWN,SDL_SCANCODE_A,SDLK_a,KMOD_LCTRL); key(SDL_KEYDOWN,SDL_SCANCODE_A,SDLK_a,KMOD_LCTRL,1); key(SDL_KEYUP,SDL_SCANCODE_A,SDLK_a);
    SDL_Event text{}; text.type = SDL_TEXTINPUT; std::strcpy(text.text.text, std::getenv("SHIRAZ_FIXTURE_BAD_TEXT") ? "\xed\xa0\x80" : "\xc3\xa9\xf0\x9f\x98\x80"); push(env,text);
    key(SDL_KEYDOWN,SDL_SCANCODE_ESCAPE,SDLK_ESCAPE); key(SDL_KEYUP,SDL_SCANCODE_ESCAPE,SDLK_ESCAPE);
    key(SDL_KEYDOWN,SDL_SCANCODE_LSHIFT,SDLK_LSHIFT,KMOD_LSHIFT); key(SDL_KEYDOWN,SDL_SCANCODE_B,SDLK_b,KMOD_LSHIFT);
    push(env,focus); key(SDL_KEYUP,SDL_SCANCODE_B,SDLK_b); key(SDL_KEYUP,SDL_SCANCODE_LSHIFT,SDLK_LSHIFT);
    key(SDL_KEYDOWN,SDL_SCANCODE_RETURN,SDLK_RETURN); key(SDL_KEYUP,SDL_SCANCODE_RETURN,SDLK_RETURN);

}
extern "C" JNIEXPORT void JNICALL Java_org_shiraz_fixture_DialogFixture_inject(JNIEnv* env, jclass) {
    SDL_Window* window = nullptr;
    for (Uint32 id = 1; id <= 64 && !window; ++id) window = SDL_GetWindowFromID(id);
    if (!window) {
        auto type = env->FindClass("java/lang/IllegalStateException");
        if (type) { env->ThrowNew(type, "No dialog test window"); env->DeleteLocalRef(type); }
        return;
    }
    const Uint32 actions[] = {SDL_MOUSEBUTTONDOWN, SDL_MOUSEBUTTONUP};
    for (Uint32 action : actions) {
        SDL_Event event{}; event.type = action; event.button.windowID=SDL_GetWindowID(window);
        event.button.button=SDL_BUTTON_LEFT; event.button.x=48; event.button.y=80; push(env,event);
    }
}

extern "C" JNIEXPORT void JNICALL Java_shiraz_NativeSurfaceSmoke_resizeWindow(JNIEnv* env,jclass,jint width,jint height) {
    SDL_Window* window=nullptr;
    for(Uint32 id=1;id<=64&&!window;++id)window=SDL_GetWindowFromID(id);
    if(!window){auto type=env->FindClass("java/lang/IllegalStateException");if(type){env->ThrowNew(type,"No resize test window");env->DeleteLocalRef(type);}return;}
    SDL_SetWindowSize(window,width,height);
}
extern "C" JNIEXPORT void JNICALL Java_org_shiraz_fixture_ResizeActivity_inject(JNIEnv* env,jclass,jint width,jint height,jint x,jint y) {
    SDL_Window* window=nullptr;
    for(Uint32 id=1;id<=64&&!window;++id)window=SDL_GetWindowFromID(id);
    if(!window){auto type=env->FindClass("java/lang/IllegalStateException");if(type){env->ThrowNew(type,"Missing resize activity window");env->DeleteLocalRef(type);}return;}
    if(width>0&&height>0)SDL_SetWindowSize(window,width,height);
    if(x>=0&&y>=0){SDL_Event event{};event.type=SDL_MOUSEBUTTONDOWN;event.button.windowID=SDL_GetWindowID(window);event.button.button=SDL_BUTTON_LEFT;event.button.x=x;event.button.y=y;push(env,event);event.type=SDL_MOUSEBUTTONUP;push(env,event);}
}
extern "C" JNIEXPORT void JNICALL Java_org_shiraz_fixture_TitleFixture_assertNativeTitle(JNIEnv* env,jclass,jbyteArray expected){
    SDL_Window* window=nullptr;for(Uint32 id=1;id<=64&&!window;++id)window=SDL_GetWindowFromID(id);
    bool matches=false;
    if(window&&expected){const char* actual=SDL_GetWindowTitle(window);jsize size=env->GetArrayLength(expected);jbyte* bytes=env->GetByteArrayElements(expected,nullptr);if(!bytes)return;matches=std::strlen(actual)==static_cast<std::size_t>(size)&&std::memcmp(actual,bytes,static_cast<std::size_t>(size))==0;env->ReleaseByteArrayElements(expected,bytes,JNI_ABORT);}
    if(!matches){auto type=env->FindClass("java/lang/AssertionError");if(type){env->ThrowNew(type,"SDL title differs from activity title and runtime version");env->DeleteLocalRef(type);}}
}

extern "C" JNIEXPORT void JNICALL Java_shiraz_NativeSurfaceSmoke_focusWindow(JNIEnv* env,jclass,jboolean value){SDL_Event event{};event.type=SDL_WINDOWEVENT;event.window.event=value?SDL_WINDOWEVENT_FOCUS_GAINED:SDL_WINDOWEVENT_FOCUS_LOST;push(env,event);}

extern "C" JNIEXPORT void JNICALL Java_shiraz_NativeSurfaceSmoke_visibilityWindow(JNIEnv* env,jclass,jboolean value){SDL_Event event{};event.type=SDL_WINDOWEVENT;event.window.event=value?SDL_WINDOWEVENT_RESTORED:SDL_WINDOWEVENT_MINIMIZED;push(env,event);}
extern "C" JNIEXPORT void JNICALL Java_shiraz_NativeSurfaceSmoke_finger(JNIEnv* env,jclass,jint action,jlong id,jfloat x,jfloat y) {
    SDL_Event event{};event.type=action==0?SDL_FINGERDOWN:action==1?SDL_FINGERUP:SDL_FINGERMOTION;
    event.tfinger.touchId=17;event.tfinger.fingerId=id;event.tfinger.x=x;event.tfinger.y=y;event.tfinger.pressure=action==1?0.f:.75f;push(env,event);
}
