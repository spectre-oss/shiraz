// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.os.Parcel;
import android.os.Parcelable;

/** Android sync counters; carries results without scheduling synchronization. */
public class SyncStats implements Parcelable {
    public long numAuthExceptions,numIoExceptions,numParseExceptions,numConflictDetectedExceptions,numInserts,numUpdates,numDeletes,numEntries,numSkippedEntries;
    public SyncStats(){}
    public SyncStats(Parcel source){
        numAuthExceptions=source.readLong();
        numIoExceptions=source.readLong();
        numParseExceptions=source.readLong();
        numConflictDetectedExceptions=source.readLong();
        numInserts=source.readLong();
        numUpdates=source.readLong();
        numDeletes=source.readLong();
        numEntries=source.readLong();
        numSkippedEntries=source.readLong();
    }
    public void clear(){numAuthExceptions=numIoExceptions=numParseExceptions=numConflictDetectedExceptions=numInserts=numUpdates=numDeletes=numEntries=numSkippedEntries=0;}
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel target,int flags){
        target.writeLong(numAuthExceptions);
        target.writeLong(numIoExceptions);
        target.writeLong(numParseExceptions);
        target.writeLong(numConflictDetectedExceptions);
        target.writeLong(numInserts);
        target.writeLong(numUpdates);
        target.writeLong(numDeletes);
        target.writeLong(numEntries);
        target.writeLong(numSkippedEntries);
    }
    public static final Parcelable.Creator<SyncStats> CREATOR=new Parcelable.Creator<SyncStats>(){
        public SyncStats createFromParcel(Parcel source){return new SyncStats(source);}
        public SyncStats[] newArray(int count){return new SyncStats[count];}
    };
    @Override public String toString(){return "SyncStats"+java.util.Arrays.toString(new long[]{numAuthExceptions,numIoExceptions,numParseExceptions,numConflictDetectedExceptions,numInserts,numUpdates,numDeletes,numEntries,numSkippedEntries});}
}
