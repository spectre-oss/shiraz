// SPDX-License-Identifier: GPL-3.0-only
import android.util.Size;
public final class SizeSmoke {
 public static void main(String[] args){
  for(int width:new int[]{Integer.MIN_VALUE,-1,0,720,Integer.MAX_VALUE})for(int height:new int[]{Integer.MIN_VALUE,-7,0,1280,Integer.MAX_VALUE}){
   Size size=new Size(width,height),copy=Size.parseSize(size.toString());if(size.getWidth()!=width||size.getHeight()!=height||!size.equals(copy)||size.hashCode()!=copy.hashCode()||size.equals(null)||size.equals(size.toString()))throw new AssertionError("Dimension value contract");
  }
  if(!Size.parseSize("+3*-6").equals(new Size(3,-6))||new Size(720,1280).equals(new Size(1280,720)))throw new AssertionError("Signed alternate separator/orientation");
  for(String invalid:new String[]{"","1","1X2","1x2x3","1*2x3"," 1x2","1x2 ","2147483648x0","0x-2147483649","x2","2x"})try{Size.parseSize(invalid);throw new AssertionError("Accepted malformed size: "+invalid);}catch(NumberFormatException expected){}
  try{Size.parseSize(null);throw new AssertionError("Accepted null size");}catch(NullPointerException expected){}
  for(float width:new float[]{-Float.MAX_VALUE,-0.0f,0,Float.MIN_VALUE,1.25f,Float.MAX_VALUE}){
   android.util.SizeF size=new android.util.SizeF(width,3.5f),copy=android.util.SizeF.parseSizeF(size.toString());if(!size.equals(copy)||size.hashCode()!=copy.hashCode()||size.getHeight()!=3.5f||size.getWidth()!=width||size.equals(new Size(1,2)))throw new AssertionError("Floating size round trip");
  }
  if(!android.util.SizeF.parseSizeF("-1.5*+2e2").equals(new android.util.SizeF(-1.5f,200)))throw new AssertionError("Floating alternate syntax");
  for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})try{new android.util.SizeF(invalid,0);throw new AssertionError("Non-finite dimensions");}catch(IllegalArgumentException expected){}
  for(String invalid:new String[]{"NaNx1","1xInfinity","1e99x2","1x2x3","1x"})try{android.util.SizeF.parseSizeF(invalid);throw new AssertionError("Invalid floating size");}catch(NumberFormatException expected){}
  System.out.println("SIZE_VALUE_OK");
 }
}
