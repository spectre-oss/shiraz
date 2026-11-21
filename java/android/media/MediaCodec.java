// SPDX-License-Identifier: GPL-3.0-only
package android.media;

/** Bounded AAC/Opus codecs and optional OS video decoding, with callback delivery. */
public final class MediaCodec {
    public static final int BUFFER_FLAG_KEY_FRAME = 1, BUFFER_FLAG_SYNC_FRAME = 1, BUFFER_FLAG_CODEC_CONFIG = 2,
        BUFFER_FLAG_END_OF_STREAM = 4, BUFFER_FLAG_PARTIAL_FRAME = 8, BUFFER_FLAG_DECODE_ONLY = 32;
    public static final int CRYPTO_MODE_UNENCRYPTED = 0, CRYPTO_MODE_AES_CTR = 1, CRYPTO_MODE_AES_CBC = 2;

    public static final int CONFIGURE_FLAG_ENCODE = 1, CONFIGURE_FLAG_USE_BLOCK_MODEL = 2,
        CONFIGURE_FLAG_USE_CRYPTO_ASYNC = 4, CONFIGURE_FLAG_DETACHED_SURFACE = 8;
    public static final int INFO_TRY_AGAIN_LATER = -1, INFO_OUTPUT_FORMAT_CHANGED = -2, INFO_OUTPUT_BUFFERS_CHANGED = -3;

    public static final String PARAMETER_KEY_HDR10_PLUS_INFO = "hdr10-plus-info";
    public static final String PARAMETER_KEY_LOW_LATENCY = "low-latency";
    public static final String PARAMETER_KEY_OFFSET_TIME = "time-offset-us";
    public static final String PARAMETER_KEY_QP_OFFSET_MAP = "qp-offset-map";
    public static final String PARAMETER_KEY_QP_OFFSET_RECTS = "qp-offset-rects";
    public static final String PARAMETER_KEY_REQUEST_SYNC_FRAME = "request-sync";
    public static final String PARAMETER_KEY_SUSPEND = "drop-input-frames";
    public static final String PARAMETER_KEY_SUSPEND_TIME = "drop-start-time-us";
    public static final String PARAMETER_KEY_TUNNEL_PEEK = "tunnel-peek";
    public static final String PARAMETER_KEY_VIDEO_BITRATE = "video-bitrate";

    public static final int VIDEO_SCALING_MODE_SCALE_TO_FIT = 1;
    public static final int VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING = 2;

