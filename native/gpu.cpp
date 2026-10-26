// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/gpu.hpp"
#include "include/core/SkCanvas.h"
#include "include/core/SkColorFilter.h"
#include "include/core/SkImage.h"
#include "include/core/SkPath.h"
#include "include/pathops/SkPathOps.h"
#include "include/core/SkShader.h"
#include "include/core/SkSurface.h"
#include "include/effects/SkDashPathEffect.h"
#include "include/effects/SkGradientShader.h"
#include "include/effects/SkImageFilters.h"
#include "include/gpu/ganesh/GrBackendSurface.h"
#include "include/gpu/ganesh/GrDirectContext.h"
#include "include/gpu/ganesh/SkSurfaceGanesh.h"
#include "include/gpu/ganesh/gl/GrGLAssembleInterface.h"
#include "include/gpu/ganesh/gl/GrGLBackendSurface.h"
#include "include/gpu/ganesh/gl/GrGLDirectContext.h"
#include "include/utils/SkShadowUtils.h"
#include "shiraz/compositor.hpp"
#include <array>
#include <atomic>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <jni.h>
#include <list>
#include <mutex>
#include <stdexcept>
#include <unordered_map>
#include <vector>
namespace {
struct Surface {
  sk_sp<SkSurface> target;
  SkPaint paint;
  size_t bytes;
  bool simplePaintValid = false;
  std::array<jint, 12> paintValues{};
  std::array<float, 6> paintScalars{};
  jweak backing = nullptr;
  bool written = false;
  uint64_t used = 0;
};
struct Engine {
  sk_sp<GrDirectContext> context;
  sk_sp<SkSurface> presentation;
  std::unordered_map<jlong, Surface> surfaces;
  jlong displayed = 0;
  uint64_t revision = 0;
  size_t bytes = 0;
  uint64_t serial = 0;
};
thread_local Engine engine;
thread_local JNIEnv *ownerEnv = nullptr;
std::atomic<jlong> sequence{1};
std::mutex releaseMutex;
std::vector<jlong> releases;
void collect() {
  std::lock_guard<std::mutex> lock(releaseMutex);
  for (jlong h : releases) {
    auto p = engine.surfaces.find(h);
    if (p != engine.surfaces.end()) {
      engine.bytes -= p->second.bytes;
      if (p->second.backing && ownerEnv)
        ownerEnv->DeleteWeakGlobalRef(p->second.backing);
      engine.surfaces.erase(p);
      if (engine.displayed == h)
        engine.displayed = 0;
    }
  }
  releases.clear();
}

Surface &surface(jlong h) {
  auto p = engine.surfaces.find(h);
  if (p == engine.surfaces.end())
    throw std::runtime_error(
        "GPU surface is stale or used from another thread");
  p->second.used = ++engine.serial;
  return p->second;
}
void fail(JNIEnv *e, const char *message) {
  if (!e->ExceptionCheck())
    e->ThrowNew(e->FindClass("java/lang/IllegalStateException"), message);
}
// Fixed-size draw arguments stay on the stack; JNI copies do not pin the heap.
template <size_t N> std::array<float, N> fixedFloats(JNIEnv *e, jfloatArray a) {
  if (!a || e->GetArrayLength(a) != N)
    throw std::invalid_argument("GPU float array size");
  std::array<float, N> v;
  e->GetFloatArrayRegion(a, 0, N, v.data());
  for (float x : v)
    if (!std::isfinite(x))
      throw std::invalid_argument("Non-finite GPU coordinate");
  return v;
}
template <size_t N> std::array<jint, N> fixedInts(JNIEnv *e, jintArray a) {
  if (!a || e->GetArrayLength(a) != N)
    throw std::invalid_argument("GPU int array size");
  std::array<jint, N> v;
  e->GetIntArrayRegion(a, 0, N, v.data());
  return v;
}
std::vector<float> floats(JNIEnv *e, jfloatArray a, int expected = -1) {
  if (!a) {
    if (expected > 0)
      throw std::invalid_argument("Missing GPU float array");
    return {};
  }
  int n = e->GetArrayLength(a);
  if (n > 1048576 || (expected >= 0 && n != expected))
    throw std::invalid_argument("GPU float array size");
  std::vector<float> v(n);
  e->GetFloatArrayRegion(a, 0, n, v.data());
  for (float x : v)
    if (!std::isfinite(x))
      throw std::invalid_argument("Non-finite GPU coordinate");
  return v;
}
std::vector<jint> ints(JNIEnv *e, jintArray a, int expected = -1) {
  if (!a) {
    if (expected > 0)
      throw std::invalid_argument("Missing GPU int array");
    return {};
  }
  int n = e->GetArrayLength(a);
  if (n > 32 * 1024 * 1024 || (expected >= 0 && n != expected))
    throw std::invalid_argument("GPU int array size");
  std::vector<jint> v(n);
  e->GetIntArrayRegion(a, 0, n, v.data());
  return v;
}
template <typename Values> SkMatrix matrix(const Values &v) {
  if (v.size() != 9)
    throw std::invalid_argument("GPU matrix size");
  SkMatrix m;
  m.set9(v.data());
  return m;
}
SkBlendMode blend(int mode) {
  static const SkBlendMode modes[] = {
      SkBlendMode::kClear,   SkBlendMode::kSrc,     SkBlendMode::kDst,
      SkBlendMode::kSrcOver, SkBlendMode::kDstOver, SkBlendMode::kSrcIn,
      SkBlendMode::kDstIn,   SkBlendMode::kSrcOut,  SkBlendMode::kDstOut,
      SkBlendMode::kSrcATop, SkBlendMode::kDstATop, SkBlendMode::kXor,
      SkBlendMode::kDarken,  SkBlendMode::kLighten, SkBlendMode::kModulate,
      SkBlendMode::kScreen,  SkBlendMode::kPlus,    SkBlendMode::kOverlay};
  if (mode < 0 || mode >= 18)
    throw std::invalid_argument("GPU blend mode");
  return modes[mode];
}
struct CachedPath {
  uint64_t key;
  int fill;
  std::vector<float> commands;
  SkPath value;
};
thread_local std::list<CachedPath> paths;
thread_local std::unordered_map<uint64_t, std::list<CachedPath>::iterator>
    pathIndex;
thread_local size_t pathBytes = 0;
SkPath path(const std::vector<float> &commands, int fill) {
  uint64_t hash = 1469598103934665603ull;
  for (float v : commands) {
    uint32_t bits;
    std::memcpy(&bits, &v, 4);
    hash = (hash ^ bits) * 1099511628211ull;
  }
  hash = (hash ^ uint32_t(fill)) * 1099511628211ull;
  auto found = pathIndex.find(hash);
  if (found != pathIndex.end()) {
    auto it = found->second;
    if (it->fill == fill && it->commands == commands) {
      paths.splice(paths.begin(), paths, it);
      return it->value;
    }
    pathBytes -= it->commands.size() * 4;
    paths.erase(it);
    pathIndex.erase(found);
  }
  if (fill < 0 || fill > 3)
    throw std::invalid_argument("GPU fill type");
  SkPath p;
  p.setFillType(static_cast<SkPathFillType>(fill));
  for (size_t i = 0; i < commands.size();) {
    int op = static_cast<int>(commands[i++]);
    size_t n = op == 3 ? 6 : op == 4 ? 0 : 2;
    if (op < 1 || op > 4 || i + n > commands.size())
      throw std::invalid_argument("GPU path encoding");
    if (op == 1)
      p.moveTo(commands[i], commands[i + 1]);
    if (op == 2)
      p.lineTo(commands[i], commands[i + 1]);
    if (op == 3)
      p.cubicTo(commands[i], commands[i + 1], commands[i + 2], commands[i + 3],
                commands[i + 4], commands[i + 5]);
    if (op == 4)
      p.close();
    i += n;
  }
  if (commands.size() * 4 <= 1024 * 1024) {
    paths.push_front(CachedPath{hash, fill, commands, p});
    pathIndex[hash] = paths.begin();
    pathBytes += commands.size() * 4;
    while (paths.size() > 1024 || pathBytes > 8 * 1024 * 1024) {
      auto &last = paths.back();
      pathBytes -= last.commands.size() * 4;
      pathIndex.erase(last.key);
      paths.pop_back();
    }
  }
  return p;
}
jlong allocate(int w, int h) {
  collect();
  if (!engine.context || w <= 0 || h <= 0 || w > 8192 || h > 8192 ||
      int64_t(w) * h > 32 * 1024 * 1024)
    throw std::invalid_argument("GPU surface dimensions/context");
  size_t bytes = size_t(w) * h * 4;
  // CPU-backed images can be uploaded again. Never evict authoritative GPU
  // render targets or the surface currently being presented.
  while (engine.bytes + bytes > 256u * 1024 * 1024 ||
         engine.surfaces.size() >= 4096) {
    auto oldest = engine.surfaces.end();
    for (auto it = engine.surfaces.begin(); it != engine.surfaces.end(); ++it)
      if (!it->second.written && it->first != engine.displayed &&
          (oldest == engine.surfaces.end() ||
           it->second.used < oldest->second.used))
        oldest = it;
    if (oldest == engine.surfaces.end())
      throw std::runtime_error("GPU render-target budget exceeded");
    engine.bytes -= oldest->second.bytes;
    if (oldest->second.backing && ownerEnv)
      ownerEnv->DeleteWeakGlobalRef(oldest->second.backing);
    engine.surfaces.erase(oldest);
  }
  auto target = SkSurfaces::RenderTarget(
      engine.context.get(), skgpu::Budgeted::kYes,
      SkImageInfo::Make(w, h, kRGBA_8888_SkColorType, kPremul_SkAlphaType), 0,
      nullptr);
  if (!target)
    throw std::runtime_error("Cannot allocate GPU surface");
  target->getCanvas()->clear(SK_ColorTRANSPARENT);
  jlong id = sequence++;
  engine.surfaces.emplace(id, Surface{std::move(target), SkPaint{}, bytes});
  engine.bytes += bytes;
  return id;
}
} // namespace
namespace shiraz {
bool gpu_initialize(void *(*getProc)(const char *)) {
  const char *mode = std::getenv("SHIRAZ_CANVAS");
  if (mode && std::string(mode) != "auto" && std::string(mode) != "gpu" &&
      std::string(mode) != "software")
    throw std::invalid_argument("SHIRAZ_CANVAS must be auto, gpu, or software");
  if (mode && std::string(mode) == "software")
    return false;
  auto interface =
      GrGLMakeAssembledInterface(&getProc, [](void *ctx, const char *name) {
        return reinterpret_cast<GrGLFuncPtr>(
            (*static_cast<void *(**)(const char *)>(ctx))(name));
      });
  engine.context = GrDirectContexts::MakeGL(interface);
  if (!engine.context) {
    std::cerr << "SHIRAZ_CANVAS_FALLBACK reason=skia-context\n";
    return false;
  }
  // Probe the exact target format before advertising a hardware Canvas.
  auto probe = SkSurfaces::RenderTarget(
      engine.context.get(), skgpu::Budgeted::kYes,
      SkImageInfo::Make(8, 8, kRGBA_8888_SkColorType, kPremul_SkAlphaType));
  if (!probe) {
    engine.context.reset();
    std::cerr << "SHIRAZ_CANVAS_FALLBACK reason=render-target\n";
    return false;
  }
  engine.context->setResourceCacheLimit(128u * 1024 * 1024);
  std::cerr << "SHIRAZ_CANVAS backend=skia-ganesh gpu=1 antialias=coverage\n";
  return true;
}
void gpu_shutdown() {
  collect();
  jthrowable pending = ownerEnv ? ownerEnv->ExceptionOccurred() : nullptr;
  if (pending)
    ownerEnv->ExceptionClear();
  for (auto &entry : engine.surfaces) {
    auto &s = entry.second;
    if (s.backing && ownerEnv) {
      auto array = static_cast<jintArray>(ownerEnv->NewLocalRef(s.backing));
      if (array && s.written) {
        int w = s.target->width(), h = s.target->height();
        std::vector<jint> data(w * h);
        if (s.target->readPixels(SkImageInfo::Make(w, h, kBGRA_8888_SkColorType,
                                                   kUnpremul_SkAlphaType),
                                 data.data(), w * 4, 0, 0))
          ownerEnv->SetIntArrayRegion(array, 0, w * h, data.data());
      }
      if (array)
        ownerEnv->DeleteLocalRef(array);
      ownerEnv->DeleteWeakGlobalRef(s.backing);
    }
  }
  engine.surfaces.clear();
  engine.bytes = 0;
  engine.displayed = 0;
  engine.presentation.reset();
  engine.context.reset();
  if (pending) {
    ownerEnv->Throw(pending);
    ownerEnv->DeleteLocalRef(pending);
  }
}
void gpu_prepare() {
  if (engine.context)
    engine.context->resetContext();
}
uint64_t gpu_revision() { return engine.revision; }
void gpu_software_frame() {
  engine.displayed = 0;
  ++engine.revision;
}
bool gpu_present(int w, int h, const uint32_t *pixels, int sw, int sh) {
  if (!engine.context)
    return false;
  engine.context->resetContext();
  sk_sp<SkImage> image;
  if (engine.displayed)
    image = surface(engine.displayed).target->makeImageSnapshot();
  else
    image = SkImages::RasterFromPixmapCopy(
        SkPixmap(SkImageInfo::Make(sw, sh, kBGRA_8888_SkColorType,
                                   kUnpremul_SkAlphaType),
                 pixels, sw * 4));
  // The default framebuffer keeps its identity across swaps. Rewrap only on
  // resize; rebuilding a SkSurface every frame discards reusable GPU state.
  if (!engine.presentation || engine.presentation->width() != w ||
      engine.presentation->height() != h) {
    GrGLFramebufferInfo info{0, 0x8058};
    auto target = GrBackendRenderTargets::MakeGL(w, h, 0, 0, info);
    engine.presentation = SkSurfaces::WrapBackendRenderTarget(
        engine.context.get(), target, kBottomLeft_GrSurfaceOrigin,
        kRGBA_8888_SkColorType, nullptr, nullptr);
    if (!engine.presentation)
      throw std::runtime_error("Cannot wrap GPU window framebuffer");
  }
  auto &output = engine.presentation;
  output->getCanvas()->clear(SK_ColorBLACK);
  auto viewport = fit(image->width(), image->height(), w, h);
  output->getCanvas()->drawImageRect(
      image,
      SkRect::MakeXYWH(int(viewport.x), int(viewport.y), int(viewport.width),
                       int(viewport.height)),
      SkSamplingOptions(SkFilterMode::kLinear));
  engine.context->flushAndSubmit();
  return true;
}
} // namespace shiraz
#define JNI(name) extern "C" JNIEXPORT
#define TRY try
#define CATCH                                                                  \
  catch (const std::exception &ex) {                                           \
    fail(e, ex.what());                                                        \
  }
