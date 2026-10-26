// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.text.TextDirectionHeuristics;
import java.util.Locale;
public final class TextDirectionFixture {
 private static void check(boolean ok){if(!ok)throw new AssertionError("Text direction heuristic");}
 public static void check(){
  String rtl="\u05d0\u05d1",mixed="abc "+rtl,isolated="\u2067"+rtl+"\u2069abc";
  check(!TextDirectionHeuristics.LTR.isRtl(rtl,0,2));check(TextDirectionHeuristics.RTL.isRtl("abc",0,3));
  check(TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(rtl,0,2));check(!TextDirectionHeuristics.FIRSTSTRONG_RTL.isRtl(mixed,0,mixed.length()));
  check(TextDirectionHeuristics.ANYRTL_LTR.isRtl(mixed.toCharArray(),0,mixed.length()));
  check(!TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(isolated,0,isolated.length()));check(!TextDirectionHeuristics.ANYRTL_LTR.isRtl(isolated,0,isolated.length()));
  check(!TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl("123",0,3));check(TextDirectionHeuristics.FIRSTSTRONG_RTL.isRtl("123",0,3));
  check(TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(mixed,4,2));
  try{TextDirectionHeuristics.LTR.isRtl("a",1,1);throw new AssertionError("Invalid range accepted");}catch(IllegalArgumentException expected){}
  Locale saved=Locale.getDefault();try{Locale.setDefault(new Locale("ar"));check(TextDirectionHeuristics.LOCALE.isRtl("",0,0));Locale.setDefault(Locale.ENGLISH);check(!TextDirectionHeuristics.LOCALE.isRtl("",0,0));}finally{Locale.setDefault(saved);}
  System.out.println("TEXT_DIRECTION_HEURISTICS_OK");
 }
}
