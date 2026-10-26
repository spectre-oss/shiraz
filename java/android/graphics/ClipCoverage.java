// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import java.util.Arrays;
import java.util.LinkedHashMap;
/** Immutable device-space coverage. Region remains integer geometry for Android callers. */
final class ClipCoverage {
 final int left,top,width,height;
 final byte[] pixels;
 private static final int CACHE_BYTES=2*1024*1024;
 private static int cachedBytes;
 private static final LinkedHashMap<Key,ClipCoverage> cache=new LinkedHashMap<Key,ClipCoverage>(32,.75f,true);
 private static final class Key {
  final float[] commands,matrix;final int fill,width,height,hash;
  Key(Path path,float[] matrix,int width,int height){commands=path.commands();this.matrix=matrix;fill=path.getFillType().ordinal();this.width=width;this.height=height;hash=31*(31*(31*Arrays.hashCode(commands)+Arrays.hashCode(matrix))+fill)+31*width+height;}
  public int hashCode(){return hash;}
  public boolean equals(Object other){if(!(other instanceof Key))return false;Key k=(Key)other;return fill==k.fill&&width==k.width&&height==k.height&&Arrays.equals(commands,k.commands)&&Arrays.equals(matrix,k.matrix);}
  int bytes(){return 4*(commands.length+matrix.length)+64;}
 }
 ClipCoverage(int left,int top,int width,int height,byte[] pixels){this.left=left;this.top=top;this.width=width;this.height=height;this.pixels=pixels;}
 int at(int x,int y){return x<left||y<top||x>=left+width||y>=top+height?0:pixels[(y-top)*width+x-left]&255;}
 static ClipCoverage path(Path path,float[] matrix,Rect bounds){
  int width=bounds.width(),height=bounds.height();if(width<=0||height<=0)return new ClipCoverage(0,0,0,0,new byte[0]);
  if((long)width*height>16L*1024*1024)throw new IllegalArgumentException("Clip coverage exceeds 16M pixels");
  float[] local=matrix.clone();local[4]-=bounds.left;local[5]-=bounds.top;Key key=new Key(path,local,width,height);
  ClipCoverage result;synchronized(cache){result=cache.get(key);}
  if(result==null){
   int[] rgba=new int[width*height];
   if(path.shirazIsFinite())shiraz.runtime.NativePath.draw(rgba,width,height,key.commands,key.fill,0xffffffff,0,0,0,0,4,true,local,0,0,width,height);
   byte[] coverage=new byte[rgba.length];for(int i=0;i<rgba.length;i++)coverage[i]=(byte)(rgba[i]>>>24);
   result=new ClipCoverage(0,0,width,height,coverage);
   int bytes=key.bytes()+coverage.length;
   if(bytes<=CACHE_BYTES)synchronized(cache){
    if(!cache.containsKey(key)){while(!cache.isEmpty()&&(cachedBytes+bytes>CACHE_BYTES||cache.size()>=64)){Key first=cache.keySet().iterator().next();ClipCoverage old=cache.remove(first);cachedBytes-=first.bytes()+old.pixels.length;}cache.put(key,result);cachedBytes+=bytes;}
   }
  }
  return new ClipCoverage(bounds.left,bounds.top,width,height,result.pixels);
 }
 Rect nonzeroBounds(Rect limit){
  int l=Math.max(left,limit.left),t=Math.max(top,limit.top),r=Math.min(left+width,limit.right),b=Math.min(top+height,limit.bottom);
  int minX=r,minY=b,maxX=l,maxY=t;
  for(int y=t;y<b;y++)for(int x=l;x<r;x++)if(at(x,y)!=0){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x+1);maxY=Math.max(maxY,y+1);}
  return minX>=maxX||minY>=maxY?new Rect():new Rect(minX,minY,maxX,maxY);
 }
 static ClipCoverage combine(ClipCoverage previous,Rect previousBounds,Region region,ClipCoverage shape,Region.Op op){
  Rect shapeBounds=new Rect(shape.left,shape.top,shape.left+shape.width,shape.top+shape.height),bounds=new Rect(previousBounds);
  boolean sameShape=previous!=null&&previous.pixels==shape.pixels&&previous.left==shape.left&&previous.top==shape.top;
  if(op==Region.Op.INTERSECT&&region==null&&previousBounds.contains(shapeBounds)&&(previous==null||sameShape))return shape;
  if(op==Region.Op.INTERSECT){if(!bounds.intersect(shapeBounds))bounds.setEmpty();}
  else if(op==Region.Op.REPLACE||op==Region.Op.REVERSE_DIFFERENCE)bounds=shapeBounds;
  else if(op!=Region.Op.DIFFERENCE)bounds.union(shapeBounds);
  int w=bounds.width(),h=bounds.height();if(w<=0||h<=0)return new ClipCoverage(0,0,0,0,new byte[0]);
  if((long)w*h>16L*1024*1024)throw new IllegalArgumentException("Combined clip exceeds 16M pixels");
  byte[] data=new byte[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   int px=bounds.left+x,py=bounds.top+y;
   int a=previousBounds.contains(px,py)&&(region==null||region.contains(px,py))?(previous==null?255:previous.at(px,py)):0,b=shape.at(px,py),value;
   // Coverage algebra; identical cached intersections are handled above.
   switch(op){case INTERSECT:value=sameShape?a:(a*b+127)/255;break;case DIFFERENCE:value=(a*(255-b)+127)/255;break;case REVERSE_DIFFERENCE:value=(b*(255-a)+127)/255;break;case UNION:value=a+b-(a*b+127)/255;break;case XOR:value=(a*(255-b)+b*(255-a)+127)/255;break;default:value=b;}
   data[y*w+x]=(byte)value;
  }
  return new ClipCoverage(bounds.left,bounds.top,w,h,data);
 }
 static ClipCoverage region(Region region){Rect b=region.getBounds();int w=b.width(),h=b.height();if((long)w*h>16L*1024*1024)throw new IllegalArgumentException("Region clip size");byte[] pixels=new byte[w*h];for(Rect r:region.snapshot())for(int y=r.top;y<r.bottom;y++)Arrays.fill(pixels,(y-b.top)*w+r.left-b.left,(y-b.top)*w+r.right-b.left,(byte)255);return new ClipCoverage(b.left,b.top,w,h,pixels);}
}
