// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <png.h>
#include <zlib.h>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <new>
#include <stdexcept>
#include <vector>
#include <array>
namespace {
void error(JNIEnv* env,const char* type,const char* message) {
    if (env->ExceptionCheck()) return;
    auto cls=env->FindClass(type); if (cls) { env->ThrowNew(cls,message); env->DeleteLocalRef(cls); }
}
std::uint32_t big(const unsigned char* p) { return (std::uint32_t(p[0])<<24)|(std::uint32_t(p[1])<<16)|(std::uint32_t(p[2])<<8)|p[3]; }
struct Inflate {
    z_stream stream{};
    bool initialized=false;
    ~Inflate() { if (initialized) inflateEnd(&stream); }
};
bool metadata(const unsigned char* data,size_t length,bool compressed,size_t& total) {
    constexpr size_t limit=1024*1024;
    if (!compressed) { if (length > limit-total) throw std::invalid_argument("PNG text exceeds 1MiB budget"); total+=length; return true; }
    Inflate decoder; if (inflateInit(&decoder.stream) != Z_OK) throw std::bad_alloc(); decoder.initialized=true;
    decoder.stream.next_in=const_cast<Bytef*>(data); decoder.stream.avail_in=static_cast<uInt>(length);
    std::array<unsigned char,8192> buffer{};
    int status=Z_OK;
    while (status == Z_OK) {
        decoder.stream.next_out=buffer.data(); decoder.stream.avail_out=static_cast<uInt>(buffer.size());
        status=inflate(&decoder.stream,Z_NO_FLUSH); size_t count=buffer.size()-decoder.stream.avail_out;
        if (count > limit-total) throw std::invalid_argument("PNG text exceeds 1MiB budget");
        total+=count;
    }
    return status == Z_STREAM_END && decoder.stream.avail_in == 0;
}
struct Image { png_image value{}; Image() { value.version=PNG_IMAGE_VERSION; } ~Image() { png_image_free(&value); } };
}
extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativeImage_png(JNIEnv* env,jclass,jbyteArray input,jint offset,jint length,jboolean bounds,jintArray dimensions) {
    try {
        if (!input || !dimensions) { error(env,"java/lang/NullPointerException","PNG input/dimensions"); return nullptr; }
        if (env->GetArrayLength(dimensions) != 2) throw std::invalid_argument("PNG dimensions output length");
        if (offset < 0 || length < 0 || std::int64_t(offset)+length > env->GetArrayLength(input)) { error(env,"java/lang/ArrayIndexOutOfBoundsException","PNG byte range"); return nullptr; }
        if (length > 16*1024*1024) throw std::invalid_argument("PNG input exceeds 16MiB");
        if (length < 8) return nullptr;
        std::vector<unsigned char> data(static_cast<size_t>(length)); env->GetByteArrayRegion(input,offset,length,reinterpret_cast<jbyte*>(data.data()));
        if (env->ExceptionCheck()) return nullptr;
        if (png_sig_cmp(data.data(),0,8)) return nullptr;
        size_t position=8,textBytes=0; unsigned chunks=0; std::uint32_t width=0,height=0; bool end=false,header=false;
        while (position < data.size()) {
            if (++chunks > 4096 || data.size()-position < 12) return nullptr;
            const auto count=big(data.data()+position); if (count > data.size()-position-12) return nullptr;
            const auto* type=data.data()+position+4; const auto* bytes=type+4;
            for (int i=0;i<4;i++) if (!((type[i]>='A' && type[i]<='Z') || (type[i]>='a' && type[i]<='z'))) return nullptr;
            if (type[2] >= 'a' && type[2] <= 'z') return nullptr;
            if (type[0] >= 'A' && type[0] <= 'Z' && std::memcmp(type,"IHDR",4) && std::memcmp(type,"PLTE",4) && std::memcmp(type,"IDAT",4) && std::memcmp(type,"IEND",4)) return nullptr;
            if (crc32(0,type,static_cast<uInt>(count)+4) != big(bytes+count)) return nullptr;
            if (!header) {
                if (std::memcmp(type,"IHDR",4) || count != 13) return nullptr;
                width=big(bytes); height=big(bytes+4); header=true;
                if (!width || !height || width > 0x7fffffff || height > 0x7fffffff) return nullptr;
                const unsigned depth=bytes[8],color=bytes[9];
                const bool valid=(color == 0 && (depth == 1 || depth == 2 || depth == 4 || depth == 8 || depth == 16)) ||
                    (color == 3 && (depth == 1 || depth == 2 || depth == 4 || depth == 8)) ||
                    ((color == 2 || color == 4 || color == 6) && (depth == 8 || depth == 16));
                if (!valid || bytes[10] || bytes[11] || bytes[12] > 1) return nullptr;
            } else if (!std::memcmp(type,"IHDR",4)) return nullptr;
            if (!std::memcmp(type,"iCCP",4)) { position+=static_cast<size_t>(count)+12; continue; }
            if (!std::memcmp(type,"tEXt",4)) { if (!metadata(bytes,count,false,textBytes)) return nullptr; }
            if (!std::memcmp(type,"zTXt",4) || !std::memcmp(type,"iTXt",4)) {
                const auto* separator=static_cast<const unsigned char*>(std::memchr(bytes,0,count));
                if (!separator || separator == bytes || separator-bytes > 79) return nullptr;
                size_t at=static_cast<size_t>(separator-bytes)+1; bool compressed=true;
                if (!std::memcmp(type,"iTXt",4)) {
                    if (count-at < 2 || bytes[at] > 1 || bytes[at+1] != 0) return nullptr;
                    compressed=bytes[at] != 0; at+=2;
                    for (int i=0;i<2;i++) {
                        const auto* endString=static_cast<const unsigned char*>(std::memchr(bytes+at,0,count-at));
                        if (!endString) return nullptr;
                        at=static_cast<size_t>(endString-bytes)+1;
                    }
                } else { if (at >= count || bytes[at++] != 0) return nullptr; }
                if (!metadata(bytes,at,false,textBytes) || !metadata(bytes+at,count-at,compressed,textBytes)) return nullptr;
            }
            // Compiled nine-patch metadata is validated by BitmapFactory after PNG validation.
            position+=static_cast<size_t>(count)+12;
            if (!std::memcmp(type,"IEND",4)) { if (count || position != data.size()) return nullptr; end=true; break; }
        }
        if (!end) return nullptr;
        jint sizes[2]={static_cast<jint>(width),static_cast<jint>(height)};
        if (bounds) { env->SetIntArrayRegion(dimensions,0,2,sizes); return nullptr; }
        if (width > 8192 || height > 8192 || std::uint64_t(width)*height > 16*1024*1024) throw std::invalid_argument("Decoded PNG exceeds bitmap dimensions");
        Image image;
        if (!png_image_begin_read_from_memory(&image.value,data.data(),data.size())) return nullptr;
        if (image.value.width != width || image.value.height != height) return nullptr;
        image.value.format=PNG_FORMAT_RGBA;
        std::vector<jint> pixels(static_cast<size_t>(width)*height);
        if (!png_image_finish_read(&image.value,nullptr,pixels.data(),0,nullptr)) return nullptr;
        for (auto& pixel:pixels) {
            const auto* c=reinterpret_cast<const unsigned char*>(&pixel);
            const std::uint32_t color=(std::uint32_t(c[3])<<24)|(std::uint32_t(c[0])<<16)|(std::uint32_t(c[1])<<8)|c[2];
            pixel=static_cast<jint>(color);
        }
        auto output=env->NewIntArray(static_cast<jsize>(pixels.size())); if (!output) return nullptr;
        env->SetIntArrayRegion(output,0,static_cast<jsize>(pixels.size()),pixels.data()); if (env->ExceptionCheck()) return nullptr;
        env->SetIntArrayRegion(dimensions,0,2,sizes); return output;
    } catch (const std::bad_alloc&) { error(env,"java/lang/OutOfMemoryError","Cannot allocate PNG image"); }
      catch (const std::invalid_argument& issue) { error(env,"java/lang/IllegalArgumentException",issue.what()); }
      catch (const std::exception&) { error(env,"java/lang/IllegalStateException","Cannot decode PNG image"); }
    return nullptr;
}

