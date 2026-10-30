// SPDX-License-Identifier: GPL-3.0-only
package android.database;

import android.database.sqlite.SQLiteClosable;
import java.util.ArrayList;

/** Bounded host row window. Android shared-memory parcel transport is unsupported. */
public class CursorWindow extends SQLiteClosable implements android.os.Parcelable {
    private final String name;private final long capacity;
    private final ArrayList<Object[]> rows=new ArrayList<Object[]>();
    private int columns,start;private long used=64;private boolean disposed;
    public CursorWindow(String name){this(name,2*1024*1024L);}
    public CursorWindow(boolean localWindow){this(null);}
    public CursorWindow(String name,long size){
        if(size<64||size>64*1024*1024L)throw new IllegalArgumentException("Cursor window size must be 64 bytes to 64 MiB");
        this.name=name==null||name.isEmpty()?"<unnamed>":name;capacity=size;
    }
    private void live(){if(disposed)throw new IllegalStateException("Cursor window is closed");}
    @Override protected synchronized void onAllReferencesReleased(){disposed=true;rows.clear();used=0;}
    public String getName(){return name;}
    public synchronized int getStartPosition(){live();return start;}
    public synchronized void setStartPosition(int value){live();if(value<0)throw new IllegalArgumentException("Window start");start=value;}
    public synchronized int getNumRows(){live();return rows.size();}
    public synchronized void clear(){live();rows.clear();columns=0;start=0;used=64;}
    public synchronized boolean setNumColumns(int value){live();if(value<0||value>1024)return false;if((columns!=0||!rows.isEmpty())&&columns!=value)return false;columns=value;return true;}
    public synchronized boolean allocRow(){live();long cost=16+16L*columns;if(rows.size()>=1048576||cost>capacity-used)return false;rows.add(new Object[columns]);used+=cost;return true;}
    private static long bytes(Object value){return value instanceof byte[]?((byte[])value).length:value instanceof String?2L*((String)value).length()+2:0;}
    public synchronized void freeLastRow(){live();if(rows.isEmpty())return;Object[] row=rows.remove(rows.size()-1);used-=16+16L*columns;for(Object value:row)used-=bytes(value);}
    private int offset(int row){long offset=(long)row-start;return offset<0||offset>=rows.size()?-1:(int)offset;}
    private Object value(int row,int column){live();int offset=offset(row);if(offset<0||column<0||column>=columns)throw new CursorIndexOutOfBoundsException("Window row/column out of bounds");return rows.get(offset)[column];}
    private synchronized boolean put(Object value,int row,int column){
        live();int offset=offset(row);if(offset<0||column<0||column>=columns)return false;
        Object[] target=rows.get(offset);long next=used-bytes(target[column])+bytes(value);if(next>capacity)return false;
        target[column]=value instanceof byte[]?((byte[])value).clone():value;used=next;return true;
    }
    public boolean putBlob(byte[] value,int row,int column){return put(value,row,column);}
    public boolean putString(String value,int row,int column){return put(value,row,column);}
    public boolean putLong(long value,int row,int column){return put(Long.valueOf(value),row,column);}
    public boolean putDouble(double value,int row,int column){return put(Double.valueOf(value),row,column);}
    public boolean putNull(int row,int column){return put(null,row,column);}
    public synchronized int getType(int row,int column){Object value=value(row,column);return value==null?0:value instanceof byte[]?4:value instanceof String?3:value instanceof Double?2:1;}
    public synchronized String getString(int row,int column){Object value=value(row,column);if(value instanceof byte[])throw new android.database.sqlite.SQLiteException("Cannot convert blob to text");return value==null?null:value.toString();}
    public synchronized byte[] getBlob(int row,int column){Object value=value(row,column);if(value==null)return null;if(value instanceof byte[])return ((byte[])value).clone();if(value instanceof String)return ((String)value).getBytes(java.nio.charset.StandardCharsets.UTF_8);throw new android.database.sqlite.SQLiteException("Cannot convert number to blob");}
    private static final java.util.regex.Pattern INTEGER=java.util.regex.Pattern.compile("^[\\x00-\\x20]*([+-]?[0-9]+)");
    public synchronized long getLong(int row,int column){
        Object value=value(row,column);if(value==null)return 0;if(value instanceof Number)return ((Number)value).longValue();if(!(value instanceof String))throw new android.database.sqlite.SQLiteException("Cannot convert blob to integer");
        java.util.regex.Matcher match=INTEGER.matcher((String)value);if(!match.find())return 0;
        String number=match.group(1);try{return Long.parseLong(number);}catch(NumberFormatException overflow){return number.charAt(0)=='-'?Long.MIN_VALUE:Long.MAX_VALUE;}
    }
    private static final java.util.regex.Pattern REAL=java.util.regex.Pattern.compile("^[\\x00-\\x20]*([+-]?(?:(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|NaN|Infinity))",java.util.regex.Pattern.CASE_INSENSITIVE);
    public synchronized double getDouble(int row,int column){Object value=value(row,column);if(value==null)return 0;if(value instanceof Number)return ((Number)value).doubleValue();if(!(value instanceof String))throw new android.database.sqlite.SQLiteException("Cannot convert blob to real");java.util.regex.Matcher match=REAL.matcher((String)value);if(!match.find())return 0;String number=match.group(1);if(number.equalsIgnoreCase("nan"))return Double.NaN;if(number.toLowerCase(java.util.Locale.ROOT).endsWith("infinity"))return number.startsWith("-")?Double.NEGATIVE_INFINITY:Double.POSITIVE_INFINITY;return Double.parseDouble(number);}
    public short getShort(int row,int column){return (short)getLong(row,column);}public int getInt(int row,int column){return (int)getLong(row,column);}public float getFloat(int row,int column){return (float)getDouble(row,column);}
    public boolean isNull(int row,int column){return getType(row,column)==0;}public boolean isBlob(int row,int column){int type=getType(row,column);return type==0||type==4;}public boolean isString(int row,int column){int type=getType(row,column);return type==0||type==3;}public boolean isLong(int row,int column){return getType(row,column)==1;}public boolean isFloat(int row,int column){return getType(row,column)==2;}
    public void copyStringToBuffer(int row,int column,CharArrayBuffer buffer){if(buffer==null)throw new NullPointerException("buffer");String text=getString(row,column);int size=text==null?0:text.length();if(buffer.data==null||buffer.data.length<size)buffer.data=new char[size];if(text!=null)text.getChars(0,size,buffer.data,0);buffer.sizeCopied=size;}
    public int describeContents(){return 0;}
    public void writeToParcel(android.os.Parcel parcel,int flags){throw new UnsupportedOperationException("Cursor window parcel transport is not implemented");}
    public static final android.os.Parcelable.Creator<CursorWindow> CREATOR=new android.os.Parcelable.Creator<CursorWindow>(){public CursorWindow createFromParcel(android.os.Parcel parcel){throw new UnsupportedOperationException("Cursor window parcel transport is not implemented");}public CursorWindow[] newArray(int size){return new CursorWindow[size];}};
}
