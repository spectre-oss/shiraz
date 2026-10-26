// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import android.content.Context;
import android.net.Uri;
import android.graphics.Bitmap;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
/** Bounded MPEG-4 duration/audio metadata reader; video decoding is unavailable. */
public class MediaMetadataRetriever implements AutoCloseable {
 public static final int METADATA_KEY_DURATION=9,METADATA_KEY_MIMETYPE=12,METADATA_KEY_HAS_AUDIO=16,METADATA_KEY_HAS_VIDEO=17,METADATA_KEY_VIDEO_WIDTH=18,METADATA_KEY_VIDEO_HEIGHT=19,METADATA_KEY_BITRATE=20;
 public static final int OPTION_PREVIOUS_SYNC=0,OPTION_NEXT_SYNC=1,OPTION_CLOSEST_SYNC=2,OPTION_CLOSEST=3;
 private long duration=-1;private boolean audio,video,recognized;private int boxes;private MediaDataSource owned;
 public MediaMetadataRetriever(){}
 public void setDataSource(Context context,Uri uri){if(context==null||uri==null)throw new IllegalArgumentException("media source");try(InputStream in=context.getContentResolver().openInputStream(uri)){parse(in);}catch(IOException e){throw new IllegalArgumentException("Cannot read media metadata",e);}}
 public void setDataSource(String path){if(path==null)throw new IllegalArgumentException("path");try(InputStream in=new FileInputStream(path)){parse(in);}catch(IOException e){throw new IllegalArgumentException("Cannot read media metadata",e);}}
 public void setDataSource(FileDescriptor fd){setDataSource(fd,0,256*1024*1024L);}
 public void setDataSource(FileDescriptor fd,final long offset,final long length){
  if(fd==null||offset<0||length<0||offset>Long.MAX_VALUE-length)throw new IllegalArgumentException("media descriptor range");
  try(final android.os.ParcelFileDescriptor duplicate=android.os.ParcelFileDescriptor.dup(fd);final FileInputStream stream=new FileInputStream(duplicate.getFileDescriptor())){
   final FileChannel channel=stream.getChannel();InputStream in=new InputStream(){long position;public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0]&255;}public int read(byte[] b,int at,int n)throws IOException{if(position>=length)return -1;int count=channel.read(ByteBuffer.wrap(b,at,(int)Math.min(n,length-position)),offset+position);if(count>0)position+=count;return count;}public long skip(long n){long count=Math.min(Math.max(n,0),length-position);position+=count;return count;}};parse(in);
  }catch(IOException e){throw new IllegalArgumentException("Cannot read media descriptor",e);}
 }
 public void setDataSource(final MediaDataSource source){if(source==null)throw new IllegalArgumentException("source");try{parse(new InputStream(){long position;public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0]&255;}public int read(byte[] b,int offset,int length)throws IOException{int n=source.readAt(position,b,offset,length);if(n>0)position+=n;return n;}public long skip(long n){position+=n;return n;}});owned=source;}catch(IOException e){throw new IllegalArgumentException("Cannot read media source",e);}}
 private void parse(InputStream in)throws IOException{release();boxes=0;walk(in,256*1024*1024L,0);if(!recognized)throw new IllegalArgumentException("Only MPEG-4 media metadata is supported");}
 private static byte[] read(InputStream in,int count)throws IOException{byte[] bytes=new byte[count];int at=0;while(at<count){int n=in.read(bytes,at,count-at);if(n<=0)throw new EOFException("Truncated media atom");at+=n;}return bytes;}
 private static void skip(InputStream in,long count)throws IOException{while(count>0){long n=in.skip(count);if(n>0)count-=n;else{if(in.read()<0)throw new EOFException("Truncated media atom");count--;}}}
 private static long uint(byte[] bytes,int offset){return ByteBuffer.wrap(bytes,offset,4).getInt()&0xffffffffL;}
 private void walk(InputStream in,long limit,int depth)throws IOException{
  if(depth>8)throw new IOException("Media nesting limit");long consumed=0;
  while(consumed<limit){int first=in.read();if(first<0){if(depth==0)return;throw new EOFException("Truncated media container");}if(limit-consumed<8)throw new IOException("Invalid media atom header");byte[] rest=read(in,7);byte[] head=new byte[8];head[0]=(byte)first;System.arraycopy(rest,0,head,1,7);long size=uint(head,0),header=8;String type=new String(head,4,4,StandardCharsets.US_ASCII);if(size==1){byte[] wide=read(in,8);size=ByteBuffer.wrap(wide).getLong();header=16;}if(size==0){if(depth==0)return;size=limit-consumed;}if(size<header||size>limit-consumed||++boxes>100000)throw new IOException("Invalid media atom size");long body=size-header;
   if("ftyp".equals(type)){recognized=true;skip(in,body);}
   else if("moov".equals(type)||"trak".equals(type)||"mdia".equals(type)){walk(in,body,depth+1);}
   else if("mvhd".equals(type)||"mdhd".equals(type)){int length=(int)Math.min(body,32);byte[] data=read(in,length);if(data.length<20)throw new IOException("Truncated media duration");int version=data[0]&255;long scale,ticks;if(version==0){scale=uint(data,12);ticks=uint(data,16);}else if(version==1&&data.length>=32){scale=uint(data,20);ticks=ByteBuffer.wrap(data,24,8).getLong();}else throw new IOException("Unsupported media duration version");if(scale>0&&ticks>=0&&ticks<=Long.MAX_VALUE/1000)duration=Math.max(duration,ticks*1000/scale);skip(in,body-length);}
   else if("hdlr".equals(type)){int length=(int)Math.min(body,12);byte[] data=read(in,length);if(length>=12){String handler=new String(data,8,4,StandardCharsets.US_ASCII);audio|="soun".equals(handler);video|="vide".equals(handler);}skip(in,body-length);}
   else skip(in,body);consumed+=size;
  }
 }
 public String extractMetadata(int key){if(key==METADATA_KEY_DURATION)return duration<0?null:Long.toString(duration);if(key==METADATA_KEY_HAS_AUDIO)return audio?"yes":null;if(key==METADATA_KEY_HAS_VIDEO)return video?"yes":null;if(key==METADATA_KEY_MIMETYPE)return recognized?(video?"video/mp4":"audio/mp4"):null;return null;}
 public Bitmap getFrameAtTime(){return null;}public Bitmap getFrameAtTime(long time){return null;}public Bitmap getFrameAtTime(long time,int option){return null;}public Bitmap getScaledFrameAtTime(long time,int option,int width,int height){if(width<1||height<1)throw new IllegalArgumentException("thumbnail dimensions");return null;}
 public void release(){duration=-1;audio=video=recognized=false;if(owned!=null){try{owned.close();}catch(IOException ignored){}owned=null;}}
 public void close(){release();}
}
