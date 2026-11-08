// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import android.content.Context;
import android.net.Uri;
/** Default sound URIs only; the host has no ringtone database. */
public class RingtoneManager {
 public static final int TYPE_RINGTONE=1,TYPE_NOTIFICATION=2,TYPE_ALARM=4,TYPE_ALL=7;
 public static final int ID_COLUMN_INDEX=0,TITLE_COLUMN_INDEX=1,URI_COLUMN_INDEX=2;
 private int type=TYPE_RINGTONE;private android.database.MatrixCursor cursor;
 public RingtoneManager(Context context){if(context==null)throw new NullPointerException("context");}
 public void setType(int type){if((type&~TYPE_ALL)!=0||type==0)throw new IllegalArgumentException("Ringtone type");if(cursor!=null)throw new IllegalStateException("Ringtone cursor already queried");this.type=type;}
 public android.database.Cursor getCursor(){if(cursor==null||cursor.isClosed())cursor=new android.database.MatrixCursor(new String[]{"_id","title","uri"},0);return cursor;}
 public static Uri getDefaultUri(int type){return Uri.parse("content://settings/system/"+(type==TYPE_ALARM?"alarm_alert":type==TYPE_NOTIFICATION?"notification_sound":"ringtone"));}
 public static Ringtone getRingtone(Context context,Uri uri){return new Ringtone(uri);}
}