JNI(available)
jboolean JNICALL Java_shiraz_runtime_NativeGpu_available(JNIEnv *, jclass) {
  return engine.context != nullptr;
}
JNI(create)
jlong JNICALL Java_shiraz_runtime_NativeGpu_create(JNIEnv *e, jclass, jint w,
                                                   jint h, jintArray pixels) {
  TRY {
    if (w <= 0 || h <= 0 || w > 8192 || h > 8192 ||
        int64_t(w) * h > 32 * 1024 * 1024)
      throw std::invalid_argument("GPU dimensions");
    ownerEnv = e;
    auto data = ints(e, pixels, pixels ? w * h : 0);
    auto id = allocate(w, h);
    surface(id).backing = pixels ? e->NewWeakGlobalRef(pixels) : nullptr;
    if (pixels) {
      auto info = SkImageInfo::Make(w, h, kBGRA_8888_SkColorType,
                                    kUnpremul_SkAlphaType);
      surface(id).target->writePixels(SkPixmap(info, data.data(), w * 4), 0, 0);
    }
    return id;
  }
  CATCH return 0;
}
JNI(release)
void JNICALL Java_shiraz_runtime_NativeGpu_release(JNIEnv *, jclass, jlong h) {
  std::lock_guard<std::mutex> lock(releaseMutex);
  releases.push_back(h);
}
JNI(begin)
void JNICALL Java_shiraz_runtime_NativeGpu_begin(JNIEnv *e, jclass, jlong h){
    TRY{surface(h).written = true;
auto c = surface(h).target->getCanvas();
c->restoreToCount(1);
c->resetMatrix();
c->save();
}
CATCH
}
JNI(read)
void JNICALL Java_shiraz_runtime_NativeGpu_read(JNIEnv *e, jclass, jlong h,
                                                jintArray array){
    TRY{auto s = surface(h).target;
int w = s->width(), height = s->height();
if (!array || e->GetArrayLength(array) != w * height)
  throw std::invalid_argument("GPU read size");
std::vector<jint> data(w *height);
if (!s->readPixels(SkImageInfo::Make(w, height, kBGRA_8888_SkColorType,
                                     kUnpremul_SkAlphaType),
                   data.data(), w * 4, 0, 0))
  throw std::runtime_error("GPU read failed");
e->SetIntArrayRegion(array, 0, w *height, data.data());
}
CATCH
}
JNI(clear)
void JNICALL Java_shiraz_runtime_NativeGpu_clear(JNIEnv *e, jclass, jlong h,
                                                 jint color){
    TRY{surface(h).written = true;
auto c = surface(h).target->getCanvas();
c->restoreToCount(1);
c->resetMatrix();
c->clear(color);
}
CATCH
}
JNI(copyInto)
void JNICALL Java_shiraz_runtime_NativeGpu_copyInto(JNIEnv *e, jclass,
                                                    jlong src, jlong dst){
    TRY{surface(dst).written = true;
auto image = surface(src).target->makeImageSnapshot();
auto c = surface(dst).target->getCanvas();
c->restoreToCount(1);
c->save();
c->resetMatrix();
SkPaint p;
p.setBlendMode(SkBlendMode::kSrc);
c->drawImage(image, 0, 0, SkSamplingOptions(), &p);
c->restore();
}
CATCH
}
JNI(post)
void JNICALL Java_shiraz_runtime_NativeGpu_post(JNIEnv *e, jclass,
                                                jlong h){TRY{surface(h);
engine.displayed = h;
++engine.revision;
}
CATCH
}
JNI(save)
void JNICALL Java_shiraz_runtime_NativeGpu_save(JNIEnv *e, jclass, jlong h){
    TRY{surface(h).target -> getCanvas()->save();
}
CATCH
}
JNI(restore)
void JNICALL Java_shiraz_runtime_NativeGpu_restore(JNIEnv *e, jclass, jlong h){
    TRY{surface(h).target -> getCanvas()->restore();
}
CATCH
}
JNI(matrix)
void JNICALL Java_shiraz_runtime_NativeGpu_matrix(JNIEnv *e, jclass, jlong h,
                                                  jfloatArray m){TRY{
    auto canvas = surface(h).target->getCanvas();
    auto next = matrix(fixedFloats<9>(e, m));
    if (canvas->getTotalMatrix() != next) canvas->setMatrix(next);
}
CATCH
}
JNI(layer)
void JNICALL Java_shiraz_runtime_NativeGpu_layer(JNIEnv *e, jclass, jlong h,
                                                 jfloatArray b){
    TRY{auto &s = surface(h);
auto v = floats(e, b, b ? 4 : 0);
SkRect r = b ? SkRect::MakeLTRB(v[0], v[1], v[2], v[3]) : SkRect::MakeEmpty();
s.target->getCanvas()->saveLayer(b ? &r : nullptr, &s.paint);
}
CATCH
}
JNI(pathOp)
jfloatArray JNICALL Java_shiraz_runtime_NativeGpu_pathOp(JNIEnv *e, jclass,
    jfloatArray first, jint firstFill, jfloatArray second, jint secondFill, jint operation) {
  TRY {
    if(operation<0 || operation>4) throw std::invalid_argument("Path operation");
    auto a=floats(e,first), b=floats(e,second);
    if(a.size()>65536 || b.size()>65536) throw std::invalid_argument("Path operation input limit");
    SkPath out;
    if(!Op(path(a,firstFill),path(b,secondFill),static_cast<SkPathOp>(operation),&out)) return nullptr;
    std::vector<float> result{static_cast<float>(out.getFillType())};
    SkPath::Iter it(out,false); SkPoint p[4];
    for(auto verb=it.next(p);verb!=SkPath::kDone_Verb;verb=it.next(p)) {
      switch(verb) {
        case SkPath::kMove_Verb: result.insert(result.end(),{1,p[0].x(),p[0].y()}); break;
        case SkPath::kLine_Verb: result.insert(result.end(),{2,p[1].x(),p[1].y()}); break;
        case SkPath::kQuad_Verb:
          result.insert(result.end(),{3,p[0].x()+2.f/3*(p[1].x()-p[0].x()),p[0].y()+2.f/3*(p[1].y()-p[0].y()),
            p[2].x()+2.f/3*(p[1].x()-p[2].x()),p[2].y()+2.f/3*(p[1].y()-p[2].y()),p[2].x(),p[2].y()}); break;
        case SkPath::kCubic_Verb: result.insert(result.end(),{3,p[1].x(),p[1].y(),p[2].x(),p[2].y(),p[3].x(),p[3].y()}); break;
        case SkPath::kClose_Verb: result.push_back(4); break;
        default: return nullptr; // Input has no conics; never approximate an unsupported result.
      }
      if(result.size()>65537) return nullptr;
    }
    auto value=e->NewFloatArray(result.size());
    if(value) e->SetFloatArrayRegion(value,0,result.size(),result.data());
    return value;
  } CATCH return nullptr;
}
JNI(shape)
void JNICALL Java_shiraz_runtime_NativeGpu_shape(JNIEnv *e, jclass, jlong h,
                                                 jint kind, jfloat l, jfloat t,
                                                 jfloat r, jfloat b, jfloat rx,
                                                 jfloat ry){
    TRY{for (float v : {l, t, r, b, rx, ry}){if (!std::isfinite(v)) return;
if (std::abs(v) > 10000000)
  throw std::invalid_argument("Path coordinate range");
}
if (l >= r || t >= b || (kind == 1 && (rx < 0 || ry < 0)))
  return;
auto &s = surface(h);
auto c = s.target->getCanvas();
auto rect = SkRect::MakeLTRB(l, t, r, b);
if (kind == 0)
  c->drawRect(rect, s.paint);
else if (kind == 1)
  c->drawRoundRect(rect, std::max(0.f, rx), std::max(0.f, ry), s.paint);
else if (kind == 2)
  c->drawOval(rect, s.paint);
else
  throw std::invalid_argument("GPU shape kind");
}
CATCH
}
JNI(clipRect)
jboolean JNICALL Java_shiraz_runtime_NativeGpu_clipRect(JNIEnv *e, jclass,
                                                        jlong h, jfloat l,
                                                        jfloat t, jfloat r,
                                                        jfloat b,
                                                        jboolean difference) {
  TRY {
    // Compose uses Float.MAX_VALUE to leave one axis unconstrained. Skia bounds
    // clipping to the device and ignores non-finite rectangles itself.
    auto c = surface(h).target->getCanvas();
    SkRect rect=SkRect::MakeLTRB(l,t,r,b);
    if(rect.isFinite()&&!(l<r&&t<b))rect=SkRect::MakeEmpty();
    c->clipRect(
        rect,
        difference ? SkClipOp::kDifference : SkClipOp::kIntersect, true);
    return !c->isClipEmpty();
  }
  CATCH return false;
}
JNI(path)
void JNICALL Java_shiraz_runtime_NativeGpu_path(JNIEnv *e, jclass, jlong h,
                                                jfloatArray a, jint fill){
    TRY{auto &s = surface(h);
s.target->getCanvas()->drawPath(path(floats(e, a), fill), s.paint);
}
CATCH
}
JNI(clip)
void JNICALL Java_shiraz_runtime_NativeGpu_clip(JNIEnv *e, jclass, jlong h,
                                                jfloatArray a, jint fill,
                                                jboolean difference){
    TRY{surface(h).target -> getCanvas()->clipPath(
        path(floats(e, a), fill),
        difference ? SkClipOp::kDifference : SkClipOp::kIntersect, true);
}
CATCH
}
JNI(clipBounds)
jintArray JNICALL Java_shiraz_runtime_NativeGpu_clipBounds(JNIEnv *e, jclass,
                                                           jlong h) {
  TRY {
    SkRect r;
    surface(h).target->getCanvas()->getLocalClipBounds(&r);
    SkIRect q = r.roundOut();
    jint values[] = {q.left(), q.top(), q.right(), q.bottom()};
    auto result = e->NewIntArray(4);
    if (result)
      e->SetIntArrayRegion(result, 0, 4, values);
    return result;
  }
  CATCH return nullptr;
}
JNI(image)
void JNICALL Java_shiraz_runtime_NativeGpu_image(
    JNIEnv *e, jclass, jlong h, jlong src, jfloatArray from, jfloatArray to,
    jboolean filter){TRY{auto &s = surface(h);
auto a = fixedFloats<4>(e, from), b = fixedFloats<4>(e, to);
s.target->getCanvas()->drawImageRect(
    surface(src).target->makeImageSnapshot(),
    SkRect::MakeLTRB(a[0], a[1], a[2], a[3]),
    SkRect::MakeLTRB(b[0], b[1], b[2], b[3]),
    SkSamplingOptions(filter ? SkFilterMode::kLinear : SkFilterMode::kNearest),
    &s.paint, SkCanvas::kStrict_SrcRectConstraint);
}
CATCH
}
JNI(color)
void JNICALL Java_shiraz_runtime_NativeGpu_color(JNIEnv *e, jclass, jlong h,
                                                 jint c){
    TRY{surface(h).target -> getCanvas()->drawColor(c);
}
CATCH
}
JNI(paint)
void JNICALL Java_shiraz_runtime_NativeGpu_paint(
    JNIEnv *e, jclass, jlong h, jfloatArray transform, jintArray iv, jfloatArray fv, jfloatArray cm,
    jlong image, jfloatArray sm, jintArray colors, jfloatArray positions,
    jfloatArray points, jfloatArray dash){TRY{auto i = fixedInts<12>(e, iv);
auto f = fixedFloats<6>(e, fv);
auto &state = surface(h);
auto canvas = state.target->getCanvas();
auto next = matrix(fixedFloats<9>(e, transform));
if (canvas->getTotalMatrix() != next) canvas->setMatrix(next);
// Mutable image/gradient/dash data must be rebuilt. Value-only paints can
// reuse the native state even across different Java Paint/Canvas instances.
bool simple = !cm && !image && !colors && !dash;
if (simple && state.simplePaintValid && state.paintValues == i && state.paintScalars == f)
  return;
SkPaint p;
p.setColor(i[0]);
if (i[1] < 0 || i[1] > 2 || i[2] < 0 || i[2] > 2 || i[3] < 0 || i[3] > 2)
  throw std::invalid_argument("GPU paint enum");
p.setStyle(static_cast<SkPaint::Style>(i[1]));
p.setStrokeCap(static_cast<SkPaint::Cap>(i[2]));
p.setStrokeJoin(static_cast<SkPaint::Join>(i[3]));
p.setAntiAlias(i[4]);
p.setBlendMode(blend(i[5]));
p.setStrokeWidth(f[0]);
p.setStrokeMiter(f[1]);
if (i[7] >= 0)
  p.setColorFilter(SkColorFilters::Blend(i[6], blend(i[7])));
else if (i[7] == -2) {
  auto v = floats(e, cm, 20);
  for (int j : {4, 9, 14, 19})
    v[j] /= 255;
  p.setColorFilter(SkColorFilters::Matrix(v.data()));
}
if (dash) {
  auto v = floats(e, dash);
  p.setPathEffect(
      SkDashPathEffect::Make(SkSpan<const float>(v.data(), v.size()), f[2]));
}
if (image || colors) {
  auto m = matrix(fixedFloats<9>(e, sm));
  if (i[8] < 0 || i[8] > 2 || i[9] < 0 || i[9] > 2)
    throw std::invalid_argument("GPU tile mode");
  if (image)
    p.setShader(surface(image).target->makeImageSnapshot()->makeShader(
        static_cast<SkTileMode>(i[8]), static_cast<SkTileMode>(i[9]),
        SkSamplingOptions(i[10] ? SkFilterMode::kLinear
                                : SkFilterMode::kNearest),
        &m));
  else {
    auto c = ints(e, colors);
    auto stops = floats(e, positions, c.size());
    auto v = floats(e, points);
    if(v.size()==3){
      if(v[2]<=0)throw std::invalid_argument("GPU gradient radius");
      p.setShader(SkGradientShader::MakeRadial({v[0],v[1]},v[2],
          reinterpret_cast<SkColor *>(c.data()),stops.data(),c.size(),
          static_cast<SkTileMode>(i[8]),SkGradientShader::kInterpolateColorsInPremul_Flag,&m));
    }else if(v.size()==4){
      SkPoint pts[] = {{v[0], v[1]}, {v[2], v[3]}};
      p.setShader(SkGradientShader::MakeLinear(
          pts, reinterpret_cast<SkColor *>(c.data()), stops.data(), c.size(),
          static_cast<SkTileMode>(i[8]), SkGradientShader::kInterpolateColorsInPremul_Flag, &m));
    }else throw std::invalid_argument("GPU gradient geometry");
  }
}
if (f[3] > 0)
  p.setImageFilter(SkImageFilters::DropShadow(f[4], f[5], f[3] * .57735f + .5f,
                                              f[3] * .57735f + .5f, i[11],
                                              nullptr));
state.paint = p;
state.simplePaintValid = simple;
if (simple) { state.paintValues = i; state.paintScalars = f; }
}
CATCH
}

