// SPDX-License-Identifier: GPL-3.0-only
package android.text.style;
import android.os.Parcel;
import android.text.Layout;
import android.text.ParcelableSpan;
/** Paragraph alignment exposed to text consumers such as Compose HTML conversion. */
public interface AlignmentSpan extends ParagraphStyle {
 Layout.Alignment getAlignment();
 class Standard implements AlignmentSpan, ParcelableSpan {
  private final Layout.Alignment alignment;
  public Standard(Layout.Alignment alignment){this.alignment=alignment;}
  public Standard(Parcel source){alignment=Layout.Alignment.valueOf(source.readString());}
  public Layout.Alignment getAlignment(){return alignment;}
  public int describeContents(){return 0;}
  public int getSpanTypeId(){return 1;}
  public int getSpanTypeIdInternal(){return getSpanTypeId();}
  public void writeToParcel(Parcel destination,int flags){destination.writeString(alignment.name());}
  public void writeToParcelInternal(Parcel destination,int flags){writeToParcel(destination,flags);}
 }
}
