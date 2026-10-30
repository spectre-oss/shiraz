// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.Typeface;
import android.graphics.Paint;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.graphics.fonts.FontStyle;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
public final class ModernFontSmoke {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  File file=new File(args[0]);byte[] bytes=Files.readAllBytes(file.toPath());
  Font normal=new Font.Builder(file).build();check(normal.getStyle().equals(new FontStyle(400,0)),"read regular font metadata");
  ByteBuffer direct=ByteBuffer.allocateDirect(bytes.length);direct.put(bytes).flip();Font.Builder fromBuffer=new Font.Builder(direct);direct.put(0,(byte)0xff);
  Font snapshot=fromBuffer.build();check(snapshot.getStyle().equals(normal.getStyle()),"builder owns byte snapshot");
  try{snapshot.getBuffer().put(0,(byte)0);throw new AssertionError("mutable font buffer");}catch(ReadOnlyBufferException expected){}
  Font bold=new Font.Builder(normal).setWeight(700).build();FontFamily.Builder familyBuilder=new FontFamily.Builder(normal);FontFamily first=familyBuilder.build();familyBuilder.addFont(bold);FontFamily family=familyBuilder.build();
  check(first.getSize()==1&&family.getSize()==2,"family snapshot");
  try{familyBuilder.addFont(snapshot);throw new AssertionError("duplicate style accepted");}catch(IllegalArgumentException expected){}
  Typeface face=new Typeface.CustomFallbackBuilder(family).setStyle(new FontStyle(700,0)).build();
  check(face.isBold()&&face.shirazFamily().equals(bold.shirazTypeface().shirazFamily()),"select requested font style");
  Paint paint=new Paint();paint.setTypeface(face);paint.setTextSize(24);check(paint.measureText("Font rendering")>0,"native custom font shaping");
  try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)){
   try(ParcelFileDescriptor.AutoCloseInputStream owner=new ParcelFileDescriptor.AutoCloseInputStream(fd.dup())){
    owner.getChannel().position(7);Font descriptorFont=new Font.Builder(fd).build();check(fd.getStatSize()==bytes.length&&owner.getChannel().position()==7&&descriptorFont.getStyle().equals(normal.getStyle()),"font builder preserves descriptor ownership and position");
   }
  }
  try{new FontStyle(0,0);throw new AssertionError("zero weight accepted");}catch(IllegalArgumentException expected){}
  try{new Font.Builder(normal).setSlant(2);throw new AssertionError("invalid slant accepted");}catch(IllegalArgumentException expected){}
  try{new Font.Builder(ByteBuffer.allocateDirect(16)).build();throw new AssertionError("invalid font accepted");}catch(IOException expected){}
  try{new Font.Builder(normal).setTtcIndex(1).build();throw new AssertionError("unsupported collection accepted");}catch(IOException expected){}
  System.out.println("MODERN_FONT_LOADING_AND_MATCHING_OK");
 }
}
