// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

/** Managed 3x3 geometry. Canvas integration is provided separately. */
public class Matrix {
 public static final int MSCALE_X=0,MSKEW_X=1,MTRANS_X=2,MSKEW_Y=3,MSCALE_Y=4,MTRANS_Y=5,MPERSP_0=6,MPERSP_1=7,MPERSP_2=8;
 public enum ScaleToFit {FILL,START,CENTER,END}
 private final float[] m=new float[9];
 public Matrix(){reset();}public Matrix(Matrix other){set(other);}
 public void reset(){java.util.Arrays.fill(m,0);m[0]=m[4]=m[8]=1;}
 public void set(Matrix other){if(other==null)reset();else System.arraycopy(other.m,0,m,0,9);}
 public void setValues(float[] values){if(values.length<9)throw new ArrayIndexOutOfBoundsException();System.arraycopy(values,0,m,0,9);}
 public void getValues(float[] values){if(values.length<9)throw new ArrayIndexOutOfBoundsException();System.arraycopy(m,0,values,0,9);}
 public boolean isIdentity(){return m[0]==1&&m[4]==1&&m[8]==1&&m[1]==0&&m[2]==0&&m[3]==0&&m[5]==0&&m[6]==0&&m[7]==0;}
 public boolean isAffine(){return m[6]==0&&m[7]==0&&m[8]==1;}
 public boolean rectStaysRect(){return isAffine()&&((m[1]==0&&m[3]==0&&m[0]!=0&&m[4]!=0)||(m[0]==0&&m[4]==0&&m[1]!=0&&m[3]!=0));}
 public void setTranslate(float x,float y){reset();m[2]=x;m[5]=y;}
 public void setScale(float x,float y){setScale(x,y,0,0);}public void setScale(float x,float y,float px,float py){reset();m[0]=x;m[4]=y;m[2]=px-x*px;m[5]=py-y*py;}
 public void setRotate(float degrees){setRotate(degrees,0,0);}public void setRotate(float degrees,float px,float py){double a=Math.toRadians(degrees%360);float s=(float)Math.sin(a),c=(float)Math.cos(a);if(Math.abs(s)<1e-7)s=0;if(Math.abs(c)<1e-7)c=0;setSinCos(s,c,px,py);}
 public void setSinCos(float s,float c){setSinCos(s,c,0,0);}public void setSinCos(float s,float c,float px,float py){reset();m[0]=m[4]=c;m[1]=-s;m[3]=s;m[2]=px-c*px+s*py;m[5]=py-s*px-c*py;}
 public void setSkew(float x,float y){setSkew(x,y,0,0);}public void setSkew(float x,float y,float px,float py){reset();m[1]=x;m[3]=y;m[2]=-x*py;m[5]=-y*px;}
 public boolean setConcat(Matrix a,Matrix b){float[] result=new float[9];for(int r=0;r<3;r++)for(int c=0;c<3;c++){double v=0;for(int k=0;k<3;k++)v+=(double)a.m[r*3+k]*b.m[k*3+c];result[r*3+c]=(float)v;}setValues(result);return true;}
 public boolean preConcat(Matrix other){return setConcat(this,other);}public boolean postConcat(Matrix other){return setConcat(other,this);}
 public boolean preTranslate(float x,float y){Matrix n=new Matrix();n.setTranslate(x,y);return preConcat(n);}public boolean postTranslate(float x,float y){Matrix n=new Matrix();n.setTranslate(x,y);return postConcat(n);}
 public boolean preScale(float x,float y){return preScale(x,y,0,0);}public boolean preScale(float x,float y,float px,float py){Matrix n=new Matrix();n.setScale(x,y,px,py);return preConcat(n);}
 public boolean postScale(float x,float y){return postScale(x,y,0,0);}public boolean postScale(float x,float y,float px,float py){Matrix n=new Matrix();n.setScale(x,y,px,py);return postConcat(n);}
 public boolean preRotate(float a){return preRotate(a,0,0);}public boolean preRotate(float a,float x,float y){Matrix n=new Matrix();n.setRotate(a,x,y);return preConcat(n);}
 public boolean postRotate(float a){return postRotate(a,0,0);}public boolean postRotate(float a,float x,float y){Matrix n=new Matrix();n.setRotate(a,x,y);return postConcat(n);}
 public boolean preSkew(float x,float y){return preSkew(x,y,0,0);}public boolean preSkew(float x,float y,float px,float py){Matrix n=new Matrix();n.setSkew(x,y,px,py);return preConcat(n);}
 public boolean postSkew(float x,float y){return postSkew(x,y,0,0);}public boolean postSkew(float x,float y,float px,float py){Matrix n=new Matrix();n.setSkew(x,y,px,py);return postConcat(n);}
 public boolean invert(Matrix inverse){double a=m[0],b=m[1],c=m[2],d=m[3],e=m[4],f=m[5],g=m[6],h=m[7],i=m[8];double det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);if(det==0||!Double.isFinite(det))return false;double[] cof={e*i-f*h,c*h-b*i,b*f-c*e,f*g-d*i,a*i-c*g,c*d-a*f,d*h-e*g,b*g-a*h,a*e-b*d};float[] out=new float[9];for(int k=0;k<9;k++){out[k]=(float)(cof[k]/det);if(!Float.isFinite(out[k]))return false;}if(inverse!=null)inverse.setValues(out);return true;}
 private static void range(float[] a,int offset,int count){if(offset<0||count<0||(long)offset+2L*count>a.length)throw new ArrayIndexOutOfBoundsException();}
 private void map(float[] dst,int di,float[] src,int si,int count,boolean vectors){range(src,si,count);range(dst,di,count);float[] input=src;int start=si;if(dst==src&&di>si&&di<(long)si+2L*count){input=java.util.Arrays.copyOfRange(src,si,si+count*2);start=0;}double ox=0,oy=0;if(vectors&&!isAffine()){double z=m[8];if(z!=0){ox=m[2]/z;oy=m[5]/z;}}
  for(int k=0;k<count;k++){double x=input[start+2*k],y=input[start+2*k+1],z=m[6]*x+m[7]*y+m[8];double px=m[0]*x+m[1]*y,py=m[3]*x+m[4]*y;if(!vectors||!isAffine()){px+=m[2];py+=m[5];}if(z==0){px=py=0;}else{px/=z;py/=z;}dst[di+2*k]=(float)(px-ox);dst[di+2*k+1]=(float)(py-oy);}}
 public void mapPoints(float[] dst,int di,float[] src,int si,int count){map(dst,di,src,si,count,false);}public void mapVectors(float[] dst,int di,float[] src,int si,int count){map(dst,di,src,si,count,true);}
 public void mapPoints(float[] dst,float[] src){if(dst.length!=src.length)throw new ArrayIndexOutOfBoundsException();mapPoints(dst,0,src,0,dst.length/2);}public void mapPoints(float[] points){mapPoints(points,points);}
 public void mapVectors(float[] dst,float[] src){if(dst.length!=src.length)throw new ArrayIndexOutOfBoundsException();mapVectors(dst,0,src,0,dst.length/2);}public void mapVectors(float[] points){mapVectors(points,points);}
 public boolean mapRect(RectF dst,RectF src){float[] p={src.left,src.top,src.right,src.top,src.right,src.bottom,src.left,src.bottom};mapPoints(p);float l=p[0],r=l,t=p[1],b=t;for(int k=2;k<8;k+=2){l=Math.min(l,p[k]);r=Math.max(r,p[k]);t=Math.min(t,p[k+1]);b=Math.max(b,p[k+1]);}dst.set(l,t,r,b);return rectStaysRect();}public boolean mapRect(RectF rect){return mapRect(rect,rect);}
 public float mapRadius(float radius){float[] p={radius,0,0,radius};mapVectors(p);return (float)Math.sqrt(Math.hypot(p[0],p[1])*Math.hypot(p[2],p[3]));}
 public boolean setRectToRect(RectF src,RectF dst,ScaleToFit fit){if(fit==null)throw new NullPointerException("fit");if(src.isEmpty()){reset();return false;}if(dst.isEmpty()){setScale(0,0);return true;}float sx=dst.width()/src.width(),sy=dst.height()/src.height(),dx=0,dy=0;if(fit!=ScaleToFit.FILL){sx=sy=Math.min(sx,sy);float weight=fit==ScaleToFit.START?0:fit==ScaleToFit.CENTER?.5f:1;dx=(dst.width()-src.width()*sx)*weight;dy=(dst.height()-src.height()*sy)*weight;}setScale(sx,sy);m[2]=dst.left-src.left*sx+dx;m[5]=dst.top-src.top*sy+dy;return true;}
 public boolean setPolyToPoly(float[] src,int si,float[] dst,int di,int count){if(count<0||count>4)throw new IllegalArgumentException("pointCount");range(src,si,count);range(dst,di,count);if(count==0){reset();return true;}if(count==1){setTranslate(dst[di]-src[si],dst[di+1]-src[si+1]);return true;}throw new UnsupportedOperationException("Matrix polygon fitting is not implemented");}
 @Override public boolean equals(Object other){if(!(other instanceof Matrix))return false;float[] n=((Matrix)other).m;for(int k=0;k<9;k++)if(m[k]!=n[k])return false;return true;}
 @Override public int hashCode(){return 44;}
 public String toShortString(){StringBuilder out=new StringBuilder();toShortString(out);return out.toString();}public void toShortString(StringBuilder out){for(int r=0;r<3;r++)out.append('[').append(m[r*3]).append(", ").append(m[r*3+1]).append(", ").append(m[r*3+2]).append(']');}
 public void printShortString(java.io.PrintWriter out){out.print(toShortString());}@Override public String toString(){return "Matrix{"+toShortString()+"}";}
}
