// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;
import android.graphics.*;
import android.os.*;
import android.text.SpannedString;
import java.util.*;
/** Immutable editor geometry snapshot. Parcel transport is not implemented. */
public final class CursorAnchorInfo implements Parcelable {
 public static final int FLAG_HAS_VISIBLE_REGION=1,FLAG_HAS_INVISIBLE_REGION=2,FLAG_IS_RTL=4;
 private final int selectionStart,selectionEnd,composingStart,markerFlags;
 private final CharSequence composing;
 private final float[] marker,matrix;
 private final int[] indices,flags;private final float[][] bounds;
 private CursorAnchorInfo(Builder b){selectionStart=b.start;selectionEnd=b.end;composingStart=b.composingStart;composing=b.text;markerFlags=b.markerFlags;marker=b.marker.clone();matrix=new float[9];(b.matrix==null?new Matrix():b.matrix).getValues(matrix);indices=new int[b.indices.size()];flags=new int[indices.length];bounds=new float[indices.length][];for(int i=0;i<indices.length;i++){indices[i]=b.indices.get(i);flags[i]=b.flags.get(i);bounds[i]=b.bounds.get(i).clone();}}
 public int getSelectionStart(){return selectionStart;}public int getSelectionEnd(){return selectionEnd;}public int getComposingTextStart(){return composingStart;}public CharSequence getComposingText(){return composing;}
 public int getInsertionMarkerFlags(){return markerFlags;}public float getInsertionMarkerHorizontal(){return marker[0];}public float getInsertionMarkerTop(){return marker[1];}public float getInsertionMarkerBaseline(){return marker[2];}public float getInsertionMarkerBottom(){return marker[3];}
 public RectF getCharacterBounds(int index){int at=Arrays.binarySearch(indices,index);if(at<0)return null;float[] b=bounds[at];return new RectF(b[0],b[1],b[2],b[3]);}
 public int getCharacterBoundsFlags(int index){int at=Arrays.binarySearch(indices,index);return at<0?0:flags[at];}
 public Matrix getMatrix(){Matrix result=new Matrix();result.setValues(matrix);return result;}
 @Override public boolean equals(Object other){if(!(other instanceof CursorAnchorInfo))return false;CursorAnchorInfo c=(CursorAnchorInfo)other;return selectionStart==c.selectionStart&&selectionEnd==c.selectionEnd&&composingStart==c.composingStart&&markerFlags==c.markerFlags&&Objects.equals(composing,c.composing)&&Arrays.equals(marker,c.marker)&&Arrays.equals(matrix,c.matrix)&&Arrays.equals(indices,c.indices)&&Arrays.equals(flags,c.flags)&&Arrays.deepEquals(bounds,c.bounds);}
 @Override public int hashCode(){int h=Objects.hash(selectionStart,selectionEnd,composingStart,markerFlags,composing);for(int part:new int[]{Arrays.hashCode(marker),Arrays.hashCode(matrix),Arrays.hashCode(indices),Arrays.hashCode(flags),Arrays.deepHashCode(bounds)})h=31*h+part;return h;}
 public static final class Builder {
  private int start=-1,end=-1,composingStart=-1,markerFlags;private CharSequence text;private Matrix matrix;
  private final float[] marker={Float.NaN,Float.NaN,Float.NaN,Float.NaN};
  private final ArrayList<Integer> indices=new ArrayList<Integer>(),flags=new ArrayList<Integer>();private final ArrayList<float[]> bounds=new ArrayList<float[]>();
  public Builder(){}
  public Builder setSelectionRange(int start,int end){this.start=start;this.end=end;return this;}
  public Builder setComposingText(int start,CharSequence text){if(text!=null&&text.length()>1048576)throw new IllegalArgumentException("Composing text limit");this.composingStart=start;this.text=text==null?null:new SpannedString(text);return this;}
  public Builder setInsertionMarkerLocation(float horizontal,float top,float baseline,float bottom,int flags){marker[0]=horizontal;marker[1]=top;marker[2]=baseline;marker[3]=bottom;markerFlags=flags;return this;}
  public Builder addCharacterBounds(int index,float left,float top,float right,float bottom,int flags){if(index<0||!indices.isEmpty()&&index<=indices.get(indices.size()-1))throw new IllegalArgumentException("Character indices must increase");if(indices.size()>=65536)throw new IllegalStateException("Character bounds limit");indices.add(index);this.flags.add(flags);bounds.add(new float[]{left,top,right,bottom});return this;}
  public Builder setMatrix(Matrix matrix){this.matrix=matrix==null?new Matrix():new Matrix(matrix);return this;}
  public CursorAnchorInfo build(){if(matrix==null){if(!indices.isEmpty())throw new IllegalArgumentException("Position data requires a matrix");for(float value:marker)if(!Float.isNaN(value))throw new IllegalArgumentException("Position data requires a matrix");}return new CursorAnchorInfo(this);}
  public void reset(){start=end=composingStart=-1;markerFlags=0;text=null;matrix=null;Arrays.fill(marker,Float.NaN);indices.clear();flags.clear();bounds.clear();}
 }
 public int describeContents(){return 0;}public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("CursorAnchorInfo parcel transport");}
 public static final Parcelable.Creator<CursorAnchorInfo> CREATOR=new Parcelable.Creator<CursorAnchorInfo>(){public CursorAnchorInfo createFromParcel(Parcel in){throw new UnsupportedOperationException("CursorAnchorInfo parcel transport");}public CursorAnchorInfo[] newArray(int count){return new CursorAnchorInfo[count];}};
}
