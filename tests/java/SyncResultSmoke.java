// SPDX-License-Identifier: GPL-3.0-only
import android.content.SyncResult;
import android.content.SyncStats;
import android.os.Parcel;

public final class SyncResultSmoke {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        SyncResult result=new SyncResult();SyncStats identity=result.stats;
        check(!result.hasError()&&!result.madeSomeProgress(),"fresh result");
        for(String field:new String[]{"numAuthExceptions","numParseExceptions","numConflictDetectedExceptions"}){
            result.stats.getClass().getField(field).setLong(result.stats,1);
            check(result.hasHardError()&&!result.hasSoftError()&&result.hasError(),"hard statistic: "+field);result.clear();
        }
        for(String field:new String[]{"tooManyDeletions","tooManyRetries","databaseError"}){
            result.getClass().getField(field).setBoolean(result,true);
            check(result.hasHardError()&&!result.hasSoftError(),"hard flag: "+field);result.clear();
        }
        result.stats.numIoExceptions=1;check(result.hasSoftError()&&!result.hasHardError(),"network failure");
        result.databaseError=true;check(result.hasSoftError()&&result.hasHardError(),"mixed failures");result.clear();
        result.stats.numDeletes=1;check(result.madeSomeProgress(),"deletion progress");
        result.tooManyDeletions=true;check(!result.madeSomeProgress(),"blocked deletions counted as progress");
        result.stats.numInserts=1;check(result.madeSomeProgress(),"insert progress despite blocked deletions");result.clear();
        result.stats.numUpdates=1;check(result.madeSomeProgress(),"update progress");result.clear();
        result.stats.numEntries=3;result.stats.numSkippedEntries=2;check(!result.madeSomeProgress(),"informational counts treated as progress");result.clear();
        check(result.stats==identity,"clear replaced statistics object");
        SyncResult busy=SyncResult.ALREADY_IN_PROGRESS;
        check(busy.hasSoftError()&&!busy.hasHardError()&&busy.syncAlreadyInProgress,"busy classification");
        try{busy.clear();throw new AssertionError("busy result cleared");}catch(UnsupportedOperationException expected){}
        Parcel wire=Parcel.obtain();
        try{
            // Independent ABI input: seven boolean ints, delay long, nine statistic longs.
            for(int value:new int[]{0,1,0,1,0,1,0})wire.writeInt(value);
            wire.writeLong(Long.MAX_VALUE);
            for(long value=11;value<=19;value++)wire.writeLong(value);
            wire.setDataPosition(0);SyncResult decoded=SyncResult.CREATOR.createFromParcel(wire);
            check(decoded.tooManyDeletions&&!decoded.tooManyRetries&&decoded.databaseError&&!decoded.fullSyncRequested&&decoded.partialSyncUnavailable&&!decoded.moreRecordsToGet,"wire flag order");
            check(decoded.delayUntil==Long.MAX_VALUE&&decoded.stats.numAuthExceptions==11&&decoded.stats.numIoExceptions==12&&decoded.stats.numParseExceptions==13&&decoded.stats.numConflictDetectedExceptions==14&&decoded.stats.numInserts==15&&decoded.stats.numUpdates==16&&decoded.stats.numDeletes==17&&decoded.stats.numEntries==18&&decoded.stats.numSkippedEntries==19,"wire counter order");
            check(decoded.toDebugString().equals("r1X1e13c14a11D1b1x1I12"),"debug status");
            Parcel encoded=Parcel.obtain();
            try{
                decoded.writeToParcel(encoded,0);encoded.setDataPosition(0);
                for(int expected:new int[]{0,1,0,1,0,1,0})check(encoded.readInt()==expected,"serialized flags");
                check(encoded.readLong()==Long.MAX_VALUE,"serialized delay");
                for(long expected=11;expected<=19;expected++)check(encoded.readLong()==expected,"serialized counters");
            }finally{encoded.recycle();}
            decoded.clear();check(!decoded.hasError()&&!decoded.madeSomeProgress()&&decoded.delayUntil==0&&!decoded.partialSyncUnavailable&&decoded.stats.numSkippedEntries==0,"decoded reset");
        }finally{wire.recycle();}
        check(SyncStats.CREATOR.newArray(2).length==2&&SyncResult.CREATOR.newArray(3).length==3,"creator arrays");
        System.out.println("SYNC_RESULT_CONTRACT_OK");
    }
}
