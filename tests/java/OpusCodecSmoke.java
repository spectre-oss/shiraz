// SPDX-License-Identifier: GPL-3.0-only
public final class OpusCodecSmoke {
 public static void main(String[] args)throws Exception {
  boolean listed=false;
  for(android.media.MediaCodecInfo info:new android.media.MediaCodecList(0).getCodecInfos())for(String type:info.getSupportedTypes())if(type.equals("audio/opus")){listed=true;if(info.getCapabilitiesForType(type).getVideoCapabilities()!=null)throw new AssertionError("Opus advertised as video");}
  if(!listed)throw new AssertionError("OS Opus not listed");
  byte[] head={'O','p','u','s','H','e','a','d',1,1,0,0,(byte)0x80,(byte)0xbb,0,0,0,0,0};
  android.media.MediaFormat format=android.media.MediaFormat.createAudioFormat("audio/opus",48000,1);format.setByteBuffer("csd-0",java.nio.ByteBuffer.wrap(head));
  android.media.MediaCodec codec=android.media.MediaCodec.createByCodecName("OMX.shiraz.system.opus");
  try{codec.configure(format,null,null,0);codec.start();int index=codec.dequeueInputBuffer(0);codec.getInputBuffer(index).put(new byte[]{(byte)0xf8,(byte)0xff,(byte)0xfe});codec.queueInputBuffer(index,0,3,0,0);
   android.media.MediaCodec.BufferInfo info=new android.media.MediaCodec.BufferInfo();if(codec.dequeueOutputBuffer(info,0)!=-2)throw new AssertionError("Missing output format");index=codec.dequeueOutputBuffer(info,0);if(index<0||info.size!=1920||!codec.getOutputBuffer(index).isDirect())throw new AssertionError("Opus PCM/direct buffer");codec.releaseOutputBuffer(index,false);
   codec.flush();if(codec.getOutputFormat().getInteger("sample-rate")!=48000)throw new AssertionError("Opus flush format");
   System.out.println("SYSTEM_OPUS_CODEC_PCM_FLUSH_OK");
  }finally{codec.release();}
 }
}
