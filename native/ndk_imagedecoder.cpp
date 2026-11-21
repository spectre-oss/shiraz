// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/ndk/imagedecoder.h"
#include "shiraz/ndk/asset_manager.h"
#include <algorithm>
#include <cerrno>
#include <csetjmp>
#include <cstdio>
#include <cstring>
#include <jpeglib.h>
#include <new>
#include <png.h>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>
#include <webp/decode.h>

namespace {
constexpr int32_t kFormatRgba8888 = 1;
constexpr int32_t kFormatRgb565 = 4;
constexpr int32_t kFormatAlpha8 = 8;
constexpr int32_t kAlphaOpaque = 0;
constexpr int32_t kAlphaPremul = 1;
constexpr int32_t kDataSpaceSrgb = 142671872;
} // namespace

struct AImageDecoderHeaderInfo {
    int32_t width = 0;
    int32_t height = 0;
    std::string mime_type;
    int32_t format = kFormatRgba8888;
    int32_t alpha_flags = kAlphaPremul;
    int32_t data_space = kDataSpaceSrgb;
};

struct AImageDecoderFrameInfo {
    int64_t duration_ns = 0;
    ARect rect{0, 0, 0, 0};
    bool has_alpha = true;
    int32_t dispose_op = 1;
    int32_t blend_op = 1;
};

struct AImageDecoder {
    AImageDecoderHeaderInfo header;
    std::vector<uint8_t> straight_rgba;
    int32_t output_format = kFormatRgba8888;
    bool unpremultiplied = false;
    int32_t target_width = 0;
    int32_t target_height = 0;
    bool has_crop = false;
    ARect crop{0, 0, 0, 0};
    bool handle_dispose = true;
    int frame_index = 0;
};

