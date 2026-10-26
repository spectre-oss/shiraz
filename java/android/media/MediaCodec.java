// SPDX-License-Identifier: GPL-3.0-only
package android.media;

/** Bounded AAC/Opus codecs and optional OS video decoding, with callback delivery. */
public final class MediaCodec {
    public static final int BUFFER_FLAG_KEY_FRAME = 1, BUFFER_FLAG_SYNC_FRAME = 1, BUFFER_FLAG_CODEC_CONFIG = 2, BUFFER_FLAG_END_OF_STREAM = 4, BUFFER_FLAG_PARTIAL_FRAME = 8;
    public static final int CRYPTO_MODE_UNENCRYPTED = 0, CRYPTO_MODE_AES_CTR = 1, CRYPTO_MODE_AES_CBC = 2;

    public static final int CONFIGURE_FLAG_ENCODE=1,INFO_TRY_AGAIN_LATER=-1,INFO_OUTPUT_FORMAT_CHANGED=-2,INFO_OUTPUT_BUFFERS_CHANGED=-3;
    public abstract static class Callback {
        public abstract void onInputBufferAvailable(MediaCodec codec,int index);
        public abstract void onOutputBufferAvailable(MediaCodec codec,int index,BufferInfo info);
        public abstract void onError(MediaCodec codec,CodecException error);
        public abstract void onOutputFormatChanged(MediaCodec codec,MediaFormat format);
    }
    public static final class CodecException extends IllegalStateException {
        public static final int ERROR_INSUFFICIENT_RESOURCE=1100,ERROR_RECLAIMED=1101;
        private final int code;
        public CodecException(int code,String message){super(message);this.code=code;}
        public int getErrorCode(){return code;}public boolean isRecoverable(){return false;}public boolean isTransient(){return false;}
        public String getDiagnosticInfo(){return "android.media.MediaCodec.error_"+code;}
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
    public java.nio.ByteBuffer[] getInputBuffers(){return new java.nio.ByteBuffer[]{input};}
    public java.nio.ByteBuffer[] getOutputBuffers(){throw new UnsupportedOperationException("Use per-buffer codec output access");}
    public void releaseOutputBuffer(int index,long renderTimestampNs){releaseOutputBuffer(index,true);}
    private boolean opus;private String videoMime;private long video;private android.view.Surface videoSurface;private int videoWidth,videoHeight;private final java.util.HashMap<Integer,Packet> videoOutputs=new java.util.HashMap<Integer,Packet>();
    private boolean decoding;private long decoder;private int outputRate,outputChannels;
    private MediaCodec(){}
    private MediaFormat format;private long encoder;private boolean started,formatPending,inputOwned,ended;private long samples,baseTime;
    private java.nio.ByteBuffer input=java.nio.ByteBuffer.allocateDirect(65536);private final java.io.ByteArrayOutputStream pending=new java.io.ByteArrayOutputStream();
    private static final class Packet {final byte[] bytes;final long time;final int flags;java.nio.ByteBuffer direct;Packet(byte[] b,long t,int f){bytes=b;time=t;flags=f;}}
    private final java.util.ArrayDeque<Packet> output=new java.util.ArrayDeque<Packet>();private Packet held;
    public static MediaCodec createEncoderByType(String mime)throws java.io.IOException{if(!"audio/mp4a-latm".equals(mime))throw new java.io.IOException("Only AAC-LC encoding is supported");return new MediaCodec();}
    public static MediaCodec createDecoderByType(String mime)throws java.io.IOException{if(mime!=null&&(mime.startsWith("video/")||mime.equals("audio/opus"))){for(String available:shiraz.runtime.NativeVideo.codecs())if(available.equals(mime)){MediaCodec c=new MediaCodec();c.decoding=true;c.opus=mime.equals("audio/opus");if(!c.opus)c.videoMime=mime;c.input=java.nio.ByteBuffer.allocateDirect(4*1024*1024);return c;}throw new java.io.IOException("OS video decoder unavailable for "+mime);}if(!"audio/mp4a-latm".equals(mime))throw new java.io.IOException("Only AAC-LC decoding is supported");MediaCodec c=new MediaCodec();c.decoding=true;return c;}
    public static MediaCodec createByCodecName(String name)throws java.io.IOException{if(name!=null&&name.startsWith("OMX.shiraz.system."))return createDecoderByType((name.endsWith(".opus")?"audio/":"video/")+name.substring("OMX.shiraz.system.".length()));if("OMX.shiraz.aac.decoder".equals(name))return createDecoderByType("audio/mp4a-latm");if("OMX.shiraz.aac.encoder".equals(name))return createEncoderByType("audio/mp4a-latm");throw new java.io.IOException("Unknown codec: "+name);}
    public MediaCodecInfo getCodecInfo(){return opus?new MediaCodecInfo("audio/opus"):videoMime!=null?new MediaCodecInfo(videoMime):new MediaCodecInfo(!decoding);}
    public synchronized void flush(){if(!started)throw new IllegalStateException("Codec stopped");stop();if(callback==null)start();}
    public void configure(MediaFormat value,android.view.Surface surface,MediaCrypto crypto,int flags){
        if(opus){if(started||value==null||surface!=null||crypto!=null||flags!=0||!"audio/opus".equals(value.getString("mime"))||!value.containsKey("csd-0"))throw new IllegalArgumentException("Opus decoder configuration");outputChannels=value.getInteger("channel-count");if(outputChannels<1||outputChannels>2)throw new IllegalArgumentException("Opus channels");outputRate=48000;format=value;return;}
        if(videoMime!=null){if(started||value==null||surface==null||!surface.isValid()||crypto!=null||flags!=0||!videoMime.equals(value.getString("mime")))throw new IllegalArgumentException("OS video decoder configuration");videoWidth=value.getInteger("width");videoHeight=value.getInteger("height");if(videoWidth<1||videoHeight<1||videoWidth>8192||videoHeight>8192||(long)videoWidth*videoHeight>16*1024*1024)throw new IllegalArgumentException("Video dimensions");format=value;videoSurface=surface;return;}
        if(started||value==null||surface!=null||crypto!=null||flags!=(decoding?0:CONFIGURE_FLAG_ENCODE)||!"audio/mp4a-latm".equals(value.getString("mime")))throw new IllegalArgumentException("AAC encoder configuration");
        int rate=value.getInteger("sample-rate"),channels=value.getInteger("channel-count");int index=rateIndex(rate);if(index<0||rate>48000||channels<1||channels>2||value.getInteger("aac-profile",2)!=2)throw new IllegalArgumentException("AAC-LC format");
        format=MediaFormat.createAudioFormat("audio/mp4a-latm",rate,channels);format.setInteger("bitrate",value.getInteger("bitrate",32000));format.setInteger("aac-profile",2);
        int config=2<<11|index<<7|channels<<3;format.setByteBuffer("csd-0",decoding&&value.containsKey("csd-0")?value.getByteBuffer("csd-0"):java.nio.ByteBuffer.wrap(new byte[]{(byte)(config>>8),(byte)config}));
    }
    static int rateIndex(int rate){int[] rates={96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000};for(int i=0;i<rates.length;i++)if(rates[i]==rate)return i;return -1;}
    public synchronized void start(){if(started||format==null)throw new IllegalStateException("Encoder not configured");if(videoMime!=null){java.io.ByteArrayOutputStream csd=new java.io.ByteArrayOutputStream();for(String key:new String[]{"csd-0","csd-1"})if(format.containsKey(key)){java.nio.ByteBuffer b=format.getByteBuffer(key).duplicate();byte[] bytes=new byte[b.remaining()];b.get(bytes);csd.write(bytes,0,bytes.length);}video=shiraz.runtime.NativeVideo.open(videoMime,videoWidth,videoHeight,csd.toByteArray());}else if(opus){java.nio.ByteBuffer b=format.getByteBuffer("csd-0").duplicate();byte[] bytes=new byte[b.remaining()];b.get(bytes);decoder=shiraz.runtime.NativeVideo.opusOpen(bytes);}else if(decoding){java.nio.ByteBuffer config=format.getByteBuffer("csd-0");byte[] bytes=new byte[config.remaining()];config.get(bytes);decoder=shiraz.runtime.NativePlayback.decoderOpen(bytes);int[] actual=shiraz.runtime.NativePlayback.decoderFormat(decoder);outputRate=actual[0];outputChannels=actual[1];}else encoder=shiraz.runtime.NativeCapture.aacOpen(format.getInteger("sample-rate"),format.getInteger("channel-count"),format.getInteger("bitrate"));started=true;formatPending=true;ended=false;samples=0;baseTime=0;pump();}
    public synchronized void stop(){callbackGeneration++;callbackPending=false;if(video!=0)shiraz.runtime.NativeVideo.close(video);video=0;videoOutputs.clear();if(decoder!=0){if(opus)shiraz.runtime.NativeVideo.opusClose(decoder);else shiraz.runtime.NativePlayback.close(decoder);}decoder=0;if(encoder!=0)shiraz.runtime.NativeCapture.close(encoder);encoder=0;started=false;inputOwned=false;held=null;pending.reset();output.clear();}
    public void release(){stop();}
    public synchronized int dequeueInputBuffer(long timeout){if(!started)throw new IllegalStateException("Encoder stopped");if(inputOwned||ended||output.size()>=64||(video!=0&&!shiraz.runtime.NativeVideo.canQueue(video)))return -1;inputOwned=true;input.clear();return 0;}
    public java.nio.ByteBuffer getInputBuffer(int index){if(!started||!inputOwned||index!=0)throw new IllegalStateException("Input buffer not owned");return input;}
    public synchronized void queueInputBuffer(int index,int offset,int size,long time,int flags){
        if(!started||!inputOwned||index!=0||offset<0||size<0||(long)offset+size>input.capacity()||(flags&~(BUFFER_FLAG_END_OF_STREAM|BUFFER_FLAG_CODEC_CONFIG|BUFFER_FLAG_KEY_FRAME))!=0)throw new IllegalArgumentException("PCM input buffer");
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
    private void encode(byte[] pcm){if(output.size()>=256)throw new IllegalStateException("AAC output queue limit");byte[] bytes=shiraz.runtime.NativeCapture.aacEncode(encoder,pcm);output.addLast(new Packet(bytes,baseTime+samples*1000000L/format.getInteger("sample-rate"),0));samples+=1024;}
    public synchronized int dequeueOutputBuffer(BufferInfo info,long timeout){if(!started)throw new IllegalStateException("Encoder stopped");if(info==null)throw new NullPointerException("info");if(formatPending){formatPending=false;return INFO_OUTPUT_FORMAT_CHANGED;}if(video!=0){long[] next=shiraz.runtime.NativeVideo.poll(video);if(next==null)return INFO_TRY_AGAIN_LATER;int slot=(int)next[3],flags=next[0]<0?BUFFER_FLAG_END_OF_STREAM:0;if(flags==0){videoWidth=(int)next[0];videoHeight=(int)next[1];}videoOutputs.put(slot,new Packet(new byte[0],next[2],flags));info.set(0,flags==0?videoWidth*videoHeight*4:0,next[2],flags);return slot;}if(held!=null||output.isEmpty())return INFO_TRY_AGAIN_LATER;held=output.removeFirst();info.set(0,held.bytes.length,held.time,held.flags);return 0;}
    public synchronized java.nio.ByteBuffer getOutputBuffer(int index){if(video!=0){if(!videoOutputs.containsKey(index))throw new IllegalStateException("Output buffer not owned");return null;}if(index!=0||held==null)throw new IllegalStateException("Output buffer not owned");if(held.direct==null){held.direct=java.nio.ByteBuffer.allocateDirect(held.bytes.length).order(java.nio.ByteOrder.nativeOrder());held.direct.put(held.bytes).flip();}return held.direct.asReadOnlyBuffer().order(java.nio.ByteOrder.nativeOrder());}
    public synchronized void releaseOutputBuffer(int index,boolean render){if(video!=0){Packet packet=videoOutputs.remove(index);if(packet==null)throw new IllegalStateException("Video output buffer not owned");if((packet.flags&BUFFER_FLAG_END_OF_STREAM)==0)shiraz.runtime.NativeVideo.releaseFrame(video,index,render?videoSurface.shirazNativeWindow():0,render);pump();return;}if(index!=0||held==null)throw new IllegalStateException("Output buffer not owned");if(render)throw new UnsupportedOperationException("Audio codec cannot render to a surface");held=null;pump();}
    public synchronized MediaFormat getOutputFormat(){if(!started)throw new IllegalStateException("Encoder stopped");if(videoMime!=null){MediaFormat result=MediaFormat.createVideoFormat(videoMime,videoWidth,videoHeight);result.setInteger("color-format",2130708361);result.setInteger("stride",videoWidth);result.setInteger("slice-height",videoHeight);return result;}MediaFormat copy=MediaFormat.createAudioFormat(decoding?"audio/raw":"audio/mp4a-latm",decoding?outputRate:format.getInteger("sample-rate"),decoding?outputChannels:format.getInteger("channel-count"));if(decoding){copy.setInteger("pcm-encoding",2);return copy;}copy.setInteger("bitrate",format.getInteger("bitrate"));copy.setByteBuffer("csd-0",format.getByteBuffer("csd-0"));return copy;}

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
