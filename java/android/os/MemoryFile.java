// SPDX-License-Identifier: GPL-3.0-only
package android.os;
import java.io.*;
/** Descriptor-backed shared memory, with bounded positional Java access. */
public class MemoryFile {
 static {System.loadLibrary("android");}
 private final int length;private ParcelFileDescriptor descriptor;private RandomAccessFile access;private boolean active=true;
 private static native int nativeCreate(String name,int length)throws IOException;
 public MemoryFile(String name,int length)throws IOException{
  if(length<0||length>128*1024*1024)throw new IOException("MemoryFile length outside supported bounds");this.length=length;
  descriptor=ParcelFileDescriptor.adoptFd(nativeCreate(name,length));
  try{access=new RandomAccessFile("/proc/self/fd/"+descriptor.getFd(),"rw");}catch(IOException error){descriptor.close();throw error;}
 }
 public static int getSize(FileDescriptor fd)throws IOException{long size=shiraz.runtime.NativeFd.size(fd);if(size>Integer.MAX_VALUE)throw new IOException("MemoryFile too large");return (int)size;}
 public int length(){return length;}
 public synchronized FileDescriptor getFileDescriptor()throws IOException{if(descriptor==null)throw new IOException("MemoryFile closed");return descriptor.getFileDescriptor();}
 public synchronized void deactivate(){active=false;}
 public synchronized void close(){active=false;try{if(access!=null)access.close();}catch(IOException ignored){}finally{access=null;}try{if(descriptor!=null)descriptor.close();}catch(IOException ignored){}finally{descriptor=null;}}
 public boolean isPurgingAllowed(){return false;}
 public synchronized boolean allowPurging(boolean allow)throws IOException{if(descriptor==null)throw new IOException("MemoryFile closed");if(allow)throw new IOException("Purgeable shared memory is unavailable");return false;}
 private void bounds(byte[] buffer,int offset,int memory,int count)throws IOException{if(!active||access==null)throw new IOException("MemoryFile inactive");if(buffer==null)throw new NullPointerException("buffer");if(offset<0||memory<0||count<0||(long)offset+count>buffer.length||(long)memory+count>length)throw new IndexOutOfBoundsException();}
 public synchronized int readBytes(byte[] buffer,int srcOffset,int destOffset,int count)throws IOException{bounds(buffer,destOffset,srcOffset,count);access.seek(srcOffset);access.readFully(buffer,destOffset,count);return count;}
 public synchronized void writeBytes(byte[] buffer,int srcOffset,int destOffset,int count)throws IOException{bounds(buffer,srcOffset,destOffset,count);access.seek(destOffset);access.write(buffer,srcOffset,count);}
 public InputStream getInputStream(){return new InputStream(){private int position;public int available(){return length-position;}public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0]&255;}public int read(byte[] b,int off,int count)throws IOException{if(b==null)throw new NullPointerException();if(off<0||count<0||(long)off+count>b.length)throw new IndexOutOfBoundsException();if(count==0)return 0;if(position==length)return -1;int n=readBytes(b,position,off,Math.min(count,length-position));position+=n;return n;}};}
 public OutputStream getOutputStream(){return new OutputStream(){private int position;public void write(int value)throws IOException{write(new byte[]{(byte)value},0,1);}public void write(byte[] b,int off,int count)throws IOException{writeBytes(b,off,position,count);position+=count;}};}
 @Override protected void finalize()throws Throwable{try{close();}finally{super.finalize();}}
}
