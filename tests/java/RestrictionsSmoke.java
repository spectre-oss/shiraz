// SPDX-License-Identifier: GPL-3.0-only
import android.content.RestrictionsManager;
import android.os.PersistableBundle;
public final class RestrictionsSmoke {
    public static void main(String[] args){
        RestrictionsManager manager=new RestrictionsManager();
        if(manager.hasRestrictionsProvider()||manager.createLocalApprovalIntent()!=null)throw new AssertionError("invented provider");
        manager.getApplicationRestrictions().putBoolean("injected",true);
        if(!manager.getApplicationRestrictions().isEmpty())throw new AssertionError("caller mutated restrictions");
        try{manager.requestPermission("approval","id",new PersistableBundle());throw new AssertionError("accepted provider request");}catch(IllegalStateException expected){}
        try{manager.notifyPermissionResponse("app",new PersistableBundle());throw new AssertionError("accepted provider response");}catch(SecurityException expected){}
        System.out.println("RESTRICTIONS_OK");
    }
}
