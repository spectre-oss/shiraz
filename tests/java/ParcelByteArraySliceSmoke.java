// SPDX-License-Identifier: GPL-3.0-only
import android.os.Parcel;
import java.util.Arrays;
public final class ParcelByteArraySliceSmoke {
 public static void main(String[] args){
  Parcel p=Parcel.obtain();try{
   byte[] source={11,22,33,44,55};
   p.writeByteArray(source,1,3);p.writeInt(0x12345678);source[2]=99;
   p.writeByteArray(source,source.length,0);p.writeByteArray(null,-1,Integer.MAX_VALUE);
   p.setDataPosition(0);
   if(!Arrays.equals(p.createByteArray(),new byte[]{22,33,44})||p.readInt()!=0x12345678||p.createByteArray().length!=0||p.createByteArray()!=null||p.dataAvail()!=0)throw new AssertionError("Slice round trip");
   for(int[] range:new int[][]{{-1,1},{0,-1},{4,2},{6,0},{Integer.MAX_VALUE,1},{1,Integer.MAX_VALUE}}){
    byte[] before=p.marshall();int pos=p.dataPosition();
    try{p.writeByteArray(source,range[0],range[1]);throw new AssertionError("Invalid range accepted");}catch(ArrayIndexOutOfBoundsException expected){}
    if(p.dataPosition()!=pos||!Arrays.equals(before,p.marshall()))throw new AssertionError("Invalid range changed parcel");
   }
   p.setDataPosition(0);p.writeByteArray(new byte[]{1,2,3},1,2);p.setDataPosition(0);byte[] target=new byte[2];p.readByteArray(target);
   if(!Arrays.equals(target,new byte[]{2,3}))throw new AssertionError("Read into array");
  }finally{p.recycle();}
  System.out.println("PARCEL_BYTE_ARRAY_SLICE_OK");
 }
}
