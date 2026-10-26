// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.os.*;
import java.util.ArrayList;
/** MIME metadata for an in-process transfer. */
public class ClipDescription implements Parcelable {
 public static final String MIMETYPE_TEXT_PLAIN="text/plain",MIMETYPE_TEXT_HTML="text/html",MIMETYPE_TEXT_URILIST="text/uri-list",MIMETYPE_TEXT_INTENT="text/vnd.android.intent";
 private long timestamp;
 private final CharSequence label;private final String[] types;
 public ClipDescription(CharSequence label,String[] mimeTypes){if(mimeTypes==null)throw new NullPointerException("mimeTypes");if(mimeTypes.length>1024)throw new IllegalArgumentException("MIME count limit");this.label=label;types=mimeTypes.clone();}
 public ClipDescription(ClipDescription other){this(other.label,other.types);timestamp=other.timestamp;}
 public long getTimestamp(){return timestamp;}
 public void setTimestamp(long timestamp){this.timestamp=timestamp;}
 public CharSequence getLabel(){return label;}public int getMimeTypeCount(){return types.length;}public String getMimeType(int index){return types[index];}
 public static boolean compareMimeTypes(String concrete,String desired){if(desired.equals("*/*"))return true;int slash=desired.indexOf('/');return slash>0&&desired.endsWith("/*")?concrete.startsWith(desired.substring(0,slash+1)):concrete.equals(desired);}
 public boolean hasMimeType(String type){for(String candidate:types)if(compareMimeTypes(candidate,type))return true;return false;}
 public String[] filterMimeTypes(String type){ArrayList<String> result=new ArrayList<String>();for(String candidate:types)if(compareMimeTypes(candidate,type))result.add(candidate);return result.isEmpty()?null:result.toArray(new String[result.size()]);}
 public void validate(){if(types.length==0)throw new IllegalArgumentException("Empty MIME types");for(String type:types)if(type==null)throw new NullPointerException("MIME type");}
 public int describeContents(){return 0;}public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("ClipDescription parcel transport");}
 public static final Parcelable.Creator<ClipDescription> CREATOR=new Parcelable.Creator<ClipDescription>(){public ClipDescription createFromParcel(Parcel in){throw new UnsupportedOperationException("ClipDescription parcel transport");}public ClipDescription[] newArray(int count){return new ClipDescription[count];}};
}
