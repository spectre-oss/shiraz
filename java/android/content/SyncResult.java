// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.os.Parcel;
import android.os.Parcelable;

/** Outcome reported by a sync adapter, independent of scheduler policy. */
public final class SyncResult implements Parcelable {
    public static final SyncResult ALREADY_IN_PROGRESS=new SyncResult(true);
    public final boolean syncAlreadyInProgress;
    public final SyncStats stats;
    public boolean tooManyDeletions,tooManyRetries,databaseError,fullSyncRequested,partialSyncUnavailable,moreRecordsToGet;
    public long delayUntil;
    public SyncResult(){this(false);}
    private SyncResult(boolean busy){syncAlreadyInProgress=busy;stats=new SyncStats();}
    private SyncResult(Parcel source){
        syncAlreadyInProgress=source.readInt()!=0;
        tooManyDeletions=source.readInt()!=0;tooManyRetries=source.readInt()!=0;databaseError=source.readInt()!=0;
        fullSyncRequested=source.readInt()!=0;partialSyncUnavailable=source.readInt()!=0;moreRecordsToGet=source.readInt()!=0;
        delayUntil=source.readLong();stats=new SyncStats(source);
    }
    public boolean hasHardError(){return databaseError||tooManyRetries||tooManyDeletions||stats.numAuthExceptions>0||stats.numParseExceptions>0||stats.numConflictDetectedExceptions>0;}
    public boolean hasSoftError(){return stats.numIoExceptions>0||syncAlreadyInProgress;}
    public boolean hasError(){return hasHardError()||hasSoftError();}
    public boolean madeSomeProgress(){return stats.numUpdates>0||stats.numInserts>0||(!tooManyDeletions&&stats.numDeletes>0);}
    public void clear(){
        if(syncAlreadyInProgress)throw new UnsupportedOperationException("Cannot clear an in-progress result");
        tooManyDeletions=tooManyRetries=databaseError=fullSyncRequested=partialSyncUnavailable=moreRecordsToGet=false;
        delayUntil=0;stats.clear();
    }
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel target,int flags){
        for(boolean value:new boolean[]{syncAlreadyInProgress,tooManyDeletions,tooManyRetries,databaseError,fullSyncRequested,partialSyncUnavailable,moreRecordsToGet})target.writeInt(value?1:0);
        target.writeLong(delayUntil);stats.writeToParcel(target,flags);
    }
    public static final Parcelable.Creator<SyncResult> CREATOR=new Parcelable.Creator<SyncResult>(){
        public SyncResult createFromParcel(Parcel source){return new SyncResult(source);}
        public SyncResult[] newArray(int count){return new SyncResult[count];}
    };
    public String toDebugString(){
        StringBuilder result=new StringBuilder();
        long[] values={fullSyncRequested?1:0,partialSyncUnavailable?1:0,hasHardError()?1:0,stats.numParseExceptions,stats.numConflictDetectedExceptions,stats.numAuthExceptions,tooManyDeletions?1:0,tooManyRetries?1:0,databaseError?1:0,hasSoftError()?1:0,syncAlreadyInProgress?1:0,stats.numIoExceptions};
        String codes="frXecaDRbxlI";
        for(int i=0;i<values.length;i++)if(values[i]>0)result.append(codes.charAt(i)).append(values[i]);
        return result.toString();
    }
    @Override public String toString(){return "SyncResult{"+toDebugString()+", moreRecordsToGet="+moreRecordsToGet+", delayUntil="+delayUntil+", "+stats+"}";}
}