namespace {

bool decode_png(const uint8_t* data, size_t len, int32_t& w, int32_t& h, bool& has_alpha,
                std::vector<uint8_t>& out) {
    if (len < 8 || png_sig_cmp(data, 0, 8) != 0)
        return false;
    png_image img{};
    img.version = PNG_IMAGE_VERSION;
    if (!png_image_begin_read_from_memory(&img, data, len))
        return false;
    has_alpha = (img.format & PNG_FORMAT_FLAG_ALPHA) != 0;
    img.format = PNG_FORMAT_RGBA;
    w = static_cast<int32_t>(img.width);
    h = static_cast<int32_t>(img.height);
    if (w <= 0 || h <= 0 || w > 16384 || h > 16384) {
        png_image_free(&img);
        return false;
    }
    out.resize(static_cast<size_t>(w) * static_cast<size_t>(h) * 4);
    if (!png_image_finish_read(&img, nullptr, out.data(), 0, nullptr)) {
        png_image_free(&img);
        return false;
    }
    return true;
}

struct JpegErr {
    jpeg_error_mgr pub;
    jmp_buf env;
};

void jpeg_fail(j_common_ptr cinfo) {
    auto* err = reinterpret_cast<JpegErr*>(cinfo->err);
    longjmp(err->env, 1);
}

bool decode_jpeg(const uint8_t* data, size_t len, int32_t& w, int32_t& h,
                 std::vector<uint8_t>& out) {
    if (len < 3 || data[0] != 0xFF || data[1] != 0xD8)
        return false;
    jpeg_decompress_struct cinfo{};
    JpegErr jerr{};
    cinfo.err = jpeg_std_error(&jerr.pub);
    jerr.pub.error_exit = jpeg_fail;
    if (setjmp(jerr.env)) {
        jpeg_destroy_decompress(&cinfo);
        return false;
    }
    jpeg_create_decompress(&cinfo);
    jpeg_mem_src(&cinfo, data, static_cast<unsigned long>(len));
    if (jpeg_read_header(&cinfo, TRUE) != JPEG_HEADER_OK) {
        jpeg_destroy_decompress(&cinfo);
        return false;
    }
    cinfo.out_color_space = JCS_RGB;
    jpeg_start_decompress(&cinfo);
    w = static_cast<int32_t>(cinfo.output_width);
    h = static_cast<int32_t>(cinfo.output_height);
    if (w <= 0 || h <= 0 || w > 16384 || h > 16384) {
        jpeg_destroy_decompress(&cinfo);
        return false;
    }
    out.resize(static_cast<size_t>(w) * static_cast<size_t>(h) * 4);
    std::vector<uint8_t> row(static_cast<size_t>(w) * 3);
    while (cinfo.output_scanline < cinfo.output_height) {
        uint8_t* ptr = row.data();
        jpeg_read_scanlines(&cinfo, &ptr, 1);
        size_t y = cinfo.output_scanline - 1;
        uint8_t* dst = out.data() + y * static_cast<size_t>(w) * 4;
        for (int32_t x = 0; x < w; ++x) {
            dst[x * 4 + 0] = row[x * 3 + 0];
            dst[x * 4 + 1] = row[x * 3 + 1];
            dst[x * 4 + 2] = row[x * 3 + 2];
            dst[x * 4 + 3] = 255;
        }
    }
    jpeg_finish_decompress(&cinfo);
    jpeg_destroy_decompress(&cinfo);
    return true;
}

bool decode_webp(const uint8_t* data, size_t len, int32_t& w, int32_t& h, bool& has_alpha,
                 std::vector<uint8_t>& out) {
    WebPBitstreamFeatures features{};
    if (WebPGetFeatures(data, len, &features) != VP8_STATUS_OK)
        return false;
    w = features.width;
    h = features.height;
    has_alpha = features.has_alpha != 0;
    if (w <= 0 || h <= 0 || w > 16384 || h > 16384)
        return false;
    out.resize(static_cast<size_t>(w) * static_cast<size_t>(h) * 4);
    return WebPDecodeRGBAInto(data, len, out.data(), out.size(), w * 4) != nullptr;
}

uint16_t pack_rgb565(uint8_t r, uint8_t g, uint8_t b) {
    return static_cast<uint16_t>(((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3));
}

int create_from_bytes(const uint8_t* bytes, size_t len, AImageDecoder** outDecoder) {
    if (!outDecoder)
        return ANDROID_IMAGE_DECODER_BAD_PARAMETER;
    *outDecoder = nullptr;
    if (!bytes || len < 4)
        return ANDROID_IMAGE_DECODER_INVALID_INPUT;
    auto* dec = new (std::nothrow) AImageDecoder();
    if (!dec)
        return ANDROID_IMAGE_DECODER_INTERNAL_ERROR;

    int32_t w = 0, h = 0;
    bool has_alpha = false;
    if (decode_png(bytes, len, w, h, has_alpha, dec->straight_rgba)) {
        dec->header.mime_type = "image/png";
    } else if (decode_jpeg(bytes, len, w, h, dec->straight_rgba)) {
        dec->header.mime_type = "image/jpeg";
        has_alpha = false;
    } else if (decode_webp(bytes, len, w, h, has_alpha, dec->straight_rgba)) {
        dec->header.mime_type = "image/webp";
    } else {
        delete dec;
        return ANDROID_IMAGE_DECODER_UNSUPPORTED_FORMAT;
    }

    dec->header.width = w;
    dec->header.height = h;
    dec->header.format = kFormatRgba8888;
    dec->header.alpha_flags = has_alpha ? kAlphaPremul : kAlphaOpaque;
    dec->header.data_space = kDataSpaceSrgb;
    dec->target_width = w;
    dec->target_height = h;
    *outDecoder = dec;
    return ANDROID_IMAGE_DECODER_SUCCESS;
}

size_t bytes_per_pixel(int32_t format) {
    if (format == kFormatRgb565)
        return 2;
    if (format == kFormatAlpha8)
        return 1;
    return 4;
}

} // namespace

extern "C" {

const char* AImageDecoder_resultToString(int result) {
    switch (result) {
    case ANDROID_IMAGE_DECODER_SUCCESS:
        return "ANDROID_IMAGE_DECODER_SUCCESS";
    case ANDROID_IMAGE_DECODER_INCOMPLETE:
        return "ANDROID_IMAGE_DECODER_INCOMPLETE";
    case ANDROID_IMAGE_DECODER_ERROR:
        return "ANDROID_IMAGE_DECODER_ERROR";
    case ANDROID_IMAGE_DECODER_INVALID_CONVERSION:
        return "ANDROID_IMAGE_DECODER_INVALID_CONVERSION";
    case ANDROID_IMAGE_DECODER_INVALID_SCALE:
        return "ANDROID_IMAGE_DECODER_INVALID_SCALE";
    case ANDROID_IMAGE_DECODER_BAD_PARAMETER:
        return "ANDROID_IMAGE_DECODER_BAD_PARAMETER";
    case ANDROID_IMAGE_DECODER_INVALID_INPUT:
        return "ANDROID_IMAGE_DECODER_INVALID_INPUT";
    case ANDROID_IMAGE_DECODER_SEEK_ERROR:
        return "ANDROID_IMAGE_DECODER_SEEK_ERROR";
    case ANDROID_IMAGE_DECODER_INTERNAL_ERROR:
        return "ANDROID_IMAGE_DECODER_INTERNAL_ERROR";
    case ANDROID_IMAGE_DECODER_UNSUPPORTED_FORMAT:
        return "ANDROID_IMAGE_DECODER_UNSUPPORTED_FORMAT";
    case ANDROID_IMAGE_DECODER_FINISHED:
        return "ANDROID_IMAGE_DECODER_FINISHED";
    case ANDROID_IMAGE_DECODER_INVALID_STATE:
        return "ANDROID_IMAGE_DECODER_INVALID_STATE";
    default:
        return "ANDROID_IMAGE_DECODER_UNKNOWN";
    }
}

int AImageDecoder_createFromBuffer(const void* buffer, size_t length, AImageDecoder** outDecoder) {
    return create_from_bytes(static_cast<const uint8_t*>(buffer), length, outDecoder);
}

int AImageDecoder_createFromAAsset(AAsset* asset, AImageDecoder** outDecoder) {
    if (!asset || !outDecoder)
        return ANDROID_IMAGE_DECODER_BAD_PARAMETER;
    const void* buf = AAsset_getBuffer(asset);
    off_t len = AAsset_getLength(asset);
    if (!buf || len <= 0)
        return ANDROID_IMAGE_DECODER_INVALID_INPUT;
    return create_from_bytes(static_cast<const uint8_t*>(buf), static_cast<size_t>(len),
                             outDecoder);
}

int AImageDecoder_createFromFd(int fd, AImageDecoder** outDecoder) {
    if (fd < 0 || !outDecoder)
        return ANDROID_IMAGE_DECODER_BAD_PARAMETER;
    off_t cur = lseek(fd, 0, SEEK_CUR);
    struct stat st{};
    if (fstat(fd, &st) != 0 || st.st_size <= 0 || st.st_size > 128 * 1024 * 1024)
        return ANDROID_IMAGE_DECODER_INVALID_INPUT;
    size_t start = cur > 0 ? static_cast<size_t>(cur) : 0;
    if (start >= static_cast<size_t>(st.st_size))
        return ANDROID_IMAGE_DECODER_INVALID_INPUT;
    std::vector<uint8_t> data(static_cast<size_t>(st.st_size) - start);
    size_t done = 0;
    while (done < data.size()) {
        ssize_t n = read(fd, data.data() + done, data.size() - done);
        if (n < 0) {
            if (errno == EINTR)
                continue;
            return ANDROID_IMAGE_DECODER_ERROR;
        }
        if (n == 0)
            break;
        done += static_cast<size_t>(n);
    }
    return create_from_bytes(data.data(), done, outDecoder);
}

void AImageDecoder_delete(AImageDecoder* decoder) {
    delete decoder;
}

const AImageDecoderHeaderInfo* AImageDecoder_getHeaderInfo(const AImageDecoder* decoder) {
    return decoder ? &decoder->header : nullptr;
}
int32_t AImageDecoderHeaderInfo_getWidth(const AImageDecoderHeaderInfo* info) {
    return info ? info->width : 0;
}
int32_t AImageDecoderHeaderInfo_getHeight(const AImageDecoderHeaderInfo* info) {
    return info ? info->height : 0;
}
const char* AImageDecoderHeaderInfo_getMimeType(const AImageDecoderHeaderInfo* info) {
    return info ? info->mime_type.c_str() : nullptr;
}
int32_t AImageDecoderHeaderInfo_getAndroidBitmapFormat(const AImageDecoderHeaderInfo* info) {
    return info ? info->format : 0;
}
int32_t AImageDecoderHeaderInfo_getAlphaFlags(const AImageDecoderHeaderInfo* info) {
    return info ? info->alpha_flags : 0;
}
int32_t AImageDecoderHeaderInfo_getDataSpace(const AImageDecoderHeaderInfo* info) {
    return info ? info->data_space : 0;
}

int AImageDecoder_setAndroidBitmapFormat(AImageDecoder* decoder, int32_t format) {
    if (!decoder)
        return ANDROID_IMAGE_DECODER_BAD_PARAMETER;
    if (format != kFormatRgba8888 && format != kFormatRgb565 && format != kFormatAlpha8)
        return ANDROID_IMAGE_DECODER_INVALID_CONVERSION;
    if (format == kFormatRgb565 && decoder->header.alpha_flags != kAlphaOpaque)
        return ANDROID_IMAGE_DECODER_INVALID_CONVERSION;
    decoder->output_format = format;
    return ANDROID_IMAGE_DECODER_SUCCESS;
}

int AImageDecoder_setUnpremultipliedRequired(AImageDecoder* decoder, bool required) {
    if (!decoder)
        return ANDROID_IMAGE_DECODER_BAD_PARAMETER;
    decoder->unpremultiplied = required;
    return ANDROID_IMAGE_DECODER_SUCCESS;
}

int AImageDecoder_setDataSpace(AImageDecoder* decoder, int32_t dataspace) {
    if (!decoder)
        return ANDROID_IMAGE_DECODER_BAD_PARAMETER;
    decoder->header.data_space = dataspace;
    return ANDROID_IMAGE_DECODER_SUCCESS;
}

int AImageDecoder_setTargetSize(AImageDecoder* decoder, int32_t width, int32_t height) {
    if (!decoder || width <= 0 || height <= 0)
        return ANDROID_IMAGE_DECODER_INVALID_SCALE;
    decoder->target_width = width;
    decoder->target_height = height;
    decoder->has_crop = false;
    return ANDROID_IMAGE_DECODER_SUCCESS;
}

int AImageDecoder_computeSampledSize(const AImageDecoder* decoder, int sampleSize, int32_t* width,
                                     int32_t* height) {
    if (!decoder || sampleSize <= 0 || !width || !height)
        return ANDROID_IMAGE_DECODER_BAD_PARAMETER;
    *width = std::max<int32_t>(1, decoder->header.width / sampleSize);
    *height = std::max<int32_t>(1, decoder->header.height / sampleSize);
    return ANDROID_IMAGE_DECODER_SUCCESS;
}

int AImageDecoder_setCrop(AImageDecoder* decoder, ARect crop) {
    if (!decoder)
        return ANDROID_IMAGE_DECODER_BAD_PARAMETER;
    if (crop.left == 0 && crop.top == 0 && crop.right == 0 && crop.bottom == 0) {
        decoder->has_crop = false;
        return ANDROID_IMAGE_DECODER_SUCCESS;
    }
    if (crop.left < 0 || crop.top < 0 || crop.right <= crop.left || crop.bottom <= crop.top ||
        crop.right > decoder->target_width || crop.bottom > decoder->target_height)
        return ANDROID_IMAGE_DECODER_BAD_PARAMETER;
    decoder->crop = crop;
    decoder->has_crop = true;
    return ANDROID_IMAGE_DECODER_SUCCESS;
}

size_t AImageDecoder_getMinimumStride(AImageDecoder* decoder) {
    if (!decoder)
        return 0;
    int32_t out_w =
        decoder->has_crop ? (decoder->crop.right - decoder->crop.left) : decoder->target_width;
    return static_cast<size_t>(out_w) * bytes_per_pixel(decoder->output_format);
}

int AImageDecoder_decodeImage(AImageDecoder* decoder, void* pixels, size_t stride, size_t size) {
    if (!decoder || !pixels)
        return ANDROID_IMAGE_DECODER_BAD_PARAMETER;
    int32_t ox = decoder->has_crop ? decoder->crop.left : 0;
    int32_t oy = decoder->has_crop ? decoder->crop.top : 0;
    int32_t out_w =
        decoder->has_crop ? (decoder->crop.right - decoder->crop.left) : decoder->target_width;
    int32_t out_h =
        decoder->has_crop ? (decoder->crop.bottom - decoder->crop.top) : decoder->target_height;
    size_t min_stride = AImageDecoder_getMinimumStride(decoder);
    if (stride < min_stride || size < stride * static_cast<size_t>(out_h))
        return ANDROID_IMAGE_DECODER_BAD_PARAMETER;

    auto* dst_base = static_cast<uint8_t*>(pixels);
    for (int32_t y = 0; y < out_h; ++y) {
        int32_t sy = static_cast<int32_t>(static_cast<int64_t>(oy + y) * decoder->header.height /
                                          decoder->target_height);
        sy = std::min(sy, decoder->header.height - 1);
        uint8_t* dst_row = dst_base + static_cast<size_t>(y) * stride;
        for (int32_t x = 0; x < out_w; ++x) {
            int32_t sx = static_cast<int32_t>(static_cast<int64_t>(ox + x) * decoder->header.width /
                                              decoder->target_width);
            sx = std::min(sx, decoder->header.width - 1);
            const uint8_t* src =
                decoder->straight_rgba.data() +
                (static_cast<size_t>(sy) * static_cast<size_t>(decoder->header.width) +
                 static_cast<size_t>(sx)) *
                    4;
            uint8_t r = src[0], g = src[1], b = src[2], a = src[3];
            if (decoder->output_format == kFormatAlpha8) {
                dst_row[x] = a;
            } else if (decoder->output_format == kFormatRgb565) {
                uint16_t v = pack_rgb565(r, g, b);
                std::memcpy(dst_row + x * 2, &v, 2);
            } else {
                if (!decoder->unpremultiplied && a < 255) {
                    r = static_cast<uint8_t>((r * a + 127) / 255);
                    g = static_cast<uint8_t>((g * a + 127) / 255);
                    b = static_cast<uint8_t>((b * a + 127) / 255);
                }
                dst_row[x * 4 + 0] = r;
                dst_row[x * 4 + 1] = g;
                dst_row[x * 4 + 2] = b;
                dst_row[x * 4 + 3] = a;
            }
        }
    }
    return ANDROID_IMAGE_DECODER_SUCCESS;
}

bool AImageDecoder_isAnimated(AImageDecoder*) {
    return false;
}
int32_t AImageDecoder_getRepeatCount(AImageDecoder*) {
    return 0;
}
int AImageDecoder_advanceFrame(AImageDecoder* decoder) {
    if (!decoder)
        return ANDROID_IMAGE_DECODER_BAD_PARAMETER;
    return ANDROID_IMAGE_DECODER_FINISHED;
}
int AImageDecoder_rewind(AImageDecoder* decoder) {
    if (!decoder)
        return ANDROID_IMAGE_DECODER_BAD_PARAMETER;
    decoder->frame_index = 0;
    return ANDROID_IMAGE_DECODER_SUCCESS;
}
int AImageDecoder_getFrameInfo(AImageDecoder* decoder, AImageDecoderFrameInfo* info) {
    if (!decoder || !info)
        return ANDROID_IMAGE_DECODER_BAD_PARAMETER;
    info->duration_ns = 0;
    info->rect = {0, 0, decoder->header.width, decoder->header.height};
    info->has_alpha = decoder->header.alpha_flags != kAlphaOpaque;
    return ANDROID_IMAGE_DECODER_SUCCESS;
}
void AImageDecoder_setInternallyHandleDisposePrevious(AImageDecoder* decoder, bool handle) {
    if (decoder)
        decoder->handle_dispose = handle;
}

AImageDecoderFrameInfo* AImageDecoderFrameInfo_create() {
    return new (std::nothrow) AImageDecoderFrameInfo();
}
void AImageDecoderFrameInfo_delete(AImageDecoderFrameInfo* info) {
    delete info;
}
int64_t AImageDecoderFrameInfo_getDuration(const AImageDecoderFrameInfo* info) {
    return info ? info->duration_ns : 0;
}
ARect AImageDecoderFrameInfo_getFrameRect(const AImageDecoderFrameInfo* info) {
    return info ? info->rect : ARect{0, 0, 0, 0};
}
bool AImageDecoderFrameInfo_hasAlphaWithinBounds(const AImageDecoderFrameInfo* info) {
    return info ? info->has_alpha : false;
}
int32_t AImageDecoderFrameInfo_getDisposeOp(const AImageDecoderFrameInfo* info) {
    return info ? info->dispose_op : 0;
}
int32_t AImageDecoderFrameInfo_getBlendOp(const AImageDecoderFrameInfo* info) {
    return info ? info->blend_op : 0;
}

} // extern "C"
