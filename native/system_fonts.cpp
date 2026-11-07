// SPDX-License-Identifier: GPL-3.0-only
#if defined(__APPLE__)
#include <CoreFoundation/CoreFoundation.h>
#include <CoreText/CoreText.h>
#include <algorithm>
#include <cerrno>
#include <climits>
#include <cstdint>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <type_traits>
#include <unistd.h>
#else
#include <fontconfig/fontconfig.h>
#endif
#include <cstdlib>
#include <memory>
#include <mutex>
#include <new>
#include <set>
#include <string>
#include <vector>
struct AFont { std::string path;int index;uint16_t weight;bool italic;std::string locale; };
struct ASystemFontIterator { std::shared_ptr<const std::vector<AFont>> fonts;size_t position=0; };
namespace {
#if defined(__APPLE__)
// macOS lists font files with CoreText, then reads each sfnt face's OS/2 and head tables,
// the fields fontconfig derives FC_WEIGHT and FC_SLANT from. CoreText descriptor lists
// expand variable-font named instances, so their positions are not collection indices;
// descriptors only describe files that are not sfnt or lack an OS/2 table.
template<class T> using Owned=std::unique_ptr<std::remove_pointer_t<T>,void(*)(CFTypeRef)>;
template<class T> Owned<T> own(T value){return Owned<T>(value,CFRelease);}
uint16_t be16(const uint8_t* p){return uint16_t(p[0]<<8|p[1]);}
uint32_t be32(const uint8_t* p){return uint32_t(p[0])<<24|uint32_t(p[1])<<16|uint32_t(p[2])<<8|p[3];}
bool readAt(int fd,uint64_t offset,void* data,size_t size){
    auto* out=static_cast<uint8_t*>(data);
    while(size){ssize_t n=pread(fd,out,size,off_t(offset));if(n<0&&errno==EINTR)continue;if(n<=0)return false;out+=n;size-=size_t(n);offset+=uint64_t(n);}
    return true;
}
std::vector<uint32_t> faceOffsets(int fd){
    uint8_t header[12];if(!readAt(fd,0,header,sizeof header))return {};
    uint32_t tag=be32(header);if(tag==0x00010000u||tag==0x74727565u||tag==0x4f54544fu)return {0};// 1.0, 'true', 'OTTO'
    if(tag!=0x74746366u)return {};// 'ttcf'
    uint32_t count=be32(header+8);if(count<1||count>1024)return {};
    std::vector<uint8_t> table(size_t(count)*4);if(!readAt(fd,12,table.data(),table.size()))return {};
    std::vector<uint32_t> offsets;for(uint32_t i=0;i<count;i++)offsets.push_back(be32(&table[size_t(i)*4]));return offsets;
}
// Returns false when the face has no readable OS/2 or head table.
bool faceStyle(int fd,uint32_t offset,int& weight,bool& italic,bool& weightKnown){
    uint8_t header[12];if(!readAt(fd,offset,header,sizeof header))return false;
    unsigned tables=be16(header+4);if(!tables||tables>512)return false;
    std::vector<uint8_t> directory(size_t(tables)*16);if(!readAt(fd,uint64_t(offset)+12,directory.data(),directory.size()))return false;
    bool os2=false,head=false;unsigned selection=0,style=0;int weightClass=0;
    for(unsigned i=0;i<tables;i++){
        const uint8_t* entry=&directory[size_t(i)*16];uint32_t tag=be32(entry),at=be32(entry+8),length=be32(entry+12);uint8_t data[64];
        if(tag==0x4f532f32u&&!os2&&length>=64&&readAt(fd,at,data,64)){os2=true;weightClass=be16(data+4);selection=be16(data+62);}// 'OS/2'
        else if(tag==0x68656164u&&!head&&length>=46&&readAt(fd,at,data,46)){head=true;style=be16(data+44);}// 'head'
    }
    if(!os2&&!head)return false;
    // OS/2 italic or oblique selection bits, otherwise the head macStyle italic bit.
    weightKnown=os2;weight=os2?weightClass:(style&1?700:400);italic=os2?(selection&0x201)!=0:(style&2)!=0;return true;
}
// Maps CoreText's -1..1 weight trait onto the OpenType scale through the NSFontWeight
// values CoreText reports for weight classes 100..900.
int traitWeight(double value){
    static const double map[][2]={{-1,1},{-0.8,100},{-0.6,200},{-0.4,300},{0,400},{0.23,500},{0.3,600},{0.4,700},{0.56,800},{0.62,900},{1,1000}};
    if(value!=value)return 400;if(value<=-1)return 1;
    for(size_t i=1;i<sizeof map/sizeof *map;i++)if(value<=map[i][0])return int(map[i-1][1]+(value-map[i-1][0])/(map[i][0]-map[i-1][0])*(map[i][1]-map[i-1][1])+0.5);
    return 1000;
}
bool descriptorStyle(CTFontDescriptorRef descriptor,int& weight,bool& italic){
    auto traits=own(static_cast<CFDictionaryRef>(CTFontDescriptorCopyAttribute(descriptor,kCTFontTraitsAttribute)));
    if(!traits||CFGetTypeID(traits.get())!=CFDictionaryGetTypeID())return false;
    double value=0;int32_t symbolic=0;
    auto number=static_cast<CFNumberRef>(CFDictionaryGetValue(traits.get(),kCTFontWeightTrait));if(number&&CFGetTypeID(number)==CFNumberGetTypeID())CFNumberGetValue(number,kCFNumberDoubleType,&value);
    number=static_cast<CFNumberRef>(CFDictionaryGetValue(traits.get(),kCTFontSymbolicTrait));if(number&&CFGetTypeID(number)==CFNumberGetTypeID())CFNumberGetValue(number,kCFNumberSInt32Type,&symbolic);
    weight=traitWeight(value);italic=(uint32_t(symbolic)&kCTFontItalicTrait)!=0;return true;
}
// Bounded recursive walk, sorted for a stable order; fontconfig also scans app font
// directories recursively.
void scanDirectory(const std::string& directory,int depth,std::vector<std::string>& files){
    if(depth>8||files.size()>=4096)return;
    DIR* handle=opendir(directory.c_str());if(!handle)return;
    std::vector<std::string> names;while(dirent* entry=readdir(handle)){if(std::strcmp(entry->d_name,".")&&std::strcmp(entry->d_name,"..")&&names.size()<65536)names.emplace_back(entry->d_name);}
    closedir(handle);std::sort(names.begin(),names.end());
    for(const auto& name:names){
        std::string path=directory+(directory.back()=='/'?"":"/")+name;struct stat info;if(path.size()>4096||stat(path.c_str(),&info))continue;
        if(S_ISDIR(info.st_mode))scanDirectory(path,depth+1,files);else if(S_ISREG(info.st_mode)&&files.size()<4096)files.push_back(std::move(path));
    }
}
std::shared_ptr<const std::vector<AFont>> catalog(){
    static std::mutex mutex;static std::shared_ptr<const std::vector<AFont>> saved;
    std::lock_guard<std::mutex> lock(mutex);if(saved)return saved;
    auto urls=own(CTFontManagerCopyAvailableFontURLs());if(!urls)return {};
    // Bundled Android fonts come first, then every CoreText font file once.
    std::vector<std::string> paths;const char* directory=std::getenv("SHIRAZ_FONT_DIR");if(!directory)directory=std::getenv("SHIRAZ_ANDROID_FONT_DIR");
    if(directory&&directory[0]=='/')scanDirectory(directory,0,paths);
    std::set<std::string> system;
    for(CFIndex i=0;i<CFArrayGetCount(urls.get());i++){
        auto url=static_cast<CFURLRef>(CFArrayGetValueAtIndex(urls.get(),i));char file[PATH_MAX];
        // The file system representation drops the #postscript-name face fragment.
        if(url&&CFGetTypeID(url)==CFURLGetTypeID()&&CFURLGetFileSystemRepresentation(url,true,reinterpret_cast<UInt8*>(file),sizeof file))system.emplace(file);
    }
    paths.insert(paths.end(),system.begin(),system.end());
    auto result=std::make_shared<std::vector<AFont>>();std::set<std::pair<std::string,int>> seen;size_t bytes=0;
    for(auto& path:paths){
        if(result->size()>=4096)break;
        if(path.empty()||path[0]!='/'||path.size()>4096)continue;
        int fd=open(path.c_str(),O_RDONLY|O_CLOEXEC);if(fd<0)continue;
        std::vector<uint32_t> offsets=faceOffsets(fd);
        Owned<CFArrayRef> descriptors(nullptr,CFRelease);bool described=false;
        auto describe=[&]{if(!described){described=true;auto url=own(CFURLCreateFromFileSystemRepresentation(nullptr,reinterpret_cast<const UInt8*>(path.data()),CFIndex(path.size()),false));if(url)descriptors=own(CTFontManagerCreateFontDescriptorsFromURL(url.get()));}return descriptors?size_t(CFArrayGetCount(descriptors.get())):size_t(0);};
        size_t faces=offsets.empty()?describe():offsets.size();
        for(size_t index=0;index<faces&&result->size()<4096;index++){
            int weight=400;bool italic=false,weightKnown=false;
            if(offsets.empty()){if(!descriptorStyle(static_cast<CTFontDescriptorRef>(CFArrayGetValueAtIndex(descriptors.get(),CFIndex(index))),weight,italic))continue;weightKnown=true;}
            else if(!faceStyle(fd,offsets[index],weight,italic,weightKnown))continue;
            // Without OS/2, CoreText traits apply only when its descriptors map one to one.
            if(!weightKnown&&describe()==faces){int traitValue=400;bool traitItalic=false;if(descriptorStyle(static_cast<CTFontDescriptorRef>(CFArrayGetValueAtIndex(descriptors.get(),CFIndex(index))),traitValue,traitItalic))weight=traitValue;}
            if(bytes+path.size()>16*1024*1024||!seen.emplace(path,int(index)).second)continue;
            int css=weight;if(css<1)css=400;if(css>1000)css=1000;
            bytes+=path.size();result->push_back({path,int(index),uint16_t(css),italic,""});
        }
        close(fd);
    }
    saved=result;return saved;
}
#else
std::shared_ptr<const std::vector<AFont>> catalog(){
    static std::mutex mutex;static std::shared_ptr<const std::vector<AFont>> saved;
    std::lock_guard<std::mutex> lock(mutex);if(saved)return saved;
    auto config=std::unique_ptr<FcConfig,decltype(&FcConfigDestroy)>(FcInitLoadConfigAndFonts(),FcConfigDestroy);if(!config)return {};
    const char* directory=std::getenv("SHIRAZ_FONT_DIR");if(!directory)directory=std::getenv("SHIRAZ_ANDROID_FONT_DIR");
    if(directory&&directory[0]=='/')FcConfigAppFontAddDir(config.get(),reinterpret_cast<const FcChar8*>(directory));
    auto pattern=std::unique_ptr<FcPattern,decltype(&FcPatternDestroy)>(FcPatternCreate(),FcPatternDestroy);
    auto objects=std::unique_ptr<FcObjectSet,decltype(&FcObjectSetDestroy)>(FcObjectSetBuild(FC_FILE,FC_INDEX,FC_WEIGHT,FC_SLANT,nullptr),FcObjectSetDestroy);
    if(!pattern||!objects)return {};
    auto set=std::unique_ptr<FcFontSet,decltype(&FcFontSetDestroy)>(FcFontList(config.get(),pattern.get(),objects.get()),FcFontSetDestroy);if(!set)return {};
    auto result=std::make_shared<std::vector<AFont>>();std::set<std::pair<std::string,int>> seen;size_t bytes=0;
    for(int i=0;i<set->nfont&&result->size()<4096;i++){
        FcChar8* file=nullptr;int index=0,weight=FC_WEIGHT_REGULAR,slant=FC_SLANT_ROMAN;
        if(FcPatternGetString(set->fonts[i],FC_FILE,0,&file)!=FcResultMatch||!file||file[0]!='/')continue;
        std::string path(reinterpret_cast<const char*>(file));if(path.size()>4096||bytes+path.size()>16*1024*1024)continue;
        FcPatternGetInteger(set->fonts[i],FC_INDEX,0,&index);FcPatternGetInteger(set->fonts[i],FC_WEIGHT,0,&weight);FcPatternGetInteger(set->fonts[i],FC_SLANT,0,&slant);
        // Fontconfig encodes named variable-font instances above the TTC index.
        // The public collection index refers to the face only.
        index&=0xffff;if(!seen.emplace(path,index).second)continue;
        int css=FcWeightToOpenType(weight);if(css<1)css=400;if(css>1000)css=1000;
        bytes+=path.size();result->push_back({std::move(path),index,uint16_t(css),slant!=FC_SLANT_ROMAN,""});
    }
    saved=result;return saved;
}
#endif
}
extern "C" ASystemFontIterator* ASystemFontIterator_open() try {auto fonts=catalog();return fonts?new ASystemFontIterator{fonts}:nullptr;}catch(const std::bad_alloc&){return nullptr;}
extern "C" void ASystemFontIterator_close(ASystemFontIterator* iterator){delete iterator;}
extern "C" AFont* ASystemFontIterator_next(ASystemFontIterator* iterator) try {
    if(!iterator||iterator->position>=iterator->fonts->size())return nullptr;
    AFont* font=new AFont((*iterator->fonts)[iterator->position]);++iterator->position;return font;
}catch(const std::bad_alloc&){return nullptr;}
extern "C" void AFont_close(AFont* font){delete font;}
extern "C" const char* AFont_getFontFilePath(const AFont* font){return font?font->path.c_str():nullptr;}
extern "C" size_t AFont_getCollectionIndex(const AFont* font){return font?size_t(font->index):0;}
extern "C" uint16_t AFont_getWeight(const AFont* font){return font?font->weight:0;}
extern "C" bool AFont_isItalic(const AFont* font){return font&&font->italic;}
extern "C" const char* AFont_getLocale(const AFont* font){return font?font->locale.c_str():nullptr;}
extern "C" size_t AFont_getAxisCount(const AFont*){return 0;}
