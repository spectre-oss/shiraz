// SPDX-License-Identifier: GPL-3.0-only
import android.os.UserHandle;
import android.os.UserManager;
public final class UserIdentitySmoke {
    public static void main(String[] args){
        UserManager users=new UserManager();
        if(users.getUserCount()!=1||!users.isSystemUser()||users.isDemoUser())throw new AssertionError("desktop identity");
        if(!UserHandle.SYSTEM.equals(users.getUserForSerialNumber(users.getSerialNumberForUser(UserHandle.SYSTEM))))throw new AssertionError("serial round trip");
        for(long serial:new long[]{-1,1,Long.MIN_VALUE,Long.MAX_VALUE,1L<<32})if(users.getUserForSerialNumber(serial)!=null)throw new AssertionError("invented user");
        UserHandle absent=new UserHandle(1);
        if(users.getSerialNumberForUser(absent)!=-1||users.isUserRunning(absent)||!users.isUserRunning(UserHandle.SYSTEM))throw new AssertionError("running identity");
        if(users.hasUserRestriction("no_install_apps")||users.hasUserRestriction("no_modify_accounts",UserHandle.SYSTEM))throw new AssertionError("invented policy");
        users.getUserRestrictions().putBoolean("no_install_apps",true);
        if(!users.getUserRestrictions(UserHandle.SYSTEM).isEmpty())throw new AssertionError("caller modified policy");
        try{users.hasUserRestriction("no_install_apps",absent);throw new AssertionError("foreign user queried");}catch(SecurityException expected){}
        try{users.getUserRestrictions(absent);throw new AssertionError("foreign bundle queried");}catch(SecurityException expected){}
        android.content.Context owner=new android.content.ContextWrapper(null){public String getPackageName(){return "test.owner";}};
        UserManager scoped=new UserManager(owner);
        android.os.Bundle policies=scoped.getApplicationRestrictions("test.owner");
        if(!policies.isEmpty())throw new AssertionError("invented application policy");
        policies.putBoolean("restricted",true);
        if(!scoped.getApplicationRestrictions("test.owner",UserHandle.SYSTEM).isEmpty())throw new AssertionError("caller modified policy");
        try{scoped.getApplicationRestrictions("test.foreign");throw new AssertionError("foreign application policy");}catch(SecurityException expected){}
        try{scoped.getApplicationRestrictions("test.owner",absent);throw new AssertionError("foreign user application policy");}catch(SecurityException expected){}
        try{users.getApplicationRestrictions("test.owner");throw new AssertionError("unbound manager policy");}catch(SecurityException expected){}
        try{scoped.getApplicationRestrictions(null);throw new AssertionError("null package accepted");}catch(NullPointerException expected){}
        System.out.println("USER_IDENTITY_OK");
    }
}