JNI(shadow)
void JNICALL Java_shiraz_runtime_NativeGpu_shadow(JNIEnv *e, jclass, jlong h,
                                                  jfloatArray commands,
                                                  jfloat z, jfloat opacity){
    TRY{auto p = path(floats(e, commands), 0);
SkShadowUtils::DrawShadow(surface(h).target->getCanvas(), p, {0, 0, z},
                          {0, -450, 600}, 800,
                          SkColorSetARGB(int(255 * .16f * opacity), 0, 0, 0),
                          SkColorSetARGB(int(255 * .24f * opacity), 0, 0, 0),
                          0);
}
CATCH
}

JNI(valid)
jboolean JNICALL Java_shiraz_runtime_NativeGpu_valid(JNIEnv *, jclass,
                                                     jlong h) {
  return engine.surfaces.count(h) != 0;
}
#include "include/effects/SkRuntimeEffect.h"
JNI(ripple)
void JNICALL Java_shiraz_runtime_NativeGpu_ripple(JNIEnv *e, jclass, jlong h,
                                                  jfloatArray geometry,
                                                  jint color, jint sparkle) {
  TRY {
    auto v = floats(e, geometry, 12);
    static const sk_sp<SkRuntimeEffect> effect = []() {
      auto result = SkRuntimeEffect::MakeForShader(SkString(
#include "ripple_gpu.inc"
          ));
      if (!result.effect)
        throw std::runtime_error(result.errorText.c_str());
      return result.effect;
    }();
    SkRuntimeShaderBuilder builder(effect);
    float center[] = {v[0], v[1]}, resolution[] = {v[5], v[6]},
          bound[] = {v[7], v[8], v[9]};
    builder.uniform("center").set(center, 2);
    builder.uniform("radius") = v[2];
    builder.uniform("progress") = v[3];
    builder.uniform("phase") = v[4];
    builder.uniform("resolution").set(resolution, 2);
    builder.uniform("bound").set(bound, 3);
    builder.uniform("color") = SkColor4f::FromColor(color);
    builder.uniform("sparkle") = SkColor4f::FromColor(sparkle);
    float oscillation[4];
    for (int i = 0; i < 4; i++)
      oscillation[i] =
          std::sin(3.14159265358979323846 * (v[4] * .001f + .35f * i));
    builder.uniform("oscillation").set(oscillation, 4);
    float grids[12];
    for (int i = 0; i < 3; i++) {
      float base = i == 0   ? .75f
                   : i == 1 ? .3f
                            : 1.5f,
            velocity = i == 0 ? .01f : -.0066f,
            angle = 1.5f * (i == 0   ? .55f
                            : i == 1 ? .45f
                                     : .35f);
      double rotation =
          v[4] * 3.14159265358979323846 * (i == 1 ? -.0078125 : .0078125) +
          (i == 0   ? 1.7
           : i == 1 ? 2
                    : 2.75) *
              3.14159265358979323846;
      grids[4 * i] = base + v[4] * velocity * std::cos(angle);
      grids[4 * i + 1] = base + v[4] * velocity * std::sin(angle);
      grids[4 * i + 2] = std::cos(rotation);
      grids[4 * i + 3] = std::sin(rotation);
    }
    builder.uniform("grids").set(grids, 12);
    auto &s = surface(h);
    SkPaint paint = s.paint;
    paint.setShader(builder.makeShader());
    s.target->getCanvas()->drawRect(
        SkRect::MakeLTRB(v[7] - v[9] - .5f, v[8] - v[9] - .5f,
                         v[7] + v[9] + .5f, v[8] + v[9] + .5f),
        paint);
  }
  CATCH
}
