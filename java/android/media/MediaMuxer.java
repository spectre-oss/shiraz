// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Bounded, single-track AAC-LC MPEG-4 writer for voice recordings. */
public final class MediaMuxer {
 public static final class OutputFormat {public static final int MUXER_OUTPUT_MPEG_4=0;private OutputFormat(){}}
 private RandomAccessFile file;private MediaFormat format;private boolean started,stopped;private long dataOffset,dataSize;private final List<Integer> sizes=new ArrayList<Integer>();
 public MediaMuxer(String path,int outputFormat)throws IOException{if(path==null||outputFormat!=0)throw new IllegalArgumentException("Only AAC MPEG-4 output is supported");file=new RandomAccessFile(path,"rw");file.setLength(0);}
 public int addTrack(MediaFormat value){if(started||format!=null||value==null||!"audio/mp4a-latm".equals(value.getString("mime"))||value.getInteger("channel-count")<1||value.getInteger("channel-count")>2)throw new IllegalArgumentException("AAC track");format=MediaFormat.createAudioFormat("audio/mp4a-latm",value.getInteger("sample-rate"),value.getInteger("channel-count"));format.setByteBuffer("csd-0",value.getByteBuffer("csd-0"));return 0;}
 public void start(){if(started||stopped||format==null||file==null)throw new IllegalStateException("Muxer state");try{file.write(box("ftyp",ascii("M4A "),ints(0),ascii("M4A isommp42")));dataOffset=file.getFilePointer();file.writeInt(0);file.write(ascii("mdat"));started=true;}catch(IOException e){throw new IllegalStateException("Cannot start MPEG-4 output",e);}}
 public void writeSampleData(int track,ByteBuffer data,MediaCodec.BufferInfo info){
  if(!started||stopped||track!=0)throw new IllegalStateException("Muxer state");if(data==null||info==null||info.offset<0||info.size<0||(long)info.offset+info.size>data.limit()||info.size>1024*1024)throw new IllegalArgumentException("AAC sample");if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0||info.size==0)return;
  if(sizes.size()>=1000000||dataSize+info.size>256*1024*1024)throw new IllegalStateException("Voice recording exceeds limit");
  byte[] bytes=new byte[info.size];ByteBuffer copy=data.duplicate();copy.position(info.offset);copy.get(bytes);try{file.write(bytes);sizes.add(info.size);dataSize+=info.size;}catch(IOException e){throw new IllegalStateException("Cannot write AAC sample",e);}
 }
 public void stop(){if(!started||stopped||file==null)throw new IllegalStateException("Muxer not started");try{long end=file.getFilePointer();file.seek(dataOffset);file.writeInt((int)dataSize+8);file.seek(end);file.write(movie());stopped=true;}catch(IOException e){throw new IllegalStateException("Cannot finish MPEG-4 output",e);}}
 public void release(){if(file==null)return;try{file.close();}catch(IOException e){throw new IllegalStateException(e);}finally{file=null;}}
 private static byte[] ascii(String text){return text.getBytes(StandardCharsets.US_ASCII);}
 private static byte[] ints(int... numbers){ByteBuffer b=ByteBuffer.allocate(numbers.length*4);for(int n:numbers)b.putInt(n);return b.array();}
 private static byte[] shorts(int... numbers){ByteBuffer b=ByteBuffer.allocate(numbers.length*2);for(int n:numbers)b.putShort((short)n);return b.array();}
 private static byte[] join(byte[]... chunks){ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] b:chunks)out.write(b,0,b.length);return out.toByteArray();}
 private static byte[] box(String type,byte[]... chunks){byte[] data=join(chunks);return join(ints(data.length+8),ascii(type),data);}
 private static byte[] full(String type,int flags,byte[]... chunks){return box(type,ints(flags),join(chunks));}
 private static byte[] matrix(){return ints(0x10000,0,0,0,0x10000,0,0,0,0x40000000);}
 private static byte[] descriptor(int tag,byte[] data){if(data.length>127)throw new IllegalArgumentException("AAC descriptor length");return join(new byte[]{(byte)tag,(byte)data.length},data);}
 private byte[] movie(){
  int rate=format.getInteger("sample-rate"),channels=format.getInteger("channel-count"),duration=sizes.size()*1024;
  byte[] mvhd=full("mvhd",0,ints(0,0,rate,duration,0x10000),shorts(0x100,0),new byte[8],matrix(),new byte[24],ints(2));
  byte[] tkhd=full("tkhd",7,ints(0,0,1,0,duration),new byte[8],shorts(0,0,0x100,0),matrix(),ints(0,0));
  byte[] mdhd=full("mdhd",0,ints(0,0,rate,duration),shorts(0x55c4,0));
  byte[] hdlr=full("hdlr",0,ints(0),ascii("soun"),new byte[12],ascii("Shiraz audio\0"));
  ByteBuffer config=format.getByteBuffer("csd-0");byte[] csd=new byte[config.remaining()];config.get(csd);
  byte[] decoder=descriptor(4,join(new byte[]{0x40,0x15,0,0,0},ints(32000,32000),descriptor(5,csd)));
  byte[] esds=full("esds",0,descriptor(3,join(shorts(1),new byte[]{0},decoder,descriptor(6,new byte[]{2}))));
  byte[] mp4a=box("mp4a",new byte[6],shorts(1),new byte[8],shorts(channels,16,0,0),ints(rate<<16),esds);
  byte[] stsd=full("stsd",0,ints(1),mp4a);
  byte[] stts=full("stts",0,sizes.isEmpty()?ints(0):ints(1,sizes.size(),1024));
  byte[] stsc=full("stsc",0,sizes.isEmpty()?ints(0):ints(1,1,sizes.size(),1));
  ByteBuffer sampleSizes=ByteBuffer.allocate(8+sizes.size()*4);sampleSizes.putInt(0).putInt(sizes.size());for(int size:sizes)sampleSizes.putInt(size);
  byte[] stsz=full("stsz",0,sampleSizes.array());
  byte[] stco=full("stco",0,sizes.isEmpty()?ints(0):ints(1,(int)dataOffset+8));
  byte[] stbl=box("stbl",stsd,stts,stsc,stsz,stco);
  byte[] dinf=box("dinf",full("dref",0,ints(1),full("url ",1)));
  byte[] minf=box("minf",full("smhd",0,shorts(0,0)),dinf,stbl);
  return box("moov",mvhd,box("trak",tkhd,box("mdia",mdhd,hdlr,minf)));
 }
}
