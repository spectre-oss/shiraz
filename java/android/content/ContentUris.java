// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.net.Uri;
public class ContentUris {
 private ContentUris(){}
 public static long parseId(Uri uri){String last=uri.getLastPathSegment();return last==null?-1:Long.parseLong(last);}
 public static Uri withAppendedId(Uri uri,long id){return Uri.parse(uri.toString().replaceAll("/+$","")+"/"+id);}
}
