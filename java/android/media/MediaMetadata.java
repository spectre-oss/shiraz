// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import android.os.*;
import android.graphics.Bitmap;
import java.util.*;
/** Snapshot of media information shared with local session controllers. */
public final class MediaMetadata implements Parcelable {
 public static final String METADATA_KEY_TITLE="android.media.metadata.TITLE";
 public static final String METADATA_KEY_ARTIST="android.media.metadata.ARTIST";
 public static final String METADATA_KEY_DURATION="android.media.metadata.DURATION";
 public static final String METADATA_KEY_ALBUM="android.media.metadata.ALBUM";
 public static final String METADATA_KEY_AUTHOR="android.media.metadata.AUTHOR";
 public static final String METADATA_KEY_WRITER="android.media.metadata.WRITER";
 public static final String METADATA_KEY_COMPOSER="android.media.metadata.COMPOSER";
 public static final String METADATA_KEY_COMPILATION="android.media.metadata.COMPILATION";
 public static final String METADATA_KEY_DATE="android.media.metadata.DATE";
 public static final String METADATA_KEY_YEAR="android.media.metadata.YEAR";
 public static final String METADATA_KEY_GENRE="android.media.metadata.GENRE";
 public static final String METADATA_KEY_TRACK_NUMBER="android.media.metadata.TRACK_NUMBER";
 public static final String METADATA_KEY_NUM_TRACKS="android.media.metadata.NUM_TRACKS";
 public static final String METADATA_KEY_DISC_NUMBER="android.media.metadata.DISC_NUMBER";
 public static final String METADATA_KEY_ALBUM_ARTIST="android.media.metadata.ALBUM_ARTIST";
 public static final String METADATA_KEY_ART="android.media.metadata.ART";
 public static final String METADATA_KEY_ART_URI="android.media.metadata.ART_URI";
 public static final String METADATA_KEY_ALBUM_ART="android.media.metadata.ALBUM_ART";
 public static final String METADATA_KEY_ALBUM_ART_URI="android.media.metadata.ALBUM_ART_URI";
 public static final String METADATA_KEY_USER_RATING="android.media.metadata.USER_RATING";
 public static final String METADATA_KEY_RATING="android.media.metadata.RATING";
 public static final String METADATA_KEY_DISPLAY_TITLE="android.media.metadata.DISPLAY_TITLE";
 public static final String METADATA_KEY_DISPLAY_SUBTITLE="android.media.metadata.DISPLAY_SUBTITLE";
 public static final String METADATA_KEY_DISPLAY_DESCRIPTION="android.media.metadata.DISPLAY_DESCRIPTION";
 public static final String METADATA_KEY_DISPLAY_ICON="android.media.metadata.DISPLAY_ICON";
 public static final String METADATA_KEY_DISPLAY_ICON_URI="android.media.metadata.DISPLAY_ICON_URI";
 public static final String METADATA_KEY_MEDIA_ID="android.media.metadata.MEDIA_ID";
 public static final String METADATA_KEY_BT_FOLDER_TYPE="android.media.metadata.BT_FOLDER_TYPE";
 public static final String METADATA_KEY_MEDIA_URI="android.media.metadata.MEDIA_URI";
 public static final String METADATA_KEY_ADVERTISEMENT="android.media.metadata.ADVERTISEMENT";
 public static final String METADATA_KEY_DOWNLOAD_STATUS="android.media.metadata.DOWNLOAD_STATUS";
 private final Map<String,Object> values;
 private MediaMetadata(Map<String,Object> source){values=new HashMap<>(source);}
 public boolean containsKey(String key){return values.containsKey(key);}
 public int size(){return values.size();}
 public Set<String> keySet(){return Collections.unmodifiableSet(values.keySet());}
 public CharSequence getText(String key){Object v=values.get(key);return v instanceof CharSequence?(CharSequence)v:null;}
 public String getString(String key){CharSequence v=getText(key);return v==null?null:v.toString();}
 public long getLong(String key){Object v=values.get(key);return v instanceof Long?(Long)v:0;}
 public Bitmap getBitmap(String key){Object v=values.get(key);return v instanceof Bitmap?(Bitmap)v:null;}
 public Rating getRating(String key){Object v=values.get(key);return v instanceof Rating?(Rating)v:null;}
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel p,int flags){
  Bundle b=new Bundle();for(Map.Entry<String,Object> e:values.entrySet()){
   Object v=e.getValue();if(v instanceof Bitmap)throw new UnsupportedOperationException("MediaMetadata bitmap parceling");
   if(v instanceof Long)b.putLong(e.getKey(),(Long)v);else if(v instanceof Rating)b.putParcelable(e.getKey(),(Rating)v);else b.putCharSequence(e.getKey(),(CharSequence)v);
  }b.writeToParcel(p,flags);
 }
 public static final Parcelable.Creator<MediaMetadata> CREATOR=new Parcelable.Creator<MediaMetadata>(){
  public MediaMetadata createFromParcel(Parcel p){Bundle b=Bundle.CREATOR.createFromParcel(p);Map<String,Object> v=new HashMap<>();for(String key:b.keySet())v.put(key,b.get(key));return new MediaMetadata(v);}
  public MediaMetadata[] newArray(int n){return new MediaMetadata[n];}
 };
 private static Class<?> type(String key){
  if(METADATA_KEY_TITLE.equals(key))return CharSequence.class;
  if(METADATA_KEY_ARTIST.equals(key))return CharSequence.class;
  if(METADATA_KEY_DURATION.equals(key))return Long.class;
  if(METADATA_KEY_ALBUM.equals(key))return CharSequence.class;
  if(METADATA_KEY_AUTHOR.equals(key))return CharSequence.class;
  if(METADATA_KEY_WRITER.equals(key))return CharSequence.class;
  if(METADATA_KEY_COMPOSER.equals(key))return CharSequence.class;
  if(METADATA_KEY_COMPILATION.equals(key))return CharSequence.class;
  if(METADATA_KEY_DATE.equals(key))return CharSequence.class;
  if(METADATA_KEY_YEAR.equals(key))return Long.class;
  if(METADATA_KEY_GENRE.equals(key))return CharSequence.class;
  if(METADATA_KEY_TRACK_NUMBER.equals(key))return Long.class;
  if(METADATA_KEY_NUM_TRACKS.equals(key))return Long.class;
  if(METADATA_KEY_DISC_NUMBER.equals(key))return Long.class;
  if(METADATA_KEY_ALBUM_ARTIST.equals(key))return CharSequence.class;
  if(METADATA_KEY_ART.equals(key))return Bitmap.class;
  if(METADATA_KEY_ART_URI.equals(key))return CharSequence.class;
  if(METADATA_KEY_ALBUM_ART.equals(key))return Bitmap.class;
  if(METADATA_KEY_ALBUM_ART_URI.equals(key))return CharSequence.class;
  if(METADATA_KEY_USER_RATING.equals(key))return Rating.class;
  if(METADATA_KEY_RATING.equals(key))return Rating.class;
  if(METADATA_KEY_DISPLAY_TITLE.equals(key))return CharSequence.class;
  if(METADATA_KEY_DISPLAY_SUBTITLE.equals(key))return CharSequence.class;
  if(METADATA_KEY_DISPLAY_DESCRIPTION.equals(key))return CharSequence.class;
  if(METADATA_KEY_DISPLAY_ICON.equals(key))return Bitmap.class;
  if(METADATA_KEY_DISPLAY_ICON_URI.equals(key))return CharSequence.class;
  if(METADATA_KEY_MEDIA_ID.equals(key))return CharSequence.class;
  if(METADATA_KEY_BT_FOLDER_TYPE.equals(key))return Long.class;
  if(METADATA_KEY_MEDIA_URI.equals(key))return CharSequence.class;
  if(METADATA_KEY_ADVERTISEMENT.equals(key))return Long.class;
  if(METADATA_KEY_DOWNLOAD_STATUS.equals(key))return Long.class;
  return null;
 }
 public static final class Builder {
  private final Map<String,Object> values=new HashMap<>();
  public Builder(){}public Builder(MediaMetadata source){values.putAll(source.values);}
  private Builder put(String key,Object value,Class<?> kind){Class<?> expected=type(key);if(expected!=null&&expected!=kind)throw new IllegalArgumentException("Wrong metadata type for "+key);values.put(key,value);return this;}
  public Builder putText(String key,CharSequence value){return put(key,value==null?null:value.toString(),CharSequence.class);}
  public Builder putString(String key,String value){return putText(key,value);}
  public Builder putLong(String key,long value){return put(key,value,Long.class);}
  public Builder putBitmap(String key,Bitmap value){return put(key,value,Bitmap.class);}
  public Builder putRating(String key,Rating value){return put(key,value,Rating.class);}
  public MediaMetadata build(){return new MediaMetadata(values);}
 }
}
