// SPDX-License-Identifier: GPL-3.0-only
package android.database;

/** Cursor backed by a replaceable, owned window of typed rows. */
public abstract class AbstractWindowedCursor extends AbstractCursor {
    protected CursorWindow mWindow;
    @Override protected void checkPosition(){super.checkPosition();if(mWindow==null)throw new IllegalStateException("Cursor has no row window");}
    @Override public byte[] getBlob(int column){checkPosition();return mWindow.getBlob(mPos,column);}
    @Override public String getString(int column){checkPosition();return mWindow.getString(mPos,column);}
    @Override public short getShort(int column){checkPosition();return mWindow.getShort(mPos,column);}
    @Override public int getInt(int column){checkPosition();return mWindow.getInt(mPos,column);}
    @Override public long getLong(int column){checkPosition();return mWindow.getLong(mPos,column);}
    @Override public float getFloat(int column){checkPosition();return mWindow.getFloat(mPos,column);}
    @Override public double getDouble(int column){checkPosition();return mWindow.getDouble(mPos,column);}
    @Override public int getType(int column){checkPosition();return mWindow.getType(mPos,column);}
    @Override public boolean isNull(int column){return getType(column)==FIELD_TYPE_NULL;}
    public boolean isBlob(int column){int type=getType(column);return type==FIELD_TYPE_NULL||type==FIELD_TYPE_BLOB;}
    public boolean isString(int column){int type=getType(column);return type==FIELD_TYPE_NULL||type==FIELD_TYPE_STRING;}
    public boolean isLong(int column){return getType(column)==FIELD_TYPE_INTEGER;}public boolean isFloat(int column){return getType(column)==FIELD_TYPE_FLOAT;}
    @Override public void copyStringToBuffer(int column,CharArrayBuffer buffer){checkPosition();mWindow.copyStringToBuffer(mPos,column,buffer);}
    @Override public CursorWindow getWindow(){return mWindow;}
    public void setWindow(CursorWindow window){if(window==mWindow)return;closeWindow();mWindow=window;}
    public boolean hasWindow(){return mWindow!=null;}
    protected void closeWindow(){CursorWindow old=mWindow;mWindow=null;if(old!=null)old.close();}
    protected void clearOrCreateWindow(String name){if(mWindow==null)mWindow=new CursorWindow(name);else mWindow.clear();}
    @Override protected void onDeactivateOrClose(){try{closeWindow();}finally{super.onDeactivateOrClose();}}
}
