// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <androidfw/androidfw_c_api.h>
#include <limits>
#include <list>
#include <array>
#include <cstring>
#include <climits>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <sstream>
#include <unordered_map>
#include <vector>
#include "resource_colors.hpp"
#include "shiraz/archive_path.hpp"
#include <sys/stat.h>
#include <unistd.h>

namespace {
using OwnedTheme = std::unique_ptr<Theme, decltype(&Theme_delete)>;
struct ThemeSnapshot {
    std::vector<jint> styles;
    OwnedTheme theme;
};
struct Resources {
    const ApkAssets* assets = nullptr;
    const ApkAssets* framework = nullptr;
    AssetManager* manager = nullptr;
    std::mutex mutex;
    // Immutable applied-style snapshots, owned by this manager and guarded by mutex.
    std::list<ThemeSnapshot> themes;
    // APK packages are immutable for this owner. IDs are configuration-independent;
    // values still resolve against the current configuration on every read.
    std::unordered_map<std::string, jint> identifiers;
    ~Resources() { themes.clear(); if (manager) AssetManager_delete(manager); if (assets) ApkAssets_delete(assets); if (framework) ApkAssets_delete(framework); }
};
std::mutex registry_mutex;
std::unordered_map<jlong, std::shared_ptr<Resources>> registry;
jlong next_id = 1;
std::mutex ndk_managers_mutex;
std::vector<std::shared_ptr<Resources>> ndk_managers;
void error(JNIEnv* env, const char* type, const char* message) {
    if (env->ExceptionCheck()) return;
    jclass cls = env->FindClass(type);
    if (cls) { env->ThrowNew(cls, message); env->DeleteLocalRef(cls); }
}
std::shared_ptr<Resources> find(JNIEnv* env, jlong id) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    auto entry = registry.find(id);
    if (entry != registry.end()) return entry->second;
    error(env, "java/lang/IllegalStateException", "Closed or unknown resource manager");
    return {};
}
struct ResourceValue {
    Res_value value{};
    ResTable_config selected{};
    ApkAssetsCookie cookie=-1;
    uint32_t flags=0,current=0;
};
bool loadValue(JNIEnv* env,Resources& resources,uint32_t resource_id,uint16_t density,bool resolve,ResourceValue& result,bool allowBag=false) {
    std::array<uint32_t,32> visited{}; size_t depth=0; result.current=resource_id;
    while (true) {
        for (size_t i=0;i<depth;++i) if (visited[i] == result.current) {
            error(env,"android/content/res/Resources$NotFoundException","Cyclic resource reference"); return false;
        }
        if (depth == visited.size()) { error(env,"android/content/res/Resources$NotFoundException","Resource reference depth exceeds 32"); return false; }
        visited[depth++]=result.current; uint32_t localFlags=0;
        result.cookie=AssetManager_getResource(resources.manager,result.current,allowBag,density,&result.value,&result.selected,&localFlags);
        if (result.cookie < 0) { error(env,"android/content/res/Resources$NotFoundException","Missing scalar resource or resource is a complex bag"); return false; }
        result.flags |= localFlags;
        // AOSP represents complex entries by a reference to their own resource ID.
        // Like ResolveReference, stop at self-references without resolving parent bags.
        // Actual style application validates its parents; scalar-only queries stay strict.
        if (allowBag && result.value.dataType == TYPE_REFERENCE && result.value.data == result.current) return true;
        if (!resolve || result.value.dataType != TYPE_REFERENCE || result.value.data == 0) return true;
        result.current=result.value.data;
    }
}
bool validStyle(JNIEnv* env, Resources& resources, uint32_t id) {
    resource_name name{};
    bool style = AssetManager_getResourceName(resources.manager, id, &name) && name.type_len == 5;
    if (style) {
        if (name.type) style = std::string(name.type, name.type_len) == "style";
        else if (name.type16) style = std::u16string(name.type16, name.type_len) == u"style";
        else style = false;
    }
    if (!style || !AssetManager_getBag(resources.manager, id)) {
        std::ostringstream message;message << "Style or parent resources are unavailable for #" << std::hex << id;error(env, "android/content/res/Resources$NotFoundException", message.str().c_str()); return false;
    }
    return true;
}
// Call only with resources.mutex held; returned pointers never escape that lock.
Theme* themeFor(JNIEnv* env, Resources& resources, const std::vector<jint>& sequence) {
    for (auto it = resources.themes.begin(); it != resources.themes.end(); ++it) {
        if (it->styles == sequence) {
            resources.themes.splice(resources.themes.begin(), resources.themes, it);
            return resources.themes.front().theme.get();
        }
    }
    OwnedTheme theme(AssetManager_newTheme(resources.manager), Theme_delete);
    if (!theme) throw std::bad_alloc();
    for (size_t i = 0; i < sequence.size(); i += 2) {
        if (!validStyle(env, resources, static_cast<uint32_t>(sequence[i]))) return nullptr;
        Theme_applyStyle(theme.get(), static_cast<uint32_t>(sequence[i]), sequence[i + 1] != 0);
    }
    // Java theme instances may share a history. Preserve order and force bits in
    // the key; bound retained snapshots independently of application theme count.
    resources.themes.push_front(ThemeSnapshot{sequence, std::move(theme)});
    if (resources.themes.size() > 32) resources.themes.pop_back();
    return resources.themes.front().theme.get();
}
struct AppAsset {
    std::unique_ptr<AssetManager, decltype(&AssetManager_delete)> manager{nullptr, AssetManager_delete};
    std::unique_ptr<Asset, decltype(&Asset_delete)> asset{nullptr, Asset_delete};
};
AppAsset openAsset(JNIEnv* env, Resources& resources, jbyteArray path) {
    if (!path) throw std::invalid_argument("Asset path is null");
    jsize count = env->GetArrayLength(path);
    if (count < 8 || count > 1048576) throw std::invalid_argument("Invalid asset path length");
    std::string name(static_cast<size_t>(count), '\0');
    env->GetByteArrayRegion(path, 0, count, reinterpret_cast<jbyte*>(&name[0]));
    if (env->ExceptionCheck()) return {};
    if (name.compare(0, 7, "assets/") || name.find('\0') != std::string::npos || name.find("/../") != std::string::npos
            || name.find("/./") != std::string::npos || name.find("//") != std::string::npos || name.find('\\') != std::string::npos
            || (name.size() >= 3 && name.compare(name.size() - 3, 3, "/..") == 0))
        throw std::invalid_argument("Invalid asset path");
    AppAsset result; result.manager.reset(AssetManager_new());
    if (!result.manager) throw std::bad_alloc();
    if (!AssetManager_setApkAssets(result.manager.get(), &resources.assets, 1, true, false)) throw std::runtime_error("Cannot open app assets");
    result.asset.reset(AssetManager_openNonAsset(result.manager.get(), name.c_str(), ACCESS_STREAMING));
    return result;
}
struct AssetStream {
    std::shared_ptr<Resources> owner;
    AppAsset opened;
    std::mutex mutex;
};
std::mutex stream_mutex;
std::unordered_map<jlong,std::shared_ptr<AssetStream>> streams;
jlong next_stream=1;
std::shared_ptr<AssetStream> streamFor(jlong id){
    std::lock_guard<std::mutex> lock(stream_mutex);auto found=streams.find(id);
    if(found==streams.end())throw std::runtime_error("Asset stream is closed");
    return found->second;
}

}
static jlong openResources(JNIEnv* env,jbyteArray path,jbyteArray framework,jstring locale,jint width,jint height,bool systemOnly) {
    try {
        if (!path || !locale) { error(env, "java/lang/NullPointerException", "Resource path/locale"); return 0; }
        jsize length = env->GetArrayLength(path);
        if (length < 1 || length > 1048576 || width < 1 || height < 1 || width > 8192 || height > 8192)
            throw std::invalid_argument("Invalid resource path or configuration");
        std::string filename(static_cast<size_t>(length), '\0');
        env->GetByteArrayRegion(path, 0, length, reinterpret_cast<jbyte*>(&filename[0]));
        if (env->ExceptionCheck()) return 0;
        if (filename.find('\0') != std::string::npos) throw std::invalid_argument("NUL in APK path");
        auto resources = std::make_shared<Resources>();
        resources->assets = ApkAssets_load(filename.c_str(), systemOnly);
        if (!resources->assets) { error(env, "android/content/res/Resources$NotFoundException", "Cannot load APK resources"); return 0; }
        resources->manager = AssetManager_new();
        if (!resources->manager) throw std::bad_alloc();
        const ApkAssets* packages[2] = {resources->assets, nullptr}; size_t package_count = 1;
        if (framework) {
            jsize count = env->GetArrayLength(framework);
            if (count < 1 || count > 1048576) throw std::invalid_argument("Invalid framework resource path");
            std::string file(static_cast<size_t>(count), '\0');
            env->GetByteArrayRegion(framework, 0, count, reinterpret_cast<jbyte*>(&file[0]));
            if (env->ExceptionCheck()) return 0;
            if (file.find('\0') != std::string::npos) throw std::invalid_argument("NUL in framework path");
            resources->framework = ApkAssets_load(file.c_str(), true);
            if (!resources->framework) { error(env, "android/content/res/Resources$NotFoundException", "Cannot load framework resources"); return 0; }
            packages[0] = resources->framework; packages[1] = resources->assets; package_count = 2;
        }
        if (!AssetManager_setApkAssets(resources->manager, packages, package_count, true, false))
            throw std::runtime_error("Cannot install APK resources");
        ResTable_config config{};
        config.size = sizeof(config); config.sdkVersion = 27; config.density = 160;
        config.screenWidth = config.screenWidthDp = static_cast<uint16_t>(width);
        config.screenHeight = config.screenHeightDp = static_cast<uint16_t>(height);
        config.smallestScreenWidthDp = static_cast<uint16_t>(width < height ? width : height);
        config.orientation = width > height ? 2 : 1;
        const char* tag = env->GetStringUTFChars(locale, nullptr);
        if (!tag) return 0;
        ResTable_config_setLocale(&config, tag);
        env->ReleaseStringUTFChars(locale, tag);
        AssetManager_setConfiguration(resources->manager, &config);
        std::lock_guard<std::mutex> lock(registry_mutex);
        if (next_id == std::numeric_limits<jlong>::max()) throw std::runtime_error("Resource IDs exhausted");
        jlong id = next_id++;
        registry.emplace(id, std::move(resources));
        return id;
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot allocate APK resources"); }
      catch (const std::exception&) { error(env, "java/lang/IllegalStateException", "Cannot initialize APK resource manager"); }
    return 0;
}
extern "C" JNIEXPORT jlong JNICALL Java_android_content_res_Resources_nativeOpen(JNIEnv* env,jclass,jbyteArray path,jbyteArray framework,jstring locale,jint width,jint height) {return openResources(env,path,framework,locale,width,height,false);}
extern "C" JNIEXPORT jlong JNICALL Java_android_content_res_Resources_nativeOpenSystem(JNIEnv* env,jclass,jbyteArray path,jstring locale,jint width,jint height) {return openResources(env,path,nullptr,locale,width,height,true);}

extern "C" JNIEXPORT void JNICALL Java_android_content_res_Resources_nativeClose(JNIEnv*, jclass, jlong id) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    registry.erase(id);
}
extern "C" JNIEXPORT void JNICALL Java_android_content_res_Resources_nativeConfigure(JNIEnv* env, jclass, jlong id, jstring locale, jintArray values) {
    try {
        auto resources = find(env, id);
        if (!resources) return;
        if (!locale || !values) { error(env, "java/lang/NullPointerException", "Resource configuration"); return; }
        if (env->GetArrayLength(values) != 9 || env->GetStringLength(locale) > 1024) {
            error(env, "java/lang/IllegalArgumentException", "Invalid native resource configuration"); return;
        }
        jint c[9]; env->GetIntArrayRegion(values, 0, 9, c);
        if (env->ExceptionCheck()) return;
        for (int i = 0; i <= 5; ++i) if (c[i] < 1 || c[i] > (i == 2 ? 1280 : 8192)) {
            error(env, "java/lang/IllegalArgumentException", "Invalid resource geometry/density"); return;
        }
        if (c[6] < 1 || c[6] > 2 || c[7] < 0 || c[7] > 255 || c[8] < 0 || c[8] > 63) {
            error(env, "java/lang/IllegalArgumentException", "Invalid resource orientation/layout/UI mode"); return;
        }
        ResTable_config config{}; config.size = sizeof(config); config.sdkVersion = 27;
        config.screenWidth = static_cast<uint16_t>(c[0]); config.screenHeight = static_cast<uint16_t>(c[1]); config.density = static_cast<uint16_t>(c[2]);
        config.screenWidthDp = static_cast<uint16_t>(c[3]); config.screenHeightDp = static_cast<uint16_t>(c[4]); config.smallestScreenWidthDp = static_cast<uint16_t>(c[5]);
        config.orientation = static_cast<uint8_t>(c[6]); config.screenLayout = static_cast<uint8_t>(c[7]); config.uiMode = static_cast<uint8_t>(c[8]);
        const char* tag = env->GetStringUTFChars(locale, nullptr);
        if (!tag) return;
        ResTable_config_setLocale(&config, tag); env->ReleaseStringUTFChars(locale, tag);
        std::lock_guard<std::mutex> lock(resources->mutex);
        resources->themes.clear();
        AssetManager_setConfiguration(resources->manager, &config);
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot allocate resource configuration"); }
      catch (const std::exception&) { error(env, "java/lang/IllegalStateException", "Cannot update resource configuration"); }
}
extern "C" JNIEXPORT void JNICALL Java_android_content_res_Resources_nativeValue(JNIEnv* env,jclass,jlong handle,jint resource_id,jint density,jboolean resolve,jboolean allowBag,jintArray out,jobjectArray text) {
    try {
        if (!out || !text) { error(env,"java/lang/NullPointerException","Typed resource output"); return; }
        if (env->GetArrayLength(out) != 6 || env->GetArrayLength(text) != 1 || density < 0 || density > 65535)
            throw std::invalid_argument("Invalid typed resource output or density");
        auto resources=find(env,handle); if (!resources) return;
        std::lock_guard<std::mutex> lock(resources->mutex);
        ResourceValue result;
        if (!loadValue(env,*resources,static_cast<uint32_t>(resource_id),static_cast<uint16_t>(density),resolve,result,allowBag)) return;
        auto& value=result.value; auto& selected=result.selected; auto cookie=result.cookie;
        jstring string=nullptr;
        if (value.dataType == TYPE_STRING) {
            auto pool=AssetManager_getStringPoolForCookie(resources->manager,cookie); size_t count=0;
            auto chars=pool ? ResStringPool_stringAt(pool,value.data,&count) : nullptr;
            if (!chars || count > 1024*1024) { error(env,"android/content/res/Resources$NotFoundException","Invalid or oversized resource string"); return; }
            string=env->NewString(reinterpret_cast<const jchar*>(chars),static_cast<jsize>(count)); if (!string) return;
        }
        jint fields[6]={jint(value.dataType),jint(value.data),jint(cookie+1),jint(result.flags),jint(result.current),jint(selected.density)};
        env->SetObjectArrayElement(text,0,string); if (string) env->DeleteLocalRef(string);
        if (!env->ExceptionCheck()) env->SetIntArrayRegion(out,0,6,fields);
    } catch (const std::bad_alloc&) { error(env,"java/lang/OutOfMemoryError","Cannot read typed resource"); }
      catch (const std::invalid_argument& issue) { error(env,"java/lang/IllegalArgumentException",issue.what()); }
      catch (const std::exception&) { error(env,"java/lang/IllegalStateException","Cannot read APK resource value"); }
}
extern "C" JNIEXPORT jstring JNICALL Java_android_content_res_Resources_nativeString(JNIEnv* env, jclass cls, jlong id, jint resource_id) {
    try {
        auto resources = find(env, id);
        if (!resources) return nullptr;
        std::lock_guard<std::mutex> lock(resources->mutex);
        ResourceValue result;
        if (!loadValue(env,*resources,static_cast<uint32_t>(resource_id),0,true,result)) return nullptr;
        auto& value=result.value; auto cookie=result.cookie;
        if (value.dataType == TYPE_NULL) {
            error(env, "android/content/res/Resources$NotFoundException", "Missing string resource"); return nullptr;
        }
        if (value.dataType != TYPE_STRING) {
            auto coerce = env->GetStaticMethodID(cls, "coerce", "(II)Ljava/lang/String;");
            if (!coerce) return nullptr;
            return static_cast<jstring>(env->CallStaticObjectMethod(cls, coerce, jint(value.dataType), jint(value.data)));
        }
        auto pool = AssetManager_getStringPoolForCookie(resources->manager, cookie);
        size_t length = 0;
        auto text = pool ? ResStringPool_stringAt(pool, value.data, &length) : nullptr;
        if (!text || length > static_cast<size_t>(std::numeric_limits<jsize>::max())) {
            error(env, "android/content/res/Resources$NotFoundException", "Invalid resource string pool entry"); return nullptr;
        }
        static_assert(sizeof(char16_t) == sizeof(jchar));
        return env->NewString(reinterpret_cast<const jchar*>(text), static_cast<jsize>(length));
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot read APK string"); }
      catch (const std::exception&) { error(env, "java/lang/IllegalStateException", "Cannot read APK resource"); }
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL Java_android_content_res_Resources_nativeResourceName(JNIEnv* env,jclass,jlong id,jint resource_id,jint part) {
    try {
        auto resources=find(env,id);if(!resources)return nullptr;
        std::lock_guard<std::mutex> lock(resources->mutex);
        resource_name name{};
        if(!AssetManager_getResourceName(resources->manager,static_cast<uint32_t>(resource_id),&name)) {error(env,"android/content/res/Resources$NotFoundException","Resource name is unavailable");return nullptr;}
        const char* utf8=nullptr;const char16_t* utf16=nullptr;size_t length=0;
        switch(part){case 0:utf8=name.package;length=name.package_len;break;case 1:utf8=name.type;utf16=name.type16;length=name.type_len;break;case 2:utf8=name.entry;utf16=name.entry16;length=name.entry_len;break;default:error(env,"java/lang/IllegalArgumentException","Resource name part");return nullptr;}
        if(length>65536||(!utf8&&!utf16)){error(env,"android/content/res/Resources$NotFoundException","Invalid resource name");return nullptr;}
        if(utf16)return env->NewString(reinterpret_cast<const jchar*>(utf16),static_cast<jsize>(length));
        jbyteArray bytes=env->NewByteArray(static_cast<jsize>(length));if(!bytes)return nullptr;
        env->SetByteArrayRegion(bytes,0,static_cast<jsize>(length),reinterpret_cast<const jbyte*>(utf8));
        if(env->ExceptionCheck()){env->DeleteLocalRef(bytes);return nullptr;}
        jclass string=env->FindClass("java/lang/String");if(!string){env->DeleteLocalRef(bytes);return nullptr;}
        jmethodID constructor=env->GetMethodID(string,"<init>","([BLjava/lang/String;)V");
        if(!constructor){env->DeleteLocalRef(bytes);env->DeleteLocalRef(string);return nullptr;}
        jstring encoding=env->NewStringUTF("UTF-8");
        jstring result=encoding?static_cast<jstring>(env->NewObject(string,constructor,bytes,encoding)):nullptr;
        if(encoding){env->DeleteLocalRef(encoding);}env->DeleteLocalRef(bytes);env->DeleteLocalRef(string);return result;
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Cannot read resource name");}
     catch(const std::exception&){error(env,"java/lang/IllegalStateException","Cannot read resource name");}
    return nullptr;
}

extern "C" JNIEXPORT jint JNICALL Java_android_content_res_Resources_nativeIdentifier(JNIEnv* env, jclass, jlong id, jbyteArray name, jbyteArray type, jbyteArray package_name) {
    try {
        auto resources = find(env, id);
        if (!resources) return 0;
        std::string values[3];
        jbyteArray inputs[3] = {name, type, package_name};
        for (int i = 0; i < 3; ++i) {
            if (!inputs[i]) { error(env, "java/lang/NullPointerException", "Resource identifier component"); return 0; }
            jsize length = env->GetArrayLength(inputs[i]);
            if (length > 1048576) { error(env, "java/lang/IllegalArgumentException", "Resource identifier component too long"); return 0; }
            values[i].resize(static_cast<size_t>(length));
            if (length) env->GetByteArrayRegion(inputs[i], 0, length, reinterpret_cast<jbyte*>(&values[i][0]));
            if (env->ExceptionCheck()) return 0;
            if (values[i].find('\0') != std::string::npos) return 0;
        }
        std::lock_guard<std::mutex> lock(resources->mutex);
        // NUL was rejected above, so separators preserve all three components.
        // Bound both key size and entry count; large names remain supported uncached.
        std::string key;
        const bool cacheable = values[0].size() + values[1].size() + values[2].size() <= 1024;
        if (cacheable) {
            key = values[0]; key.push_back('\0'); key += values[1];
            key.push_back('\0'); key += values[2];
            auto cached = resources->identifiers.find(key);
            if (cached != resources->identifiers.end()) return cached->second;
        }
        uint32_t resource_id = 0;
        bool complete = AssetManager_findResourceId(resources->manager, values[0].c_str(), values[1].c_str(), values[2].c_str(), &resource_id);
        jint result = static_cast<jint>(resource_id);
        // A missing name is stable too; incomplete I/O must remain retryable.
        if (cacheable && complete) {
            if (resources->identifiers.size() == 4096) resources->identifiers.clear();
            resources->identifiers.emplace(std::move(key), result);
        }
        return result;
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot allocate resource identifier"); }
      catch (const std::exception&) { error(env, "java/lang/IllegalStateException", "Cannot look up APK resource name"); }
    return 0;
}

extern "C" JNIEXPORT void JNICALL Java_android_content_res_Resources_nativeValidateStyle(JNIEnv* env, jclass, jlong id, jint style) {
    try {
        auto resources = find(env, id); if (!resources) return;
        std::lock_guard<std::mutex> lock(resources->mutex);
        validStyle(env, *resources, static_cast<uint32_t>(style));
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot validate style"); }
      catch (const std::exception&) { error(env, "java/lang/IllegalStateException", "Cannot validate APK style"); }
}
extern "C" JNIEXPORT jboolean JNICALL Java_android_content_res_Resources_nativeThemeResolve(JNIEnv* env, jclass, jlong id, jintArray styles, jint attribute, jboolean resolve, jintArray out, jobjectArray text) {
    try {
        auto resources = find(env, id); if (!resources) return JNI_FALSE;
        if (!styles || !out || !text) { error(env, "java/lang/NullPointerException", "Theme arguments"); return JNI_FALSE; }
        jsize count = env->GetArrayLength(styles);
        if (count > 2048 || count % 2 || env->GetArrayLength(out) != 6 || env->GetArrayLength(text) != 1) {
            error(env, "java/lang/IllegalArgumentException", "Invalid theme argument lengths"); return JNI_FALSE;
        }
        std::vector<jint> sequence(static_cast<size_t>(count));
        if (count) env->GetIntArrayRegion(styles, 0, count, sequence.data());
        if (env->ExceptionCheck()) return JNI_FALSE;
        std::lock_guard<std::mutex> lock(resources->mutex);
        Theme* theme = themeFor(env, *resources, sequence);
        if (!theme) return JNI_FALSE;
        Res_value value{}; uint32_t flags = 0, last = 0; ResTable_config selected{};
        auto cookie = Theme_getAttribute(theme, static_cast<uint32_t>(attribute), &value, &flags);
        if (cookie < 0) return JNI_FALSE;
        if (resolve) {
            cookie = AssetManager_resolveReference(resources->manager, cookie, &value, &selected, &flags, &last);
            if (cookie < 0) return JNI_FALSE;
        }
        jstring string = nullptr;
        if (value.dataType == TYPE_STRING) {
            auto pool = AssetManager_getStringPoolForCookie(resources->manager, cookie);
            size_t length = 0; auto chars = pool ? ResStringPool_stringAt(pool, value.data, &length) : nullptr;
            if (!chars || length > static_cast<size_t>(std::numeric_limits<jsize>::max())) {
                error(env, "android/content/res/Resources$NotFoundException", "Invalid theme string pool entry"); return JNI_FALSE;
            }
            string = env->NewString(reinterpret_cast<const jchar*>(chars), static_cast<jsize>(length));
            if (!string) return JNI_FALSE;
        }
        jint fields[6] = {jint(value.dataType), jint(value.data), jint(cookie + 1), jint(flags), jint(last), jint(selected.density)};
        env->SetObjectArrayElement(text, 0, string);
        if (string) env->DeleteLocalRef(string);
        if (env->ExceptionCheck()) return JNI_FALSE;
        env->SetIntArrayRegion(out, 0, 6, fields);
        return env->ExceptionCheck() ? JNI_FALSE : JNI_TRUE;
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot allocate APK theme"); }
      catch (const std::exception&) { error(env, "java/lang/IllegalStateException", "Cannot resolve APK theme attribute"); }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jintArray JNICALL Java_android_content_res_Resources_nativeColorList(JNIEnv* env, jclass, jlong id, jint resource_id, jintArray styles, jboolean integers) {
    try {
        auto resources = find(env, id); if (!resources) return nullptr;
        std::vector<jint> sequence;
        if (styles) {
            jsize count = env->GetArrayLength(styles);
            if (count > 2048 || count % 2) { error(env, "java/lang/IllegalArgumentException", "Invalid color theme history"); return nullptr; }
            sequence.resize(static_cast<size_t>(count));
            if (count) env->GetIntArrayRegion(styles, 0, count, sequence.data());
            if (env->ExceptionCheck()) return nullptr;
        }
        std::lock_guard<std::mutex> lock(resources->mutex);
        Theme* theme = styles ? themeFor(env, *resources, sequence) : nullptr;
        if (styles && !theme) return nullptr;
        shiraz::resources::ColorReader reader{resources->manager, resources->assets, resources->framework, theme, {}};
        auto entries = reader.colors(reader.resource(static_cast<uint32_t>(resource_id)), 0, integers == JNI_TRUE);
        std::vector<jint> packed; packed.push_back(static_cast<jint>(entries.size()));
        for (const auto& entry : entries) {
            packed.push_back(static_cast<jint>(entry.color));
            packed.push_back(entry.colorAttr); packed.push_back(entry.alphaAttr); packed.push_back(entry.baseColor);
            jint alpha; std::memcpy(&alpha, &entry.alpha, sizeof(alpha)); packed.push_back(alpha);
            packed.push_back(static_cast<jint>(entry.states.size()));
            for (auto state : entry.states) packed.push_back(state);
        }
        jintArray result = env->NewIntArray(static_cast<jsize>(packed.size()));
        if (result) env->SetIntArrayRegion(result, 0, static_cast<jsize>(packed.size()), packed.data());
        return result;
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot allocate color resource"); }
      catch (const std::logic_error& issue) { error(env, "java/lang/UnsupportedOperationException", issue.what()); }
      catch (const std::exception& issue) { error(env, "android/content/res/Resources$NotFoundException", issue.what()); }
    return nullptr;
}
extern "C" JNIEXPORT jlongArray JNICALL Java_android_content_res_Resources_nativeAssetStream(JNIEnv* env,jclass,jlong id,jbyteArray path){
    try{
        auto owner=find(env,id);if(!owner)return nullptr;
        auto stream=std::make_shared<AssetStream>();stream->owner=owner;
        {std::lock_guard<std::mutex> lock(owner->mutex);stream->opened=openAsset(env,*owner,path);}
        if(env->ExceptionCheck())return nullptr;
        if(!stream->opened.asset){error(env,"java/io/FileNotFoundException","Missing APK asset");return nullptr;}
        size_t length=Asset_getLength(stream->opened.asset.get());
        if(length>1024ULL*1024*1024)throw std::invalid_argument("Asset stream exceeds 1GiB");
        jlongArray result=env->NewLongArray(2);if(!result)return nullptr;
        std::lock_guard<std::mutex> lock(stream_mutex);
        if(streams.size()>=256||next_stream==std::numeric_limits<jlong>::max())throw std::runtime_error("Asset stream handle limit");
        jlong key=next_stream++;jlong values[2]={key,static_cast<jlong>(length)};
        env->SetLongArrayRegion(result,0,2,values);if(env->ExceptionCheck())return nullptr;
        streams.emplace(key,std::move(stream));return result;
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Cannot open asset stream");}
     catch(const std::exception& e){error(env,"java/io/IOException",e.what());}return nullptr;
}
extern "C" JNIEXPORT jint JNICALL Java_android_content_res_AssetManager_nativeRead(JNIEnv* env,jclass,jlong id,jbyteArray target,jint offset,jint count){
    try{
        if(!target){error(env,"java/lang/NullPointerException","target");return -1;}
        if(offset<0||count<0||offset>env->GetArrayLength(target)-count){error(env,"java/lang/IndexOutOfBoundsException","Asset read range");return -1;}
        auto stream=streamFor(id);std::lock_guard<std::mutex> lock(stream->mutex);if(count==0)return 0;
        std::array<jbyte,65536> buffer;int size=Asset_read(stream->opened.asset.get(),buffer.data(),std::min<size_t>(count,buffer.size()));
        if(size<0)throw std::runtime_error("Cannot read APK asset");
        if(size==0)return -1;
        env->SetByteArrayRegion(target,offset,size,buffer.data());return size;
    }catch(const std::exception& e){error(env,"java/io/IOException",e.what());return -1;}
}
extern "C" JNIEXPORT jlong JNICALL Java_android_content_res_AssetManager_nativeSeek(JNIEnv* env,jclass,jlong id,jlong position){
    try{
        auto stream=streamFor(id);std::lock_guard<std::mutex> lock(stream->mutex);
        if(position<0||static_cast<uint64_t>(position)>Asset_getLength(stream->opened.asset.get()))throw std::invalid_argument("Asset seek range");
        auto result=Asset_seek(stream->opened.asset.get(),position,SEEK_SET);if(result<0)throw std::runtime_error("Cannot seek APK asset");return result;
    }catch(const std::exception& e){error(env,"java/io/IOException",e.what());return -1;}
}
extern "C" JNIEXPORT void JNICALL Java_android_content_res_AssetManager_nativeClose(JNIEnv*,jclass,jlong id){
    std::lock_guard<std::mutex> lock(stream_mutex);streams.erase(id);
}
extern "C" JNIEXPORT jlongArray JNICALL Java_android_content_res_Resources_nativeAssetFd(JNIEnv* env, jclass, jlong id, jbyteArray path) {
    try {
        auto resources = find(env, id); if (!resources) return nullptr;
        std::lock_guard<std::mutex> lock(resources->mutex); auto opened = openAsset(env, *resources, path);
        if (env->ExceptionCheck()) return nullptr;
        if (!opened.asset) { error(env, "java/io/FileNotFoundException", "Missing APK asset"); return nullptr; }
        jlongArray result = env->NewLongArray(3); if (!result) return nullptr;
        off64_t start = 0, length = 0; int fd = Asset_openFileDescriptor(opened.asset.get(), &start, &length);
        if (fd < 0) { error(env, "java/io/IOException", "Asset is compressed or cannot provide a file descriptor"); return nullptr; }
        struct OwnedFd { int value; ~OwnedFd() { if (value >= 0) ::close(value); } } owned{fd};
        struct stat info{};
        if (start < 0 || length < 0 || fstat(fd, &info) || start > info.st_size || length > info.st_size - start) {
            error(env, "java/io/IOException", "Invalid APK asset descriptor region"); return nullptr;
        }
        jlong values[3] = {fd, start, length}; env->SetLongArrayRegion(result, 0, 3, values);
        if (env->ExceptionCheck()) return nullptr;
        owned.value = -1; return result;
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot open APK asset descriptor"); }
      catch (const std::invalid_argument& issue) { error(env, "java/lang/IllegalArgumentException", issue.what()); }
      catch (const std::exception& issue) { error(env, "java/io/IOException", issue.what()); }
    return nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_android_content_res_Resources_nativeResourceFile(JNIEnv* env,jclass,jlong handle,jint javaCookie,jbyteArray path) {
    try {
        auto resources=find(env,handle); if (!resources) return nullptr;
        if (!path) { error(env,"java/lang/NullPointerException","Resource file path"); return nullptr; }
        jsize length=env->GetArrayLength(path); if (length < 1 || length > 65536) throw std::invalid_argument("Resource file path length");
        std::string name(static_cast<size_t>(length),'\0'); env->GetByteArrayRegion(path,0,length,reinterpret_cast<jbyte*>(&name[0])); if (env->ExceptionCheck()) return nullptr;
        if (!shiraz::validArchiveFilePath(name))
            throw std::invalid_argument("Invalid resource file path");
        std::lock_guard<std::mutex> lock(resources->mutex);
        const ApkAssets* owner=javaCookie == 1 && resources->framework ? resources->framework : javaCookie == (resources->framework ? 2 : 1) ? resources->assets : nullptr;
        if (!owner && javaCookie != 0) throw std::invalid_argument("Unknown resource asset cookie");
        std::unique_ptr<AssetManager,decltype(&AssetManager_delete)> files(AssetManager_new(),AssetManager_delete); if (!files) throw std::bad_alloc();
        if (owner && !AssetManager_setApkAssets(files.get(),&owner,1,true,false)) throw std::runtime_error("Cannot open resource file owner");
        std::unique_ptr<Asset,decltype(&Asset_delete)> asset(AssetManager_openNonAsset(javaCookie == 0 ? resources->manager : files.get(),name.c_str(),ACCESS_BUFFER),Asset_delete);
        if (!asset) throw std::runtime_error("Missing resource file");
        size_t count=Asset_getLength(asset.get()); if (count > 16*1024*1024) throw std::runtime_error("Resource file exceeds 16MiB");
        const void* bytes=count ? Asset_getBuffer(asset.get(),true) : nullptr; if (count && !bytes) throw std::runtime_error("Cannot read resource file");
        auto result=env->NewByteArray(static_cast<jsize>(count)); if (result && count) env->SetByteArrayRegion(result,0,static_cast<jsize>(count),reinterpret_cast<const jbyte*>(bytes)); return result;
    } catch (const std::bad_alloc&) { error(env,"java/lang/OutOfMemoryError","Cannot read resource file"); }
      catch (const std::invalid_argument& issue) { error(env,"java/lang/IllegalArgumentException",issue.what()); }
      catch (const std::exception& issue) { error(env,"android/content/res/Resources$NotFoundException",issue.what()); }
    return nullptr;
}

extern "C" JNIEXPORT jlongArray JNICALL Java_android_content_res_Resources_nativeResourceFd(JNIEnv* env,jclass,jlong handle,jint javaCookie,jbyteArray path) {
    try {
        auto resources=find(env,handle); if (!resources) return nullptr;
        if (!path) { error(env,"java/lang/NullPointerException","Resource file path"); return nullptr; }
        jsize length=env->GetArrayLength(path); if (length < 1 || length > 65536) throw std::invalid_argument("Resource file path length");
        std::string name(static_cast<size_t>(length),'\0'); env->GetByteArrayRegion(path,0,length,reinterpret_cast<jbyte*>(&name[0])); if (env->ExceptionCheck()) return nullptr;
        if (!shiraz::validArchiveFilePath(name))
            throw std::invalid_argument("Invalid resource file path");
        std::lock_guard<std::mutex> lock(resources->mutex);
        const ApkAssets* owner=javaCookie == 1 && resources->framework ? resources->framework : javaCookie == (resources->framework ? 2 : 1) ? resources->assets : nullptr;
        if (!owner) throw std::invalid_argument("Unknown resource asset cookie");
        std::unique_ptr<AssetManager,decltype(&AssetManager_delete)> files(AssetManager_new(),AssetManager_delete); if (!files) throw std::bad_alloc();
        if (!AssetManager_setApkAssets(files.get(),&owner,1,true,false)) throw std::runtime_error("Cannot open resource file owner");
        std::unique_ptr<Asset,decltype(&Asset_delete)> asset(AssetManager_openNonAsset(files.get(),name.c_str(),ACCESS_BUFFER),Asset_delete);
        if (!asset) throw std::runtime_error("Missing resource file");
        off64_t start=0,regionLength=0;int fd=Asset_openFileDescriptor(asset.get(),&start,&regionLength);
        if(fd<0) return nullptr; // Android returns null for compressed resources.
        struct OwnedFd {int value;~OwnedFd(){if(value>=0)::close(value);}} owned{fd};
        struct stat info{};if(start<0||regionLength<0||fstat(fd,&info)||start>info.st_size||regionLength>info.st_size-start)throw std::runtime_error("Invalid resource descriptor region");
        jlongArray result=env->NewLongArray(3);if(!result)return nullptr;jlong values[]={fd,start,regionLength};env->SetLongArrayRegion(result,0,3,values);if(env->ExceptionCheck())return nullptr;owned.value=-1;return result;
    } catch (const std::bad_alloc&) { error(env,"java/lang/OutOfMemoryError","Cannot read resource file"); }
      catch (const std::invalid_argument& issue) { error(env,"java/lang/IllegalArgumentException",issue.what()); }
      catch (const std::exception& issue) { error(env,"android/content/res/Resources$NotFoundException",issue.what()); }
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_content_res_Resources_nativeStyleValue(JNIEnv* env,jclass,jlong id,jint style,jint attr,jintArray out,jobjectArray text) {
    try {
        auto resources=find(env,id);if(!resources)return JNI_FALSE;
        if(!out||!text||env->GetArrayLength(out)!=6||env->GetArrayLength(text)!=1){error(env,"java/lang/IllegalArgumentException","Style output lengths");return JNI_FALSE;}
        std::lock_guard<std::mutex> lock(resources->mutex);
        // Attribute queries use any available bag and otherwise fall back to the theme.
        // Explicit Theme.applyStyle validation remains separate.
        auto bag=AssetManager_getBag(resources->manager,static_cast<uint32_t>(style));
        if(!bag)return JNI_FALSE;
        if(bag->entry_count>65536){error(env,"java/lang/IllegalArgumentException","Style entry limit");return JNI_FALSE;}
        for(uint32_t i=0;i<bag->entry_count;i++) {
            const auto& entry=bag->entries[i];if(entry.key!=static_cast<uint32_t>(attr))continue;
            jstring string=nullptr;
            if(entry.value.dataType==TYPE_STRING) {
                auto pool=AssetManager_getStringPoolForCookie(resources->manager,entry.cookie);
                size_t length=0;auto chars=pool?ResStringPool_stringAt(pool,entry.value.data,&length):nullptr;
                if(!chars||length>static_cast<size_t>(std::numeric_limits<jsize>::max())){error(env,"android/content/res/Resources$NotFoundException","Style string pool entry");return JNI_FALSE;}
                string=env->NewString(reinterpret_cast<const jchar*>(chars),static_cast<jsize>(length));if(!string)return JNI_FALSE;
            }
            jint fields[6]={jint(entry.value.dataType),jint(entry.value.data),jint(entry.cookie+1),jint(bag->type_spec_flags),0,0};
            env->SetObjectArrayElement(text,0,string);if(string)env->DeleteLocalRef(string);
            if(env->ExceptionCheck())return JNI_FALSE;
            env->SetIntArrayRegion(out,0,6,fields);return env->ExceptionCheck()?JNI_FALSE:JNI_TRUE;
        }
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Style lookup allocation");}
     catch(const std::exception&){error(env,"java/lang/IllegalStateException","Style lookup failed");}
    return JNI_FALSE;
}

extern "C" JNIEXPORT jintArray JNICALL Java_android_content_res_Resources_nativeSizeConfigurations(JNIEnv* env,jclass,jlong id) {
    try {
    auto resources=find(env,id);if(!resources)return nullptr;
    std::lock_guard<std::mutex> lock(resources->mutex);
    constexpr size_t maximum=32768;
    std::vector<uint16_t> triples(maximum*3);
    size_t count=0;
    if(!AssetManager_getSizeConfigurations(resources->manager,triples.data(),maximum,&count)){
        error(env,"android/content/res/Resources$NotFoundException","Cannot enumerate app size configurations within 32768 limit");return nullptr;
    }
    if(count>maximum){error(env,"java/lang/IllegalStateException","Resource size configuration count overflow");return nullptr;}
    std::vector<jint> values(triples.begin(),triples.begin()+count*3);
    jintArray result=env->NewIntArray(static_cast<jsize>(values.size()));
    if(result&&!values.empty())env->SetIntArrayRegion(result,0,static_cast<jsize>(values.size()),values.data());
    return result;
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Resource size configuration allocation");return nullptr;}
    catch(const std::exception& failure){error(env,"android/content/res/Resources$NotFoundException",failure.what());return nullptr;}
}

extern "C" JNIEXPORT jobjectArray JNICALL Java_android_content_res_Resources_nativePlural(JNIEnv* env,jclass,jlong handle,jint id) {
    try {
        auto resources=find(env,handle);if(!resources)return nullptr;
        std::lock_guard<std::mutex> lock(resources->mutex);
        ResourceValue resolved;
        if(!loadValue(env,*resources,static_cast<uint32_t>(id),0,true,resolved,true))return nullptr;
        auto bag=AssetManager_getBag(resources->manager,resolved.current);
        if(!bag||bag->entry_count==0||bag->entry_count>64)throw std::runtime_error("Missing plural resource or plural exceeds 64 entries");
        auto strings=env->FindClass("java/lang/String");auto objects=env->FindClass("java/lang/Object");
        if(!strings||!objects)return nullptr;
        auto keys=env->NewIntArray(static_cast<jsize>(bag->entry_count));
        auto values=env->NewObjectArray(static_cast<jsize>(bag->entry_count),strings,nullptr);
        auto pair=env->NewObjectArray(2,objects,nullptr);
        env->DeleteLocalRef(strings);env->DeleteLocalRef(objects);
        if(!keys||!values||!pair)return nullptr;
        std::vector<jint> keyValues(bag->entry_count);
        size_t total=0;
        for(uint32_t i=0;i<bag->entry_count;i++) {
            auto value=bag->entries[i].value;auto cookie=bag->entries[i].cookie;
            keyValues[i]=static_cast<jint>(bag->entries[i].key);
            if(value.dataType==TYPE_REFERENCE&&value.data){
                ResourceValue item;if(!loadValue(env,*resources,value.data,0,true,item))return nullptr;
                value=item.value;cookie=item.cookie;
            }
            if(value.dataType!=TYPE_STRING)continue;
            auto pool=AssetManager_getStringPoolForCookie(resources->manager,cookie);
            size_t length=0;auto chars=pool?ResStringPool_stringAt(pool,value.data,&length):nullptr;
            if(!chars||length>1048576||total+length>16*1024*1024)throw std::runtime_error("Invalid or oversized plural string");
            total+=length;auto text=env->NewString(reinterpret_cast<const jchar*>(chars),static_cast<jsize>(length));if(!text)return nullptr;
            env->SetObjectArrayElement(values,i,text);env->DeleteLocalRef(text);if(env->ExceptionCheck())return nullptr;
        }
        env->SetIntArrayRegion(keys,0,static_cast<jsize>(keyValues.size()),keyValues.data());
        env->SetObjectArrayElement(pair,0,keys);env->SetObjectArrayElement(pair,1,values);
        env->DeleteLocalRef(keys);env->DeleteLocalRef(values);
        return env->ExceptionCheck()?nullptr:pair;
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Plural allocation");}
     catch(const std::exception& issue){error(env,"android/content/res/Resources$NotFoundException",issue.what());}
    return nullptr;
}

extern "C" JNIEXPORT jobjectArray JNICALL Java_android_content_res_Resources_nativeTextArray(JNIEnv* env,jclass,jlong handle,jint id) {
    try {
        auto resources=find(env,handle);if(!resources)return nullptr;
        std::lock_guard<std::mutex> lock(resources->mutex);
        ResourceValue resolved;
        if(!loadValue(env,*resources,static_cast<uint32_t>(id),0,true,resolved,true))return nullptr;
        auto bag=AssetManager_getBag(resources->manager,resolved.current);
        if(!bag||bag->entry_count>65536)throw std::runtime_error("Missing resource array or array exceeds 65536 entries");
        auto cls=env->FindClass("java/lang/String");if(!cls)return nullptr;
        auto result=env->NewObjectArray(bag->entry_count,cls,nullptr);env->DeleteLocalRef(cls);if(!result)return nullptr;
        size_t total=0;
        for(uint32_t i=0;i<bag->entry_count;i++) {
            auto value=bag->entries[i].value;auto cookie=bag->entries[i].cookie;
            if(value.dataType==TYPE_REFERENCE&&value.data){
                ResourceValue item;if(!loadValue(env,*resources,value.data,0,true,item))return nullptr;
                value=item.value;cookie=item.cookie;
            }
            if(value.dataType!=TYPE_STRING)continue;
            auto pool=AssetManager_getStringPoolForCookie(resources->manager,cookie);
            size_t length=0;auto chars=pool?ResStringPool_stringAt(pool,value.data,&length):nullptr;
            if(!chars||length>1048576||total+length>16*1024*1024)throw std::runtime_error("Invalid or oversized array string");
            total+=length;auto text=env->NewString(reinterpret_cast<const jchar*>(chars),static_cast<jsize>(length));if(!text)return nullptr;
            env->SetObjectArrayElement(result,i,text);env->DeleteLocalRef(text);if(env->ExceptionCheck())return nullptr;
        }
        return result;
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Resource array allocation");}
     catch(const std::exception& issue){error(env,"android/content/res/Resources$NotFoundException",issue.what());}
    return nullptr;
}

extern "C" JNIEXPORT jintArray JNICALL Java_android_content_res_Resources_nativeIntArray(JNIEnv* env,jclass,jlong handle,jint id) {
    try {
        auto resources=find(env,handle);if(!resources)return nullptr;
        std::lock_guard<std::mutex> lock(resources->mutex);
        ResourceValue resolved;
        if(!loadValue(env,*resources,static_cast<uint32_t>(id),0,true,resolved,true))return nullptr;
        auto bag=AssetManager_getBag(resources->manager,resolved.current);
        if(!bag||bag->entry_count>65536)throw std::runtime_error("Missing resource array or array exceeds 65536 entries");
        auto result=env->NewIntArray(static_cast<jsize>(bag->entry_count));if(!result)return nullptr;
        std::vector<jint> values(bag->entry_count);
        for(uint32_t i=0;i<bag->entry_count;i++) {
            auto value=bag->entries[i].value;
            if(value.dataType==TYPE_REFERENCE&&value.data){
                ResourceValue item;if(!loadValue(env,*resources,value.data,0,true,item))return nullptr;
                value=item.value;
            }
            if(value.dataType<0x10||value.dataType>0x1f)throw std::runtime_error("Resource array entry is not an integer");
            values[i]=static_cast<jint>(value.data);
        }
        env->SetIntArrayRegion(result,0,static_cast<jsize>(values.size()),values.data());
        return env->ExceptionCheck()?nullptr:result;
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Resource int array allocation");}
     catch(const std::exception& issue){error(env,"android/content/res/Resources$NotFoundException",issue.what());}
    return nullptr;
}

extern "C" JNIEXPORT jobjectArray JNICALL Java_android_content_res_Resources_nativeTypedArray(JNIEnv* env,jclass,jlong handle,jint id) {
    try {
        auto resources=find(env,handle);if(!resources)return nullptr;
        std::lock_guard<std::mutex> lock(resources->mutex);
        ResourceValue resolved;
        if(!loadValue(env,*resources,static_cast<uint32_t>(id),0,true,resolved,true))return nullptr;
        auto bag=AssetManager_getBag(resources->manager,resolved.current);
        if(!bag||bag->entry_count>65536)throw std::runtime_error("Missing resource array or array exceeds 65536 entries");
        auto objectClass=env->FindClass("java/lang/Object");
        auto stringClass=env->FindClass("java/lang/String");
        if(!objectClass||!stringClass)return nullptr;
        auto pair=env->NewObjectArray(2,objectClass,nullptr);
        auto fields=env->NewIntArray(static_cast<jsize>(bag->entry_count*6));
        auto strings=env->NewObjectArray(static_cast<jsize>(bag->entry_count),stringClass,nullptr);
        env->DeleteLocalRef(objectClass);env->DeleteLocalRef(stringClass);
        if(!pair||!fields||!strings)return nullptr;
        std::vector<jint> fieldValues(bag->entry_count*6);
        size_t total=0;
        for(uint32_t i=0;i<bag->entry_count;i++) {
            auto value=bag->entries[i].value;auto cookie=bag->entries[i].cookie;
            uint32_t resId=0;uint32_t flags=resolved.flags;uint16_t density=resolved.selected.density;
            if(value.dataType==TYPE_REFERENCE&&value.data){
                resId=value.data;
                ResourceValue item;if(!loadValue(env,*resources,value.data,0,true,item,true))return nullptr;
                value=item.value;cookie=item.cookie;resId=item.current;flags|=item.flags;density=item.selected.density;
            }
            if(value.dataType==TYPE_STRING){
                auto pool=AssetManager_getStringPoolForCookie(resources->manager,cookie);
                size_t length=0;auto chars=pool?ResStringPool_stringAt(pool,value.data,&length):nullptr;
                if(!chars||length>1048576||total+length>16*1024*1024)throw std::runtime_error("Invalid or oversized typed array string");
                total+=length;auto text=env->NewString(reinterpret_cast<const jchar*>(chars),static_cast<jsize>(length));if(!text)return nullptr;
                env->SetObjectArrayElement(strings,static_cast<jsize>(i),text);env->DeleteLocalRef(text);if(env->ExceptionCheck())return nullptr;
            }
            size_t base=static_cast<size_t>(i)*6;
            fieldValues[base]=jint(value.dataType);
            fieldValues[base+1]=jint(value.data);
            fieldValues[base+2]=jint(cookie+1);
            fieldValues[base+3]=jint(flags);
            fieldValues[base+4]=jint(resId);
            fieldValues[base+5]=jint(density);
        }
        env->SetIntArrayRegion(fields,0,static_cast<jsize>(fieldValues.size()),fieldValues.data());
        env->SetObjectArrayElement(pair,0,fields);env->SetObjectArrayElement(pair,1,strings);
        env->DeleteLocalRef(fields);env->DeleteLocalRef(strings);
        return env->ExceptionCheck()?nullptr:pair;
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Resource typed array allocation");}
     catch(const std::exception& issue){error(env,"android/content/res/Resources$NotFoundException",issue.what());}
    return nullptr;
}

// NDK asset access shares the same immutable APK archive used by Java Resources.
// Keep the owner alive because Android's AAssetManager API has no release call.
extern "C" AssetManager* AAssetManager_fromJava(JNIEnv* env, jobject java_manager) {
    if (!env || !java_manager) return nullptr;
    jclass manager_class = env->GetObjectClass(java_manager);
    if (!manager_class) return nullptr;
    jfieldID resources_field = env->GetFieldID(manager_class, "resources", "Landroid/content/res/Resources;");
    env->DeleteLocalRef(manager_class);
    if (!resources_field) return nullptr;
    jobject java_resources = env->GetObjectField(java_manager, resources_field);
    if (!java_resources) return nullptr;
    jclass resources_class = env->GetObjectClass(java_resources);
    if (!resources_class) { env->DeleteLocalRef(java_resources); return nullptr; }
    jfieldID handle_field = env->GetFieldID(resources_class, "handle", "J");
    env->DeleteLocalRef(resources_class);
    if (!handle_field) { env->DeleteLocalRef(java_resources); return nullptr; }
    jlong handle = env->GetLongField(java_resources, handle_field);
    env->DeleteLocalRef(java_resources);
    std::shared_ptr<Resources> owner;
    {
        std::lock_guard<std::mutex> lock(registry_mutex);
        auto found = registry.find(handle);
        if (found == registry.end()) return nullptr;
        owner = found->second;
    }
    try {
        std::lock_guard<std::mutex> lock(ndk_managers_mutex);
        for (const auto& retained : ndk_managers)
            if (retained.get() == owner.get()) return owner->manager;
        ndk_managers.push_back(owner);
    } catch (const std::bad_alloc&) { return nullptr; }
    return owner->manager;
}

extern "C" Asset* AAssetManager_open(AssetManager* manager, const char* name, int mode) {
    if (!manager || !name || mode < ACCESS_UNKNOWN || mode > ACCESS_BUFFER) return nullptr;
    size_t length = strnlen(name, 65537);
    if (!length || length > 65536 || name[0] == '/' || strchr(name, '\\')) return nullptr;
    std::string path;
    try {
        path = "assets/";
        const char* begin = name;
        for (const char* current = name; ; ++current) {
            if (*current == '/' || !*current) {
                size_t count = static_cast<size_t>(current - begin);
                if (!count || (count == 1 && begin[0] == '.') ||
                    (count == 2 && begin[0] == '.' && begin[1] == '.')) return nullptr;
                if (!*current) break;
                begin = current + 1;
            }
        }
        path += name;
    } catch (const std::bad_alloc&) { return nullptr; }
    Asset* asset = AssetManager_openNonAsset(manager, path.c_str(), static_cast<AccessMode>(mode));
    if (asset && Asset_getLength(asset) > 128 * 1024 * 1024) {
        Asset_delete(asset);
        return nullptr;
    }
    return asset;
}

extern "C" const void* AAsset_getBuffer(Asset* asset) {
    return asset ? Asset_getBuffer(asset, false) : nullptr;
}
extern "C" off_t AAsset_getLength(Asset* asset) {
    return asset ? static_cast<off_t>(Asset_getLength(asset)) : 0;
}
extern "C" int AAsset_isAllocated(Asset* asset) {
    return asset && Asset_getBuffer(asset, false) ? 1 : 0;
}
extern "C" void AAsset_close(Asset* asset) {
    if (asset) Asset_delete(asset);
}
