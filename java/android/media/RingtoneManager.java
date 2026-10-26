// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import android.content.Context;
import android.net.Uri;
/** Default sound URIs only; the host has no ringtone database. */
public class RingtoneManager {
 public static final int TYPE_RINGTONE=1,TYPE_NOTIFICATION=2,TYPE_ALARM=4,TYPE_ALL=7;
 private RingtoneManager(){}
 public static Uri getDefaultUri(int type){return Uri.parse("content://settings/system/"+(type==TYPE_ALARM?"alarm_alert":type==TYPE_NOTIFICATION?"notification_sound":"ringtone"));}
 public static Ringtone getRingtone(Context context,Uri uri){return new Ringtone(uri);}
}