#include <webp/decode.h>
extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativeImage_webp(JNIEnv* env,jclass,jbyteArray input,jint offset,jint length,jboolean bounds,jintArray dimensions) {
 try {
  if(!input||!dimensions){error(env,"java/lang/NullPointerException","WebP input/dimensions");return nullptr;}
  if(env->GetArrayLength(dimensions)!=2)throw std::invalid_argument("WebP dimensions output length");
  if(offset<0||length<0||std::int64_t(offset)+length>env->GetArrayLength(input)){error(env,"java/lang/ArrayIndexOutOfBoundsException","WebP byte range");return nullptr;}
  if(length>16*1024*1024)throw std::invalid_argument("WebP input exceeds 16MiB");
  std::vector<unsigned char> data(static_cast<size_t>(length));env->GetByteArrayRegion(input,offset,length,reinterpret_cast<jbyte*>(data.data()));if(env->ExceptionCheck())return nullptr;
  WebPBitstreamFeatures features{};if(WebPGetFeatures(data.data(),data.size(),&features)!=VP8_STATUS_OK)return nullptr;
  if(features.has_animation){error(env,"java/lang/UnsupportedOperationException","Animated WebP decoding");return nullptr;}
  if(features.width<=0||features.height<=0||features.width>8192||features.height>8192||std::uint64_t(features.width)*features.height>16*1024*1024)throw std::invalid_argument("Decoded WebP exceeds bitmap dimensions");
  jint sizes[2]={features.width,features.height};if(bounds){env->SetIntArrayRegion(dimensions,0,2,sizes);return nullptr;}
  std::vector<jint> pixels(static_cast<size_t>(features.width)*features.height);
  if(!WebPDecodeRGBAInto(data.data(),data.size(),reinterpret_cast<unsigned char*>(pixels.data()),pixels.size()*4,features.width*4))return nullptr;
  for(auto& pixel:pixels){const auto* c=reinterpret_cast<const unsigned char*>(&pixel);pixel=static_cast<jint>((std::uint32_t(c[3])<<24)|(std::uint32_t(c[0])<<16)|(std::uint32_t(c[1])<<8)|c[2]);}
  auto output=env->NewIntArray(static_cast<jsize>(pixels.size()));if(!output)return nullptr;env->SetIntArrayRegion(output,0,static_cast<jsize>(pixels.size()),pixels.data());if(env->ExceptionCheck())return nullptr;env->SetIntArrayRegion(dimensions,0,2,sizes);return output;
 }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Cannot allocate WebP image");}catch(const std::invalid_argument& issue){error(env,"java/lang/IllegalArgumentException",issue.what());}catch(const std::exception&){error(env,"java/lang/IllegalStateException","Cannot decode WebP image");}return nullptr;
}

