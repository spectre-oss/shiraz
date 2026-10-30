// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.fonts;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.nio.ByteBuffer;
/** A validated font backed by the existing bounded native font registry. */
public final class Font {
 private final byte[] bytes;
 private final FontStyle style;
 private final Typeface face;
 private final File file;
 private Font(byte[] bytes,FontStyle style,Typeface face,File file){this.bytes=bytes;this.style=style;this.face=face;this.file=file;}
 public FontStyle getStyle(){return style;}
 public int getTtcIndex(){return 0;}
 public FontVariationAxis[] getAxes(){return new FontVariationAxis[0];}
 public File getFile(){return file;}
 public ByteBuffer getBuffer(){return ByteBuffer.wrap(bytes).asReadOnlyBuffer();}
 public Typeface shirazTypeface(){return face;}
 public static final class Builder {
  private interface Source {byte[] read() throws IOException;}
  private final Source source;
  private File file;
  private int weight=-1,slant=-1,ttc;
  private FontVariationAxis[] axes;
  private static final int LIMIT=16*1024*1024;
  private static byte[] read(InputStream stream)throws IOException{
   try(InputStream in=stream){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;
    while((n=in.read(chunk))!=-1){if(n>LIMIT-out.size())throw new IOException("Font exceeds 16 MiB");out.write(chunk,0,n);}
    if(out.size()==0)throw new IOException("Empty font");return out.toByteArray();
   }
  }
  public Builder(final Resources resources,final int id){if(resources==null)throw new NullPointerException("resources");source=new Source(){public byte[] read()throws IOException{return Builder.read(resources.openRawResource(id));}};}
  public Builder(final AssetManager assets,final String path){if(assets==null||path==null)throw new NullPointerException("font asset");source=new Source(){public byte[] read()throws IOException{return Builder.read(assets.open(path));}};}
  public Builder(final File file){if(file==null)throw new NullPointerException("file");this.file=file;source=new Source(){public byte[] read()throws IOException{return Builder.read(new FileInputStream(file));}};}
  public Builder(final ParcelFileDescriptor fd){if(fd==null)throw new NullPointerException("fd");source=new Source(){public byte[] read()throws IOException{
   try(ParcelFileDescriptor.AutoCloseInputStream input=new ParcelFileDescriptor.AutoCloseInputStream(fd.dup())){
    java.nio.channels.FileChannel channel=input.getChannel();long size=channel.size();if(size<1||size>LIMIT)throw new IOException("Font size");
    ByteBuffer buffer=ByteBuffer.allocate((int)size);while(buffer.hasRemaining()){int n=channel.read(buffer,buffer.position());if(n<=0)throw new IOException("Truncated font descriptor");}return buffer.array();
   }
  }};}
  public Builder(ByteBuffer buffer){
   if(buffer==null)throw new NullPointerException("buffer");if(!buffer.isDirect())throw new IllegalArgumentException("Font buffer must be direct");
   ByteBuffer copy=buffer.duplicate();if(copy.remaining()<1||copy.remaining()>LIMIT)throw new IllegalArgumentException("Font size");
   final byte[] snapshot=new byte[copy.remaining()];copy.get(snapshot);source=new Source(){public byte[] read(){return snapshot;}};
  }
  public Builder(Font font){if(font==null)throw new NullPointerException("font");final byte[] snapshot=font.bytes;source=new Source(){public byte[] read(){return snapshot;}};weight=font.style.getWeight();slant=font.style.getSlant();file=font.file;}
  public Builder setWeight(int value){new FontStyle(value,slant<0?0:slant);weight=value;return this;}
  public Builder setSlant(int value){new FontStyle(weight<0?400:weight,value);slant=value;return this;}
  public Builder setTtcIndex(int value){if(value<0)throw new IllegalArgumentException("TTC index");ttc=value;return this;}
  public Builder setFontVariationSettings(String value){return setFontVariationSettings(FontVariationAxis.fromFontVariationSettings(value));}
  public Builder setFontVariationSettings(FontVariationAxis[] value){axes=value==null?null:value.clone();return this;}
  private static FontStyle analyze(byte[] bytes)throws IOException{
   if(bytes.length<12)throw new IOException("Invalid font header");
   ByteBuffer data=ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.BIG_ENDIAN);int base=0;
   if(data.getInt(0)==0x74746366){if(bytes.length<16||data.getInt(8)<1)throw new IOException("Invalid font collection");base=data.getInt(12);}
   if(base<0||base>bytes.length-12)throw new IOException("Invalid font offset");
   int signature=data.getInt(base);if(signature!=0x00010000&&signature!=0x4f54544f&&signature!=0x74727565)throw new IOException("Unsupported font format");
   int count=data.getShort(base+4)&0xffff;if(count<1||count>(bytes.length-base-12)/16)throw new IOException("Invalid font table directory");
   int detectedWeight=400,detectedSlant=0;boolean os2=false;
   for(int i=0;i<count;i++){
    int record=base+12+i*16,tag=data.getInt(record);long offset=data.getInt(record+8)&0xffffffffL,length=data.getInt(record+12)&0xffffffffL;
    if(offset>bytes.length||length>bytes.length-offset)throw new IOException("Invalid font table range");int start=(int)offset;
    if(tag==0x4f532f32&&length>=64){int value=data.getShort(start+4)&0xffff;if(value>=1&&value<=1000)detectedWeight=value;detectedSlant=(data.getShort(start+62)&0x201)!=0?1:0;os2=true;}
    if(tag==0x68656164&&length>=46&&!os2)detectedSlant=(data.getShort(start+44)&2)!=0?1:0;
   }
   return new FontStyle(detectedWeight,detectedSlant);
  }
  public Font build()throws IOException{
   if(ttc!=0||(axes!=null&&axes.length!=0))throw new IOException("Font collections and variation axes are not supported by this loader");
   byte[] bytes=source.read();FontStyle detected=analyze(bytes);int actualWeight=weight<0?detected.getWeight():weight,actualSlant=slant<0?detected.getSlant():slant;android.graphics.FontFamily family=new android.graphics.FontFamily();
   if(!family.addFontFromBuffer(ByteBuffer.wrap(bytes),0,null,actualWeight,actualSlant)||!family.freeze())throw new IOException("Invalid font");
   return new Font(bytes,new FontStyle(actualWeight,actualSlant),Typeface.createFromFamiliesWithDefault(new android.graphics.FontFamily[]{family},actualWeight,actualSlant),file);
  }
 }
}
