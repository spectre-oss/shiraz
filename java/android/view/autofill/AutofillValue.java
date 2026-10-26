// SPDX-License-Identifier: GPL-3.0-only
package android.view.autofill;
import android.os.*;
/** Immutable field value; constructing it never transmits data to a provider. */
public final class AutofillValue implements Parcelable {
 private final int type;private final Object value;
 private AutofillValue(int type,Object value){this.type=type;this.value=value;}
 public static AutofillValue forText(CharSequence value){return value==null?null:new AutofillValue(1,value.toString().trim());}
 public static AutofillValue forToggle(boolean value){return new AutofillValue(2,value);}
 public static AutofillValue forList(int value){return new AutofillValue(3,value);}
 public static AutofillValue forDate(long value){return new AutofillValue(4,value);}
 public boolean isText(){return type==1;}public boolean isToggle(){return type==2;}public boolean isList(){return type==3;}public boolean isDate(){return type==4;}
 private void require(int expected){if(type!=expected)throw new IllegalStateException("Autofill value type");}
 public CharSequence getTextValue(){require(1);return (String)value;}public boolean getToggleValue(){require(2);return (Boolean)value;}public int getListValue(){require(3);return (Integer)value;}public long getDateValue(){require(4);return (Long)value;}
 public boolean isEmpty(){return isText()&&((String)value).isEmpty();}
 public boolean equals(Object other){return other instanceof AutofillValue&&((AutofillValue)other).type==type&&value.equals(((AutofillValue)other).value);}
 public int hashCode(){return 31*type+value.hashCode();}
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel out,int flags){out.writeInt(type);switch(type){case 1:out.writeString((String)value);break;case 2:out.writeInt((Boolean)value?1:0);break;case 3:out.writeInt((Integer)value);break;case 4:out.writeLong((Long)value);break;}}
 public static final Creator<AutofillValue> CREATOR=new Creator<AutofillValue>(){public AutofillValue createFromParcel(Parcel in){switch(in.readInt()){case 1:return forText(in.readString());case 2:return forToggle(in.readInt()!=0);case 3:return forList(in.readInt());case 4:return forDate(in.readLong());default:throw new IllegalArgumentException("Autofill value type");}}public AutofillValue[] newArray(int count){return new AutofillValue[count];}};
}
