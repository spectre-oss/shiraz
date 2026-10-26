// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import java.nio.*;
import java.util.Arrays;
/** CPU stretch-grid rendering of validated compiled Android nine-patch chunks. */
public class NinePatch {
 private final Bitmap bitmap;
 private final byte[] chunk;
 private final String name;
 private Paint paint;
 private final int[] x,y;
 public NinePatch(Bitmap bitmap,byte[] chunk,String name){if(bitmap==null)throw new NullPointerException("bitmap");validate(chunk,bitmap.getWidth(),bitmap.getHeight());this.bitmap=bitmap;this.chunk=chunk.clone();this.name=name;x=divs(chunk,1,32);y=divs(chunk,2,32+4*(chunk[1]&255));}
 public NinePatch(NinePatch other){this(other.bitmap,other.chunk,other.name);paint=other.paint==null?null:new Paint(other.paint);}
 private static ByteBuffer buffer(byte[] data){return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);}
 private static int[] divs(byte[] data,int countAt,int offset){int[] result=new int[(data[countAt]&255)+2];ByteBuffer b=buffer(data);for(int i=1;i<result.length-1;i++)result[i]=b.getInt(offset+(i-1)*4);return result;}
 static void validate(byte[] data,int width,int height){
  if(data==null||data.length<32||data[0]!=1)throw new IllegalArgumentException("Nine-patch header");int nx=data[1]&255,ny=data[2]&255,nc=data[3]&255;
  if(nx==0||ny==0||(nx&1)!=0||(ny&1)!=0||nc==0||data.length!=32+4*(nx+ny+nc))throw new IllegalArgumentException("Nine-patch counts");
  ByteBuffer b=buffer(data);int at=32;for(int axis=0;axis<2;axis++){int previous=-1,count=axis==0?nx:ny,limit=axis==0?width:height;for(int i=0;i<count;i++){int value=b.getInt(at);at+=4;if(value<previous||value<0||value>limit)throw new IllegalArgumentException("Nine-patch division");previous=value;}}
  for(int i=0;i<4;i++){int value=b.getInt(12+i*4);if(value<0||value>(i<2?width:height))throw new IllegalArgumentException("Nine-patch padding");}
 }
 public static boolean isNinePatchChunk(byte[] data){try{validate(data,Integer.MAX_VALUE,Integer.MAX_VALUE);return true;}catch(IllegalArgumentException failure){return false;}}
 static byte[] fromPng(byte[] data,int offset,int length,int width,int height){
  byte[] result=null;int end=offset+length;for(int at=offset+8;at+12<=end;){int n=ByteBuffer.wrap(data,at,4).getInt();if(n<0||n>end-at-12)throw new IllegalArgumentException("PNG chunk length");if(data[at+4]=='n'&&data[at+5]=='p'&&data[at+6]=='T'&&data[at+7]=='c'){
   if(result!=null||n<32)throw new IllegalArgumentException("Nine-patch metadata");result=Arrays.copyOfRange(data,at+8,at+8+n);ByteBuffer source=ByteBuffer.wrap(result.clone()).order(ByteOrder.BIG_ENDIAN),dest=buffer(result);result[0]=1;for(int i=12;i<28;i+=4)dest.putInt(i,source.getInt(i));for(int i=32;i+4<=n;i+=4)dest.putInt(i,source.getInt(i));validate(result,width,height);
  }at+=n+12;}return result;
 }
 static byte[] scaled(byte[] data,int oldWidth,int oldHeight,int width,int height){if(data==null)return null;byte[] result=data.clone();ByteBuffer b=buffer(result);for(int i=0;i<4;i++){float scale=i<2?(float)width/oldWidth:(float)height/oldHeight;b.putInt(12+4*i,Math.round(b.getInt(12+4*i)*scale));}int nx=data[1]&255,ny=data[2]&255;for(int i=0;i<nx+ny;i++){float scale=i<nx?(float)width/oldWidth:(float)height/oldHeight;b.putInt(32+4*i,Math.round(b.getInt(32+4*i)*scale));}validate(result,width,height);return result;}
 public void shirazPadding(Rect out){ByteBuffer b=buffer(chunk);out.set(b.getInt(12),b.getInt(20),b.getInt(16),b.getInt(24));}
 public String getName(){return name;}public Bitmap getBitmap(){return bitmap;}public int getWidth(){return bitmap.getWidth();}public int getHeight(){return bitmap.getHeight();}public int getDensity(){return bitmap.getDensity();}public boolean hasAlpha(){return true;}
 public Paint getPaint(){return paint;}public void setPaint(Paint value){paint=value;}
 private static float[] positions(int[] divisions,int extent,float start,float length,float density){
  divisions[divisions.length-1]=extent;int stretch=0;for(int i=1;i<divisions.length-1;i+=2)stretch+=divisions[i+1]-divisions[i];float fixed=(extent-stretch)*density;float fixedScale=length<fixed&&fixed>0?length/fixed:1;float extra=Math.max(0,length-fixed);float[] out=new float[divisions.length];out[0]=start;
  for(int i=0;i<divisions.length-1;i++){int source=divisions[i+1]-divisions[i];out[i+1]=out[i]+((i&1)==0?source*density*fixedScale:stretch==0?0:extra*source/stretch);}out[out.length-1]=start+length;return out;
 }
 public void draw(Canvas canvas,Rect bounds){draw(canvas,bounds,paint);}public void draw(Canvas canvas,RectF bounds){draw(canvas,bounds,paint);}public void draw(Canvas canvas,Rect bounds,Paint paint){draw(canvas,new RectF(bounds),paint);}
 public void draw(Canvas canvas,RectF bounds,Paint paint){shirazDraw(canvas,bounds,paint,canvas.getDensity());}
 public void shirazDraw(Canvas canvas,RectF bounds,Paint paint,int targetDensity){if(bounds.isEmpty())return;float density=getDensity()==0||targetDensity==0?1:(float)targetDensity/getDensity();float[] dx=positions(x,getWidth(),bounds.left,bounds.width(),density),dy=positions(y,getHeight(),bounds.top,bounds.height(),density);for(int j=0;j<y.length-1;j++)for(int i=0;i<x.length-1;i++)if(x[i+1]>x[i]&&y[j+1]>y[j]&&dx[i+1]>dx[i]&&dy[j+1]>dy[j])canvas.drawBitmap(bitmap,new Rect(x[i],y[j],x[i+1],y[j+1]),new RectF(dx[i],dy[j],dx[i+1],dy[j+1]),paint);}
}
