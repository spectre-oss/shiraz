// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/native_window.h"
#include "shiraz/presentation.hpp"
#include "shiraz/window_snapshot.hpp"
#include <cstdlib>
#include <dlfcn.h>
#include "version.hpp"
#include <jni.h>
#include "shiraz/gpu.hpp"
#include <limits>
#include <iostream>
#include <memory>
#include <mutex>
#include <unordered_map>

static_assert(sizeof(jint) == sizeof(uint32_t), "JNI pixel size must match ARGB8888");
extern "C" void* ALooper_prepare(int);
extern "C" int ALooper_pollOnce(int, int*, int*, void**);
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSurface_prepareNativeLooper(JNIEnv* env,jclass) {
    if (!ALooper_prepare(1)) {
        jclass type=env->FindClass("java/lang/IllegalStateException");
        if (type) { env->ThrowNew(type,"Cannot prepare native Looper"); env->DeleteLocalRef(type); }
    }
}
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeSurface_pollNativeLooper(JNIEnv*,jclass) {
    int handled=0;
    for (;handled<64;++handled) {
        int result=ALooper_pollOnce(0,nullptr,nullptr,nullptr);
        if (result>=0 || result==-3 || result==-4) break;
    }
    return handled;
}

namespace {
using Window = std::shared_ptr<ANativeWindow>;
std::mutex registry_mutex;
std::unordered_map<jlong, Window> windows;
jlong next_id = 1;
void error(JNIEnv* env, const char* kind, const char* message) {
    if (!env->ExceptionCheck()) {
        jclass type = env->FindClass(kind);
        if (type) { env->ThrowNew(type, message); env->DeleteLocalRef(type); }
    }
}
Window find(JNIEnv* env, jlong id) {
    {
        std::lock_guard<std::mutex> guard(registry_mutex);
        auto item = windows.find(id);
        if (item != windows.end()) return item->second;
    }
    error(env, "java/lang/IllegalStateException", "Unknown or closed native surface");
    return {};
}
shiraz::Surface snapshot(ANativeWindow* window) {
    shiraz::Surface image(ANativeWindow_getWidth(window), ANativeWindow_getHeight(window));
    if (shiraz_window_copy_argb(window, image.pixels.data(), image.pixels.size()))
        throw std::runtime_error("Cannot copy native surface");
    return image;
}
}
extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativeSurface_prepareDisplayNative(JNIEnv* env,jclass,jint width,jint height) {
    try {
        auto display=shiraz::prepare_presentation(width,height);
        jint values[]={display.pixelWidth,display.pixelHeight,display.densityDpi};
        jintArray result=env->NewIntArray(3);
        if(result)env->SetIntArrayRegion(result,0,3,values);
        if(env->ExceptionCheck()||!result)shiraz::discard_prepared_presentation();
        return result;
    }catch(const std::exception& ex){error(env,"java/lang/IllegalStateException",ex.what());return nullptr;}
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSurface_discardPreparedDisplay(JNIEnv*,jclass){shiraz::discard_prepared_presentation();}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeSurface_create(JNIEnv* env, jclass, jint width, jint height) {
    try {
        Window window(shiraz_window_create(width, height), ANativeWindow_release);
        if (!window) { error(env, "java/lang/OutOfMemoryError", "Cannot allocate native surface"); return 0; }
        std::lock_guard<std::mutex> guard(registry_mutex);
        if (next_id == std::numeric_limits<jlong>::max()) {
            error(env, "java/lang/IllegalStateException", "Native surface IDs exhausted"); return 0;
        }
        auto id = next_id++;
        windows.emplace(id, std::move(window));
        return id;
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot register native surface"); return 0; }
}
namespace { thread_local jlong presenting_id=0; struct PresentationId { jlong previous; explicit PresentationId(jlong id):previous(presenting_id){presenting_id=id;} ~PresentationId(){presenting_id=previous;} }; }
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSurface_requestCursor(JNIEnv* env,jclass,jlong id,jint type){
    if(!find(env,id))return;
    if(presenting_id!=id){error(env,"java/lang/IllegalStateException","Cursor must target the active surface");return;}
    try{shiraz::request_pointer_icon(type);}catch(const std::exception& ex){error(env,"java/lang/IllegalStateException",ex.what());}
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSurface_requestColorCursor(JNIEnv* env,jclass,jlong id,jintArray pixels,jint width,jint height,jint hotX,jint hotY){
    if(!find(env,id))return;
    if(presenting_id!=id){error(env,"java/lang/IllegalStateException","Cursor must target the active surface");return;}
    int64_t count=int64_t(width)*height;
    if(!pixels||width<1||height<1||width>8192||height>8192||count>32*1024*1024||hotX<0||hotY<0||hotX>=width||hotY>=height||env->GetArrayLength(pixels)!=count){
        error(env,"java/lang/IllegalArgumentException","Invalid color cursor geometry");return;
    }
    try{
        std::vector<jint> copy(static_cast<size_t>(count));
        env->GetIntArrayRegion(pixels,0,static_cast<jsize>(count),copy.data());
        if(env->ExceptionCheck())return;
        shiraz::request_color_pointer_icon(reinterpret_cast<const uint32_t*>(copy.data()),width,height,hotX,hotY);
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Cannot copy cursor pixels");}
    catch(const std::exception& ex){error(env,"java/lang/IllegalStateException",ex.what());}
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSurface_requestScreenAwake(JNIEnv* env,jclass,jlong id,jboolean awake){
    if(!find(env,id))return;
    if(presenting_id!=id){error(env,"java/lang/IllegalStateException","Screen inhibition must target the active surface");return;}
    try{shiraz::request_screen_awake(awake==JNI_TRUE);}catch(const std::exception& ex){error(env,"java/lang/IllegalStateException",ex.what());}
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSurface_requestTitle(JNIEnv* env,jclass,jlong id,jbyteArray bytes){
    if(!find(env,id))return;
    if(presenting_id!=id){error(env,"java/lang/IllegalStateException","Window title must target the active surface");return;}
    if(!bytes||env->GetArrayLength(bytes)>16384){error(env,"java/lang/IllegalArgumentException","Invalid window title bytes");return;}
    try{std::string title(static_cast<std::size_t>(env->GetArrayLength(bytes)),'\0');
        env->GetByteArrayRegion(bytes,0,jsize(title.size()),reinterpret_cast<jbyte*>(title.data()));if(env->ExceptionCheck())return;
        if(title.find('\0')!=std::string::npos){error(env,"java/lang/IllegalArgumentException","Window title contains NUL");return;}
        shiraz::request_window_title(title+" | Shiraz "+SHIRAZ_VERSION);
    }catch(const std::exception& ex){error(env,"java/lang/IllegalStateException",ex.what());}
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSurface_requestSize(JNIEnv* env,jclass,jlong id,jint width,jint height){
    if(!find(env,id))return;
    if(presenting_id!=id){error(env,"java/lang/IllegalStateException","Window request must target the active surface");return;}
    try{shiraz::request_window_size(width,height);}catch(const std::exception& ex){error(env,"java/lang/IllegalStateException",ex.what());}
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSurface_resizeBuffer(JNIEnv* env,jclass,jlong id,jint width,jint height) {
    auto window=find(env,id);if(!window)return;
    if(width<1||height<1||width>8192||height>8192||uint64_t(width)*height>16*1024*1024){error(env,"java/lang/IllegalArgumentException","Invalid surface resize");return;}
    if(ANativeWindow_setBuffersGeometry(window.get(),width,height,1))error(env,"java/lang/IllegalStateException","Cannot resize native surface");
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSurface_destroy(JNIEnv*, jclass, jlong id) {
    std::lock_guard<std::mutex> guard(registry_mutex); windows.erase(id);
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSurface_postPixels(JNIEnv* env, jclass, jlong id, jintArray pixels) {
    shiraz::gpu_software_frame();
    auto window = find(env, id);
    if (!window) return;
    if (!pixels) { error(env, "java/lang/NullPointerException", "pixels"); return; }
    auto count = ANativeWindow_getWidth(window.get()) * ANativeWindow_getHeight(window.get());
    if (env->GetArrayLength(pixels) != count) { error(env, "java/lang/IllegalArgumentException", "Pixel count mismatch"); return; }
    jint* values = env->GetIntArrayElements(pixels, nullptr);
    if (!values) return;
    int result=shiraz_window_post_argb(window.get(),reinterpret_cast<const uint32_t*>(values),uint64_t(count));
    env->ReleaseIntArrayElements(pixels, values, JNI_ABORT);
    if(result)error(env,"java/lang/IllegalStateException","Cannot post native surface");
}
extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativeSurface_readPixels(JNIEnv* env, jclass, jlong id) {
    auto window = find(env, id);
    if (!window) return nullptr;
    try {
        auto image = snapshot(window.get());
        auto result = env->NewIntArray(jsize(image.pixels.size()));
        if (result) env->SetIntArrayRegion(result, 0, jsize(image.pixels.size()), reinterpret_cast<const jint*>(image.pixels.data()));
        return result;
    } catch (const std::exception& ex) { error(env, "java/lang/RuntimeException", ex.what()); return nullptr; }
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSurface_presentPixels(JNIEnv* env, jclass, jlong id, jint frames) {
    auto window = find(env, id);
    if (!window) return;
    if (frames != -1 && (frames < 1 || frames > 600)) { error(env, "java/lang/IllegalArgumentException", "Invalid frame limit"); return; }
    try { shiraz::present(snapshot(window.get()), std::string("Shiraz ") + SHIRAZ_VERSION + " | Java surface bridge", frames); }
    catch (const std::exception& ex) { error(env, "java/lang/RuntimeException", ex.what()); }
}

namespace {
jstring committedText(JNIEnv* env, const std::string& input) {
    if (input.size() > 31) throw std::runtime_error("Committed SDL text exceeds event limit");
    jchar output[64]; jsize count = 0;
    for (std::size_t at = 0; at < input.size();) {
        unsigned first = static_cast<unsigned char>(input[at++]), cp = first; int extra = 0;
        if (first >= 0xf0 && first <= 0xf4) { extra = 3; cp &= 7; }
        else if (first >= 0xe0 && first <= 0xef) { extra = 2; cp &= 15; }
        else if (first >= 0xc2 && first <= 0xdf) { extra = 1; cp &= 31; }
        else if (first >= 0x80) throw std::runtime_error("Malformed UTF-8 in committed SDL text");
        if (at + extra > input.size()) throw std::runtime_error("Truncated UTF-8 in committed SDL text");
        for (int next = 0; next < extra; ++next) { unsigned byte = static_cast<unsigned char>(input[at++]); if ((byte & 0xc0) != 0x80) throw std::runtime_error("Malformed UTF-8 continuation in SDL text"); cp = (cp << 6) | (byte & 63); }
        if ((extra == 1 && cp < 0x80) || (extra == 2 && cp < 0x800) || (extra == 3 && cp < 0x10000) || cp > 0x10ffff || (cp >= 0xd800 && cp <= 0xdfff)) throw std::runtime_error("Invalid UTF-8 code point in SDL text");
        if (cp > 0xffff) { cp -= 0x10000; output[count++] = jchar(0xd800 + (cp >> 10)); output[count++] = jchar(0xdc00 + (cp & 1023)); }
        else output[count++] = jchar(cp);
    }
    return env->NewString(output, count);
}
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeSurface_presentDispatch(JNIEnv* env, jclass, jlong id, jint frames, jobject callback) {
    auto window = find(env, id);
    if (!window) return;
    if (!callback || (frames != -1 && (frames < 1 || frames > 600))) {
        error(env, "java/lang/IllegalArgumentException", "Invalid frame callback or frame limit"); return;
    }
    jclass type = env->GetObjectClass(callback);
    if (!type) return;
    jmethodID method = env->GetMethodID(type, "onFrame", "()Z");
    jmethodID pointerMethod = method ? env->GetMethodID(type, "onPointer", "(IFFIJ)V") : nullptr;
    jmethodID touchMethod=pointerMethod?env->GetMethodID(type,"onTouch","(I[I[F[F[FJ)V"):nullptr;
    jmethodID scrollMethod=pointerMethod?env->GetMethodID(type,"onScroll","(FFFFJ)V"):nullptr;
    jmethodID keyMethod = pointerMethod ? env->GetMethodID(type, "onKey", "(IIIIIIJJ)V") : nullptr;
    jmethodID textMethod = keyMethod ? env->GetMethodID(type, "onText", "(Ljava/lang/String;J)V") : nullptr;
    jmethodID focusMethod=textMethod?env->GetMethodID(type,"onWindowFocus","(Z)V"):nullptr;
    jmethodID visibilityMethod=focusMethod?env->GetMethodID(type,"onWindowVisibility","(Z)V"):nullptr;
    jmethodID locationMethod=visibilityMethod?env->GetMethodID(type,"onTestLocation","(DDF)V"):nullptr;
    env->DeleteLocalRef(type);
    if (!method || !pointerMethod || !touchMethod || !scrollMethod || !keyMethod || !textMethod || !focusMethod || !visibilityMethod || !locationMethod) return;
    jclass resizeType=env->FindClass("shiraz/runtime/NativeSurface$ResizeCallback");
    if(!resizeType)return;
    jmethodID resizeMethod=env->IsInstanceOf(callback,resizeType)?env->GetMethodID(resizeType,"onDisplayChanged","(III)V"):nullptr;
    env->DeleteLocalRef(resizeType);if(env->ExceptionCheck())return;
    uint64_t revision=0,frameCalls=0,copies=0;
    auto read_revision = reinterpret_cast<uint64_t (*)(ANativeWindow*)>(dlsym(RTLD_DEFAULT, "shiraz_window_revision"));
    if (!read_revision) { error(env,"java/lang/IllegalStateException","Native window revision API is unavailable"); return; }
    auto refresh=[&](shiraz::Surface& image){if(shiraz::refresh_window_snapshot(window.get(),image,revision,read_revision))++copies;};
    std::function<void(shiraz::Surface&,const shiraz::DisplayGeometry&)> displayChanged;
    if(resizeMethod)displayChanged=[&](shiraz::Surface& image,const shiraz::DisplayGeometry& display){
        env->CallVoidMethod(callback,resizeMethod,jint(display.pixelWidth),jint(display.pixelHeight),jint(display.densityDpi));
        if(env->ExceptionCheck())throw std::runtime_error("Java resize callback failed");
        refresh(image);
    };
    try {
        PresentationId active(id);
        std::cerr<<"SHIRAZ_RUNTIME version="<<SHIRAZ_VERSION<<"\n";
        shiraz::Surface initial(1,1);refresh(initial);
        shiraz::present(initial, std::string("Shiraz ") + SHIRAZ_VERSION + " | Android activity", frames,
            [&](shiraz::Surface& image) {
                ++frameCalls;
                jboolean running = env->CallBooleanMethod(callback, method);
                if (env->ExceptionCheck()) throw std::runtime_error("Java frame callback failed");
                if (!running) return false;
                refresh(image);
                return true;
            }, [&](const shiraz::PointerEvent& event) {
                if(event.action==shiraz::PointerAction::Scroll)env->CallVoidMethod(callback,scrollMethod,jfloat(event.point.x),jfloat(event.point.y),jfloat(event.scrollX),jfloat(event.scrollY),jlong(event.time));
                else if(!event.contacts.empty()) {
                    jsize count=jsize(event.contacts.size());jint ids[32];jfloat xs[32],ys[32],pressures[32];
                    for(jsize i=0;i<count;++i){ids[i]=event.contacts[i].id;xs[i]=event.contacts[i].point.x;ys[i]=event.contacts[i].point.y;pressures[i]=event.contacts[i].pressure;}
                    jintArray ji=env->NewIntArray(count);jfloatArray jx=env->NewFloatArray(count),jy=env->NewFloatArray(count),jp=env->NewFloatArray(count);
                    if(ji&&jx&&jy&&jp){env->SetIntArrayRegion(ji,0,count,ids);env->SetFloatArrayRegion(jx,0,count,xs);env->SetFloatArrayRegion(jy,0,count,ys);env->SetFloatArrayRegion(jp,0,count,pressures);
                        env->CallVoidMethod(callback,touchMethod,jint(event.action)|(event.actionIndex<<8),ji,jx,jy,jp,jlong(event.time));}
                    if(ji)env->DeleteLocalRef(ji);
                    if(jx)env->DeleteLocalRef(jx);
                    if(jy)env->DeleteLocalRef(jy);
                    if(jp)env->DeleteLocalRef(jp);
                }
                else env->CallVoidMethod(callback, pointerMethod, jint(event.action), jfloat(event.point.x), jfloat(event.point.y), jint(event.source), jlong(event.time));
                if (env->ExceptionCheck()) throw std::runtime_error("Java pointer callback failed");
            }, [&](const shiraz::KeyEvent& event) {
                env->CallVoidMethod(callback, keyMethod, jint(event.action), jint(event.key), jint(event.scan), jint(event.repeat), jint(event.meta), jint(event.flags), jlong(event.down), jlong(event.time));
                if (env->ExceptionCheck()) throw std::runtime_error("Java key callback failed");
            }, [&](const std::string& text, int64_t time) {
                jstring value = committedText(env, text); if (!value) throw std::runtime_error("Cannot allocate committed Java text");
                env->CallVoidMethod(callback, textMethod, value, jlong(time)); env->DeleteLocalRef(value);
                if (env->ExceptionCheck()) throw std::runtime_error("Java committed-text callback failed");
            }, {}, [&](bool focused){env->CallVoidMethod(callback,focusMethod,jboolean(focused));if(env->ExceptionCheck())throw std::runtime_error("Java window focus callback failed");}, [&](bool visible){env->CallVoidMethod(callback,visibilityMethod,jboolean(visible));if(env->ExceptionCheck())throw std::runtime_error("Java window visibility callback failed");}, [&](double latitude,double longitude,float accuracy){env->CallVoidMethod(callback,locationMethod,jdouble(latitude),jdouble(longitude),jfloat(accuracy));if(env->ExceptionCheck())throw std::runtime_error("Java test location callback failed");}, [&](){return revision;},displayChanged);
    } catch (const std::exception& ex) { error(env, "java/lang/RuntimeException", ex.what()); }
    const char* trace=std::getenv("SHIRAZ_TRACE_PRESENT");if(trace&&std::string(trace)=="1")std::cerr<<"SHIRAZ_NATIVE_COPY frame_callbacks="<<frameCalls<<" snapshots="<<copies<<"\n";
}

extern "C" JNIEXPORT jfloat JNICALL Java_shiraz_runtime_NativeSurface_refreshRate(JNIEnv*,jclass){return shiraz::presentation_refresh_rate();}

extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeSurface_hasTouchscreen(JNIEnv* env,jclass){try{return shiraz::presentation_has_touchscreen()?JNI_TRUE:JNI_FALSE;}catch(const std::exception& ex){error(env,"java/lang/IllegalStateException",ex.what());return JNI_FALSE;}}

extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeSurface_setClipboardText(JNIEnv* env,jclass,jbyteArray bytes){
    if(!bytes)return JNI_FALSE;
    jsize len=env->GetArrayLength(bytes);
    if(len<0||len>1024*1024)return JNI_FALSE;
    std::string text(static_cast<std::size_t>(len),'\0');
    if(len>0){
        env->GetByteArrayRegion(bytes,0,len,reinterpret_cast<jbyte*>(text.data()));
        if(env->ExceptionCheck())return JNI_FALSE;
    }
    return shiraz::set_clipboard_text(text)?JNI_TRUE:JNI_FALSE;
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_shiraz_runtime_NativeSurface_getClipboardText(JNIEnv* env,jclass){
    bool available=false;
    std::string text=shiraz::get_clipboard_text(available);
    if(!available)return nullptr;
    jbyteArray out=env->NewByteArray(jsize(text.size()));
    if(!out)return nullptr;
    if(!text.empty())env->SetByteArrayRegion(out,0,jsize(text.size()),reinterpret_cast<const jbyte*>(text.data()));
    return out;
}

extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeSurface_requestTextInput(JNIEnv*,jclass,jboolean visible){shiraz::request_text_input(visible==JNI_TRUE);return JNI_TRUE;}

extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeSurface_usesWaylandTextInput(JNIEnv*,jclass){return shiraz::uses_wayland_text_input()?JNI_TRUE:JNI_FALSE;}
