// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.BatteryManager;
public final class BatteryManagerSmoke {
    static Context context(final int sdk){return new ContextWrapper(null){public ApplicationInfo getApplicationInfo(){ApplicationInfo info=new ApplicationInfo();info.targetSdkVersion=sdk;return info;}};}
    public static void main(String[] args){
        BatteryManager old=BatteryManager.shirazForContext(context(27)),modern=BatteryManager.shirazForContext(context(28));
        for(int id:new int[]{1,2,3,4,5,6,0,-1,Integer.MAX_VALUE}){
            if(old.getIntProperty(id)!=0||modern.getIntProperty(id)!=Integer.MIN_VALUE||old.getLongProperty(id)!=Long.MIN_VALUE||modern.getLongProperty(id)!=Long.MIN_VALUE)throw new AssertionError("invented battery reading");
        }
        if(modern.computeChargeTimeRemaining()!=-1)throw new AssertionError("invented charge estimate");
        try{modern.isCharging();throw new AssertionError("invented charging status");}catch(UnsupportedOperationException expected){}
        System.out.println("BATTERY_MANAGER_UNAVAILABLE_OK");
    }
}
