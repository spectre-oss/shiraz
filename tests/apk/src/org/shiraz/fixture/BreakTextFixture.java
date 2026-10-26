// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
public final class BreakTextFixture {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void check(){
  Paint p=new Paint();p.setTextSize(24);p.setTypeface(Typeface.MONOSPACE);float unit=p.measureText("a"),out[]={-1,99};
  check(p.breakText("abcd",true,unit*2+.01f,out)==2&&Math.abs(out[0]-unit*2)<.01&&out[1]==99,"forward width and output slice");
  check(p.breakText("abcd",false,unit*3+.01f,out)==3&&Math.abs(out[0]-unit*3)<.01,"backward width");
  check(p.breakText(new StringBuilder("XabcdY"),1,5,true,unit*2+.01f,out)==2,"CharSequence range");
  check(p.breakText("XabcdY".toCharArray(),1,-4,unit*3+.01f,out)==3,"negative count measures selected suffix");
  check(p.breakText("ab",true,0,out)==0&&out[0]==0,"zero limit");check(p.breakText("ab",true,-1,out)==0,"negative limit");
  check(p.breakText("abcd",true,Float.POSITIVE_INFINITY,out)==4&&Math.abs(out[0]-p.measureText("abcd"))<.01,"whole shaped width");
  String pair="\ud83d\ude00";check(p.breakText(pair,true,p.measureText(pair)/2,out)==0,"surrogate pair remains whole");
  String combined="a\u0301";check(p.breakText(combined,false,p.measureText(combined)/2,out)==0,"combining cluster remains whole");
  p.setLetterSpacing(.2f);float spaced=p.measureText("abcd");check(p.breakText("abcd",true,spaced+.01f,out)==4&&Math.abs(out[0]-spaced)<.01,"letter spacing width");
  p.setTextSize(0);check(p.breakText("abcd",false,0,out)==4&&out[0]==0,"zero text size");
  try{p.breakText(new char[2],0,Integer.MIN_VALUE,1,out);throw new AssertionError("count overflow");}catch(IndexOutOfBoundsException expected){}
  try{p.breakText("a",true,1,new float[0]);throw new AssertionError("empty output");}catch(IndexOutOfBoundsException expected){}
  System.out.println("BREAK_TEXT_OK");
 }
}
