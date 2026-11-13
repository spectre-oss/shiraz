// SPDX-License-Identifier: GPL-3.0-only
package android.database;

import android.os.Bundle;

/** Cursor positioning and observer lifecycle independent of the row storage backend. */
public abstract class AbstractCursor implements CrossProcessCursor {
    protected int mPos=-1;protected boolean mClosed;
    private final DataSetObservable data=new DataSetObservable();private final java.util.ArrayList<ContentObserver> content=new java.util.ArrayList<ContentObserver>();private Bundle extras=Bundle.EMPTY;
    protected void checkPosition(){if(mClosed)throw new IllegalStateException("Cursor is closed");if(mPos<0||mPos>=getCount())throw new CursorIndexOutOfBoundsException(mPos,getCount());}
    public abstract int getCount();public abstract String[] getColumnNames();
    public abstract String getString(int column);public abstract short getShort(int column);public abstract int getInt(int column);public abstract long getLong(int column);public abstract float getFloat(int column);public abstract double getDouble(int column);public abstract boolean isNull(int column);
    public byte[] getBlob(int column){throw new UnsupportedOperationException("Blob access is not implemented by this cursor");}
    public int getType(int column){checkPosition();return isNull(column)?FIELD_TYPE_NULL:FIELD_TYPE_STRING;}
    public int getPosition(){return mPos;}
    public final boolean moveToPosition(int target){
        if(mClosed)throw new IllegalStateException("Cursor is closed");int count=getCount();
        if(target>=count){mPos=count;return false;}if(target<0){mPos=-1;return false;}if(target==mPos)return true;
        if(!onMove(mPos,target)){mPos=-1;return false;}mPos=target;return true;
    }
    public final boolean move(int offset){long target=(long)mPos+offset;return moveToPosition((int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,target)));}
    public final boolean moveToFirst(){return moveToPosition(0);}public final boolean moveToLast(){return moveToPosition(getCount()-1);}public final boolean moveToNext(){return move(1);}public final boolean moveToPrevious(){return move(-1);}
    public final boolean isFirst(){return getCount()!=0&&mPos==0;}public final boolean isLast(){return getCount()!=0&&mPos==getCount()-1;}public final boolean isBeforeFirst(){return getCount()==0||mPos==-1;}public final boolean isAfterLast(){return getCount()==0||mPos==getCount();}
    public boolean onMove(int oldPosition,int newPosition){return true;}
    public int getColumnCount(){return getColumnNames().length;}public String getColumnName(int index){return getColumnNames()[index];}
    public int getColumnIndex(String name){if(name==null)return -1;int dot=name.lastIndexOf('.');if(dot>=0)name=name.substring(dot+1);String[] names=getColumnNames();for(int i=0;i<names.length;i++)if(name.equalsIgnoreCase(names[i]))return i;return -1;}
    public int getColumnIndexOrThrow(String name){int index=getColumnIndex(name);if(index<0)throw new IllegalArgumentException("No column: "+name);return index;}
    public void copyStringToBuffer(int column,CharArrayBuffer buffer){if(buffer==null)throw new NullPointerException("buffer");String text=getString(column);int size=text==null?0:text.length();if(buffer.data==null||buffer.data.length<size)buffer.data=new char[size];if(text!=null)text.getChars(0,size,buffer.data,0);buffer.sizeCopied=size;}
    public CursorWindow getWindow(){return null;}
    public void fillWindow(int position,CursorWindow window){DatabaseUtils.cursorFillWindow(this,position,window);}
    protected void onDeactivateOrClose(){data.notifyInvalidated();}
    public void deactivate(){onDeactivateOrClose();}
    public boolean requery(){if(mClosed)return false;mPos=-1;data.notifyChanged();return true;}
    public void close(){if(mClosed)return;mClosed=true;synchronized(content){content.clear();}onDeactivateOrClose();}
    public boolean isClosed(){return mClosed;}
    public void registerContentObserver(ContentObserver observer){if(observer==null)throw new IllegalArgumentException("The observer is null.");synchronized(content){if(content.contains(observer))throw new IllegalStateException("Observer "+observer+" is already registered.");content.add(observer);}}
    public void unregisterContentObserver(ContentObserver observer){if(observer==null)throw new IllegalArgumentException("The observer is null.");synchronized(content){if(!mClosed&&!content.remove(observer))throw new IllegalStateException("Observer "+observer+" was not registered.");}}
    public void registerDataSetObserver(DataSetObserver observer){data.registerObserver(observer);}public void unregisterDataSetObserver(DataSetObserver observer){data.unregisterObserver(observer);}
    public Bundle getExtras(){return extras;}public void setExtras(Bundle value){extras=value==null?Bundle.EMPTY:new Bundle(value);}public Bundle respond(Bundle value){return Bundle.EMPTY;}
    public boolean getWantsAllOnMoveCalls(){return false;}
}
