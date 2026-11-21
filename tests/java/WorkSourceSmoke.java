// SPDX-License-Identifier: GPL-3.0-only
import android.os.*;
public final class WorkSourceSmoke {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        WorkSource source=new WorkSource();check(source.isEmpty()&&source.size()==0,"empty source");
        check(source.add(200)&&source.add(100)&&!source.add(200),"sorted unique UIDs");
        check(source.get(0)==100&&source.get(1)==200&&source.getName(0)==null,"UID access");
        WorkSource copy=new WorkSource(source);source.clear();check(copy.size()==2&&source.isEmpty(),"owned copy");
        WorkSource named=new WorkSource(100,"z");named.add(100,"a");named.add(200,"b");
        check(named.size()==3&&named.getName(0).equals("a"),"named ordering");
        try{named.add(300);throw new AssertionError("mixed names accepted");}catch(IllegalArgumentException expected){}
        WorkSource stripped=named.withoutNames();check(stripped.size()==2&&stripped.equals(copy)&&stripped.hashCode()==copy.hashCode(),"deduplicated name stripping");
        check(copy.remove(new WorkSource(100))&&copy.size()==1&&copy.add(stripped)&&copy.size()==2,"merge and removal");
        Parcel parcel=Parcel.obtain();named.writeToParcel(parcel,0);parcel.setDataPosition(0);
        WorkSource restored=WorkSource.CREATOR.createFromParcel(parcel);check(restored.equals(named)&&restored.hashCode()==named.hashCode(),"parcel round trip");parcel.recycle();
        parcel=Parcel.obtain();parcel.writeInt(4097);parcel.setDataPosition(0);
        try{WorkSource.CREATOR.createFromParcel(parcel);throw new AssertionError("oversize parcel accepted");}catch(BadParcelableException expected){}finally{parcel.recycle();}
        PowerManager.WakeLock lock=new PowerManager(new android.content.ContextWrapper(null){public int checkSelfPermission(String permission){return ("android.permission.WAKE_LOCK".equals(permission)||"android.permission.UPDATE_DEVICE_STATS".equals(permission))?android.content.pm.PackageManager.PERMISSION_GRANTED:android.content.pm.PackageManager.PERMISSION_DENIED;}}).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"work-source-smoke");
        lock.setWorkSource(named);lock.acquire();lock.release(0);check(!lock.isHeld(),"attribution and flags release API");
        PowerManager ordinary=new PowerManager(new android.content.ContextWrapper(null){public int checkSelfPermission(String permission){return "android.permission.WAKE_LOCK".equals(permission)?android.content.pm.PackageManager.PERMISSION_GRANTED:android.content.pm.PackageManager.PERMISSION_DENIED;}});
        PowerManager.WakeLock restricted=ordinary.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"restricted");
        WorkSource attribution=new WorkSource(123);restricted.setWorkSource(attribution);attribution.clear();
        try{restricted.acquire();throw new AssertionError("Unprivileged attribution acquired");}catch(SecurityException expected){}
        check(!restricted.isHeld(),"Denied attribution retained lock");
        restricted.setWorkSource(new WorkSource());restricted.acquire();
        try{restricted.setWorkSource(new WorkSource(123));throw new AssertionError("Held attribution changed without privilege");}catch(SecurityException expected){}
        check(restricted.isHeld(),"Denied attribution update released lock");restricted.release();
        restricted.acquire();restricted.release(); // Rejected update must preserve the old attribution.
        lock.setWorkSource(named);lock.acquire();lock.setWorkSource(new WorkSource(321));lock.setWorkSource(null);lock.release();
        System.out.println("WORK_SOURCE_OK");
    }
}
