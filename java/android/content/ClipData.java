// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.os.*;
import android.net.Uri;
import java.util.ArrayList;
/** In-process transfer payload; provider coercion and IPC are separate. */
public class ClipData implements Parcelable {
 private final ClipDescription description;private final ArrayList<Item> items=new ArrayList<Item>();
 public ClipData(CharSequence label,String[] types,Item item){this(new ClipDescription(label,types),item);}
 public ClipData(ClipDescription description,Item item){if(description==null)throw new NullPointerException("description");this.description=description;addItem(item);}
 public ClipData(ClipData other){description=new ClipDescription(other.description);items.addAll(other.items);}
 public static ClipData newPlainText(CharSequence label,CharSequence text){return new ClipData(label,new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN},new Item(text));}
 public static ClipData newHtmlText(CharSequence label,CharSequence text,String html){return new ClipData(label,new String[]{ClipDescription.MIMETYPE_TEXT_HTML,ClipDescription.MIMETYPE_TEXT_PLAIN},new Item(text,html));}
 public static ClipData newIntent(CharSequence label,Intent intent){return new ClipData(label,new String[]{ClipDescription.MIMETYPE_TEXT_INTENT},new Item(intent));}
 public static ClipData newRawUri(CharSequence label,Uri uri){return new ClipData(label,new String[]{ClipDescription.MIMETYPE_TEXT_URILIST},new Item(uri));}
 public ClipDescription getDescription(){return description;}public android.graphics.Bitmap getIcon(){return null;}
 public int getItemCount(){return items.size();}public Item getItemAt(int index){return items.get(index);}
 public void addItem(Item item){if(item==null)throw new NullPointerException("item");if(items.size()>=4096)throw new IllegalArgumentException("Clip item limit");items.add(item);}
 public static class Item {
  private final CharSequence text;private final String html;private final Intent intent;private final Uri uri;
  public Item(CharSequence text){this(text,null,null,null);}public Item(CharSequence text,String html){this(text,html,null,null);}public Item(Intent intent){this(null,null,intent,null);}public Item(Uri uri){this(null,null,null,uri);}
  public Item(CharSequence text,Intent intent,Uri uri){this(text,null,intent,uri);}
  public Item(CharSequence text,String html,Intent intent,Uri uri){if(html!=null&&text==null)throw new IllegalArgumentException("HTML requires plain text");this.text=text;this.html=html;this.intent=intent;this.uri=uri;}
  public CharSequence getText(){return text;}public String getHtmlText(){return html;}public Intent getIntent(){return intent;}public Uri getUri(){return uri;}
  public CharSequence coerceToText(Context context){if(text!=null)return text;if(uri!=null||intent!=null)throw new UnsupportedOperationException("URI/Intent text coercion");return "";}
 }
 public int describeContents(){return 0;}public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("ClipData parcel transport");}
 public static final Parcelable.Creator<ClipData> CREATOR=new Parcelable.Creator<ClipData>(){public ClipData createFromParcel(Parcel in){throw new UnsupportedOperationException("ClipData parcel transport");}public ClipData[] newArray(int count){return new ClipData[count];}};
}
