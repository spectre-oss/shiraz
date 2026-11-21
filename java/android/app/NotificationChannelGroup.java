// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.*;
/** App-owned grouping metadata; the desktop session has no group blocking policy. */
public final class NotificationChannelGroup implements Parcelable, Cloneable {
    private final String id;
    private final String name;
    private String description;
    private final List<NotificationChannel> channels = new ArrayList<NotificationChannel>();
    private static String trim(CharSequence value) { if(value==null)return null;String text=value.toString();return text.length()>1000?text.substring(0,1000):text; }
    public NotificationChannelGroup(String id, CharSequence name) { this.id=trim(id);this.name=trim(name); }
    public String getId(){return id;}
    public CharSequence getName(){return name;}
    public String getDescription(){return description;}
    public void setDescription(String value){description=trim(value);}
    public boolean isBlocked(){return false;}
    public List<NotificationChannel> getChannels(){List<NotificationChannel> result=new ArrayList<NotificationChannel>();for(NotificationChannel c:channels)result.add(c.copy());return result;}
    void add(NotificationChannel channel){channels.add(channel.copy());}
    void validate(){if(id==null||id.isEmpty()||name==null||name.isEmpty())throw new IllegalArgumentException("Group needs an ID and name");}
    @Override public NotificationChannelGroup clone(){NotificationChannelGroup value=new NotificationChannelGroup(id,name);value.description=description;for(NotificationChannel c:channels)value.add(c);return value;}
    @Override public boolean equals(Object other){if(!(other instanceof NotificationChannelGroup))return false;NotificationChannelGroup g=(NotificationChannelGroup)other;return Objects.equals(id,g.id)&&Objects.equals(name,g.name)&&Objects.equals(description,g.description)&&channels.equals(g.channels);}
    @Override public int hashCode(){return Objects.hash(id,name,description,channels);}
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("NotificationChannelGroup IPC is not implemented");}
    public static final Parcelable.Creator<NotificationChannelGroup> CREATOR=new Parcelable.Creator<NotificationChannelGroup>(){
        public NotificationChannelGroup createFromParcel(Parcel in){throw new UnsupportedOperationException("NotificationChannelGroup parcel decoding is not implemented");}
        public NotificationChannelGroup[] newArray(int size){return new NotificationChannelGroup[size];}
    };
}
