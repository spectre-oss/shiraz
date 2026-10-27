// SPDX-License-Identifier: GPL-3.0-only
// Native per-APK AppImage launcher. Python is used at package-build time only.
#include <sys/file.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <algorithm>
#include <cerrno>
#include <chrono>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <limits>
#include <map>
#include <regex>
#include <stdexcept>
#include <string>
#include <vector>
namespace fs = std::filesystem;
namespace {
#if defined(__aarch64__)
constexpr const char* hostLoader = "ld-linux-aarch64.so.1";
constexpr const char* artIsa = "arm64";
#elif defined(__x86_64__)
constexpr const char* hostLoader = "ld-linux-x86-64.so.2";
constexpr const char* artIsa = "x86_64";
#else
#error Unsupported Shiraz launcher architecture
#endif
[[noreturn]] void fail(const std::string& message) { throw std::runtime_error(message); }
std::string error(const std::string& operation) { return operation + ": " + std::strerror(errno); }
std::string readFile(const fs::path& path, std::size_t limit) {
    std::ifstream input(path, std::ios::binary);
    if (!input) fail("Cannot read " + path.string());
    std::string result; char buffer[4096];
    while (input) { input.read(buffer, sizeof(buffer)); result.append(buffer, static_cast<std::size_t>(input.gcount())); if (result.size() > limit) fail("File exceeds launcher limit: " + path.string()); }
    if (!input.eof()) fail("Cannot finish reading " + path.string());
    if (result.find('\0') != std::string::npos) fail("NUL in launcher metadata");
    return result;
}
std::map<std::string,std::string> config(const fs::path& file) {
    std::map<std::string,std::string> values; std::string text=readFile(file,16384); std::size_t begin=0;
    while(begin<text.size()) { auto end=text.find('\n',begin); if(end==std::string::npos)end=text.size();std::string line=text.substr(begin,end-begin);begin=end+1;if(line.empty())continue;
        auto eq=line.find('=');if(eq==std::string::npos||!values.emplace(line.substr(0,eq),line.substr(eq+1)).second)fail("Malformed launch.properties"); }
    for(const char* key:{"package","application","activity","sha256","version"})if(!values.count(key))fail(std::string("Missing launch property: ")+key);
    std::regex component("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    for(const char* key:{"package","application","activity"})if(values[key].size()>1024||!std::regex_match(values[key],component))fail("Invalid component metadata");
    if(!std::regex_match(values["sha256"],std::regex("[0-9a-f]{64}")))fail("Invalid packaged APK fingerprint");
    if(!std::regex_match(values["version"],std::regex("[0-9]+\\.[0-9]+\\.[0-9]+")))fail("Invalid runtime version");
    return values;
}
bool within(const fs::path& path,const fs::path& root) {
    auto p=path.begin(); for(auto r=root.begin();r!=root.end();++r,++p)if(p==path.end()||*p!=*r)return false;return true;
}
fs::path absolute(const std::string& value,const char* label) {
    fs::path path(value);if(!path.is_absolute())fail(std::string(label)+" must be absolute");return fs::weakly_canonical(path);
}
fs::path xdg(const char* name,const char* fallback) {
    const char* value=std::getenv(name);if(value&&*value&&fs::path(value).is_absolute())return absolute(value,name);
    const char* home=std::getenv("HOME");if(!home||!*home||!fs::path(home).is_absolute())fail("Set an absolute HOME or XDG data/cache directory");return absolute(home,"HOME")/fallback;
}
void directory(const fs::path& path) { fs::create_directories(path);if(!fs::is_directory(path))fail("Not a directory: "+path.string()); }
int number(const std::string& value,int low,int high,const char* name) {
    if(value.empty()||value.size()>10)fail(std::string("Invalid ")+name);
    long long n=0;for(char c:value){if(c<'0'||c>'9')fail(std::string("Invalid ")+name);n=n*10+c-'0';if(n>high)fail(std::string(name)+" exceeds limit");}if(n<low)fail(std::string(name)+" below minimum");return static_cast<int>(n);
}
std::string escape(const std::string& value) {
    std::string result;const char* hex="0123456789abcdef";for(unsigned char c:value){switch(c){case '\\':case ' ':case '=':case ':':case '#':case '!':result+='\\';result+=static_cast<char>(c);break;case '\n':result+="\\n";break;case '\r':result+="\\r";break;case '\t':result+="\\t";break;case '\f':result+="\\f";break;default:if(c<32){result+="\\u00";result+=hex[c>>4];result+=hex[c&15];}else result+=static_cast<char>(c);}}return result;
}
void writeAll(int fd,const std::string& text) {
    std::size_t at=0;while(at<text.size()){ssize_t n=write(fd,text.data()+at,text.size()-at);if(n<0&&errno==EINTR)continue;if(n<=0)fail(error("write"));at+=static_cast<std::size_t>(n);}
}
void atomicWrite(const fs::path& file,const std::string& text) {
    fs::path temporary=file.string()+".tmp."+std::to_string(getpid());int fd=open(temporary.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW,0600);if(fd<0)fail(error("create metadata"));
    try{writeAll(fd,text);if(fsync(fd))fail(error("sync metadata"));if(close(fd)){fd=-1;fail(error("close metadata"));}fd=-1;if(rename(temporary.c_str(),file.c_str()))fail(error("replace metadata"));}catch(...){if(fd>=0)close(fd);unlink(temporary.c_str());throw;}
}
void env(const char* name,const std::string& value) {if(setenv(name,value.c_str(),1))fail(error(std::string("set ")+name));}
void requireFile(const fs::path& file) {if(!fs::is_regular_file(file))fail("Missing packaged runtime file: "+file.string());}
long long timestamp(const std::string& value) {
    if(value.empty()||value.size()>16)fail("Invalid installation timestamp");
    long long n=0;for(char c:value){if(c<'0'||c>'9')fail("Invalid installation timestamp");n=n*10+c-'0';}if(n<=0)fail("Invalid installation timestamp");return n;
}
std::pair<long long,long long> timestamps(const fs::path& data,const std::string& sha) {
    long long now=std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
    long long first=now,last=now;fs::path record=data/".package-install.native";
    if(fs::exists(record)){std::string text=readFile(record,256);std::size_t a=text.find('\n'),b=text.find('\n',a==std::string::npos?0:a+1),c=text.find('\n',b==std::string::npos?0:b+1);
        if(a!=64||b==std::string::npos||c!=text.size()-1||!std::regex_match(text.substr(0,a),std::regex("[0-9a-f]{64}")))fail("Malformed installation record");
        first=timestamp(text.substr(a+1,b-a-1));last=timestamp(text.substr(b+1,c-b-1));if(last<first)fail("Installation timestamp order");if(text.substr(0,a)!=sha){if(last>=9999999999999999LL)fail("Installation timestamp limit");last=std::max(now,last+1);}}
    atomicWrite(record,sha+"\n"+std::to_string(first)+"\n"+std::to_string(last)+"\n");return {first,last};
}

#include "desktop_install.h"

void help(){std::cout<<"Usage: AppRun [--width N] [--height N] [--frames N]\n"
 "              [--snapshot /absolute/new.ppm] [--data-dir /absolute/directory]\n"
 "              [--version] [--diagnose] [--help]\n"
 "       AppRun install  (extract runtime and register a per-user desktop launcher)\n"
 "Runs this packaged APK using standalone ART and Shiraz.\n"
 "Default display: 720x1280. SHIRAZ_ART_MODE=jit (default) or interpreter.\n"
 "SHIRAZ_ART_HEAP_MB: Java heap cap in MiB, 128..512 (default 512).\n"
 "Data persists under XDG_DATA_HOME/shiraz-apps/<package>; logs are under\n"
 "XDG_CACHE_HOME/shiraz-apps/<package>/runs. Use an external timeout command\n"
 "for a wall-clock limit. This experimental runtime does not promise full APK compatibility.\n";}
}
int main(int argc,char** argv) {
 try{
    umask(0077);
    if(argc<1||!argv[0]||!fs::path(argv[0]).is_absolute())fail("Launcher requires its absolute executable path as argv[0]");
    // /proc/self/exe identifies ld-linux when invoked through the bundled loader.
    fs::path executable=fs::canonical(argv[0]);if(executable.filename()!="shiraz-app"||executable.parent_path().filename()!="bin"||executable.parent_path().parent_path().filename()!="usr")fail("Unexpected packaged executable layout");
    fs::path root=executable.parent_path().parent_path().parent_path(),usr=root/"usr",share=usr/"share/shiraz",lib=usr/"lib",art=usr/"art";
    if(root.string().find(':')!=std::string::npos)fail("AppDir path cannot contain a colon (Java classpath separator)");
    auto facts=config(share/"launch.properties");int width=720,height=1280,frames=-1;std::string snapshot="-",dataOption;bool diagnose=false;
    if(argc==2&&std::string(argv[1])=="install"){installDesktop(root,facts);return 0;}
    for(int i=1;i<argc;i++){std::string option=argv[i];if(option=="--help"){help();return 0;}if(option=="--version"){std::cout<<"Shiraz "<<facts["version"]<<" ("<<facts["package"]<<")\n";return 0;}if(option=="--diagnose"){diagnose=true;continue;}
        if(option!="--width"&&option!="--height"&&option!="--frames"&&option!="--snapshot"&&option!="--data-dir")fail("Unknown option: "+option);
        if(++i==argc)fail("Missing value for "+option);
        std::string value=argv[i];
        if(option=="--width")width=number(value,1,8192,"width");else if(option=="--height")height=number(value,1,8192,"height");else if(option=="--frames")frames=number(value,1,1000000,"frames");else if(option=="--snapshot")snapshot=value;else dataOption=value;
    }
    if(static_cast<long long>(width)*height>16*1024*1024)fail("Display exceeds 16 million pixel limit");
    const std::vector<std::string> jars={"core-oj-hostdex.jar","core-libart-hostdex.jar","okhttp-hostdex.jar","bouncycastle-hostdex.jar","wolfssljni-hostdex.jar"};std::string boot;
    for(const auto& jar:jars){fs::path file=art/"framework"/jar;requireFile(file);if(!boot.empty())boot+=':';boot+=file.string();}
    for(const auto& file:{lib/hostLoader,art/"bin/dalvikvm",usr/"bin/dex2oat",share/"original.apk",share/"shiraz-framework.jar",share/"framework-res.apk",share/"application.properties",share/"fonts.conf",share/"cacerts.jks",share/"espeak-ng-data/phondata"})requireFile(file);
    std::string mode="jit";if(const char* configured=std::getenv("SHIRAZ_ART_MODE"))mode=configured;if(mode!="jit"&&mode!="interpreter")fail("SHIRAZ_ART_MODE must be jit or interpreter");if(mode=="jit")requireFile(lib/"libart-compiler.so");
    const std::string aotProperty=facts.count("aot")?facts.at("aot"):"false";
    if(aotProperty!="true"&&aotProperty!="false")fail("Invalid packaged AOT mode");
    const bool packagedAot=aotProperty=="true";
    if(packagedAot){
        for(size_t i=0;i<jars.size();++i){
            const std::string stem=i==0?"boot":"boot-"+fs::path(jars[i]).stem().string();
            for(const char* extension:{".art",".oat",".vdex"})requireFile(art/"framework"/artIsa/(stem+extension));
        }
        for(const char* stem:{"shiraz-framework","original"})for(const char* extension:{".odex",".vdex"})requireFile(share/"oat"/artIsa/(std::string(stem)+extension));
    }
    const std::string nativeProperty=facts.count("native")?facts.at("native"):"false";
    if(nativeProperty!="true"&&nativeProperty!="false")fail("Invalid packaged native mode");
    const bool packagedNative=nativeProperty=="true";
    if(packagedNative){
        if(!fs::is_directory(usr/"android-lib")||fs::is_empty(usr/"android-lib"))fail("Missing Android native libraries");
        for(const auto& file:{lib/"libshiraz-android-fonts.so",lib/"libshiraz-android-process.so",lib/"libjnigraphics.so",share/"fonts/android-fonts.xml",share/"bionic_translation/cfg.d/bionic_translation.cfg",share/"bionic_translation/cfg.d/shiraz-host.cfg"})requireFile(file);
    }
    int heapMiB=512;if(const char* configured=std::getenv("SHIRAZ_ART_HEAP_MB"))heapMiB=number(configured,128,512,"SHIRAZ_ART_HEAP_MB");
    fs::path data=dataOption.empty()?xdg("XDG_DATA_HOME",".local/share")/"shiraz-apps"/facts["package"]:absolute(dataOption,"data directory");
    fs::path cache=xdg("XDG_CACHE_HOME",".cache")/"shiraz-apps"/facts["package"];
    data=fs::weakly_canonical(data);cache=fs::weakly_canonical(cache);if(within(data,root)||within(cache,root))fail("Writable directories must be outside AppDir");
    if(snapshot!="-"){fs::path requested=absolute(snapshot,"snapshot");if(requested.extension()!=".ppm"||within(requested,root)||fs::exists(requested)||fs::is_symlink(fs::symlink_status(requested)))fail("Snapshot must be a new .ppm file outside AppDir");if(!fs::is_directory(requested.parent_path()))fail("Snapshot parent directory does not exist");snapshot=requested.string();}
    if(diagnose){std::cout<<"AppDir: "<<root<<"\nPackage: "<<facts["package"]<<"\nRuntime: "<<facts["version"]<<"\nART mode: "<<mode<<"\nPackaged AOT: "<<(packagedAot?"yes":"no")<<"\nJava heap cap MiB: "<<heapMiB<<"\nData: "<<data<<"\nCache: "<<cache<<"\nPayload layout checks passed; this does not test display, audio, TLS or APK compatibility.\n";return 0;}
    directory(data);directory(cache);
    int lock=open((data/".session.lock").c_str(),O_RDWR|O_CREAT|O_NOFOLLOW,0600);if(lock<0)fail(error("open session lock"));if(flock(lock,LOCK_EX|LOCK_NB))fail("This data directory already has a running session"); // intentionally inherited by ART
    auto times=timestamps(data,facts["sha256"]);
    fs::path runs=cache/"runs";directory(runs);std::string pattern=(runs/"run-XXXXXX").string();std::vector<char> temp(pattern.begin(),pattern.end());temp.push_back('\0');if(!mkdtemp(temp.data()))fail(error("create run directory"));fs::path work=temp.data();
    for(const char* child:{"tmp","home","cache","android-data/dalvik-cache"})directory(work/child);
    std::string metadata=readFile(share/"application.properties",4*1024*1024);if(!metadata.empty()&&metadata.back()!='\n')metadata+='\n';
    for(const auto& pair:std::vector<std::pair<std::string,std::string>>{{"sourceDir",(share/"original.apk").string()},{"dataDir",data.string()},{"firstInstallTime",std::to_string(times.first)},{"lastUpdateTime",std::to_string(times.second)}})metadata+=pair.first+"="+escape(pair.second)+"\n";
    if(packagedNative)metadata+="nativeLibraryDir="+escape((usr/"android-lib").string())+"\n";
    fs::path properties=work/"application.properties";atomicWrite(properties,metadata);
    // Desktop sessions can expose PipeWire without an ALSA hardware card.
    // Preserve explicit device overrides and use the bundled plugin when available.
    if(!std::getenv("SHIRAZ_AUDIO_DEVICE")) {
        const char* runtime=std::getenv("XDG_RUNTIME_DIR");
        if(runtime && fs::is_socket(fs::path(runtime)/"pipewire-0") &&
           fs::is_regular_file(usr/"lib/alsa-lib/libasound_module_pcm_pipewire.so"))
            env("SHIRAZ_AUDIO_DEVICE","pipewire");
    }
    if(fs::is_directory(usr/"share/alsa")) {
        env("ALSA_CONFIG_DIR",(usr/"share/alsa").string());
        if(fs::is_regular_file(usr/"share/alsa/alsa.conf"))env("ALSA_CONFIG_PATH",(usr/"share/alsa/alsa.conf").string());
    }
    for(const auto& setting:std::vector<std::pair<const char*,fs::path>>{
        {"ALSA_PLUGIN_DIR",usr/"lib/alsa-lib"},{"SPA_PLUGIN_DIR",usr/"lib/spa-0.2"},
        {"PIPEWIRE_MODULE_DIR",usr/"lib/pipewire-0.3"},{"PIPEWIRE_CONFIG_DIR",usr/"share/pipewire"},
        {"XKB_CONFIG_ROOT",usr/"share/X11/xkb"}}) {
        if(fs::is_directory(setting.second))env(setting.first,setting.second.string());
    }
    const fs::path mesaVendor=usr/"share/glvnd/egl_vendor.d/50_mesa.json";
    if(fs::is_regular_file(mesaVendor)){
        if(!std::getenv("__EGL_VENDOR_LIBRARY_FILENAMES"))env("__EGL_VENDOR_LIBRARY_FILENAMES",mesaVendor.string());
        if(!std::getenv("LIBGL_DRIVERS_PATH"))env("LIBGL_DRIVERS_PATH",lib.string());
        if(!std::getenv("LIBGL_ALWAYS_SOFTWARE"))env("LIBGL_ALWAYS_SOFTWARE","1");
    }
    env("SHIRAZ_APPDIR",root.string());
    for(const auto& item:std::vector<std::pair<std::string,std::string>>{{"XDG_DATA_HOME",".local/share"},{"XDG_CACHE_HOME",".cache"},{"XDG_STATE_HOME",".local/state"}})
        env(("SHIRAZ_HOST_"+item.first).c_str(),xdg(item.first.c_str(),item.second.c_str()).string());
    env("ANDROID_ROOT",art.string());env("ANDROID_DATA",(work/"android-data").string());env("BOOTCLASSPATH",boot);env("LD_LIBRARY_PATH",lib.string());env("XDG_CACHE_HOME",(work/"cache").string());env("XDG_DATA_DIRS",share.string()+":/usr/share");env("SHIRAZ_FONT_DIR",(share/"fonts").string());env("FONTCONFIG_FILE",(share/"fonts.conf").string());
    if(packagedNative){env("BIONIC_LD_LIBRARY_PATH",(usr/"android-lib").string());env("SHIRAZ_ANDROID_FONT_DIR",(share/"fonts").string());env("SHIRAZ_ANDROID_DISPLAY_ID","Shiraz "+facts["version"]);env("SHIRAZ_ANDROID_RELEASE","8.1");
        if(fs::is_regular_file(lib/"libEGL_shiraz.so.1"))env("LD_PRELOAD","libandroid.so:libshiraz-resources.so:libEGL_shiraz.so.1:libjnigraphics.so");}
    else {unsetenv("BIONIC_LD_LIBRARY_PATH");unsetenv("SHIRAZ_ANDROID_FONT_DIR");}
    // Keep one non-moving allocation space: a spare compaction heap consumes scarce
    // low-address virtual memory needed by large Java pixel arrays on host ART.
    std::vector<std::string> arguments={(lib/hostLoader).string(),"--library-path",lib.string(),(art/"bin/dalvikvm").string(),"-Xmx"+std::to_string(heapMiB)+"m","-Xgc:CMS","-XX:BackgroundGC=CMS","-XX:DisableHSpaceCompactForOOM","-Xbootclasspath:"+boot,"-Xnoimage-dex2oat",mode=="jit"?"-Xusejit:true":"-Xusejit:false"};if(mode=="interpreter")arguments.push_back("-Xint");
    if(packagedNative){arguments.insert(arguments.begin()+1,{"--preload","libshiraz-android-fonts.so:libshiraz-android-process.so"});}
    if(packagedAot&&mode!="interpreter"){
        std::string logical;for(const auto& jar:jars){if(!logical.empty())logical+=':';logical+="/shiraz/boot/"+jar;}
        arguments.push_back("-Xbootclasspath-locations:"+logical);
        arguments.push_back("-Ximage:"+(art/"framework/boot.art").string());
        arguments.push_back("-Xno-dex-file-fallback");
    }
    if(const char* trace=std::getenv("SHIRAZ_TRACE_AOT"))if(std::string(trace)=="1")arguments.push_back("-verbose:oat");
    // JNI verification is a diagnostic mode; component smoke tests enable it.
    if(const char* check=std::getenv("SHIRAZ_CHECK_JNI"))if(std::string(check)=="1")arguments.push_back("-Xcheck:jni");
    for(const auto& arg:std::vector<std::string>{"-Xcompiler:"+(usr/"bin/dex2oat").string(),"-Djava.library.path="+lib.string()+(packagedNative?":"+(usr/"android-lib").string():""),"-Dshiraz.frameworkResources="+(share/"framework-res.apk").string(),"-Dshiraz.speechData="+share.string(),"-Djavax.net.ssl.trustStore="+(share/"cacerts.jks").string(),"-Djavax.net.ssl.trustStoreType=JKS","-Djavax.net.ssl.trustStorePassword=changeit","-Djava.io.tmpdir="+(work/"tmp").string(),"-Duser.home="+(work/"home").string(),"-cp",(share/"shiraz-framework.jar").string()+":"+(share/"original.apk").string(),"shiraz.runtime.ActivityRunner",facts["package"],facts["application"],facts["activity"],std::to_string(width),std::to_string(height),std::to_string(frames),data.string(),snapshot,properties.string()})arguments.push_back(arg);
    std::cerr<<"Launching "<<facts["package"]<<" with Shiraz "<<facts["version"]<<"; logs: "<<work<<"\n";
    for(const auto& entry:std::vector<std::pair<int,const char*>>{{STDOUT_FILENO,"stdout.log"},{STDERR_FILENO,"stderr.log"}}){int fd=open((work/entry.second).c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW,0600);if(fd<0)fail(error("create log"));if(dup2(fd,entry.first)<0)fail(error("redirect log"));if(fd!=entry.first)close(fd);}
    if(chdir(data.c_str()))fail(error("enter app data directory"));
    std::vector<char*> raw;for(auto& arg:arguments)raw.push_back(arg.data());raw.push_back(nullptr);execv(raw[0],raw.data());fail(error("launch ART"));
 }catch(const std::exception& failure){std::cerr<<"Shiraz launcher: "<<failure.what()<<"\n";return 1;}
}
