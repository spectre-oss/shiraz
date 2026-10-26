// SPDX-License-Identifier: GPL-3.0-only
#include <fontconfig/fontconfig.h>
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
