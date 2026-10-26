// SPDX-License-Identifier: GPL-3.0-only
import android.view.inputmethod.CursorAnchorInfo;
import android.graphics.*;
public final class CursorAnchorSmoke {
 private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  CursorAnchorInfo.Builder builder=new CursorAnchorInfo.Builder();CursorAnchorInfo empty=builder.build();check(empty.getSelectionStart()==-1&&Float.isNaN(empty.getInsertionMarkerTop())&&empty.getMatrix().isIdentity(),"Empty defaults");
  builder.addCharacterBounds(2,1,2,8,12,5);try{builder.build();throw new AssertionError("Missing matrix accepted");}catch(IllegalArgumentException expected){}
  try{builder.addCharacterBounds(2,0,0,0,0,0);throw new AssertionError("Duplicate index accepted");}catch(IllegalArgumentException expected){}
  Matrix matrix=new Matrix();matrix.setTranslate(10,20);StringBuilder composing=new StringBuilder("route");builder.setMatrix(matrix).setSelectionRange(4,2).setComposingText(1,composing).setInsertionMarkerLocation(3,4,9,12,1);composing.append(" changed");matrix.reset();CursorAnchorInfo info=builder.build();builder.reset();
  check(info.getSelectionStart()==4&&info.getSelectionEnd()==2&&info.getComposingText().toString().equals("route")&&info.getComposingTextStart()==1,"Selection and composing snapshot");
  Matrix exposed=info.getMatrix();float[] point={1,2};exposed.mapPoints(point);check(point[0]==11&&point[1]==22,"Captured local-to-screen matrix");exposed.reset();check(!info.getMatrix().isIdentity(),"Matrix return copy");
  RectF rect=info.getCharacterBounds(2);rect.left=99;check(info.getCharacterBounds(2).left==1&&info.getCharacterBoundsFlags(2)==5&&info.getCharacterBounds(3)==null&&info.getCharacterBoundsFlags(3)==0,"Bounds snapshot/flags/missing index");
  check(info.getInsertionMarkerHorizontal()==3&&info.getInsertionMarkerBaseline()==9&&info.getInsertionMarkerFlags()==1,"Insertion marker geometry");CursorAnchorInfo reset=builder.build();check(reset.equals(empty)&&reset.hashCode()==empty.hashCode()&&!info.equals(empty),"Reset and equality");
  builder.setInsertionMarkerLocation(1,Float.NaN,Float.NaN,Float.NaN,0);try{builder.build();throw new AssertionError("Partial position without matrix");}catch(IllegalArgumentException expected){}check(builder.setMatrix(null).build().getMatrix().isIdentity(),"Explicit identity matrix");
  System.out.println("CURSOR_ANCHOR_OK");
 }
}
