// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.database.*;
import android.net.Uri;

public final class CursorNotificationSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static final class Empty extends AbstractCursor {
        public int getCount(){return 0;}public String[] getColumnNames(){return new String[]{"value"};}
        public String getString(int c){return null;}public short getShort(int c){return 0;}public int getInt(int c){return 0;}
        public long getLong(int c){return 0;}public float getFloat(int c){return 0;}public double getDouble(int c){return 0;}public boolean isNull(int c){return true;}
    }
    static void run(Cursor cursor){
        ContentResolver first=new ContentResolver(new ContextWrapper(null)),second=new ContentResolver(new ContextWrapper(null));
        final int[] changes={0},dataChanges={0};
        cursor.registerContentObserver(new ContentObserver(null){public void onChange(boolean self){check(!self,"provider change became self change");changes[0]++;}});
        cursor.registerDataSetObserver(new DataSetObserver(){public void onChanged(){dataChanges[0]++;}});
        Uri uri=Uri.parse("content://fixture/rows"),child=Uri.parse("content://fixture/rows/1");
        check(cursor.getNotificationUri()==null,"initial URI");cursor.setNotificationUri(first,uri);
        check(uri.equals(cursor.getNotificationUri()),"saved URI");first.notifyChange(child,null,false);
        check(changes[0]==1&&dataChanges[0]==0,"provider change must reach content observers, not imply refreshed data");
        cursor.deactivate();first.notifyChange(child,null,false);check(changes[0]==1,"deactivated subscription");
        check(cursor.requery(),"requery");first.notifyChange(child,null,false);check(changes[0]==2&&dataChanges[0]==1,"requery registration");
        cursor.requery();first.notifyChange(child,null,false);check(changes[0]==3,"requery duplicate registration");
        cursor.setNotificationUri(second,uri);first.notifyChange(child,null,false);check(changes[0]==3,"old resolver retained subscription");
        second.notifyChange(child,null,false);check(changes[0]==4,"replacement resolver");
        try{cursor.setNotificationUri(first,Uri.parse("file:///invalid"));throw new AssertionError("invalid URI accepted");}catch(IllegalArgumentException expected){}
        second.notifyChange(child,null,false);check(changes[0]==5&&uri.equals(cursor.getNotificationUri()),"failed replacement destroyed subscription");
        cursor.close();cursor.close();second.notifyChange(child,null,false);check(changes[0]==5,"closed subscription");
        try{cursor.setNotificationUri(first,uri);throw new AssertionError("closed cursor registered");}catch(IllegalStateException expected){}
    }
    public static void main(String[] args){run(new Empty());run(new MatrixCursor(new String[]{"value"}));System.out.println("CURSOR_NOTIFICATION_OK");}
}
