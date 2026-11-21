// SPDX-License-Identifier: GPL-3.0-only
import android.accounts.Account;
import android.content.*;
import android.os.*;

public final class SyncAdapterWireSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        final int[] starts={0},cancels={0},completed={0};final Bundle[] captured={null};
        final ISyncContext callback=new ISyncContext.Stub(){public void sendHeartbeat(){}public void onFinished(SyncResult result){check(result.stats.numUpdates==9,"completion result");completed[0]++;}};
        final Account account=new Account("fixture","test.sync");final Bundle extras=new Bundle();extras.putInt("generation",7);
        final ISyncAdapter.Stub service=new ISyncAdapter.Stub(){
            public void startSync(ISyncContext context,String authority,Account received,Bundle options)throws RemoteException{
                check(context.asBinder()==callback.asBinder(),"callback token identity");
                check("test.provider".equals(authority)&&account.equals(received)&&received!=account,"account and authority transport");
                check(options!=extras&&options.getInt("generation")==7,"extras copy");captured[0]=options;starts[0]++;
                SyncResult result=new SyncResult();result.stats.numUpdates=9;context.onFinished(result);
            }
            public void cancelSync(ISyncContext context){check(context.asBinder()==callback.asBinder(),"cancel identity");cancels[0]++;}
        };
        check(ISyncAdapter.Stub.asInterface(service)==service&&ISyncAdapter.Stub.asInterface(null)==null,"local interface");
        Binder bridge=new Binder(){protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            check(flags==IBinder.FLAG_ONEWAY&&reply==null,"adapter calls must be one-way");return service.transact(code,data,reply,flags);
        }};
        ISyncAdapter proxy=ISyncAdapter.Stub.asInterface(bridge);
        proxy.startSync(callback,"test.provider",account,extras);extras.putInt("generation",99);
        check(starts[0]==1&&completed[0]==1&&captured[0].getInt("generation")==7,"start and completion");
        proxy.cancelSync(callback);check(cancels[0]==1,"cancel dispatch");
        for(int code:new int[]{1,2}){
            Parcel malformed=Parcel.obtain();
            try{
                malformed.writeInterfaceToken("android.content.ISyncAdapter");malformed.writeStrongBinder(callback.asBinder());
                if(code==1){malformed.writeString("test.provider");malformed.writeTypedObject(account,0);malformed.writeTypedObject(extras,0);}
                malformed.writeInt(123);
                try{service.transact(code,malformed,null,IBinder.FLAG_ONEWAY);throw new AssertionError("trailing input accepted");}catch(IllegalArgumentException expected){}
                check(starts[0]==1&&cancels[0]==1,"malformed request invoked service");
            }finally{malformed.recycle();}
        }
        try{ISyncAdapter.Stub.asInterface(new Binder()).cancelSync(callback);throw new AssertionError("unsupported transaction accepted");}catch(RemoteException expected){}
        System.out.println("SYNC_ADAPTER_WIRE_OK");
    }
}
