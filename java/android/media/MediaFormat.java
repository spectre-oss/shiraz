// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import java.nio.ByteBuffer;
import java.util.*;
public final class MediaFormat {
 public static final String KEY_WIDTH="width",KEY_HEIGHT="height",KEY_MIME="mime",KEY_SAMPLE_RATE="sample-rate",KEY_CHANNEL_COUNT="channel-count",KEY_BIT_RATE="bitrate",KEY_AAC_PROFILE="aac-profile",KEY_MAX_INPUT_SIZE="max-input-size",MIMETYPE_AUDIO_AAC="audio/mp4a-latm";
 private final Map<String,Object> values=new HashMap<String,Object>();
 public MediaFormat(){}
 public static MediaFormat createAudioFormat(String mime,int rate,int channels){MediaFormat f=new MediaFormat();f.setString(KEY_MIME,mime);f.setInteger(KEY_SAMPLE_RATE,rate);f.setInteger(KEY_CHANNEL_COUNT,channels);return f;}
 public static MediaFormat createVideoFormat(String mime,int width,int height){MediaFormat f=new MediaFormat();f.setString(KEY_MIME,mime);f.setInteger(KEY_WIDTH,width);f.setInteger(KEY_HEIGHT,height);return f;}
 public void setInteger(String key,int value){values.put(key,value);}public void setLong(String key,long value){values.put(key,value);}public void setString(String key,String value){values.put(key,value);}public void setFloat(String key,float value){values.put(key,value);}
 public int getInteger(String key){Object v=values.get(key);if(!(v instanceof Number))throw new IllegalArgumentException("Missing integer "+key);return ((Number)v).intValue();}
 public int getInteger(String key,int fallback){return containsKey(key)?getInteger(key):fallback;}
 public long getLong(String key){Object v=values.get(key);if(!(v instanceof Number))throw new IllegalArgumentException("Missing long "+key);return ((Number)v).longValue();}
 public String getString(String key){return (String)values.get(key);}public boolean containsKey(String key){return values.containsKey(key);}
 public void setByteBuffer(String key,ByteBuffer value){ByteBuffer copy=value.duplicate();byte[] bytes=new byte[copy.remaining()];copy.get(bytes);values.put(key,bytes);}
 public ByteBuffer getByteBuffer(String key){byte[] bytes=(byte[])values.get(key);return bytes==null?null:ByteBuffer.wrap(bytes.clone());}
 public String toString(){return values.toString();}
}
