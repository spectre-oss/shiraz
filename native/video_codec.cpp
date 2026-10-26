// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/native_window.h"
#include <jni.h>
#include <gst/gst.h>
#include <gst/app/gstappsrc.h>
#include <gst/app/gstappsink.h>
#include <gst/video/video.h>
#include <mutex>
#include <vector>
#include <string>
#include <cstdio>
#include <cstdlib>
extern "C" bool shiraz_opus_available();
namespace {
void init(){static std::once_flag once;std::call_once(once,[]{gst_init(nullptr,nullptr);});}
const char* capsType(const char* mime){if(!strcmp(mime,"video/avc"))return "video/x-h264";if(!strcmp(mime,"video/x-vnd.on2.vp8"))return "video/x-vp8";if(!strcmp(mime,"video/x-vnd.on2.vp9"))return "video/x-vp9";return nullptr;}
GstElementFactory* factory(const char* mime){
 const char* type=capsType(mime);if(!type)return nullptr;GstCaps* caps=gst_caps_new_empty_simple(type);
 GList* all=gst_element_factory_list_get_elements(GST_ELEMENT_FACTORY_TYPE_DECODER|GST_ELEMENT_FACTORY_TYPE_MEDIA_VIDEO,GST_RANK_MARGINAL);
 GList* matching=gst_element_factory_list_filter(all,caps,GST_PAD_SINK,FALSE);GstElementFactory* result=nullptr;
 // Respect OS plugin ranks (hardware drivers normally advertise their own
 // priority); prefer a hardware decoder among equally ranked candidates.
 guint rank=0;bool hardware=false;
 for(GList* item=matching;item;item=item->next){auto* f=GST_ELEMENT_FACTORY(item->data);guint r=gst_plugin_feature_get_rank(GST_PLUGIN_FEATURE(f));const char* klass=gst_element_factory_get_metadata(f,GST_ELEMENT_METADATA_KLASS);bool hw=klass&&strstr(klass,"Hardware");if(!result||r>rank||(r==rank&&hw&&!hardware)){GstPluginFeature* loaded=gst_plugin_feature_load(GST_PLUGIN_FEATURE(f));if(loaded){gst_object_unref(loaded);result=f;rank=r;hardware=hw;}}}
 if(result)gst_object_ref(result);
 gst_plugin_feature_list_free(matching);gst_plugin_feature_list_free(all);gst_caps_unref(caps);return result;
}
bool traceCounter(unsigned& count){++count;return std::getenv("SHIRAZ_TRACE_VIDEO")&&(count<=8||count%120==0);}
void fail(JNIEnv* env,const char* message){std::fprintf(stderr,"SHIRAZ_VIDEO_ERROR %s\n",message);jclass c=env->FindClass("java/lang/IllegalStateException");if(c){env->ThrowNew(c,message);env->DeleteLocalRef(c);}}
struct Decoder {GstElement* pipeline=nullptr;GstElement* source=nullptr;GstElement* sink=nullptr;GstSample* held[16]={};bool eosSent=false;guint64 lastProgress=0;unsigned published=0,queued=0,polled=0,released=0;std::vector<uint32_t> pixels;~Decoder(){std::fprintf(stderr,"SHIRAZ_VIDEO_SUMMARY input=%u output=%u released=%u rendered=%u\n",queued,polled,released,published);if(pipeline){gst_element_set_state(pipeline,GST_STATE_NULL);gst_object_unref(pipeline);}for(auto* sample:held)if(sample)gst_sample_unref(sample);}};
void elementAdded(GstBin*,GstBin*,GstElement* element,gpointer){
 auto* f=gst_element_get_factory(element);if(!f)return;const char* klass=gst_element_factory_get_metadata(f,GST_ELEMENT_METADATA_KLASS);
 if(klass&&strstr(klass,"Decoder")&&strstr(klass,"Video"))std::fprintf(stderr,"SHIRAZ_VIDEO_ACTIVE decoder=%s hardware=%d\n",gst_plugin_feature_get_name(GST_PLUGIN_FEATURE(f)),strstr(klass,"Hardware")?1:0);
}
void padAdded(GstElement*,GstPad* pad,gpointer data){GstPad* target=gst_element_get_static_pad(static_cast<GstElement*>(data),"sink");if(!gst_pad_is_linked(target))gst_pad_link(pad,target);gst_object_unref(target);}
bool busError(JNIEnv* env,Decoder* d){GstBus* bus=gst_element_get_bus(d->pipeline);GstMessage* m=gst_bus_pop_filtered(bus,GST_MESSAGE_ERROR);gst_object_unref(bus);if(!m)return false;GError* error=nullptr;gchar* debug=nullptr;gst_message_parse_error(m,&error,&debug);fail(env,error?error->message:"OS decoder failure");g_clear_error(&error);g_free(debug);gst_message_unref(m);return true;}
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_shiraz_runtime_NativeVideo_nativeCodecs(JNIEnv* env,jclass){init();std::vector<const char*> supported;for(const char* mime:{"video/avc","video/x-vnd.on2.vp8","video/x-vnd.on2.vp9"}){auto* f=factory(mime);if(f){supported.push_back(mime);gst_object_unref(f);}}if(shiraz_opus_available())supported.push_back("audio/opus");if(std::getenv("SHIRAZ_TRACE_VIDEO"))for(const char* mime:supported)std::fprintf(stderr,"SHIRAZ_SYSTEM_CODEC mime=%s\n",mime);jclass str=env->FindClass("java/lang/String");jobjectArray result=env->NewObjectArray(supported.size(),str,nullptr);env->DeleteLocalRef(str);for(size_t i=0;i<supported.size();++i){jstring s=env->NewStringUTF(supported[i]);env->SetObjectArrayElement(result,i,s);env->DeleteLocalRef(s);}return result;}
extern "C" JNIEXPORT jlong JNICALL Java_shiraz_runtime_NativeVideo_open(JNIEnv* env,jclass,jstring mime,jint width,jint height,jbyteArray config){
 init();if(width<1||height<1||width>8192||height>8192||int64_t(width)*height>16*1024*1024){fail(env,"Video dimensions exceed supported bounds");return 0;}
 const char* text=env->GetStringUTFChars(mime,nullptr);if(!text)return 0;std::string type=text;env->ReleaseStringUTFChars(mime,text);
 GstElementFactory* selected=factory(type.c_str());if(!selected){fail(env,"No OS decoder plugin for video format");return 0;}
 std::fprintf(stderr,"SHIRAZ_VIDEO preferred_decoder=%s mime=%s\n",gst_plugin_feature_get_name(GST_PLUGIN_FEATURE(selected)),type.c_str());gst_object_unref(selected);
 auto* d=new Decoder;d->pipeline=gst_pipeline_new(nullptr);d->source=gst_element_factory_make("appsrc",nullptr);GstElement* decode=gst_element_factory_make("decodebin",nullptr);GstElement* convert=gst_element_factory_make("videoconvert",nullptr);d->sink=gst_element_factory_make("appsink",nullptr);
 if(!d->pipeline||!d->source||!decode||!convert||!d->sink){fail(env,"OS GStreamer base plugins are missing");for(auto* element:{d->source,decode,convert,d->sink})if(element)gst_object_unref(element);delete d;return 0;}
 GstCaps* caps=gst_caps_new_simple(capsType(type.c_str()),"width",G_TYPE_INT,width,"height",G_TYPE_INT,height,nullptr);
 if(type=="video/avc")gst_caps_set_simple(caps,"stream-format",G_TYPE_STRING,"byte-stream","alignment",G_TYPE_STRING,"au",nullptr);
 g_object_set(d->source,"caps",caps,"format",GST_FORMAT_TIME,"block",FALSE,"max-bytes",guint64(8*1024*1024),nullptr);gst_caps_unref(caps);
 caps=gst_caps_new_simple("video/x-raw","format",G_TYPE_STRING,"BGRA",nullptr);g_object_set(d->sink,"caps",caps,"sync",FALSE,"max-buffers",guint(4),"drop",FALSE,nullptr);gst_caps_unref(caps);
 g_signal_connect(d->pipeline,"deep-element-added",G_CALLBACK(elementAdded),nullptr);
 gst_bin_add_many(GST_BIN(d->pipeline),d->source,decode,convert,d->sink,nullptr);if(!gst_element_link(d->source,decode)||!gst_element_link(convert,d->sink)){fail(env,"OS decoder pipeline cannot link");delete d;return 0;}g_signal_connect(decode,"pad-added",G_CALLBACK(padAdded),convert);
 if(gst_element_set_state(d->pipeline,GST_STATE_PLAYING)==GST_STATE_CHANGE_FAILURE){fail(env,"OS video decoder could not start");delete d;return 0;}
 if(config&&env->GetArrayLength(config)>0){jsize size=env->GetArrayLength(config);GstBuffer* buffer=gst_buffer_new_allocate(nullptr,size,nullptr);GstMapInfo map{};gst_buffer_map(buffer,&map,GST_MAP_WRITE);env->GetByteArrayRegion(config,0,size,reinterpret_cast<jbyte*>(map.data));gst_buffer_unmap(buffer,&map);GST_BUFFER_FLAG_SET(buffer,GST_BUFFER_FLAG_HEADER);gst_app_src_push_buffer(GST_APP_SRC(d->source),buffer);}
 return reinterpret_cast<jlong>(d);
}
// Do not block the MediaCodec callback thread: it also returns output slots.
// Stop handing out input buffers until the OS pipeline has queue space again.
extern "C" JNIEXPORT jboolean JNICALL Java_shiraz_runtime_NativeVideo_canQueue(JNIEnv* env,jclass,jlong handle){
 auto* d=reinterpret_cast<Decoder*>(handle);if(busError(env,d))return JNI_FALSE;
 guint64 bytes=0,buffers=0;g_object_get(d->source,"current-level-bytes",&bytes,nullptr);
 if(g_object_class_find_property(G_OBJECT_GET_CLASS(d->source),"current-level-buffers"))
  g_object_get(d->source,"current-level-buffers",&buffers,nullptr);
 return bytes<8*1024*1024&&buffers<64?JNI_TRUE:JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeVideo_queue(JNIEnv* env,jclass,jlong handle,jbyteArray bytes,jlong time,jboolean eos){auto* d=reinterpret_cast<Decoder*>(handle);if(busError(env,d))return;jsize size=bytes?env->GetArrayLength(bytes):0;if(size){if(traceCounter(d->queued))std::fprintf(stderr,"SHIRAZ_VIDEO_INPUT packet=%u bytes=%d pts=%lld\n",d->queued,int(size),static_cast<long long>(time));guint64 queued=0;g_object_get(d->source,"current-level-bytes",&queued,nullptr);if(size>8*1024*1024||queued+size>16*1024*1024){fail(env,"OS decoder input queue limit");return;}GstBuffer* b=gst_buffer_new_allocate(nullptr,size,nullptr);GstMapInfo map{};gst_buffer_map(b,&map,GST_MAP_WRITE);env->GetByteArrayRegion(bytes,0,size,reinterpret_cast<jbyte*>(map.data));gst_buffer_unmap(b,&map);GST_BUFFER_PTS(b)=time<0?GST_CLOCK_TIME_NONE:GstClockTime(time)*1000;if(gst_app_src_push_buffer(GST_APP_SRC(d->source),b)!=GST_FLOW_OK){fail(env,"OS decoder rejected input");return;}}if(eos)gst_app_src_end_of_stream(GST_APP_SRC(d->source));}
extern "C" JNIEXPORT jlongArray JNICALL Java_shiraz_runtime_NativeVideo_poll(JNIEnv* env,jclass,jlong handle){
 auto* d=reinterpret_cast<Decoder*>(handle);if(busError(env,d))return nullptr;
 if(std::getenv("SHIRAZ_TRACE_VIDEO")){guint64 now=gst_util_get_timestamp();if(now-d->lastProgress>=5*GST_SECOND){
  d->lastProgress=now;guint64 bytes=0;g_object_get(d->source,"current-level-bytes",&bytes,nullptr);
  unsigned held=0;for(auto* frame:d->held)if(frame)++held;
  std::fprintf(stderr,"SHIRAZ_VIDEO_PROGRESS input=%u output=%u released=%u held=%u queued_bytes=%llu\n",d->queued,d->polled,d->released,held,static_cast<unsigned long long>(bytes));
 }}
 int slot=0;while(slot<16&&d->held[slot])++slot;if(slot==16)return nullptr;
 GstSample* sample=gst_app_sink_try_pull_sample(GST_APP_SINK(d->sink),0);
 jlong values[4];
 if(!sample){if(d->eosSent||!gst_app_sink_is_eos(GST_APP_SINK(d->sink)))return nullptr;d->eosSent=true;values[0]=values[1]=-1;values[2]=0;values[3]=16;}
 else{GstVideoInfo info{};if(!gst_video_info_from_caps(&info,gst_sample_get_caps(sample))){gst_sample_unref(sample);fail(env,"Invalid OS decoded frame format");return nullptr;}d->held[slot]=sample;GstBuffer* b=gst_sample_get_buffer(sample);values[0]=GST_VIDEO_INFO_WIDTH(&info);values[1]=GST_VIDEO_INFO_HEIGHT(&info);values[2]=GST_BUFFER_PTS_IS_VALID(b)?jlong(GST_BUFFER_PTS(b)/1000):0;values[3]=slot;
 if(traceCounter(d->polled))std::fprintf(stderr,"SHIRAZ_VIDEO_OUTPUT frame=%u pts=%lld slot=%d\n",d->polled,static_cast<long long>(values[2]),slot);}
 jlongArray result=env->NewLongArray(4);env->SetLongArrayRegion(result,0,4,values);return result;
}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeVideo_releaseFrame(JNIEnv* env,jclass,jlong handle,jint slot,jlong window,jboolean render){auto* d=reinterpret_cast<Decoder*>(handle);if(slot<0||slot>=16||!d->held[slot]){fail(env,"No decoded frame to release");return;}if(traceCounter(d->released))std::fprintf(stderr,"SHIRAZ_VIDEO_RELEASE frame=%u render=%d\n",d->released,int(render));if(render){GstVideoInfo info{};GstVideoFrame frame{};gst_video_info_from_caps(&info,gst_sample_get_caps(d->held[slot]));int w=GST_VIDEO_INFO_WIDTH(&info),h=GST_VIDEO_INFO_HEIGHT(&info);if(w<1||h<1||w>8192||h>8192||int64_t(w)*h>16*1024*1024||!gst_video_frame_map(&frame,&info,gst_sample_get_buffer(d->held[slot]),GST_MAP_READ)){fail(env,"Cannot map decoded video frame");return;}d->pixels.resize(size_t(w)*h);for(int y=0;y<h;y++)memcpy(d->pixels.data()+size_t(y)*w,static_cast<unsigned char*>(GST_VIDEO_FRAME_PLANE_DATA(&frame,0))+y*GST_VIDEO_FRAME_PLANE_STRIDE(&frame,0),size_t(w)*4);gst_video_frame_unmap(&frame);auto* native=reinterpret_cast<ANativeWindow*>(window);if(!native||((ANativeWindow_getWidth(native)!=w||ANativeWindow_getHeight(native)!=h)&&ANativeWindow_setBuffersGeometry(native,w,h,1))||shiraz_window_post_argb(native,d->pixels.data(),d->pixels.size()))fail(env,"Cannot publish decoded video frame");}if(render&&traceCounter(d->published))std::fprintf(stderr,"SHIRAZ_VIDEO_FRAME published=%u hash=%08x\n",d->published,d->pixels.empty()?0:d->pixels[d->pixels.size()/2]);gst_sample_unref(d->held[slot]);d->held[slot]=nullptr;}
extern "C" JNIEXPORT void JNICALL Java_shiraz_runtime_NativeVideo_close(JNIEnv*,jclass,jlong handle){delete reinterpret_cast<Decoder*>(handle);}