    public abstract static class Callback {
        public abstract void onInputBufferAvailable(MediaCodec codec,int index);
        public abstract void onOutputBufferAvailable(MediaCodec codec,int index,BufferInfo info);
        public void onOutputBuffersAvailable(MediaCodec codec, int index, java.util.ArrayDeque<BufferInfo> infos) {}
        public abstract void onError(MediaCodec codec,CodecException error);
        public void onCryptoError(MediaCodec codec, CryptoException error) {}
        public abstract void onOutputFormatChanged(MediaCodec codec,MediaFormat format);
    }
    public static final class CodecException extends IllegalStateException {
        public static final int ERROR_INSUFFICIENT_RESOURCE=1100,ERROR_RECLAIMED=1101;
        private final int code;
        public CodecException(int code,String message){super(message);this.code=code;}
        public int getErrorCode(){return code;}public boolean isRecoverable(){return false;}public boolean isTransient(){return false;}
        public String getDiagnosticInfo(){return "android.media.MediaCodec.error_"+code;}
    }
    public static final class CryptoException extends RuntimeException implements MediaDrmThrowable {
        public static final int ERROR_NO_KEY = 1;
        public static final int ERROR_KEY_EXPIRED = 2;
        public static final int ERROR_RESOURCE_BUSY = 3;
        public static final int ERROR_INSUFFICIENT_OUTPUT_PROTECTION = 4;
        public static final int ERROR_SESSION_NOT_OPENED = 5;
        public static final int ERROR_UNSUPPORTED_OPERATION = 6;
        public static final int ERROR_INSUFFICIENT_SECURITY = 7;
        public static final int ERROR_FRAME_TOO_LARGE = 8;
        public static final int ERROR_LOST_STATE = 9;
        private final int mErrorCode;
        private CryptoInfo mCryptoInfo;
        public CryptoException(int errorCode, String detailMessage) {
            super(detailMessage);
            mErrorCode = errorCode;
        }
        public int getErrorCode() { return mErrorCode; }
        public CryptoInfo getCryptoInfo() { return mCryptoInfo; }
        @Override public int getVendorError() { return 0; }
        @Override public int getOemError() { return 0; }
        @Override public int getErrorContext() { return 0; }
    }
    public static class IncompatibleWithBlockModelException extends RuntimeException {
        IncompatibleWithBlockModelException(String message) { super(message); }
    }
    public static class InvalidBufferFlagsException extends RuntimeException {
        InvalidBufferFlagsException(String message) { super(message); }
    }
    public static final class LinearBlock {
        private java.nio.ByteBuffer mBuffer;
        private boolean mRecycled;
        private LinearBlock(int capacity) {
            mBuffer = java.nio.ByteBuffer.allocateDirect(capacity);
        }
        public boolean isMappable() { return !mRecycled; }
        public java.nio.ByteBuffer map() {
            if (mRecycled) throw new IllegalStateException("LinearBlock has been recycled");
            return mBuffer;
        }
        public void recycle() {
            mRecycled = true;
            mBuffer = null;
        }
        @Override protected void finalize() { recycle(); }
        public static boolean isCodecCopyFreeCompatible(String[] codecNames) {
            java.util.Objects.requireNonNull(codecNames, "codecNames");
            return false;
        }
        public static LinearBlock obtain(int capacity, String[] codecNames) {
            if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
            java.util.Objects.requireNonNull(codecNames, "codecNames");
            return new LinearBlock(capacity);
        }
    }
    public static final class MetricsConstants {
        public static final String CODEC = "android.media.mediacodec.codec";
        public static final String ENCODER = "android.media.mediacodec.encoder";
        public static final String HEIGHT = "android.media.mediacodec.height";
        public static final String MIME_TYPE = "android.media.mediacodec.mime";
        public static final String MODE = "android.media.mediacodec.mode";
        public static final String MODE_AUDIO = "audio";
        public static final String MODE_VIDEO = "video";
        public static final String ROTATION = "android.media.mediacodec.rotation";
        public static final String SECURE = "android.media.mediacodec.secure";
        public static final String WIDTH = "android.media.mediacodec.width";
        private MetricsConstants() {}
    }
    public interface OnFirstTunnelFrameReadyListener {
        void onFirstTunnelFrameReady(MediaCodec codec);
    }
    public interface OnFrameRenderedListener {
        void onFrameRendered(MediaCodec codec, long presentationTimeUs, long nanoTime);
    }
    public static final class OutputFrame {
        private final LinearBlock mLinearBlock;
        private final android.hardware.HardwareBuffer mHardwareBuffer;
        private final long mPresentationTimeUs;
        private final int mFlags;
        private final java.util.ArrayDeque<BufferInfo> mBufferInfos = new java.util.ArrayDeque<BufferInfo>();
        private final MediaFormat mFormat;
        OutputFrame(LinearBlock block, android.hardware.HardwareBuffer hwBuf, long ptsUs, int flags, MediaFormat fmt) {
            mLinearBlock = block;
            mHardwareBuffer = hwBuf;
            mPresentationTimeUs = ptsUs;
            mFlags = flags;
            mFormat = fmt;
        }
        public LinearBlock getLinearBlock() { return mLinearBlock; }
        public android.hardware.HardwareBuffer getHardwareBuffer() { return mHardwareBuffer; }
        public long getPresentationTimeUs() { return mPresentationTimeUs; }
        public int getFlags() { return mFlags; }
        public java.util.ArrayDeque<BufferInfo> getBufferInfos() { return mBufferInfos; }
        public MediaFormat getFormat() { return mFormat; }
        public java.util.Set<String> getChangedKeys() { return java.util.Collections.emptySet(); }
    }
    public static class ParameterDescriptor {
        private final String mName;
        private final int mType;
        ParameterDescriptor(String name, int type) { mName = name; mType = type; }
        public String getName() { return mName; }
        public int getType() { return mType; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof ParameterDescriptor)) return false;
            ParameterDescriptor other = (ParameterDescriptor) o;
            return mType == other.mType && java.util.Objects.equals(mName, other.mName);
        }
        @Override public int hashCode() { return java.util.Objects.hash(mName, Integer.valueOf(mType)); }
    }
    public final class QueueRequest {
        private final int mIndex;
        private LinearBlock mBlock;
        private int mOffset;
        private int mSize;
        private long mPtsUs;
        private int mFlags;
        QueueRequest(int index) { mIndex = index; }
        public QueueRequest setLinearBlock(LinearBlock block, int offset, int size) {
            mBlock = block; mOffset = offset; mSize = size; return this;
        }
        public QueueRequest setMultiFrameLinearBlock(LinearBlock block, java.util.ArrayDeque<BufferInfo> infos) {
            mBlock = block; return this;
        }
        public QueueRequest setEncryptedLinearBlock(LinearBlock block, int offset, int size, CryptoInfo cryptoInfo) {
            mBlock = block; mOffset = offset; mSize = size; return this;
        }
        public QueueRequest setMultiFrameEncryptedLinearBlock(LinearBlock block,
                java.util.ArrayDeque<BufferInfo> infos, java.util.ArrayDeque<CryptoInfo> cryptoInfos) {
            mBlock = block; return this;
        }
        public QueueRequest setHardwareBuffer(android.hardware.HardwareBuffer buffer) { return this; }
        public QueueRequest setPresentationTimeUs(long presentationTimeUs) { mPtsUs = presentationTimeUs; return this; }
        public QueueRequest setFlags(int flags) { mFlags = flags; return this; }
        public QueueRequest setIntegerParameter(String key, int value) { return this; }
        public QueueRequest setLongParameter(String key, long value) { return this; }
        public QueueRequest setFloatParameter(String key, float value) { return this; }
        public QueueRequest setByteBufferParameter(String key, java.nio.ByteBuffer value) { return this; }
        public QueueRequest setStringParameter(String key, String value) { return this; }
        public void queue() {
            if (mBlock != null && mBlock.isMappable()) {
                java.nio.ByteBuffer src = mBlock.map().duplicate();
                src.position(mOffset);
                src.limit(mOffset + mSize);
                java.nio.ByteBuffer dst = getInputBuffer(mIndex);
                dst.clear();
                dst.put(src);
                queueInputBuffer(mIndex, 0, mSize, mPtsUs, mFlags);
            } else {
                queueInputBuffer(mIndex, mOffset, mSize, mPtsUs, mFlags);
            }
        }
    }

    private Callback callback;private android.os.Handler callbackHandler;private int callbackGeneration;
    public synchronized void setCallback(Callback value){setCallback(value,null);}
    public synchronized void setCallback(Callback value,android.os.Handler handler){if(started)throw new IllegalStateException("Codec started");callback=value;callbackHandler=value==null?null:handler!=null?handler:new android.os.Handler(android.os.Looper.myLooper()!=null?android.os.Looper.myLooper():android.os.Looper.getMainLooper());}
    private boolean callbackPending;
    private void pump(){
        if(callback==null||!started||callbackPending)return;
        callbackPending=true;final int generation=callbackGeneration;
        callbackHandler.postDelayed(new Runnable(){public void run(){dispatchCallbacks(generation);}},videoMime!=null?10:0);
    }
    private boolean callbackActive(int generation){return started&&generation==callbackGeneration&&callback!=null;}
    private void dispatchCallbacks(int generation){
        final Callback target;
        synchronized(this){if(!callbackActive(generation))return;callbackPending=false;target=callback;}
        // Client callbacks may take locks that input-queueing threads already
        // hold. Never invoke them under the codec monitor (lock inversion).
        try{
            MediaFormat changed=null;
            synchronized(this){if(!callbackActive(generation))return;if(formatPending){formatPending=false;changed=getOutputFormat();}}
            if(changed!=null)target.onOutputFormatChanged(this,changed);
            BufferInfo info=new BufferInfo();int outputIndex=-1;
            synchronized(this){if(!callbackActive(generation))return;if(video!=0||(held==null&&!output.isEmpty()))outputIndex=dequeueOutputBuffer(info,0);}
            if(outputIndex>=0)target.onOutputBufferAvailable(this,outputIndex,info);
            int inputIndex=-1;
            synchronized(this){if(!callbackActive(generation))return;if(!inputOwned&&!ended&&output.size()<64)inputIndex=dequeueInputBuffer(0);}
            if(inputIndex>=0)target.onInputBufferAvailable(this,inputIndex);
            synchronized(this){if(video!=0&&callbackActive(generation))pump();}
        }catch(RuntimeException error){
            synchronized(this){if(!callbackActive(generation))return;}
            target.onError(this,new CodecException(0,error.toString()));
        }
    }
    public String getName(){return getCodecInfo().getName();}
    public String getCanonicalName(){return getCodecInfo().getCanonicalName();}
    public java.nio.ByteBuffer[] getInputBuffers(){return new java.nio.ByteBuffer[]{input};}
    public java.nio.ByteBuffer[] getOutputBuffers(){throw new UnsupportedOperationException("Use per-buffer codec output access");}
    public void releaseOutputBuffer(int index,long renderTimestampNs){releaseOutputBuffer(index,true);}
    private boolean opus;private String videoMime;private long video;private android.view.Surface videoSurface;private int videoWidth,videoHeight;private final java.util.HashMap<Integer,Packet> videoOutputs=new java.util.HashMap<Integer,Packet>();
    private boolean decoding;private long decoder;private int outputRate,outputChannels;
    private MediaCodec(){}
    private MediaFormat format;private long encoder;private boolean started,formatPending,inputOwned,ended;private long samples,baseTime;
    public synchronized void setParameters(android.os.Bundle parameters) {
        if (!started) throw new IllegalStateException("Codec stopped");
        if (parameters == null) return;
        for (String key : parameters.keySet()) {
            if (!"operating-rate".equals(key))
                throw new UnsupportedOperationException("Codec parameter: " + key);
            Object value = parameters.get(key);
            if (!(value instanceof Number)) throw new IllegalArgumentException("Operating rate must be numeric");
            float rate = ((Number)value).floatValue();
            if (!(rate > 0) || Float.isInfinite(rate)) throw new IllegalArgumentException("Operating rate must be finite and positive");
        }
        // Resource-allocation hint, not playback speed: these software codecs
        // already process queued input as capacity permits.
        if (parameters.containsKey("operating-rate"))
            format.setFloat("operating-rate", ((Number)parameters.get("operating-rate")).floatValue());
    }
    private java.nio.ByteBuffer input=java.nio.ByteBuffer.allocateDirect(65536);private final java.io.ByteArrayOutputStream pending=new java.io.ByteArrayOutputStream();
    private static final class Packet {final byte[] bytes;final long time;final int flags;java.nio.ByteBuffer direct;Packet(byte[] b,long t,int f){bytes=b;time=t;flags=f;}}
    private final java.util.ArrayDeque<Packet> output=new java.util.ArrayDeque<Packet>();private Packet held;
    public static MediaCodec createEncoderByType(String mime)throws java.io.IOException{if(!"audio/mp4a-latm".equals(mime))throw new java.io.IOException("Only AAC-LC encoding is supported");return new MediaCodec();}
    public static MediaCodec createDecoderByType(String mime)throws java.io.IOException{if(mime!=null&&(mime.startsWith("video/")||mime.equals("audio/opus"))){for(String available:shiraz.runtime.NativeVideo.codecs())if(available.equals(mime)){MediaCodec c=new MediaCodec();c.decoding=true;c.opus=mime.equals("audio/opus");if(!c.opus)c.videoMime=mime;c.input=java.nio.ByteBuffer.allocateDirect(4*1024*1024);return c;}throw new java.io.IOException("OS video decoder unavailable for "+mime);}if(!"audio/mp4a-latm".equals(mime))throw new java.io.IOException("Only AAC-LC decoding is supported");MediaCodec c=new MediaCodec();c.decoding=true;return c;}
    public static MediaCodec createByCodecName(String name)throws java.io.IOException{if(name!=null&&name.startsWith("OMX.shiraz.system."))return createDecoderByType((name.endsWith(".opus")?"audio/":"video/")+name.substring("OMX.shiraz.system.".length()));if("OMX.shiraz.aac.decoder".equals(name))return createDecoderByType("audio/mp4a-latm");if("OMX.shiraz.aac.encoder".equals(name))return createEncoderByType("audio/mp4a-latm");throw new java.io.IOException("Unknown codec: "+name);}
    public MediaCodecInfo getCodecInfo(){return opus?new MediaCodecInfo("audio/opus"):videoMime!=null?new MediaCodecInfo(videoMime):new MediaCodecInfo(!decoding);}
    public synchronized void flush(){if(!started)throw new IllegalStateException("Codec stopped");stop();if(callback==null)start();}
    public synchronized void reset(){stop();format=null;callback=null;callbackHandler=null;}
    @Override protected void finalize(){release();}
    public void configure(MediaFormat value,android.view.Surface surface,MediaCrypto crypto,int flags){
        if(opus){if(started||value==null||surface!=null||crypto!=null||flags!=0||!"audio/opus".equals(value.getString("mime"))||!value.containsKey("csd-0"))throw new IllegalArgumentException("Opus decoder configuration");outputChannels=value.getInteger("channel-count");if(outputChannels<1||outputChannels>2)throw new IllegalArgumentException("Opus channels");outputRate=48000;format=value;return;}
        if(videoMime!=null){if(started||value==null||surface==null||!surface.isValid()||crypto!=null||flags!=0||!videoMime.equals(value.getString("mime")))throw new IllegalArgumentException("OS video decoder configuration");videoWidth=value.getInteger("width");videoHeight=value.getInteger("height");if(videoWidth<1||videoHeight<1||videoWidth>8192||videoHeight>8192||(long)videoWidth*videoHeight>16*1024*1024)throw new IllegalArgumentException("Video dimensions");format=value;videoSurface=surface;return;}
        if(started||value==null||surface!=null||crypto!=null||flags!=(decoding?0:CONFIGURE_FLAG_ENCODE)||!"audio/mp4a-latm".equals(value.getString("mime")))throw new IllegalArgumentException("AAC encoder configuration");
        int rate=value.getInteger("sample-rate"),channels=value.getInteger("channel-count");int index=rateIndex(rate);if(index<0||rate>48000||channels<1||channels>2||value.getInteger("aac-profile",2)!=2)throw new IllegalArgumentException("AAC-LC format");
        format=MediaFormat.createAudioFormat("audio/mp4a-latm",rate,channels);format.setInteger("bitrate",value.getInteger("bitrate",32000));format.setInteger("aac-profile",2);
        int config=2<<11|index<<7|channels<<3;format.setByteBuffer("csd-0",decoding&&value.containsKey("csd-0")?value.getByteBuffer("csd-0"):java.nio.ByteBuffer.wrap(new byte[]{(byte)(config>>8),(byte)config}));
    }
    public void configure(MediaFormat format, android.view.Surface surface, int flags, MediaDescrambler descrambler) {
        if (descrambler != null) throw new UnsupportedOperationException("MediaDescrambler codecs unavailable");
        configure(format, surface, null, flags);
    }
    public synchronized void setOutputSurface(android.view.Surface surface) {
        if (surface == null || !surface.isValid()) throw new IllegalArgumentException("Invalid surface");
        if (videoMime == null) throw new IllegalStateException("Codec is not configured for video surface output");
        videoSurface = surface;
    }
    public synchronized void detachOutputSurface() {
        throw new IllegalStateException("CONFIGURE_FLAG_DETACHED_SURFACE not enabled");
    }
    public static android.view.Surface createPersistentInputSurface() {
        throw new UnsupportedOperationException("Surface video encoding is unavailable on desktop host");
    }
    public void setInputSurface(android.view.Surface surface) {
        throw new UnsupportedOperationException("Surface video encoding is unavailable on desktop host");
    }
    public android.view.Surface createInputSurface() {
        throw new UnsupportedOperationException("Surface video encoding is unavailable on desktop host");
    }
    static int rateIndex(int rate){int[] rates={96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000};for(int i=0;i<rates.length;i++)if(rates[i]==rate)return i;return -1;}
    public synchronized void start(){if(started||format==null)throw new IllegalStateException("Encoder not configured");if(videoMime!=null){java.io.ByteArrayOutputStream csd=new java.io.ByteArrayOutputStream();for(String key:new String[]{"csd-0","csd-1"})if(format.containsKey(key)){java.nio.ByteBuffer b=format.getByteBuffer(key).duplicate();byte[] bytes=new byte[b.remaining()];b.get(bytes);csd.write(bytes,0,bytes.length);}video=shiraz.runtime.NativeVideo.open(videoMime,videoWidth,videoHeight,csd.toByteArray());}else if(opus){java.nio.ByteBuffer b=format.getByteBuffer("csd-0").duplicate();byte[] bytes=new byte[b.remaining()];b.get(bytes);decoder=shiraz.runtime.NativeVideo.opusOpen(bytes);}else if(decoding){java.nio.ByteBuffer config=format.getByteBuffer("csd-0");byte[] bytes=new byte[config.remaining()];config.get(bytes);decoder=shiraz.runtime.NativePlayback.decoderOpen(bytes);int[] actual=shiraz.runtime.NativePlayback.decoderFormat(decoder);outputRate=actual[0];outputChannels=actual[1];}else encoder=shiraz.runtime.NativeCapture.aacOpen(format.getInteger("sample-rate"),format.getInteger("channel-count"),format.getInteger("bitrate"));started=true;formatPending=true;ended=false;samples=0;baseTime=0;pump();}
    public synchronized void stop(){callbackGeneration++;callbackPending=false;if(video!=0)shiraz.runtime.NativeVideo.close(video);video=0;videoOutputs.clear();if(decoder!=0){if(opus)shiraz.runtime.NativeVideo.opusClose(decoder);else shiraz.runtime.NativePlayback.close(decoder);}decoder=0;if(encoder!=0)shiraz.runtime.NativeCapture.close(encoder);encoder=0;started=false;inputOwned=false;held=null;pending.reset();output.clear();}
    public void release(){stop();}
    public synchronized int dequeueInputBuffer(long timeout){if(!started)throw new IllegalStateException("Encoder stopped");if(inputOwned||ended||output.size()>=64||(video!=0&&!shiraz.runtime.NativeVideo.canQueue(video)))return -1;inputOwned=true;input.clear();return 0;}
    public java.nio.ByteBuffer getInputBuffer(int index){if(!started||!inputOwned||index!=0)throw new IllegalStateException("Input buffer not owned");return input;}
    public Image getInputImage(int index) { return null; }
    public Image getOutputImage(int index) { return null; }
    public static Image mapHardwareBuffer(android.hardware.HardwareBuffer hardwareBuffer) { return null; }
    public QueueRequest getQueueRequest(int index) {
        if (!started) throw new IllegalStateException("Codec is not executing");
        return new QueueRequest(index);
    }
    public OutputFrame getOutputFrame(int index) {
        throw new IncompatibleWithBlockModelException("Codec is not configured in block model mode");
    }
    public synchronized void queueInputBuffer(int index,int offset,int size,long time,int flags){
        if(!started||!inputOwned||index!=0||offset<0||size<0||(long)offset+size>input.capacity()||(flags&~(BUFFER_FLAG_END_OF_STREAM|BUFFER_FLAG_CODEC_CONFIG|BUFFER_FLAG_KEY_FRAME|BUFFER_FLAG_PARTIAL_FRAME|BUFFER_FLAG_DECODE_ONLY))!=0)throw new IllegalArgumentException("PCM input buffer");
        if(videoMime!=null){inputOwned=false;byte[] packet=new byte[size];java.nio.ByteBuffer copy=input.duplicate();copy.position(offset);copy.get(packet);ended=(flags&BUFFER_FLAG_END_OF_STREAM)!=0;shiraz.runtime.NativeVideo.queue(video,packet,time,ended);pump();return;}
        if(decoding){
         inputOwned=false;
         if(size>0&&(flags&BUFFER_FLAG_CODEC_CONFIG)==0){byte[] packet=new byte[size];java.nio.ByteBuffer copy=input.duplicate();copy.position(offset);copy.get(packet);byte[] pcm=opus?shiraz.runtime.NativeVideo.opusDecode(decoder,packet):shiraz.runtime.NativePlayback.decode(decoder,packet);if(pcm.length>0)output.addLast(new Packet(pcm,time,0));}
         if((flags&BUFFER_FLAG_END_OF_STREAM)!=0){ended=true;output.addLast(new Packet(new byte[0],time,BUFFER_FLAG_END_OF_STREAM));}pump();return;
        }
        inputOwned=false;if(samples==0&&pending.size()==0)baseTime=time;byte[] part=new byte[size];java.nio.ByteBuffer copy=input.duplicate();copy.position(offset);copy.get(part);pending.write(part,0,part.length);
        byte[] bytes=pending.toByteArray();pending.reset();int frame=1024*format.getInteger("channel-count")*2,at=0;
        while(bytes.length-at>=frame){encode(java.util.Arrays.copyOfRange(bytes,at,at+frame));at+=frame;}
        if(at<bytes.length)pending.write(bytes,at,bytes.length-at);
        if((flags&BUFFER_FLAG_END_OF_STREAM)!=0){if(pending.size()>0){byte[] last=new byte[frame];byte[] rest=pending.toByteArray();System.arraycopy(rest,0,last,0,rest.length);pending.reset();encode(last);}output.addLast(new Packet(new byte[0],baseTime+samples*1000000L/format.getInteger("sample-rate"),BUFFER_FLAG_END_OF_STREAM));ended=true;}pump();
    }
    public synchronized void queueInputBuffers(int index, java.util.ArrayDeque<BufferInfo> bufferInfos) {
        java.util.Objects.requireNonNull(bufferInfos, "bufferInfos");
        if (bufferInfos.isEmpty()) throw new IllegalArgumentException("bufferInfos must not be empty");
        BufferInfo first = bufferInfos.peekFirst();
        queueInputBuffer(index, first.offset, first.size, first.presentationTimeUs, first.flags);
    }
    public void queueSecureInputBuffer(int index, int offset, CryptoInfo info, long presentationTimeUs, int flags) {
        throw new CryptoException(CryptoException.ERROR_UNSUPPORTED_OPERATION, "Secure MediaCodec input unavailable");
    }
    public void queueSecureInputBuffers(int index, java.util.ArrayDeque<BufferInfo> bufferInfos,
            java.util.ArrayDeque<CryptoInfo> cryptoInfos) {
        throw new CryptoException(CryptoException.ERROR_UNSUPPORTED_OPERATION, "Secure MediaCodec input unavailable");
    }
    public void signalEndOfInputStream() {
        throw new IllegalStateException("Codec is not using an input Surface");
    }
    private void encode(byte[] pcm){if(output.size()>=256)throw new IllegalStateException("AAC output queue limit");byte[] bytes=shiraz.runtime.NativeCapture.aacEncode(encoder,pcm);output.addLast(new Packet(bytes,baseTime+samples*1000000L/format.getInteger("sample-rate"),0));samples+=1024;}
    public synchronized int dequeueOutputBuffer(BufferInfo info,long timeout){if(!started)throw new IllegalStateException("Encoder stopped");if(info==null)throw new NullPointerException("info");if(formatPending){formatPending=false;return INFO_OUTPUT_FORMAT_CHANGED;}if(video!=0){long[] next=shiraz.runtime.NativeVideo.poll(video);if(next==null)return INFO_TRY_AGAIN_LATER;int slot=(int)next[3],flags=next[0]<0?BUFFER_FLAG_END_OF_STREAM:0;if(flags==0){videoWidth=(int)next[0];videoHeight=(int)next[1];}videoOutputs.put(slot,new Packet(new byte[0],next[2],flags));info.set(0,flags==0?videoWidth*videoHeight*4:0,next[2],flags);return slot;}if(held!=null||output.isEmpty())return INFO_TRY_AGAIN_LATER;held=output.removeFirst();info.set(0,held.bytes.length,held.time,held.flags);return 0;}
    public synchronized java.nio.ByteBuffer getOutputBuffer(int index){if(video!=0){if(!videoOutputs.containsKey(index))throw new IllegalStateException("Output buffer not owned");return null;}if(index!=0||held==null)throw new IllegalStateException("Output buffer not owned");if(held.direct==null){held.direct=java.nio.ByteBuffer.allocateDirect(held.bytes.length).order(java.nio.ByteOrder.nativeOrder());held.direct.put(held.bytes).flip();}return held.direct.asReadOnlyBuffer().order(java.nio.ByteOrder.nativeOrder());}
    public synchronized void releaseOutputBuffer(int index,boolean render){if(video!=0){Packet packet=videoOutputs.remove(index);if(packet==null)throw new IllegalStateException("Video output buffer not owned");if((packet.flags&BUFFER_FLAG_END_OF_STREAM)==0)shiraz.runtime.NativeVideo.releaseFrame(video,index,render?videoSurface.shirazNativeWindow():0,render);pump();return;}if(index!=0||held==null)throw new IllegalStateException("Output buffer not owned");if(render)throw new UnsupportedOperationException("Audio codec cannot render to a surface");held=null;pump();}
    public synchronized MediaFormat getOutputFormat(){if(!started)throw new IllegalStateException("Encoder stopped");if(videoMime!=null){MediaFormat result=MediaFormat.createVideoFormat(videoMime,videoWidth,videoHeight);result.setInteger("color-format",2130708361);result.setInteger("stride",videoWidth);result.setInteger("slice-height",videoHeight);return result;}MediaFormat copy=MediaFormat.createAudioFormat(decoding?"audio/raw":"audio/mp4a-latm",decoding?outputRate:format.getInteger("sample-rate"),decoding?outputChannels:format.getInteger("channel-count"));if(decoding){copy.setInteger("pcm-encoding",2);return copy;}copy.setInteger("bitrate",format.getInteger("bitrate"));copy.setByteBuffer("csd-0",format.getByteBuffer("csd-0"));return copy;}
    public synchronized MediaFormat getInputFormat() {
        if (format == null) throw new IllegalStateException("Codec not configured");
        return format;
    }
    public synchronized MediaFormat getOutputFormat(int index) {
        return getOutputFormat();
    }
    public void setVideoScalingMode(int mode) {
        if (mode != VIDEO_SCALING_MODE_SCALE_TO_FIT && mode != VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING) {
            throw new IllegalArgumentException("Invalid scaling mode: " + mode);
        }
    }
    public void setAudioPresentation(AudioPresentation presentation) {
        java.util.Objects.requireNonNull(presentation, "presentation");
    }
    public synchronized android.os.PersistableBundle getMetrics() {
        android.os.PersistableBundle b = new android.os.PersistableBundle();
        b.putString(MetricsConstants.CODEC, getName());
        b.putInt(MetricsConstants.ENCODER, decoding ? 0 : 1);
        b.putString(MetricsConstants.MODE, videoMime != null ? MetricsConstants.MODE_VIDEO : MetricsConstants.MODE_AUDIO);
        return b;
    }
    public void setOnFirstTunnelFrameReadyListener(android.os.Handler handler, OnFirstTunnelFrameReadyListener listener) {}
    public void setOnFrameRenderedListener(OnFrameRenderedListener listener, android.os.Handler handler) {}
    public java.util.List<String> getSupportedVendorParameters() { return java.util.Collections.emptyList(); }
    public ParameterDescriptor getParameterDescriptor(String name) { return null; }
    public void subscribeToVendorParameters(java.util.List<String> names) {}
    public void unsubscribeFromVendorParameters(java.util.List<String> names) {}

    public static final class BufferInfo {
        public int offset;
        public int size;
        public long presentationTimeUs;
        public int flags;
        public void set(int newOffset, int newSize, long newTimeUs, int newFlags) {
            offset = newOffset;
            size = newSize;
            presentationTimeUs = newTimeUs;
            flags = newFlags;
        }
    }

    public static final class CryptoInfo {
        public int numSubSamples;
        public int[] numBytesOfClearData;
        public int[] numBytesOfEncryptedData;
        public byte[] key;
        public byte[] iv;
        public int mode;
        private Pattern pattern;

        public static final class Pattern {
            private int encryptBlocks, skipBlocks;
            public Pattern(int blocksToEncrypt, int blocksToSkip) {
                set(blocksToEncrypt, blocksToSkip);
            }
            public void set(int blocksToEncrypt, int blocksToSkip) {
                encryptBlocks = blocksToEncrypt;
                skipBlocks = blocksToSkip;
            }
            public int getSkipBlocks() { return skipBlocks; }
            public int getEncryptBlocks() { return encryptBlocks; }
        }

        public void set(int newNumSubSamples, int[] newNumBytesOfClearData, int[] newNumBytesOfEncryptedData, byte[] newKey, byte[] newIV, int newMode) {
            numSubSamples = newNumSubSamples;
            numBytesOfClearData = newNumBytesOfClearData;
            numBytesOfEncryptedData = newNumBytesOfEncryptedData;
            key = newKey;
            iv = newIV;
            mode = newMode;
            pattern = null;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public void setPattern(Pattern newPattern) {
            pattern = newPattern;
        }

        public Pattern shirazPattern() {
            return pattern;
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder();
            out.append(numSubSamples).append(" subsamples, mode ").append(mode);
            return out.toString();
        }
    }
}
