// SPDX-License-Identifier: GPL-3.0-only
package android.media;
public final class MediaCodecInfo {
 private final boolean encoder;private final String videoMime;
 MediaCodecInfo(boolean encoder){this.encoder=encoder;videoMime=null;}
 MediaCodecInfo(String mime){encoder=false;videoMime=mime;}
 public String getName(){return videoMime!=null?"OMX.shiraz.system."+videoMime.substring(videoMime.indexOf('/')+1):encoder?"OMX.shiraz.aac.encoder":"OMX.shiraz.aac.decoder";}
 public boolean isEncoder(){return encoder;}public boolean isAlias(){return false;}public boolean isHardwareAccelerated(){return false;}public boolean isSoftwareOnly(){return videoMime==null||!videoMime.startsWith("video/");}public boolean isVendor(){return false;}
 public String[] getSupportedTypes(){return new String[]{videoMime!=null?videoMime:"audio/mp4a-latm"};}
 public CodecCapabilities getCapabilitiesForType(String type){if(!(videoMime!=null?videoMime:"audio/mp4a-latm").equalsIgnoreCase(type))throw new IllegalArgumentException("codec MIME");return new CodecCapabilities(videoMime);}
 public static final class CodecProfileLevel {public static final int AACObjectLC=2;public int profile,level;}
 public static final class CodecCapabilities {
  public static final String FEATURE_AdaptivePlayback="adaptive-playback",FEATURE_TunneledPlayback="tunneled-playback",FEATURE_SecurePlayback="secure-playback";
  private final String mime;
  public CodecProfileLevel[] profileLevels;public int[] colorFormats=new int[0];
  CodecCapabilities(String type){mime=type==null?"audio/mp4a-latm":type;profileLevels=new CodecProfileLevel[0];if(type==null){CodecProfileLevel p=new CodecProfileLevel();p.profile=2;profileLevels=new CodecProfileLevel[]{p};}if(mime.startsWith("video/"))colorFormats=new int[]{2130708361};}
  public boolean isFeatureSupported(String feature){return false;}public boolean isFeatureRequired(String feature){return false;}
  public VideoCapabilities getVideoCapabilities(){return mime.startsWith("video/")?new VideoCapabilities():null;}
  public boolean isFormatSupported(MediaFormat format){
   if(format==null||!mime.equalsIgnoreCase(format.getString("mime")))return false;
   if(mime.startsWith("video/")){int w=format.getInteger("width",1),h=format.getInteger("height",1);return new VideoCapabilities().isSizeSupported(w,h);}
   int channels=format.getInteger("channel-count",1),rate=format.getInteger("sample-rate",48000);return channels>=1&&channels<=2&&(mime.equals("audio/opus")?rate==48000:new AudioCapabilities().isSampleRateSupported(rate));
  }
  public AudioCapabilities getAudioCapabilities(){return mime.startsWith("audio/")?new AudioCapabilities():null;}
  public int getMaxSupportedInstances(){return 16;}
 }
 public static final class VideoCapabilities {
  public android.util.Range<Integer> getSupportedWidths(){return new android.util.Range<Integer>(1,8192);}
  public android.util.Range<Integer> getSupportedHeights(){return new android.util.Range<Integer>(1,8192);}
  public android.util.Range<Double> getSupportedFrameRates(){return new android.util.Range<Double>(1.0,120.0);}
  public int getWidthAlignment(){return 1;}public int getHeightAlignment(){return 1;}
  public boolean isSizeSupported(int w,int h){return w>0&&h>0&&w<=8192&&h<=8192&&(long)w*h<=16*1024*1024;}
  public boolean areSizeAndRateSupported(int w,int h,double rate){return isSizeSupported(w,h)&&rate>0&&rate<=120;}
 }
 public static final class AudioCapabilities {
  public int getMaxInputChannelCount(){return 2;}
  public boolean isSampleRateSupported(int rate){return rate>=8000&&rate<=48000&&MediaCodec.rateIndex(rate)>=0;}
 }
}
