// SPDX-License-Identifier: GPL-3.0-only
package android.os;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
/** glibc allocator accounting for this process, including mmap-backed malloc blocks. */
public final class Debug {
 static {System.loadLibrary("shiraz-host");}
 private Debug(){}
 public static native long getNativeHeapAllocatedSize();
 public static native long getNativeHeapSize();
 public static native long getNativeHeapFreeSize();
 public static String getRuntimeStat(String statName){
  if(statName==null)return null;
  Map<String,String> stats=getRuntimeStats();
  return stats.containsKey(statName)?stats.get(statName):null;
 }
 public static Map<String,String> getRuntimeStats(){
  Map<String,String> stats=new HashMap<String,String>();
  Runtime runtime=Runtime.getRuntime();
  long allocated=Math.max(0L,runtime.totalMemory()-runtime.freeMemory());
  stats.put("art.gc.gc-count","0");
  stats.put("art.gc.gc-time","0");
  stats.put("art.gc.bytes-allocated",Long.toString(allocated));
  stats.put("art.gc.bytes-freed","0");
  stats.put("art.gc.blocking-gc-count","0");
  stats.put("art.gc.blocking-gc-time","0");
  return Collections.unmodifiableMap(stats);
 }
 public static int getBinderLocalObjectCount(){return 0;}
 public static int getBinderProxyObjectCount(){return 0;}
 public static int getBinderDeathObjectCount(){return 0;}
 public static int getLoadedClassCount(){return 1;}
 public static void dumpHprofData(String fileName) throws java.io.IOException{
  if(fileName==null)throw new NullPointerException("fileName");
  if(fileName.length()==0||fileName.length()>4096)throw new IllegalArgumentException("fileName");
  File file=new File(fileName);
  File parent=file.getParentFile();
  if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new java.io.IOException("hprof parent");
  if(!file.exists()&&!file.createNewFile())throw new java.io.IOException("hprof create");
 }
 public static void getMemoryInfo(MemoryInfo memoryInfo){
  if(memoryInfo==null)throw new NullPointerException("memoryInfo");
  Runtime runtime=Runtime.getRuntime();
  int javaKb=(int)Math.min(Integer.MAX_VALUE,Math.max(0L,(runtime.totalMemory()-runtime.freeMemory())/1024L));
  int nativeKb=(int)Math.min(Integer.MAX_VALUE,Math.max(0L,getNativeHeapAllocatedSize()/1024L));
  memoryInfo.dalvikPss=javaKb;
  memoryInfo.dalvikPrivateDirty=javaKb;
  memoryInfo.dalvikSharedDirty=0;
  memoryInfo.nativePss=nativeKb;
  memoryInfo.nativePrivateDirty=nativeKb;
  memoryInfo.nativeSharedDirty=0;
  memoryInfo.otherPss=0;
  memoryInfo.otherPrivateDirty=0;
  memoryInfo.otherSharedDirty=0;
 }
 public static class MemoryInfo implements Parcelable {
  public int dalvikPss,dalvikPrivateDirty,dalvikSharedDirty;
  public int nativePss,nativePrivateDirty,nativeSharedDirty;
  public int otherPss,otherPrivateDirty,otherSharedDirty;
  public MemoryInfo(){}
  public int getTotalPss(){return dalvikPss+nativePss+otherPss;}
  public int getTotalPrivateDirty(){return dalvikPrivateDirty+nativePrivateDirty+otherPrivateDirty;}
  public int getTotalSharedDirty(){return dalvikSharedDirty+nativeSharedDirty+otherSharedDirty;}
  public int getTotalPrivateClean(){return 0;}
  public int getTotalSharedClean(){return 0;}
  public int getTotalSwappablePss(){return 0;}
  public String getMemoryStat(String statName){
   if(statName==null)return null;
   Map<String,String> stats=getMemoryStats();
   return stats.containsKey(statName)?stats.get(statName):null;
  }
  public Map<String,String> getMemoryStats(){
   Map<String,String> stats=new HashMap<String,String>();
   stats.put("summary.java-heap",Integer.toString(dalvikPrivateDirty));
   stats.put("summary.native-heap",Integer.toString(nativePrivateDirty));
   stats.put("summary.code","0");
   stats.put("summary.stack","0");
   stats.put("summary.graphics","0");
   stats.put("summary.private-other",Integer.toString(otherPrivateDirty));
   stats.put("summary.system","0");
   stats.put("summary.total-pss",Integer.toString(getTotalPss()));
   stats.put("summary.total-swap","0");
   return Collections.unmodifiableMap(stats);
  }
  public int describeContents(){return 0;}
  public void writeToParcel(Parcel dest,int flags){
   if(dest==null)throw new NullPointerException("dest");
   dest.writeInt(dalvikPss);dest.writeInt(dalvikPrivateDirty);dest.writeInt(dalvikSharedDirty);
   dest.writeInt(nativePss);dest.writeInt(nativePrivateDirty);dest.writeInt(nativeSharedDirty);
   dest.writeInt(otherPss);dest.writeInt(otherPrivateDirty);dest.writeInt(otherSharedDirty);
  }
  public void readFromParcel(Parcel source){
   if(source==null)throw new NullPointerException("source");
   dalvikPss=source.readInt();dalvikPrivateDirty=source.readInt();dalvikSharedDirty=source.readInt();
   nativePss=source.readInt();nativePrivateDirty=source.readInt();nativeSharedDirty=source.readInt();
   otherPss=source.readInt();otherPrivateDirty=source.readInt();otherSharedDirty=source.readInt();
  }
  public static final Parcelable.Creator<MemoryInfo> CREATOR=new Parcelable.Creator<MemoryInfo>(){
   public MemoryInfo createFromParcel(Parcel source){MemoryInfo info=new MemoryInfo();info.readFromParcel(source);return info;}
   public MemoryInfo[] newArray(int size){return new MemoryInfo[size];}
  };
 }
}
