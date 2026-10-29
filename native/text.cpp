// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/compositor.hpp"
#include <jni.h>
#include <ft2build.h>
#include FT_FREETYPE_H
#include FT_OUTLINE_H
#include FT_BITMAP_H
#include FT_GLYPH_H
#include FT_STROKER_H
#include FT_MULTIPLE_MASTERS_H
#include <vector>
#include <algorithm>
#include <hb-ft.h>
#include <fontconfig/fontconfig.h>
#include <cmath>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <list>
#include <map>
#include <cstdlib>
#include <unicode/ubidi.h>
#include <unicode/uchar.h>
#include <unicode/utf16.h>

#ifndef SHIRAZ_FONT_DIR
#error Private font directory must be configured
#endif

namespace {
void error(JNIEnv* env, const char* type, const char* message) {
    if (env->ExceptionCheck()) return;
    jclass cls = env->FindClass(type);
    if (cls) { env->ThrowNew(cls, message); env->DeleteLocalRef(cls); }
}
std::mutex shapingMutex;
std::mutex memoryFontMutex;
std::vector<std::shared_ptr<std::vector<FT_Byte>>> memoryFonts;
std::shared_ptr<std::vector<FT_Byte>> memoryFontBytes(int id) {
    std::lock_guard<std::mutex> lock(memoryFontMutex);
    if (id < 1 || id > static_cast<int>(memoryFonts.size()) || !memoryFonts[id - 1]) throw std::invalid_argument("Unknown memory font");
    return memoryFonts[id - 1];
}
struct Font {
    std::string filename;int requestedStyle;
    std::shared_ptr<std::vector<FT_Byte>> owned;
    FT_Library library = nullptr;
    FT_Face face = nullptr;
    hb_font_t* shapingFont = nullptr;
    FT_F26Dot6 currentSize = -1;
    double bitmapScale = 1.0;
    void invalidateShapingFont() { if(shapingFont)hb_font_destroy(shapingFont);shapingFont=nullptr; }
    hb_font_t* shaping() {
        if(!shapingFont){
            std::vector<hb_variation_t> vars;
            if(axes)for(unsigned i=0;i<axes->num_axis;i++)
                vars.push_back({static_cast<hb_tag_t>(axes->axis[i].tag),float(current[i])/65536});
            shapingFont=hb_ft_font_create_referenced(face);
            // Coordinate changes invalidate this font; preserve its shaping caches
            // across runs that use the same size and variation coordinates.
            if(axes)hb_font_set_variations(shapingFont,vars.data(),vars.size());
        }
        return shapingFont;
    }
    std::mutex mutex;
    FT_MM_Var* axes=nullptr;bool axesChecked=false;std::vector<FT_Fixed> defaults,current;
    Font(const std::string& directPath, bool) : filename(directPath), requestedStyle(0) {
        if (FT_Init_FreeType(&library)) throw std::runtime_error("Cannot initialize FreeType");
        if (FT_New_Face(library, filename.c_str(), 0, &face)) {
            FT_Done_FreeType(library); library = nullptr; throw std::runtime_error("Cannot open direct font");
        }
    }
    Font(const std::string& family, int style, const std::vector<UChar32>& coverage = {}) : requestedStyle(style) {
        if (!family.empty() && family[0] == '\x01') {
            int id = 0;
            for (size_t i = 1; i < family.size(); ++i) {
                if (family[i] < '0' || family[i] > '9') throw std::invalid_argument("Invalid memory font");
                id = id * 10 + family[i] - '0';
            }
            auto bytes = memoryFontBytes(id);
            if (bytes->size() > 16u * 1024u * 1024u) throw std::invalid_argument("Memory font exceeds 16MiB");
            owned = bytes;
            if (FT_Init_FreeType(&library)) throw std::runtime_error("Cannot initialize FreeType");
            if (FT_New_Memory_Face(library, owned->data(), static_cast<FT_Long>(owned->size()), 0, &face)) {
                FT_Done_FreeType(library); library = nullptr; throw std::runtime_error("Cannot open memory font");
            }
            return;
        }
        std::unique_ptr<FcConfig, decltype(&FcConfigDestroy)> config(FcInitLoadConfigAndFonts(), FcConfigDestroy);
        std::unique_ptr<FcPattern, decltype(&FcPatternDestroy)> pattern(FcPatternCreate(), FcPatternDestroy);
        if (!config || !pattern) throw std::bad_alloc();
        const char* fontDirectory = std::getenv("SHIRAZ_FONT_DIR");
        if (!fontDirectory || !*fontDirectory) fontDirectory = SHIRAZ_FONT_DIR;
        if (!FcConfigAppFontAddDir(config.get(), reinterpret_cast<const FcChar8*>(fontDirectory))) throw std::runtime_error("Cannot load private fallback fonts");
        if (!coverage.empty()) {
            std::unique_ptr<FcCharSet, decltype(&FcCharSetDestroy)> chars(FcCharSetCreate(), FcCharSetDestroy);
            if (!chars) throw std::bad_alloc();
            for (auto cp : coverage) FcCharSetAddChar(chars.get(), cp);
            if (!FcPatternAddCharSet(pattern.get(), FC_CHARSET, chars.get())) throw std::bad_alloc();
        }
        if (!FcPatternAddString(pattern.get(), FC_FAMILY, reinterpret_cast<const FcChar8*>(family.c_str()))
                || !FcPatternAddInteger(pattern.get(), FC_WEIGHT, style & 1 ? FC_WEIGHT_BOLD : FC_WEIGHT_REGULAR)
                || !FcPatternAddInteger(pattern.get(), FC_SLANT, style & 2 ? FC_SLANT_ITALIC : FC_SLANT_ROMAN)) throw std::bad_alloc();
        if (!FcConfigSubstitute(config.get(), pattern.get(), FcMatchPattern)) throw std::runtime_error("Cannot configure font request");
        FcDefaultSubstitute(pattern.get());
        FcResult result;
        std::unique_ptr<FcPattern, decltype(&FcPatternDestroy)> match(FcFontMatch(config.get(), pattern.get(), &result), FcPatternDestroy);
        FcChar8* path = nullptr; int index = 0;
        if (!match || FcPatternGetString(match.get(), FC_FILE, 0, &path) != FcResultMatch) throw std::runtime_error("No matching font available");
        FcPatternGetInteger(match.get(), FC_INDEX, 0, &index);
        filename = reinterpret_cast<const char*>(path);
        if (FT_Init_FreeType(&library)) throw std::runtime_error("Cannot initialize FreeType");
        if (FT_New_Face(library, filename.c_str(), index, &face)) {
            FT_Done_FreeType(library); library = nullptr; throw std::runtime_error("Cannot open matched font");
        }
    }
    ~Font() { invalidateShapingFont(); if(axes)FT_Done_MM_Var(library,axes); if (face) FT_Done_Face(face); if (library) FT_Done_FreeType(library); }
    FT_MM_Var* metadata(){
        if(axesChecked)return axes;
        if(!FT_HAS_MULTIPLE_MASTERS(face)){axesChecked=true;return nullptr;}
        FT_MM_Var* raw=nullptr;if(FT_Get_MM_Var(face,&raw))throw std::runtime_error("Cannot read variable font axes");
        auto cleanup=[this](FT_MM_Var* p){FT_Done_MM_Var(library,p);};std::unique_ptr<FT_MM_Var,decltype(cleanup)> holder(raw,cleanup);
        if(raw->num_axis>64)throw std::invalid_argument("Font axis count");
        std::vector<FT_Fixed> baseline(raw->num_axis);
        if(FT_Get_Var_Design_Coordinates(face,raw->num_axis,baseline.data()))throw std::runtime_error("Cannot read font coordinates");
        defaults=baseline;current=baseline;axes=holder.release();axesChecked=true;return axes;
    }
    void variations(JNIEnv* env,jintArray tags,jfloatArray values) {
        if((tags==nullptr)!=(values==nullptr))throw std::invalid_argument("Variation arrays");
        const int count=tags?env->GetArrayLength(tags):0;
        if(count>64||(values&&env->GetArrayLength(values)!=count))throw std::invalid_argument("Variation axis count");
        jint requested[64];jfloat coords[64];if(count){env->GetIntArrayRegion(tags,0,count,requested);env->GetFloatArrayRegion(values,0,count,coords);if(env->ExceptionCheck())return;for(int k=0;k<count;k++)if(!std::isfinite(coords[k]))throw std::invalid_argument("Variation coordinate");}
        FT_MM_Var* raw=metadata();if(!raw){if(count)throw std::invalid_argument("Font has no variable axes");return;}
        std::vector<FT_Fixed> selected=defaults;
        for(unsigned i=0;i<raw->num_axis;i++)for(int k=0;k<count;k++)if(static_cast<FT_ULong>(static_cast<uint32_t>(requested[k]))==raw->axis[i].tag){double value=std::clamp(double(coords[k])*65536.0,double(raw->axis[i].minimum),double(raw->axis[i].maximum));selected[i]=static_cast<FT_Fixed>(std::llround(value));}
        if(selected!=current){invalidateShapingFont();currentSize=-1;if(FT_Set_Var_Design_Coordinates(face,raw->num_axis,selected.data()))throw std::runtime_error("Cannot apply font variations");current=std::move(selected);}
    }
    bool size(float pixels) {
        if (!std::isfinite(pixels) || pixels < 0 || pixels > 16384) throw std::invalid_argument("Text size must be in [0,16384]");
        if (pixels == 0) return false; // FreeType interprets zero as a default size; Android requires zero coverage.
        const auto requested=FT_F26Dot6(std::lround(pixels * 64));
        if(requested!=currentSize){
            invalidateShapingFont();currentSize=-1;
            if (!FT_IS_SCALABLE(face) && face->num_fixed_sizes > 0) {
                if (FT_Select_Size(face, 0)) throw std::runtime_error("Cannot select fixed font size");
                bitmapScale = face->available_sizes[0].y_ppem > 0 ? double(requested) / double(face->available_sizes[0].y_ppem) : 1.0;
            } else {
                if (FT_Set_Char_Size(face, 0, requested, 72, 72)) throw std::runtime_error("Cannot set font size");
                bitmapScale = 1.0;
            }
            currentSize=requested;
        }
        return true;
    }
};
std::shared_ptr<Font> font(JNIEnv* env, jstring family, jint style) {
    if (!family || style < 0 || style > 3) throw std::invalid_argument("Invalid font family or style");
    auto units = env->GetStringLength(family);
    if (units < 1 || units > 256) throw std::invalid_argument("Invalid font family length");
    jchar utf16[256]; env->GetStringRegion(family, 0, units, utf16);
    if (env->ExceptionCheck()) return nullptr;
    std::string name;
    for (int i = 0; i < units; ++i) {
        uint32_t point = utf16[i];
        if (point == 0) throw std::invalid_argument("NUL in font family");
        if (point >= 0xd800 && point <= 0xdbff) {
            if (++i == units || utf16[i] < 0xdc00 || utf16[i] > 0xdfff) throw std::invalid_argument("Unpaired font-family surrogate");
            point = 0x10000 + ((point - 0xd800) << 10) + utf16[i] - 0xdc00;
        } else if (point >= 0xdc00 && point <= 0xdfff) throw std::invalid_argument("Unpaired font-family surrogate");
        if (point < 0x80) name.push_back(static_cast<char>(point));
        else if (point < 0x800) { name.push_back(static_cast<char>(0xc0 | (point >> 6))); name.push_back(static_cast<char>(0x80 | (point & 63))); }
        else {
            if (point >= 0x10000) { name.push_back(static_cast<char>(0xf0 | (point >> 18))); name.push_back(static_cast<char>(0x80 | ((point >> 12) & 63))); }
            else name.push_back(static_cast<char>(0xe0 | (point >> 12)));
            name.push_back(static_cast<char>(0x80 | ((point >> 6) & 63))); name.push_back(static_cast<char>(0x80 | (point & 63)));
        }
    }
    struct Entry { std::string family; int style; std::shared_ptr<Font> font; };
    static std::mutex mutex;
    static std::list<Entry> cache;
    std::lock_guard<std::mutex> lock(mutex);
    for (auto it = cache.begin(); it != cache.end(); ++it) if (it->family == name && it->style == style) {
        auto value = it->font; cache.splice(cache.begin(), cache, it); return value;
    }
    auto value = std::make_shared<Font>(name, style);
    cache.push_front({std::move(name), style, value}); if (cache.size() > 16) cache.pop_back();
    return value;
}
struct Text {
    JNIEnv* env;
    jstring string;
    const jchar* chars = nullptr;
    int length;
    Text(JNIEnv* env, jstring string) : env(env), string(string), length(env->GetStringLength(string)) {
        if (length > 32768) throw std::invalid_argument("Text exceeds 32768 UTF-16 units");
        chars = env->GetStringChars(string, nullptr);
    }
    ~Text() { if (chars) env->ReleaseStringChars(string, chars); }
};
// All face size/variation and glyph operations are protected by shapingMutex.
// A fallback is selected for a complete script run, preserving joining context.
std::shared_ptr<Font> colorEmojiFont() {
    static std::shared_ptr<Font> cached;
    static bool checked = false;
    if (checked) return cached;
    checked = true;
    const char* fontDirectory = std::getenv("SHIRAZ_FONT_DIR");
    if (!fontDirectory || !*fontDirectory) fontDirectory = SHIRAZ_FONT_DIR;
    std::string path = std::string(fontDirectory) + "/NotoColorEmoji.ttf";
    try {
        auto candidate = std::make_shared<Font>(path, true);
        if (candidate->face && FT_HAS_COLOR(candidate->face)) {
            cached = candidate;
            return cached;
        }
    } catch (...) {}
    return nullptr;
}
std::shared_ptr<Font> fallback(Font& base, const Text& text, int start, int end, int style) {
    std::vector<UChar32> points;
    bool wantsEmoji = false;
    for (int at=start; at<end;) {
        UChar32 cp; U16_NEXT(text.chars,at,end,cp);
        if (cp<0 || (cp>=0xd800 && cp<=0xdfff)) throw std::invalid_argument("Unpaired text surrogate");
        if (cp >= 0x2000 && (u_hasBinaryProperty(cp, UCHAR_EMOJI) || u_hasBinaryProperty(cp, UCHAR_EMOJI_PRESENTATION))) wantsEmoji = true;
        if (!u_hasBinaryProperty(cp,UCHAR_DEFAULT_IGNORABLE_CODE_POINT) && !u_iscntrl(cp)) points.push_back(cp);
    }
    auto covers=[&](Font& candidate){for(auto cp:points)if(!FT_Get_Char_Index(candidate.face,cp))return false;return true;};
    if (wantsEmoji) {
        if (auto emoji = colorEmojiFont(); emoji && covers(*emoji)) return emoji;
    }
    if(covers(base)) return nullptr;
    struct Entry {int style;std::shared_ptr<Font> font;std::vector<UChar32> coverage;};
    static std::list<Entry> cache;
    for(auto it=cache.begin();it!=cache.end();++it)if(it->style==style&&(it->coverage==points||covers(*it->font))){
        auto value=it->font;cache.splice(cache.begin(),cache,it);return value;
    }
    auto value=std::make_shared<Font>("sans-serif",style,points);
    // Fontconfig returns its best available face even when coverage is incomplete.
    // HarfBuzz/FreeType glyph zero is the font's real .notdef glyph: preserve its
    // advance, bounds and outline instead of turning absent font coverage into
    // an application exception. Invalid UTF-16 still fails above.
    cache.push_front({style,value,points});if(cache.size()>32)cache.pop_back();return value;
}
struct Run {
    std::vector<hb_glyph_info_t> glyphs;
    std::vector<hb_glyph_position_t> positions;
    std::vector<Font*> faces;
    std::vector<unsigned char> levels;
    std::vector<std::shared_ptr<Font>> retained;
    Run(Font& owner,const Text& text,float size,float spacing,jbyteArray resolved=nullptr,jstring featureSettings=nullptr) {
        if(!std::isfinite(spacing)||std::abs(spacing)>16)throw std::invalid_argument("Letter spacing bound");
        if(resolved&&text.env->GetArrayLength(resolved)!=text.length)throw std::invalid_argument("Resolved bidi level count");
        std::vector<hb_feature_t> features;
        if(std::abs(spacing)>.03f){
            features.push_back({HB_TAG('l','i','g','a'),0,0,~0u});
            features.push_back({HB_TAG('c','l','i','g'),0,0,~0u});
        }
        if(featureSettings){
            if(text.env->GetStringLength(featureSettings)>4096)throw std::invalid_argument("Font feature settings bound");
            Text settings(text.env,featureSettings);
            if(!settings.chars)throw std::runtime_error("Cannot read font features");
            std::string ascii;
            for(int i=0;i<settings.length;i++){
                if(settings.chars[i]>127)throw std::invalid_argument("Font feature syntax");
                ascii.push_back(static_cast<char>(settings.chars[i]));
            }
            size_t start=0;unsigned count=0;
            while(start<ascii.size()){
                size_t end=ascii.find(',',start);if(end==std::string::npos)end=ascii.size();
                hb_feature_t feature;
                if(++count>64||!hb_feature_from_string(ascii.data()+start,end-start,&feature))
                    throw std::invalid_argument("Font feature syntax or count");
                features.push_back(feature);start=end+1;
            }
        }
        if(!text.length)return;
        UErrorCode status=U_ZERO_ERROR;
        std::unique_ptr<UBiDi,decltype(&ubidi_close)> bidi(ubidi_openSized(text.length,0,&status),ubidi_close);
        if(U_FAILURE(status)||!bidi)throw std::bad_alloc();
        ubidi_setPara(bidi.get(),reinterpret_cast<const UChar*>(text.chars),text.length,UBIDI_DEFAULT_LTR,nullptr,&status);
        int runs=ubidi_countRuns(bidi.get(),&status);
        if(U_FAILURE(status))throw std::runtime_error("Cannot resolve bidi text");
        int style=owner.requestedStyle;
        struct DirectionRun {int start,length;UBiDiLevel level;};std::vector<DirectionRun> directions;
        if(resolved){
            if(text.env->GetArrayLength(resolved)!=text.length)throw std::invalid_argument("Resolved bidi level count");
            std::vector<jbyte> data(text.length);text.env->GetByteArrayRegion(resolved,0,text.length,data.data());
            if(text.env->ExceptionCheck())throw std::runtime_error("Cannot read bidi levels");
            std::vector<UBiDiLevel> runLevels;
            for(int at=0;at<text.length;){int end=at+1;if(data[at]<0||data[at]>126)throw std::invalid_argument("Resolved bidi level");while(end<text.length&&data[end]==data[at])end++;if(end<text.length&&U16_IS_LEAD(text.chars[end-1])&&U16_IS_TRAIL(text.chars[end]))throw std::invalid_argument("Bidi run splits surrogate pair");directions.push_back({at,end-at,static_cast<UBiDiLevel>(data[at])});runLevels.push_back(data[at]);at=end;}
            std::vector<int32_t> order(directions.size());ubidi_reorderVisual(runLevels.data(),runLevels.size(),order.data());
            auto logical=directions;for(size_t i=0;i<order.size();i++)directions[i]=logical[order[i]];
        }else for(int visual=0;visual<runs;visual++){int start,length;ubidi_getVisualRun(bidi.get(),visual,&start,&length);directions.push_back({start,length,ubidi_getLevelAt(bidi.get(),start)});}
        for(const auto& direction:directions){
            int start=direction.start,length=direction.length;bool backwards=direction.level&1;
            struct Segment {int start,end;hb_script_t script;};std::vector<Segment> segments;
            hb_script_t previous=HB_SCRIPT_INVALID;
            for(int at=start;at<start+length;){
                int begin=at;UChar32 cp;U16_NEXT(text.chars,at,start+length,cp);
                auto script=hb_unicode_script(hb_unicode_funcs_get_default(),cp);
                bool neutral=script==HB_SCRIPT_INHERITED||u_hasBinaryProperty(cp,UCHAR_JOIN_CONTROL);
                if(neutral)script=previous;
                if((u_charType(cp)==U_OTHER_SYMBOL && cp>=0x2000) || (cp>=0x2000 && (u_hasBinaryProperty(cp,UCHAR_EMOJI) || u_hasBinaryProperty(cp,UCHAR_EMOJI_PRESENTATION))))script=HB_SCRIPT_UNKNOWN;
                if(segments.empty())segments.push_back({begin,at,script});
                else if(script==previous||script==HB_SCRIPT_INVALID)segments.back().end=at;
                else if(previous==HB_SCRIPT_INVALID){segments.back().end=at;segments.back().script=script;}
                else segments.push_back({begin,at,script});
                previous=script;
            }
            if(backwards)std::reverse(segments.begin(),segments.end());
            for(const auto& segment:segments){
                auto extra=fallback(owner,text,segment.start,segment.end,style);
                Font& selected=extra?*extra:owner;
                if(extra){selected.variations(text.env,nullptr,nullptr);selected.size(size);retained.push_back(extra);}
                hb_font_t* shapedFont=selected.shaping();
                std::unique_ptr<hb_buffer_t,decltype(&hb_buffer_destroy)> buffer(hb_buffer_create(),hb_buffer_destroy);
                hb_buffer_set_cluster_level(buffer.get(),HB_BUFFER_CLUSTER_LEVEL_MONOTONE_GRAPHEMES);
                hb_buffer_add_utf16(buffer.get(),reinterpret_cast<const uint16_t*>(text.chars),text.length,segment.start,segment.end-segment.start);
                hb_buffer_set_direction(buffer.get(),backwards?HB_DIRECTION_RTL:HB_DIRECTION_LTR);
                hb_buffer_set_script(buffer.get(),segment.script==HB_SCRIPT_INVALID?HB_SCRIPT_COMMON:segment.script);
                hb_buffer_guess_segment_properties(buffer.get());
                hb_shape(shapedFont,buffer.get(),features.data(),features.size());
                unsigned count=0;auto* info=hb_buffer_get_glyph_infos(buffer.get(),&count);auto* pos=hb_buffer_get_glyph_positions(buffer.get(),nullptr);
                if(!hb_buffer_allocation_successful(buffer.get())||glyphs.size()+count>65536)throw std::bad_alloc();
                bool allowed=true;switch(segment.script){case HB_SCRIPT_ARABIC:case HB_SCRIPT_NKO:case HB_SCRIPT_PSALTER_PAHLAVI:case HB_SCRIPT_MANDAIC:case HB_SCRIPT_MONGOLIAN:case HB_SCRIPT_PHAGS_PA:case HB_SCRIPT_DEVANAGARI:case HB_SCRIPT_BENGALI:case HB_SCRIPT_GURMUKHI:case HB_SCRIPT_MODI:case HB_SCRIPT_SHARADA:case HB_SCRIPT_SYLOTI_NAGRI:case HB_SCRIPT_TIRHUTA:case HB_SCRIPT_OGHAM:allowed=false;break;default:break;}
                const auto advance=static_cast<hb_position_t>(std::llround(double(spacing)*size*64));
                for(unsigned i=0;i<count;i++){
                    hb_glyph_position_t p = pos[i];
                    if(selected.bitmapScale != 1.0){
                        p.x_advance = static_cast<hb_position_t>(std::llround(p.x_advance * selected.bitmapScale));
                        p.y_advance = static_cast<hb_position_t>(std::llround(p.y_advance * selected.bitmapScale));
                        p.x_offset = static_cast<hb_position_t>(std::llround(p.x_offset * selected.bitmapScale));
                        p.y_offset = static_cast<hb_position_t>(std::llround(p.y_offset * selected.bitmapScale));
                    }
                    if(spacing!=0&&allowed){p.x_offset+=static_cast<hb_position_t>(std::floor(advance/2.0));if(i+1==count||info[i].cluster!=info[i+1].cluster)p.x_advance+=advance;}
                    glyphs.push_back(info[i]);positions.push_back(p);faces.push_back(&selected);levels.push_back(direction.level);
                }
            }
        }
    }
};
}
extern "C" JNIEXPORT jint JNICALL Java_android_graphics_FontFamily_nativeRegister(JNIEnv* env, jclass, jbyteArray bytes) {
    try {
        if (!bytes) throw std::invalid_argument("Missing font bytes");
        const jsize count = env->GetArrayLength(bytes);
        if (count < 1 || count > 16 * 1024 * 1024) throw std::invalid_argument("Font byte count");
        auto owned = std::make_shared<std::vector<FT_Byte>>(static_cast<size_t>(count));
        env->GetByteArrayRegion(bytes, 0, count, reinterpret_cast<jbyte*>(owned->data()));
        if (env->ExceptionCheck()) return 0;
        std::lock_guard<std::mutex> lock(memoryFontMutex);
        // Reloading the same resource must not consume another permanent font slot.
        for (size_t i = 0; i < memoryFonts.size(); ++i) {
            if (*memoryFonts[i] == *owned) return static_cast<jint>(i + 1);
        }
        if (memoryFonts.size() >= 64) throw std::invalid_argument("Memory font limit");
        memoryFonts.push_back(std::move(owned));
        return static_cast<jint>(memoryFonts.size());
    } catch (const std::exception& issue) { error(env, "java/lang/IllegalArgumentException", issue.what()); }
    return 0;
}
extern "C" JNIEXPORT jfloat JNICALL Java_shiraz_runtime_NativeText_measureResolved(JNIEnv* env, jclass, jstring text, jfloat size, jstring family, jint style, jintArray tags,jfloatArray values,jfloat spacing,jstring featureSettings,jbyteArray levels) {
    if (!text) { error(env, "java/lang/NullPointerException", "text"); return 0; }
    try {
        Text input(env, text); if (!input.chars) return 0;
        auto selected = font(env, family, style); if (!selected) return 0;
        auto& face = *selected; std::lock_guard<std::mutex> lock(shapingMutex); face.variations(env,tags,values); if(env->ExceptionCheck())return 0; if (!face.size(size)) return 0;
        Run run(face,input,size,spacing,levels,featureSettings);
        unsigned count = run.glyphs.size(); auto positions = run.positions.data();
        int64_t advance = 0; for (unsigned i = 0; i < count; ++i) advance += positions[i].x_advance;
        return float(advance) / 64;
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot shape text"); }
      catch (const std::invalid_argument& ex) { error(env, "java/lang/IllegalArgumentException", ex.what()); }
      catch (const std::exception& ex) { error(env, "java/lang/IllegalStateException", ex.what()); }
    return 0;
}
extern "C" JNIEXPORT jint JNICALL Java_shiraz_runtime_NativeText_breakResolved(JNIEnv* env,jclass,jstring text,jfloat size,jstring family,jint style,jintArray tags,jfloatArray values,jfloat spacing,jstring featureSettings,jboolean forwards,jfloat maxWidth,jfloatArray measured,jbyteArray levels) {
    if(!text){error(env,"java/lang/NullPointerException","text");return 0;}
    try {
        if(std::isnan(maxWidth)||(measured&&env->GetArrayLength(measured)<1))throw std::invalid_argument("Text break output or width");
        Text input(env,text);if(!input.chars)return 0;
        auto selected=font(env,family,style);if(!selected)return 0;
        auto& face=*selected;std::lock_guard<std::mutex> lock(shapingMutex);face.variations(env,tags,values);if(env->ExceptionCheck())return 0;
        float width=0;int accepted=0;
        if(maxWidth>=0){
            if(!face.size(size))accepted=input.length;
            else {
                Run run(face,input,size,spacing,levels,featureSettings);unsigned count=run.glyphs.size();
                auto* info=run.glyphs.data();auto* positions=run.positions.data();
                std::vector<int64_t> advances(input.length+1,0);std::vector<bool> boundary(input.length+1,false);boundary[0]=boundary[input.length]=true;
                for(unsigned i=0;i<count;i++){unsigned cluster=info[i].cluster;if(cluster>=unsigned(input.length))throw std::runtime_error("Invalid shaping cluster");boundary[cluster]=true;advances[cluster]+=positions[i].x_advance;}
                std::vector<int> starts;for(int i=0;i<=input.length;i++)if(boundary[i])starts.push_back(i);
                int64_t total=0;
                for(size_t n=0;n+1<starts.size();n++){
                    size_t part=forwards?n:starts.size()-2-n;int begin=starts[part],end=starts[part+1];int64_t next=total+advances[begin];
                    if(double(next)/64>maxWidth)break;
                    total=next;accepted+=end-begin;
                }
                width=float(total)/64;
            }
        }
        if(measured)env->SetFloatArrayRegion(measured,0,1,&width);
        return accepted;
    } catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Cannot shape text");}
      catch(const std::invalid_argument& ex){error(env,"java/lang/IllegalArgumentException",ex.what());}
      catch(const std::exception& ex){error(env,"java/lang/IllegalStateException",ex.what());}
    return 0;
}
extern "C" JNIEXPORT jfloatArray JNICALL Java_shiraz_runtime_NativeText_metricsVariable(JNIEnv* env, jclass, jfloat size, jstring family, jint style, jintArray tags,jfloatArray values) {
    try {
        auto selected = font(env, family, style); if (!selected) return nullptr;
        auto& face = *selected; std::lock_guard<std::mutex> lock(shapingMutex); face.variations(env,tags,values); if(env->ExceptionCheck())return nullptr; if (!face.size(size)) return env->NewFloatArray(5);
        auto metrics = face.face->size->metrics;
        float scale = static_cast<float>(face.bitmapScale);
        float top = !FT_IS_SCALABLE(face.face) ? -float(metrics.ascender) * scale / 64 : -float(FT_MulFix(face.face->bbox.yMax, metrics.y_scale)) / 64;
        float bottom = !FT_IS_SCALABLE(face.face) ? -float(metrics.descender) * scale / 64 : -float(FT_MulFix(face.face->bbox.yMin, metrics.y_scale)) / 64;
        float metricsValues[] = {top, -float(metrics.ascender) * scale / 64, -float(metrics.descender) * scale / 64, bottom,
                         std::max(0.f, float(metrics.height - metrics.ascender + metrics.descender) * scale / 64)};
        auto result = env->NewFloatArray(5); if (result) env->SetFloatArrayRegion(result, 0, 5, metricsValues); return result;
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot read font metrics"); }
      catch (const std::invalid_argument& ex) { error(env, "java/lang/IllegalArgumentException", ex.what()); }
      catch (const std::exception& ex) { error(env, "java/lang/IllegalStateException", ex.what()); }
    return nullptr;
}
static void loadTextGlyph(Font& face,unsigned glyphId,float skew,float scaleX,int paintStyle,int hinting,bool fakeBold) {
            if (FT_HAS_COLOR(face.face)) {
                if (FT_Load_Glyph(face.face, glyphId, FT_LOAD_COLOR)) throw std::runtime_error("Cannot load color glyph");
                return;
            }
            if (FT_Load_Glyph(face.face, glyphId, (skew == 0 && scaleX==1 && paintStyle==0 ? FT_LOAD_DEFAULT : FT_LOAD_NO_BITMAP) | (hinting==0 ? FT_LOAD_NO_HINTING | FT_LOAD_NO_BITMAP : 0))) throw std::runtime_error("Cannot load text glyph");
            // Embolden the selected glyph, preserving HarfBuzz layout advances and the chosen typeface.
            if (fakeBold && !(face.face->style_flags & FT_STYLE_FLAG_BOLD)) {
                auto glyph=face.face->glyph;
                if (glyph->format == FT_GLYPH_FORMAT_OUTLINE) {
                    FT_Pos amount=FT_MulFix(face.face->units_per_EM,face.face->size->metrics.y_scale)/34;
                    if (FT_Outline_Embolden(&glyph->outline,amount)) throw std::runtime_error("Cannot embolden glyph outline");
                } else if (glyph->format == FT_GLYPH_FORMAT_BITMAP) {
                    if (FT_GlyphSlot_Own_Bitmap(glyph) || FT_Bitmap_Embolden(glyph->library,&glyph->bitmap,64,0)) throw std::runtime_error("Cannot embolden glyph bitmap");
                } else throw std::runtime_error("Unsupported glyph format for synthetic bold");
            }
            if (skew != 0 || scaleX!=1) {
                if (face.face->glyph->format != FT_GLYPH_FORMAT_OUTLINE) throw std::runtime_error("Skew requires outline glyphs");
                // FreeType's Y axis points upward; Android skew uses downward-positive Y.
                FT_Matrix transform{static_cast<FT_Fixed>(std::lround(scaleX*65536)),static_cast<FT_Fixed>(std::lround(-skew*65536)),0,65536};
                FT_Outline_Transform(&face.face->glyph->outline,&transform);
            }
}
static void drawText(JNIEnv* env, jclass, jintArray pixels, jint width, jint height, jstring text, jfloat x, jfloat y, jfloat size, jint color, jboolean aa, jstring family, jint style, jint cl, jint ct, jint cr, jint cb, jboolean fakeBold, jfloat skew,jintArray tags,jfloatArray values,jfloat spacing,jstring featureSettings,jint paintStyle,jfloat strokeWidth,jint cap,jint join,jfloat miter,jint hinting,jfloat scaleX,jbyteArray levels,jobject pathMeasure,jfloatArray canvasMatrix,bool subpixel=false) {
    if(!std::isfinite(scaleX)||scaleX<0||scaleX>16){error(env,"java/lang/IllegalArgumentException","Invalid text scale");return;}
    if(hinting!=0&&hinting!=1){error(env,"java/lang/IllegalArgumentException","Invalid text hinting");return;}
    if (!text || !pixels) { error(env, "java/lang/NullPointerException", "Text or bitmap pixels"); return; }
    if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || int64_t(width) * height > 32 * 1024 * 1024 ||
        env->GetArrayLength(pixels) != int64_t(width) * height || cl < 0 || ct < 0 || cr < cl || cb < ct || cr > width || cb > height || !std::isfinite(x) || !std::isfinite(y) || std::abs(x) > 10000000 || std::abs(y) > 10000000) {
        error(env, "java/lang/IllegalArgumentException", "Invalid text drawing bounds"); return;
    }
    if (!std::isfinite(skew) || std::abs(skew)>16) { error(env,"java/lang/IllegalArgumentException","Text skew must be in [-16,16]"); return; }
    if(paintStyle<0 || paintStyle>2 || !std::isfinite(strokeWidth) || strokeWidth<0 || strokeWidth>512 || cap<0 || cap>2 || join<0 || join>2 || !std::isfinite(miter) || miter<0 || miter>1024){error(env,"java/lang/IllegalArgumentException","Invalid text stroke parameters");return;}
    try {
        Text input(env, text); if (!input.chars) return;
        auto selected = font(env, family, style); if (!selected) return;
        auto& face = *selected; std::lock_guard<std::mutex> lock(shapingMutex); face.variations(env,tags,values); if(env->ExceptionCheck())return; if (!face.size(size)) return;
        struct Stroker {FT_Stroker value=nullptr;~Stroker(){if(value)FT_Stroker_Done(value);}} stroker;
        if(paintStyle!=0){
            if(FT_Stroker_New(face.library,&stroker.value))throw std::runtime_error("Cannot create text stroker");
            FT_Stroker_Set(stroker.value,static_cast<FT_Fixed>(std::lround((strokeWidth==0?1:strokeWidth)*32)),
                cap==1?FT_STROKER_LINECAP_ROUND:cap==2?FT_STROKER_LINECAP_SQUARE:FT_STROKER_LINECAP_BUTT,
                join==1?FT_STROKER_LINEJOIN_ROUND:join==2?FT_STROKER_LINEJOIN_BEVEL:FT_STROKER_LINEJOIN_MITER_FIXED,
                static_cast<FT_Fixed>(std::lround(std::max(1.0f,miter)*65536)));
        }
        struct PathPlacement {
            JNIEnv* env; jobject measure; jclass cls=nullptr;jfloatArray position=nullptr,tangent=nullptr;jmethodID method=nullptr;float matrix[6]={1,0,0,1,0,0};
            PathPlacement(JNIEnv* e,jobject object,jfloatArray transform):env(e),measure(object){
                if(!transform&&!measure)return;
                if(!transform||env->GetArrayLength(transform)!=6)throw std::invalid_argument("Text path matrix");
                env->GetFloatArrayRegion(transform,0,6,matrix);
                for(int i=0;i<6;i++)if(!std::isfinite(matrix[i])||std::abs(matrix[i])>(i<4?256:10000000))throw std::invalid_argument("Text path transform bound");
            }
            bool initialize(){if(!measure)return true;cls=env->GetObjectClass(measure);if(!cls)return false;jclass expected=env->FindClass("android/graphics/PathMeasure");if(!expected)return false;bool exact=env->IsSameObject(cls,expected);env->DeleteLocalRef(expected);if(!exact)throw std::invalid_argument("Text path requires an unmodified PathMeasure snapshot");method=env->GetMethodID(cls,"getPosTan","(F[F[F)Z");if(!method)return false;position=env->NewFloatArray(2);tangent=env->NewFloatArray(2);return position&&tangent;}
            ~PathPlacement(){if(position)env->DeleteLocalRef(position);if(tangent)env->DeleteLocalRef(tangent);if(cls)env->DeleteLocalRef(cls);}
            bool place(float x,float y,double& originX,double& originY,FT_Matrix& transform){
                jfloat p[2]={x,y},t[2]={1,0};
                bool found=measure&&env->CallBooleanMethod(measure,method,x,position,tangent);if(env->ExceptionCheck())return false;
                if(found){env->GetFloatArrayRegion(position,0,2,p);env->GetFloatArrayRegion(tangent,0,2,t);if(env->ExceptionCheck())return false;}
                for(float value:{p[0],p[1],t[0],t[1]})if(!std::isfinite(value))throw std::invalid_argument("Invalid path position/tangent");
                double px=measure?p[0]-t[1]*double(y):x,py=measure?p[1]+t[0]*double(y):y;
                originX=matrix[0]*px+matrix[2]*py+matrix[4];originY=matrix[1]*px+matrix[3]*py+matrix[5];
                if(std::abs(originX)>10000000||std::abs(originY)>10000000)throw std::invalid_argument("Text path position bound");
                transform={static_cast<FT_Fixed>(std::llround((matrix[0]*t[0]+matrix[2]*t[1])*65536)),static_cast<FT_Fixed>(std::llround((matrix[0]*t[1]-matrix[2]*t[0])*65536)),static_cast<FT_Fixed>(std::llround((-matrix[1]*t[0]-matrix[3]*t[1])*65536)),static_cast<FT_Fixed>(std::llround((-matrix[1]*t[1]+matrix[3]*t[0])*65536))};return true;
            }
        } placement(env,pathMeasure,canvasMatrix);
        if(!placement.initialize())return;
        Run run(face,input,size,spacing,levels,featureSettings);
        unsigned count = run.glyphs.size(); auto glyphs = run.glyphs.data();
        auto positions = run.positions.data();
        // Stage only touched rows, keeping failure-before-commit semantics without
        // copying a potentially 128 MiB map for each label under the shared font lock.
        std::map<int, std::vector<jint>> output;
        const int rowWidth=cr-cl;
        double penX = x, penY = y;
        uint64_t work = 0;
        for (unsigned i = 0; i < count; ++i) {
            Font& glyphFace=*run.faces[i];
            uint32_t glyphColor = uint32_t(color);
            if (glyphFace.face && glyphFace.face->family_name && std::string(glyphFace.face->family_name).find("Emoji") != std::string::npos) {
                glyphColor = (glyphColor & 0xff000000u) | 0x00ffcc4du;
            }
            loadTextGlyph(glyphFace,glyphs[i].codepoint,skew,scaleX,(canvasMatrix||subpixel)?1:paintStyle,hinting,fakeBold);
            struct Glyph {FT_Glyph value=nullptr;~Glyph(){if(value)FT_Done_Glyph(value);}} owned;
            FT_Bitmap* raster=nullptr;int bitmapLeft=0,bitmapTop=0;
            double originX=penX+scaleX*positions[i].x_offset/64.0-skew*positions[i].y_offset/64.0,originY=penY-positions[i].y_offset/64.0;
            FT_Matrix pathTransform{65536,0,0,65536};if((pathMeasure||canvasMatrix)&&!placement.place(originX,originY,originX,originY,pathTransform))return;
            if (glyphFace.face->glyph->format == FT_GLYPH_FORMAT_BITMAP && glyphFace.face->glyph->bitmap.pixel_mode == FT_PIXEL_MODE_BGRA) {
                const auto& bitmap = glyphFace.face->glyph->bitmap;
                double sx = glyphFace.bitmapScale * scaleX * (canvasMatrix ? std::hypot(placement.matrix[0], placement.matrix[1]) : 1.0);
                double sy = glyphFace.bitmapScale * (canvasMatrix ? std::hypot(placement.matrix[2], placement.matrix[3]) : 1.0);
                int dstW = std::max(1, static_cast<int>(std::lround(bitmap.width * sx)));
                int dstH = std::max(1, static_cast<int>(std::lround(bitmap.rows * sy)));
                if (dstW > 4096 || dstH > 4096) throw std::invalid_argument("Color glyph raster bounds");
                work += uint64_t(dstW) * dstH;
                if (work > 64 * 1024 * 1024) throw std::invalid_argument("Text glyph coverage exceeds per-call work limit");
                int64_t left = int64_t(std::floor(originX + glyphFace.face->glyph->bitmap_left * sx));
                int64_t top = int64_t(std::floor(originY - glyphFace.face->glyph->bitmap_top * sy));
                uint32_t paintAlpha = (uint32_t(color) >> 24) & 0xffu;
                for (int row = 0; row < dstH; ++row) {
                    int64_t py = top + row; if (py < ct || py >= cb) continue;
                    double sy0 = row * bitmap.rows / double(dstH);
                    double sy1 = (row + 1) * bitmap.rows / double(dstH);
                    int iy0 = std::clamp(int(std::floor(sy0)), 0, int(bitmap.rows) - 1);
                    int iy1 = std::clamp(int(std::ceil(sy1)) - 1, iy0, int(bitmap.rows) - 1);
                    for (int col = 0; col < dstW; ++col) {
                        int64_t px = left + col; if (px < cl || px >= cr) continue;
                        double sx0 = col * bitmap.width / double(dstW);
                        double sx1 = (col + 1) * bitmap.width / double(dstW);
                        int ix0 = std::clamp(int(std::floor(sx0)), 0, int(bitmap.width) - 1);
                        int ix1 = std::clamp(int(std::ceil(sx1)) - 1, ix0, int(bitmap.width) - 1);
                        double sumB = 0, sumG = 0, sumR = 0, sumA = 0, totalW = 0;
                        for (int syi = iy0; syi <= iy1; ++syi) {
                            double wy = std::max(0.0, std::min(sy1, double(syi + 1)) - std::max(sy0, double(syi)));
                            if (wy <= 0) continue;
                            const auto* srow = bitmap.buffer + (bitmap.pitch >= 0 ? syi : int(bitmap.rows) - 1 - syi) * std::abs(bitmap.pitch);
                            for (int sxi = ix0; sxi <= ix1; ++sxi) {
                                double wx = std::max(0.0, std::min(sx1, double(sxi + 1)) - std::max(sx0, double(sxi)));
                                double w = wx * wy;
                                if (w <= 0) continue;
                                sumB += srow[sxi * 4 + 0] * w;
                                sumG += srow[sxi * 4 + 1] * w;
                                sumR += srow[sxi * 4 + 2] * w;
                                sumA += srow[sxi * 4 + 3] * w;
                                totalW += w;
                            }
                        }
                        if (totalW <= 0) continue;
                        unsigned b = static_cast<unsigned>(std::clamp(std::lround(sumB / totalW), 0l, 255l));
                        unsigned g = static_cast<unsigned>(std::clamp(std::lround(sumG / totalW), 0l, 255l));
                        unsigned r = static_cast<unsigned>(std::clamp(std::lround(sumR / totalW), 0l, 255l));
                        unsigned a = static_cast<unsigned>(std::clamp(std::lround(sumA / totalW), 0l, 255l));
                        if (!a || !paintAlpha) continue;
                        unsigned ur = std::min(255u, (r * 255u + a / 2) / a);
                        unsigned ug = std::min(255u, (g * 255u + a / 2) / a);
                        unsigned ub = std::min(255u, (b * 255u + a / 2) / a);
                        uint32_t srcColor = (paintAlpha << 24) | (ur << 16) | (ug << 8) | ub;
                        auto found = output.find(static_cast<int>(py));
                        if (found == output.end()) {
                            found = output.emplace(static_cast<int>(py), std::vector<jint>(rowWidth)).first;
                            env->GetIntArrayRegion(pixels, static_cast<jsize>(py * width + cl), rowWidth, found->second.data());
                            if (env->ExceptionCheck()) return;
                        }
                        auto& pixel = found->second[static_cast<size_t>(px - cl)];
                        pixel = jint(shiraz::over(srcColor, uint32_t(pixel), a));
                    }
                }
                penX += scaleX * positions[i].x_advance / 64.0; penY -= positions[i].y_advance / 64.0;
                continue;
            }
            // FreeType outlines use upward-positive 26.6 coordinates. Preserve
            // the fractional device origin before rasterization; integer placement
            // below remains identical when subpixel positioning is disabled.
            FT_Vector fraction{static_cast<FT_Pos>(std::lround((originX-std::floor(originX))*64)),static_cast<FT_Pos>(-std::lround((originY-std::floor(originY))*64))};
            if(paintStyle!=0||pathMeasure||canvasMatrix){
                if(glyphFace.face->glyph->format!=FT_GLYPH_FORMAT_OUTLINE)throw std::runtime_error("Text strokes require outline glyphs");
                if(FT_Get_Glyph(glyphFace.face->glyph,&owned.value))throw std::runtime_error("Cannot copy glyph outline");
                FT_Error failure=paintStyle==0?0:paintStyle==1?FT_Glyph_Stroke(&owned.value,stroker.value,1):FT_Glyph_StrokeBorder(&owned.value,stroker.value,0,1);
                if(failure)throw std::runtime_error("Cannot stroke glyph outline");
                if((pathMeasure||canvasMatrix)&&FT_Glyph_Transform(owned.value,&pathTransform,nullptr))throw std::runtime_error("Cannot transform path glyph");
                if(subpixel&&FT_Glyph_Transform(owned.value,nullptr,&fraction))throw std::runtime_error("Cannot position subpixel glyph");
                FT_BBox bounds;FT_Glyph_Get_CBox(owned.value,FT_GLYPH_BBOX_PIXELS,&bounds);
                auto bw=bounds.xMax-bounds.xMin,bh=bounds.yMax-bounds.yMin;
                if(bw<0 || bh<0 || bw>4096 || bh>4096 || work+uint64_t(bw)*bh>64*1024*1024)throw std::invalid_argument("Stroked glyph raster bounds");
                if(FT_Glyph_To_Bitmap(&owned.value,aa?FT_RENDER_MODE_NORMAL:FT_RENDER_MODE_MONO,nullptr,1))throw std::runtime_error("Cannot rasterize stroked glyph");
                auto glyph=reinterpret_cast<FT_BitmapGlyph>(owned.value);raster=&glyph->bitmap;bitmapLeft=glyph->left;bitmapTop=glyph->top;
            }else{
                if(subpixel&&glyphFace.face->glyph->format==FT_GLYPH_FORMAT_OUTLINE)FT_Outline_Translate(&glyphFace.face->glyph->outline,fraction.x,fraction.y);
                // Measurement may use amplified fonts; bound coverage before FreeType allocates a raster.
                if(glyphFace.face->glyph->format==FT_GLYPH_FORMAT_OUTLINE){
                    FT_BBox bounds;FT_Outline_Get_CBox(&glyphFace.face->glyph->outline,&bounds);
                    const int64_t bw=int64_t(std::ceil(bounds.xMax/64.0)-std::floor(bounds.xMin/64.0));
                    const int64_t bh=int64_t(std::ceil(bounds.yMax/64.0)-std::floor(bounds.yMin/64.0));
                    if(bw<0||bh<0||bw>4096||bh>4096||work+uint64_t(bw)*bh>64*1024*1024)throw std::invalid_argument("Glyph raster bounds");
                }
                if(FT_Render_Glyph(glyphFace.face->glyph,aa?FT_RENDER_MODE_NORMAL:FT_RENDER_MODE_MONO))throw std::runtime_error("Cannot rasterize text glyph");
                raster=&glyphFace.face->glyph->bitmap;bitmapLeft=glyphFace.face->glyph->bitmap_left;bitmapTop=glyphFace.face->glyph->bitmap_top;
            }
            const auto& bitmap=*raster;
            if (bitmap.width > 4096 || bitmap.rows > 4096 || (bitmap.pixel_mode != FT_PIXEL_MODE_GRAY && bitmap.pixel_mode != FT_PIXEL_MODE_MONO)) throw std::runtime_error("Unsupported font bitmap");
            work += uint64_t(bitmap.width) * bitmap.rows;
            if (work > 64 * 1024 * 1024) throw std::invalid_argument("Text glyph coverage exceeds per-call work limit");
            int64_t left = int64_t(std::floor(originX)) + bitmapLeft;
            int64_t top = int64_t(std::floor(originY)) - bitmapTop;
            for (unsigned row = 0; row < bitmap.rows; ++row) {
                int64_t py = top + row; if (py < ct || py >= cb) continue;
                const auto* data = bitmap.buffer + (bitmap.pitch >= 0 ? row : bitmap.rows - 1 - row) * std::abs(bitmap.pitch);
                for (unsigned col = 0; col < bitmap.width; ++col) {
                    int64_t px = left + col; if (px < cl || px >= cr) continue;
                    unsigned coverage = bitmap.pixel_mode == FT_PIXEL_MODE_MONO ? ((data[col / 8] >> (7 - col % 8)) & 1) * 255 : data[col];
                    if(!coverage)continue;
                    auto found=output.find(static_cast<int>(py));
                    if(found==output.end()){
                        found=output.emplace(static_cast<int>(py),std::vector<jint>(rowWidth)).first;
                        env->GetIntArrayRegion(pixels,static_cast<jsize>(py*width+cl),rowWidth,found->second.data());
                        if(env->ExceptionCheck())return;
                    }
                    auto& pixel=found->second[static_cast<size_t>(px-cl)];
                    pixel=jint(shiraz::over(glyphColor,uint32_t(pixel),coverage));
                }
            }
            penX += scaleX*positions[i].x_advance / 64.0; penY -= positions[i].y_advance / 64.0;
        }
        for(const auto& row:output){
            env->SetIntArrayRegion(pixels,row.first*width+cl,rowWidth,row.second.data());
            if(env->ExceptionCheck())return;
        }
    } catch (const std::bad_alloc&) { error(env, "java/lang/OutOfMemoryError", "Cannot rasterize text"); }
      catch (const std::invalid_argument& ex) { error(env, "java/lang/IllegalArgumentException", ex.what()); }
      catch (const std::exception& ex) { error(env, "java/lang/IllegalStateException", ex.what()); }
}

extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativeText_variationAxes(JNIEnv* env,jclass,jstring family,jint style) {
    try {auto selected=font(env,family,style);if(!selected)return nullptr;std::lock_guard<std::mutex> lock(shapingMutex);
        std::vector<jint> tags;
        auto* axes=selected->metadata();if(axes)for(unsigned i=0;i<axes->num_axis;i++)tags.push_back(static_cast<jint>(axes->axis[i].tag));
        auto out=env->NewIntArray(tags.size());if(out&&!tags.empty())env->SetIntArrayRegion(out,0,tags.size(),tags.data());return out;
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Font axes allocation");}catch(const std::exception& e){error(env,"java/lang/IllegalStateException",e.what());}return nullptr;
}

static jintArray textBounds(JNIEnv* env,jclass,jstring text,jfloat size,jstring family,jint style,jintArray tags,jfloatArray values,jfloat spacing,jstring featureSettings,jboolean fakeBold,jfloat skew,jint hinting,jfloat scaleX,jbyteArray levels,bool subpixel=false) {
    try {
        if(!text){error(env,"java/lang/NullPointerException","text");return nullptr;}
        if(!std::isfinite(skew)||std::abs(skew)>16||!std::isfinite(scaleX)||scaleX<0||scaleX>16||(hinting!=0&&hinting!=1))throw std::invalid_argument("Text bounds parameters");
        Text input(env,text);if(!input.chars)return nullptr;
        auto selected=font(env,family,style);if(!selected)return nullptr;
        auto& face=*selected;std::lock_guard<std::mutex> lock(shapingMutex);face.variations(env,tags,values);if(env->ExceptionCheck())return nullptr;
        jint bounds[4]={0,0,0,0};bool any=false;
        if(face.size(size)&&scaleX!=0){
            Run run(face,input,size,spacing,levels,featureSettings);unsigned count=run.glyphs.size();auto glyphs=run.glyphs.data();auto positions=run.positions.data();
            double penX=0,penY=0;
            for(unsigned i=0;i<count;i++){
                Font& glyphFace=*run.faces[i];
            loadTextGlyph(glyphFace,glyphs[i].codepoint,skew,scaleX,subpixel?1:0,hinting,fakeBold);
                if (glyphFace.face->glyph->format == FT_GLYPH_FORMAT_BITMAP && !FT_IS_SCALABLE(glyphFace.face)) {
                    double x=std::floor(penX+scaleX*positions[i].x_offset/64.0-skew*positions[i].y_offset/64.0),y=std::floor(penY-positions[i].y_offset/64.0);
                    double sx=glyphFace.bitmapScale*scaleX,sy=glyphFace.bitmapScale;
                    double left=x+std::floor(glyphFace.face->glyph->bitmap_left*sx),top=y-std::ceil(glyphFace.face->glyph->bitmap_top*sy);
                    double right=left+std::ceil(glyphFace.face->glyph->bitmap.width*sx),bottom=top+std::ceil(glyphFace.face->glyph->bitmap.rows*sy);
                    if(!any){bounds[0]=jint(left);bounds[1]=jint(top);bounds[2]=jint(right);bounds[3]=jint(bottom);any=true;}
                    else{bounds[0]=std::min(bounds[0],jint(left));bounds[1]=std::min(bounds[1],jint(top));bounds[2]=std::max(bounds[2],jint(right));bounds[3]=std::max(bounds[3],jint(bottom));}
                    penX+=scaleX*positions[i].x_advance/64.0;penY-=positions[i].y_advance/64.0;
                    continue;
                }
                FT_Glyph raw=nullptr;if(FT_Get_Glyph(glyphFace.face->glyph,&raw))throw std::runtime_error("Cannot read glyph bounds");
                std::unique_ptr<FT_GlyphRec_,decltype(&FT_Done_Glyph)> glyph(raw,FT_Done_Glyph);
                if(subpixel){double ox=penX+scaleX*positions[i].x_offset/64.0-skew*positions[i].y_offset/64.0,oy=penY-positions[i].y_offset/64.0;FT_Vector fraction{static_cast<FT_Pos>(std::lround((ox-std::floor(ox))*64)),static_cast<FT_Pos>(-std::lround((oy-std::floor(oy))*64))};if(FT_Glyph_Transform(raw,nullptr,&fraction))throw std::runtime_error("Cannot position glyph bounds");}
                FT_BBox box;FT_Glyph_Get_CBox(raw,FT_GLYPH_BBOX_PIXELS,&box);
                if(box.xMax>box.xMin&&box.yMax>box.yMin){
                    double x=std::floor(penX+scaleX*positions[i].x_offset/64.0-skew*positions[i].y_offset/64.0),y=std::floor(penY-positions[i].y_offset/64.0);
                    double left=x+box.xMin,top=y-box.yMax,right=x+box.xMax,bottom=y-box.yMin;
                    if(left<INT32_MIN||top<INT32_MIN||right>INT32_MAX||bottom>INT32_MAX)throw std::invalid_argument("Text bounds overflow");
                    if(!any){bounds[0]=jint(left);bounds[1]=jint(top);bounds[2]=jint(right);bounds[3]=jint(bottom);any=true;}
                    else{bounds[0]=std::min(bounds[0],jint(left));bounds[1]=std::min(bounds[1],jint(top));bounds[2]=std::max(bounds[2],jint(right));bounds[3]=std::max(bounds[3],jint(bottom));}
                }
                penX+=scaleX*positions[i].x_advance/64.0;penY-=positions[i].y_advance/64.0;
            }
        }
        auto result=env->NewIntArray(4);if(result)env->SetIntArrayRegion(result,0,4,bounds);return result;
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Text bounds allocation");}
     catch(const std::invalid_argument& ex){error(env,"java/lang/IllegalArgumentException",ex.what());}
     catch(const std::exception& ex){error(env,"java/lang/IllegalStateException",ex.what());}
    return nullptr;
}

// Five floats per visual shaping cluster: logical start/end, left/right, RTL flag.
extern "C" JNIEXPORT jfloatArray JNICALL Java_shiraz_runtime_NativeText_clustersResolved(JNIEnv* env,jclass,jstring text,jfloat size,jstring family,jint style,jintArray tags,jfloatArray values,jfloat spacing,jstring featureSettings,jbyteArray levels) {
    if(!text){error(env,"java/lang/NullPointerException","text");return nullptr;}
    try {
        Text input(env,text);if(!input.chars)return nullptr;
        auto selected=font(env,family,style);if(!selected)return nullptr;
        std::lock_guard<std::mutex> lock(shapingMutex);selected->variations(env,tags,values);if(env->ExceptionCheck())return nullptr;
        bool nonzero=selected->size(size);if(!nonzero)selected->size(1);
        Run run(*selected,input,nonzero?size:1,spacing,levels,featureSettings);
        std::vector<unsigned> boundaries;for(auto& glyph:run.glyphs)boundaries.push_back(glyph.cluster);
        boundaries.push_back(input.length);std::sort(boundaries.begin(),boundaries.end());boundaries.erase(std::unique(boundaries.begin(),boundaries.end()),boundaries.end());
        std::vector<jfloat> result;int64_t pen=0;
        for(size_t at=0;at<run.glyphs.size();){
            size_t end=at+1;int64_t width=run.positions[at].x_advance;
            while(end<run.glyphs.size()&&run.glyphs[end].cluster==run.glyphs[at].cluster){width+=run.positions[end].x_advance;end++;}
            auto start=run.glyphs[at].cluster;auto next=std::upper_bound(boundaries.begin(),boundaries.end(),start);
            if(next==boundaries.end())throw std::runtime_error("Invalid shaping cluster boundary");
            result.insert(result.end(),{float(start),float(*next),nonzero?float(pen)/64:0,nonzero?float(pen+width)/64:0,float(run.levels[at])});
            pen+=width;at=end;
        }
        auto out=env->NewFloatArray(result.size());if(out&&!result.empty())env->SetFloatArrayRegion(out,0,result.size(),result.data());return out;
    }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Text cluster allocation");}
     catch(const std::invalid_argument& ex){error(env,"java/lang/IllegalArgumentException",ex.what());}
     catch(const std::exception& ex){error(env,"java/lang/IllegalStateException",ex.what());}return nullptr;
}

extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeText_drawResolved(JNIEnv* env, jclass type, jintArray pixels, jint width, jint height, jstring text, jfloat x, jfloat y, jfloat size, jint color, jboolean aa, jstring family, jint style, jint cl, jint ct, jint cr, jint cb, jboolean fakeBold, jfloat skew,jintArray tags,jfloatArray values,jfloat spacing,jstring featureSettings,jint paintStyle,jfloat strokeWidth,jint cap,jint join,jfloat miter,jint hinting,jfloat scaleX,jbyteArray levels) {drawText(env,type,pixels,width,height,text,x,y,size,color,aa,family,style,cl,ct,cr,cb,fakeBold,skew,tags,values,spacing,featureSettings,paintStyle,strokeWidth,cap,join,miter,hinting,scaleX,levels,nullptr,nullptr);}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeText_drawPathResolved(JNIEnv* env, jclass type, jintArray pixels, jint width, jint height, jstring text, jfloat x, jfloat y, jfloat size, jint color, jboolean aa, jstring family, jint style, jint cl, jint ct, jint cr, jint cb, jboolean fakeBold, jfloat skew,jintArray tags,jfloatArray values,jfloat spacing,jstring featureSettings,jint paintStyle,jfloat strokeWidth,jint cap,jint join,jfloat miter,jint hinting,jfloat scaleX,jbyteArray levels,jobject measure,jfloatArray matrix) {drawText(env,type,pixels,width,height,text,x,y,size,color,aa,family,style,cl,ct,cr,cb,fakeBold,skew,tags,values,spacing,featureSettings,paintStyle,strokeWidth,cap,join,miter,hinting,scaleX,levels,measure,matrix);}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeText_drawAffineResolved(JNIEnv* env, jclass type, jintArray pixels, jint width, jint height, jstring text, jfloat x, jfloat y, jfloat size, jint color, jboolean aa, jstring family, jint style, jint cl, jint ct, jint cr, jint cb, jboolean fakeBold, jfloat skew,jintArray tags,jfloatArray values,jfloat spacing,jstring featureSettings,jint paintStyle,jfloat strokeWidth,jint cap,jint join,jfloat miter,jint hinting,jfloat scaleX,jbyteArray levels,jfloatArray matrix) {drawText(env,type,pixels,width,height,text,x,y,size,color,aa,family,style,cl,ct,cr,cb,fakeBold,skew,tags,values,spacing,featureSettings,paintStyle,strokeWidth,cap,join,miter,hinting,scaleX,levels,nullptr,matrix);}

extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeText_drawSubpixelResolved(JNIEnv* env, jclass type, jintArray pixels, jint width, jint height, jstring text, jfloat x, jfloat y, jfloat size, jint color, jboolean aa, jstring family, jint style, jint cl, jint ct, jint cr, jint cb, jboolean fakeBold, jfloat skew,jintArray tags,jfloatArray values,jfloat spacing,jstring featureSettings,jint paintStyle,jfloat strokeWidth,jint cap,jint join,jfloat miter,jint hinting,jfloat scaleX,jbyteArray levels,jboolean subpixel) {drawText(env,type,pixels,width,height,text,x,y,size,color,aa,family,style,cl,ct,cr,cb,fakeBold,skew,tags,values,spacing,featureSettings,paintStyle,strokeWidth,cap,join,miter,hinting,scaleX,levels,nullptr,nullptr,subpixel);}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeText_drawPathSubpixelResolved(JNIEnv* env, jclass type, jintArray pixels, jint width, jint height, jstring text, jfloat x, jfloat y, jfloat size, jint color, jboolean aa, jstring family, jint style, jint cl, jint ct, jint cr, jint cb, jboolean fakeBold, jfloat skew,jintArray tags,jfloatArray values,jfloat spacing,jstring featureSettings,jint paintStyle,jfloat strokeWidth,jint cap,jint join,jfloat miter,jint hinting,jfloat scaleX,jbyteArray levels,jobject measure,jfloatArray matrix,jboolean subpixel) {drawText(env,type,pixels,width,height,text,x,y,size,color,aa,family,style,cl,ct,cr,cb,fakeBold,skew,tags,values,spacing,featureSettings,paintStyle,strokeWidth,cap,join,miter,hinting,scaleX,levels,measure,matrix,subpixel);}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeText_drawAffineSubpixelResolved(JNIEnv* env, jclass type, jintArray pixels, jint width, jint height, jstring text, jfloat x, jfloat y, jfloat size, jint color, jboolean aa, jstring family, jint style, jint cl, jint ct, jint cr, jint cb, jboolean fakeBold, jfloat skew,jintArray tags,jfloatArray values,jfloat spacing,jstring featureSettings,jint paintStyle,jfloat strokeWidth,jint cap,jint join,jfloat miter,jint hinting,jfloat scaleX,jbyteArray levels,jfloatArray matrix,jboolean subpixel) {drawText(env,type,pixels,width,height,text,x,y,size,color,aa,family,style,cl,ct,cr,cb,fakeBold,skew,tags,values,spacing,featureSettings,paintStyle,strokeWidth,cap,join,miter,hinting,scaleX,levels,nullptr,matrix,subpixel);}
extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativeText_boundsResolved(JNIEnv* env,jclass type,jstring text,jfloat size,jstring family,jint style,jintArray tags,jfloatArray values,jfloat spacing,jstring featureSettings,jboolean fakeBold,jfloat skew,jint hinting,jfloat scaleX,jbyteArray levels) {return textBounds(env,type,text,size,family,style,tags,values,spacing,featureSettings,fakeBold,skew,hinting,scaleX,levels);}
extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativeText_boundsSubpixelResolved(JNIEnv* env,jclass type,jstring text,jfloat size,jstring family,jint style,jintArray tags,jfloatArray values,jfloat spacing,jstring featureSettings,jboolean fakeBold,jfloat skew,jint hinting,jfloat scaleX,jbyteArray levels,jboolean subpixel) {return textBounds(env,type,text,size,family,style,tags,values,spacing,featureSettings,fakeBold,skew,hinting,scaleX,levels,subpixel);}
