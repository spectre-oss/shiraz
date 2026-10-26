// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import android.content.Context;
import android.net.Uri;
public class Ringtone {
 private final Uri uri;
 Ringtone(Uri uri){this.uri=uri;}
 public String getTitle(Context context){return uri==null?"Silent":uri.getLastPathSegment();}
}
