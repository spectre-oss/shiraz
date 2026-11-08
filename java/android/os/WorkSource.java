// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/** Owned work-attribution data; host power accounting is not implied. */
public class WorkSource implements Parcelable {
    private static final int LIMIT=4096;
    private static final class Entry {
        final int uid; final String name;
        Entry(int uid,String name){this.uid=uid;this.name=name;}
    }
    private final ArrayList<Entry> entries=new ArrayList<Entry>();
    private boolean named;
    private static final Comparator<Entry> ORDER=new Comparator<Entry>() {
        public int compare(Entry a,Entry b){int value=Integer.compare(a.uid,b.uid);if(value!=0)return value;return a.name==null?(b.name==null?0:-1):b.name==null?1:a.name.compareTo(b.name);}
    };
    public WorkSource(){}
    public WorkSource(WorkSource other){set(other);}
    public WorkSource(int uid){add(uid);}
    public WorkSource(int uid,String name){add(uid,name);}
    public int size(){return entries.size();}
    public boolean isEmpty(){return entries.isEmpty();}
    public int get(int index){return entries.get(index).uid;}
    public int getUid(int index){return get(index);}
    public String getName(int index){return entries.get(index).name;}
    public String getPackageName(int index){return getName(index);}
    public void clear(){entries.clear();named=false;}
    public void set(WorkSource other){if(other==this)return;clear();if(other!=null){named=other.named;entries.addAll(other.entries);}}
    public void set(int uid){clear();add(uid);}
    public void set(int uid,String name){if(name==null)throw new NullPointerException("name");clear();add(uid,name);}
    private boolean insert(int uid,String name){
        if(!entries.isEmpty()&&named!=(name!=null))throw new IllegalArgumentException("Cannot mix named and unnamed work sources");
        Entry entry=new Entry(uid,name);int index=Collections.binarySearch(entries,entry,ORDER);
        if(index>=0)return false;
        if(entries.size()>=LIMIT)throw new IllegalStateException("WorkSource entry limit");
        named=name!=null;entries.add(-index-1,entry);return true;
    }
    public boolean add(int uid){return insert(uid,null);}
    public boolean add(int uid,String name){if(name==null)throw new NullPointerException("name");return insert(uid,name);}
    public boolean add(WorkSource other){
        if(other==null||other==this||other.isEmpty())return false;
        if(!isEmpty()&&named!=other.named)throw new IllegalArgumentException("Cannot mix named and unnamed work sources");
        WorkSource merged=new WorkSource(this);boolean changed=false;
        for(Entry entry:other.entries)changed|=merged.insert(entry.uid,entry.name);
        if(changed)set(merged);return changed;
    }
    public boolean remove(WorkSource other){
        if(other==null||other.isEmpty()||isEmpty())return false;
        if(named!=other.named)throw new IllegalArgumentException("Cannot mix named and unnamed work sources");
        boolean changed=false;for(Entry entry:new ArrayList<Entry>(other.entries)){int index=Collections.binarySearch(entries,entry,ORDER);if(index>=0){entries.remove(index);changed=true;}}
        if(entries.isEmpty())named=false;return changed;
    }
    public void clearNames(){if(!named)return;ArrayList<Entry> copy=new ArrayList<Entry>(entries);clear();for(Entry entry:copy)add(entry.uid);}
    public WorkSource withoutNames(){WorkSource copy=new WorkSource(this);copy.clearNames();return copy;}
    public boolean diff(WorkSource other){return !equals(other);}
    @Override public boolean equals(Object value){if(this==value)return true;if(!(value instanceof WorkSource))return false;WorkSource other=(WorkSource)value;if(size()!=other.size())return false;for(int i=0;i<size();i++)if(ORDER.compare(entries.get(i),other.entries.get(i))!=0)return false;return true;}
    @Override public int hashCode(){int hash=1;for(Entry entry:entries)hash=31*hash+entry.uid+(entry.name==null?0:entry.name.hashCode());return hash;}
    @Override public String toString(){StringBuilder text=new StringBuilder("WorkSource{");for(int i=0;i<size();i++){if(i>0)text.append(", ");Entry entry=entries.get(i);text.append(entry.uid);if(entry.name!=null)text.append(':').append(entry.name);}return text.append('}').toString();}
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel output,int flags){int[] uids=new int[size()];String[] names=named?new String[size()]:null;for(int i=0;i<size();i++){uids[i]=get(i);if(names!=null)names[i]=getName(i);}output.writeInt(size());output.writeIntArray(uids);output.writeStringArray(names);}
    public static final Parcelable.Creator<WorkSource> CREATOR=new Parcelable.Creator<WorkSource>(){
        public WorkSource createFromParcel(Parcel input){int size=input.readInt();if(size<0||size>LIMIT)throw new BadParcelableException("WorkSource size");int[] uids=input.createIntArray();String[] names=input.createStringArray();if((size>0&&(uids==null||uids.length<size))||(names!=null&&names.length<size))throw new BadParcelableException("WorkSource arrays");WorkSource source=new WorkSource();for(int i=0;i<size;i++){boolean added=names==null?source.add(uids[i]):source.add(uids[i],names[i]);if(!added)throw new BadParcelableException("Duplicate WorkSource entry");}return source;}
        public WorkSource[] newArray(int size){return new WorkSource[size];}
    };
}
