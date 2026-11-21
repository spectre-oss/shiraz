// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.os.*;
public final class ParcelOwnedCopySmoke {
 public static void main(String[] args)throws Exception{
  java.io.File file=java.io.File.createTempFile("parcel-owned-", ".bin");
  try(java.io.FileOutputStream output=new java.io.FileOutputStream(file)){output.write(new byte[]{1,2,3});}
  Parcel source=Parcel.obtain(),copy=Parcel.obtain();
  try{
   try(ParcelFileDescriptor original=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)){source.writeFileDescriptor(original.getFileDescriptor());}
   copy.appendFrom(source,0,source.dataSize());source.recycle();copy.setDataPosition(0);
   try(ParcelFileDescriptor received=copy.readFileDescriptor()){
    copy.recycle();if(received.getStatSize()!=3)throw new AssertionError("Copy did not own descriptor");
   }
   source=Parcel.obtain();copy=Parcel.obtain();
   try(ParcelFileDescriptor original=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)){source.writeFileDescriptor(original.getFileDescriptor());}
   try(ParcelFileDescriptor attachment=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)){
    android.content.Intent unsupported=new android.content.Intent("descriptor").putExtra("fd",attachment);
    try{PendingIntentSnapshot.capture(unsupported);throw new AssertionError("PendingIntent descriptor accepted");}catch(IllegalArgumentException expected){}
    if(attachment.getStatSize()!=3)throw new AssertionError("Rejected capture closed caller descriptor");
   }
   copy.appendFrom(source,0,4);copy.setDataPosition(0);copy.writeInt(0);
   if(copy.hasFileDescriptors())throw new AssertionError("Overwrite retained descriptor");
   source.setDataPosition(0);try(ParcelFileDescriptor retained=source.readFileDescriptor()){if(retained.getStatSize()!=3)throw new AssertionError("Destination overwrite closed source");}
  }finally{source.recycle();copy.recycle();if(!file.delete())throw new AssertionError("Fixture cleanup");}
  System.out.println("PARCEL_OWNED_COPY_OK");
 }
}