#include <csetjmp>
#include <cstdio>
#include <jpeglib.h>
namespace {
struct JpegFailure { jpeg_error_mgr base; std::jmp_buf jump; };
void jpegFail(j_common_ptr info) { std::longjmp(reinterpret_cast<JpegFailure*>(info->err)->jump,1); }
}
extern "C" JNIEXPORT jintArray JNICALL Java_shiraz_runtime_NativeImage_jpeg(JNIEnv* env,jclass,jbyteArray input,jint offset,jint length,jboolean bounds,jintArray dimensions) {
  if(!input||!dimensions){error(env,"java/lang/NullPointerException","JPEG input/dimensions");return nullptr;}
  if(env->GetArrayLength(dimensions)!=2){error(env,"java/lang/IllegalArgumentException","JPEG dimensions output length");return nullptr;}
  if(offset<0||length<0||std::int64_t(offset)+length>env->GetArrayLength(input)){error(env,"java/lang/ArrayIndexOutOfBoundsException","JPEG byte range");return nullptr;}
  if(length>16*1024*1024){error(env,"java/lang/IllegalArgumentException","JPEG input exceeds 16MiB");return nullptr;}
  std::vector<unsigned char> data(static_cast<size_t>(length));
  env->GetByteArrayRegion(input,offset,length,reinterpret_cast<jbyte*>(data.data()));if(env->ExceptionCheck())return nullptr;
  std::vector<jint> pixels;
  std::vector<unsigned char> row;
  jint sizes[2]={-1,-1};
  bool oversize=false;
  jpeg_decompress_struct info;
  JpegFailure failure;
  info.err=jpeg_std_error(&failure.base);
  failure.base.error_exit=jpegFail;
  failure.base.output_message=[](j_common_ptr){};
  if(setjmp(failure.jump)){jpeg_destroy_decompress(&info);if(oversize)error(env,"java/lang/IllegalArgumentException","Decoded JPEG exceeds bitmap dimensions");return nullptr;}
  jpeg_create_decompress(&info);
  jpeg_mem_src(&info,data.data(),data.size());
  jpeg_read_header(&info,TRUE);
  if(info.image_width==0||info.image_height==0||info.image_width>8192||info.image_height>8192||std::uint64_t(info.image_width)*info.image_height>16*1024*1024){oversize=true;jpeg_destroy_decompress(&info);error(env,"java/lang/IllegalArgumentException","Decoded JPEG exceeds bitmap dimensions");return nullptr;}
  sizes[0]=static_cast<jint>(info.image_width);sizes[1]=static_cast<jint>(info.image_height);
  if(bounds){jpeg_destroy_decompress(&info);env->SetIntArrayRegion(dimensions,0,2,sizes);return nullptr;}
  info.out_color_space=JCS_RGB;
  pixels.assign(static_cast<size_t>(sizes[0])*sizes[1],0);
  row.assign(static_cast<size_t>(sizes[0])*3,0);
  jpeg_start_decompress(&info);
  while(info.output_scanline<info.output_height){
    JSAMPROW line=row.data();
    jpeg_read_scanlines(&info,&line,1);
    jint* out=pixels.data()+static_cast<size_t>(info.output_scanline-1)*sizes[0];
    for(jint x=0;x<sizes[0];x++)out[x]=static_cast<jint>(0xFF000000u|(std::uint32_t(row[x*3])<<16)|(std::uint32_t(row[x*3+1])<<8)|row[x*3+2]);
  }
  jpeg_finish_decompress(&info);
  jpeg_destroy_decompress(&info);
  auto output=env->NewIntArray(static_cast<jsize>(pixels.size()));if(!output)return nullptr;
  env->SetIntArrayRegion(output,0,static_cast<jsize>(pixels.size()),pixels.data());if(env->ExceptionCheck())return nullptr;
  env->SetIntArrayRegion(dimensions,0,2,sizes);return output;
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_shiraz_runtime_NativeImage_jpegEncode(JNIEnv* env,jclass,jintArray pixels,jint width,jint height,jint quality){
  if(!pixels){error(env,"java/lang/NullPointerException","JPEG encode pixels");return nullptr;}
  if(width<=0||height<=0||width>8192||height>8192||std::uint64_t(width)*height>16*1024*1024){error(env,"java/lang/IllegalArgumentException","JPEG encode dimensions");return nullptr;}
  if(quality<0||quality>100){error(env,"java/lang/IllegalArgumentException","JPEG encode quality");return nullptr;}
  if(env->GetArrayLength(pixels)!=width*height){error(env,"java/lang/IllegalArgumentException","JPEG encode pixel count");return nullptr;}
  std::vector<jint> src(static_cast<size_t>(width)*height);
  env->GetIntArrayRegion(pixels,0,width*height,src.data());if(env->ExceptionCheck())return nullptr;
  unsigned char* outBuf=nullptr;unsigned long outSize=0;
  jpeg_compress_struct info;JpegFailure failure;
  info.err=jpeg_std_error(&failure.base);failure.base.error_exit=jpegFail;failure.base.output_message=[](j_common_ptr){};
  if(setjmp(failure.jump)){jpeg_destroy_compress(&info);if(outBuf)std::free(outBuf);error(env,"java/lang/IllegalStateException","JPEG encode failed");return nullptr;}
  jpeg_create_compress(&info);
  jpeg_mem_dest(&info,&outBuf,&outSize);
  info.image_width=static_cast<JDIMENSION>(width);info.image_height=static_cast<JDIMENSION>(height);
  info.input_components=3;info.in_color_space=JCS_RGB;
  jpeg_set_defaults(&info);jpeg_set_quality(&info,quality,TRUE);
  jpeg_start_compress(&info,TRUE);
  std::vector<unsigned char> row(static_cast<size_t>(width)*3);
  while(info.next_scanline<info.image_height){
    const jint* line=src.data()+static_cast<size_t>(info.next_scanline)*width;
    for(jint x=0;x<width;x++){jint c=line[x];row[x*3]=static_cast<unsigned char>(c>>16);row[x*3+1]=static_cast<unsigned char>(c>>8);row[x*3+2]=static_cast<unsigned char>(c);}
    JSAMPROW ptr=row.data();jpeg_write_scanlines(&info,&ptr,1);
  }
  jpeg_finish_compress(&info);jpeg_destroy_compress(&info);
  if(!outBuf){error(env,"java/lang/IllegalStateException","JPEG encode produced no data");return nullptr;}
  jbyteArray result=env->NewByteArray(static_cast<jsize>(outSize));
  if(result)env->SetByteArrayRegion(result,0,static_cast<jsize>(outSize),reinterpret_cast<jbyte*>(outBuf));
  std::free(outBuf);return result;
}

#include <webp/encode.h>
namespace {
struct WebpOutput {
 std::vector<unsigned char> bytes;
 static int write(const uint8_t* data,size_t size,const WebPPicture* picture) noexcept {
  auto& output=*static_cast<WebpOutput*>(picture->custom_ptr);
  if(size>16*1024*1024-output.bytes.size())return 0;
  try{output.bytes.insert(output.bytes.end(),data,data+size);return 1;}catch(...){return 0;}
 }
};
struct WebpPicture {
 WebPPicture value{};
 WebpPicture(){if(!WebPPictureInit(&value))throw std::runtime_error("WebP ABI mismatch");}
 ~WebpPicture(){WebPPictureFree(&value);}
};
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_shiraz_runtime_NativeImage_webpEncode(JNIEnv* env,jclass,jintArray pixels,jint width,jint height,jint quality,jboolean lossless){
 try{
  if(!pixels){error(env,"java/lang/NullPointerException","WebP pixels");return nullptr;}
  if(width<=0||height<=0||width>8192||height>8192||std::uint64_t(width)*height>16*1024*1024)throw std::invalid_argument("WebP encode dimensions");
  if(quality<0||quality>100)throw std::invalid_argument("WebP encode quality");
  if(env->GetArrayLength(pixels)!=width*height)throw std::invalid_argument("WebP encode pixel count");
  std::vector<jint> colors(static_cast<size_t>(width)*height);
  env->GetIntArrayRegion(pixels,0,width*height,colors.data());if(env->ExceptionCheck())return nullptr;
  std::vector<uint8_t> rgba(colors.size()*4);
  for(size_t i=0;i<colors.size();i++){
   uint32_t c=static_cast<uint32_t>(colors[i]);rgba[i*4]=c>>16;rgba[i*4+1]=c>>8;rgba[i*4+2]=c;rgba[i*4+3]=c>>24;
  }
  WebPConfig config;if(!WebPConfigInit(&config))throw std::runtime_error("WebP ABI mismatch");
  config.lossless=lossless?1:0;config.quality=static_cast<float>(quality);config.exact=1;
  if(!WebPValidateConfig(&config))throw std::invalid_argument("WebP encode configuration");
  WebpPicture picture;picture.value.use_argb=1;picture.value.width=width;picture.value.height=height;
  if(!WebPPictureImportRGBA(&picture.value,rgba.data(),width*4))throw std::bad_alloc();
  WebpOutput output;picture.value.writer=WebpOutput::write;picture.value.custom_ptr=&output;
  if(!WebPEncode(&config,&picture.value))return nullptr;
  jbyteArray result=env->NewByteArray(static_cast<jsize>(output.bytes.size()));
  if(result)env->SetByteArrayRegion(result,0,static_cast<jsize>(output.bytes.size()),reinterpret_cast<const jbyte*>(output.bytes.data()));
  return result;
 }catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Cannot allocate WebP encoding");}
 catch(const std::invalid_argument& issue){error(env,"java/lang/IllegalArgumentException",issue.what());}
 catch(const std::exception&){error(env,"java/lang/IllegalStateException","Cannot encode WebP");}
 return nullptr;
}
