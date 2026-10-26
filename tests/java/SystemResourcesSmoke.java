// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.Resources;
public final class SystemResourcesSmoke {
    public static void main(String[] args){
        if(args.length==1&&args[0].equals("missing")){
            try{Resources.getSystem();throw new AssertionError("missing framework silently accepted");}catch(Resources.NotFoundException expected){}
            System.out.println("SYSTEM_RESOURCE_MISSING_CONFIG_OK");return;
        }
        Resources.shirazSetSystemDisplay(96,160);Resources app=Resources.shirazOpen(args[0],96,160);
        try{org.shiraz.fixture.SystemResourcesFixture.check(app);}finally{app.shirazClose();}
        Resources system=Resources.getSystem();int ok=system.getIdentifier("ok","string","android");if(system.getString(ok).isEmpty())throw new AssertionError("system lifetime tied to app");
        try{system.shirazClose();throw new AssertionError("process resource manager closed by borrower");}catch(IllegalStateException expected){}
        Resources.shirazSetSystemDisplay(160,96);
        if(system.getDisplayMetrics().widthPixels!=160||system.getConfiguration().orientation!=2)throw new AssertionError("system resize not applied");
        Resources.shirazSetSystemDisplay(96,160);
        final Throwable[] errors=new Throwable[8];Thread[] threads=new Thread[8];for(int i=0;i<8;i++){final int n=i;threads[i]=new Thread(new Runnable(){public void run(){try{for(int k=0;k<100;k++)if(Resources.getSystem()!=system||system.getString(ok).isEmpty())throw new AssertionError("concurrent system resource access");}catch(Throwable failure){errors[n]=failure;}}});threads[i].start();}
        for(Thread t:threads)try{t.join();}catch(InterruptedException error){throw new AssertionError(error);}for(Throwable failure:errors)if(failure!=null)throw new AssertionError(failure);
        System.out.println("SYSTEM_RESOURCE_OWNERSHIP_OK");
    }
}
