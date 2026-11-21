// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.ServiceBrokerClient;
public final class ServiceBrokerClientSmoke {
    private static void publicPendingIntent(final ServiceBrokerClient client)throws Exception{
        Class<?> runtime=Class.forName("shiraz.runtime.RuntimeBroker");
        java.lang.reflect.Field field=runtime.getDeclaredField("client");field.setAccessible(true);field.set(null,client);
        final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger(),replies=new java.util.concurrent.atomic.AtomicInteger();
        final android.accounts.AccountAuthenticatorResponse response=new android.accounts.AccountAuthenticatorResponse(new android.accounts.IAccountAuthenticatorResponse.Stub(){
            public void onResult(android.os.Bundle result){replies.incrementAndGet();}public void onRequestContinued(){}public void onError(int code,String message){throw new AssertionError();}
        });
        android.content.Context creator=new android.content.ContextWrapper(null){
            public String getPackageName(){return "test.caller";}
            public android.content.Context getApplicationContext(){return this;}
            public ClassLoader getClassLoader(){return getClass().getClassLoader();}
            public void sendBroadcast(android.content.Intent value){
                try{if(client.lookupPackage("test.caller")==null)throw new AssertionError("Reentrant lookup");}catch(java.io.IOException e){throw new IllegalStateException(e);}
                if(!"original".equals(value.getStringExtra("value")))throw new AssertionError("Immutable extras changed");calls.incrementAndGet();
                ((android.accounts.AccountAuthenticatorResponse)value.getParcelableExtra("reply")).onResult(new android.os.Bundle());
            }
        };
        try{
            android.app.PendingIntent original=android.app.PendingIntent.getBroadcast(creator,81,new android.content.Intent("test.pending").putExtra("value","original").putExtra("reply",response),0x44000000);
            android.os.Parcel parcel=android.os.Parcel.obtain();android.app.PendingIntent copy;
            try{original.writeToParcel(parcel,0);byte[] bytes=parcel.marshall();parcel.unmarshall(bytes,0,bytes.length);parcel.setDataPosition(0);copy=android.app.PendingIntent.CREATOR.createFromParcel(parcel);parcel.enforceNoDataAvail();}finally{parcel.recycle();}
            if(!copy.equals(original)||copy.hashCode()!=original.hashCode()||!copy.getCreatorPackage().equals("test.caller")||copy.getCreatorUid()!=client.lookupPackage("test.caller").uid)throw new AssertionError("Parcel creator capability");
            copy.shirazSend(new android.content.Intent().putExtra("value","changed"));
            if(calls.get()!=1||replies.get()!=1)throw new AssertionError("Missing creator Binder delivery");
            try{original.send();throw new AssertionError("One-shot replay");}catch(android.app.PendingIntent.CanceledException expected){}
            Class<?> snapshotsClass=Class.forName("shiraz.runtime.HostPendingIntents");
            java.lang.reflect.Field retained=snapshotsClass.getDeclaredField("snapshots");retained.setAccessible(true);
            boolean empty=false;long deadline=System.nanoTime()+3000000000L;
            do{synchronized(snapshotsClass){empty=((java.util.Map<?,?>)retained.get(null)).isEmpty();}if(empty)break;Thread.sleep(20);}while(System.nanoTime()<deadline);
            if(!empty)throw new AssertionError("One-shot retained Binder snapshot was not retired");
            System.out.println("ART_PENDING_INTENT_BINDER_SNAPSHOT_OK");
            System.out.println("ART_PENDING_INTENT_PUBLIC_PARCEL_DISPATCH_OK");
        }finally{
            java.lang.reflect.Method shutdown=Class.forName("shiraz.runtime.HostPendingIntents").getDeclaredMethod("shutdown");shutdown.setAccessible(true);shutdown.invoke(null);field.set(null,null);
        }
    }
    private static void openAuthenticator(ServiceBrokerClient client)throws Exception {
        java.lang.reflect.Constructor<ServiceBrokerClient.AuthenticatorResolution> constructor=ServiceBrokerClient.AuthenticatorResolution.class.getDeclaredConstructor(android.accounts.AuthenticatorDescription.class,String.class,String.class,String.class,int.class);
        constructor.setAccessible(true);
        ServiceBrokerClient.AuthenticatorResolution target=constructor.newInstance(new android.accounts.AuthenticatorDescription("test.accounts","test.target",0,0,0,0,false),"test.target.Auth",":accounts",new String(new char[64]).replace('\0','a'),10001);
        client.openAuthenticator(target).close();
    }
    private static void openReceiver(ServiceBrokerClient client)throws Exception {
        java.lang.reflect.Method method=ServiceBrokerClient.class.getDeclaredMethod("openReceiver",android.content.Intent.class,String.class);
        method.setAccessible(true);
        try{method.invoke(client,new android.content.Intent().setComponent(new android.content.ComponentName("test.target","test.target.Receiver")),null);}
        catch(java.lang.reflect.InvocationTargetException error){throw (Exception)error.getCause();}
    }
    private static void dynamicReceiver(ServiceBrokerClient client,boolean register)throws Exception {
        java.lang.reflect.Method method=register?ServiceBrokerClient.class.getDeclaredMethod("registerDynamicReceiver",String.class,android.content.IntentFilter.class,String.class,int.class):ServiceBrokerClient.class.getDeclaredMethod("unregisterDynamicReceiver",String.class);
        method.setAccessible(true);
        try{if(register)method.invoke(client,"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",new android.content.IntentFilter("test.EVENT","text/*"),null,0);else method.invoke(client,"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");}
        catch(java.lang.reflect.InvocationTargetException error){throw (Exception)error.getCause();}
    }
    private static void openDynamic(ServiceBrokerClient client)throws Exception {
        java.lang.reflect.Constructor<ServiceBrokerClient.DynamicResolution> constructor=ServiceBrokerClient.DynamicResolution.class.getDeclaredConstructor(String.class,String.class,String.class,String.class,String.class,int.class,int.class);
        constructor.setAccessible(true);
        ServiceBrokerClient.DynamicResolution target=constructor.newInstance("test.target","test.target:events",new String(new char[64]).replace('\0','a'),new String(new char[32]).replace('\0','a'),new String(new char[32]).replace('\0','b'),10001,5);
        java.lang.reflect.Method method=ServiceBrokerClient.class.getDeclaredMethod("openDynamicReceiver",android.content.Intent.class,String.class,ServiceBrokerClient.DynamicResolution.class);method.setAccessible(true);
        try{method.invoke(client,new android.content.Intent("test.EVENT"),null,target);}catch(java.lang.reflect.InvocationTargetException error){throw (Exception)error.getCause();}
    }
    private static void authenticatorResponse()throws Exception {
        final int[] calls=new int[3];
        final android.accounts.IAccountAuthenticatorResponse.Stub target=new android.accounts.IAccountAuthenticatorResponse.Stub(){
            public void onResult(android.os.Bundle result){if(result==null||!"fixture".equals(result.getString("value")))throw new AssertionError("Callback result");calls[0]++;}
            public void onRequestContinued(){calls[1]++;}
            public void onError(int code,String message){if(code!=7||!"fixture-error".equals(message))throw new AssertionError("Callback error");calls[2]++;}
        };
        android.os.Binder bridge=new android.os.Binder(){
            protected boolean onTransact(int code,android.os.Parcel data,android.os.Parcel reply,int flags)throws android.os.RemoteException{
                if(flags!=android.os.IBinder.FLAG_ONEWAY||reply!=null)throw new AssertionError("Callback must be one-way");
                return target.transact(code,data,reply,flags);
            }
        };
        android.accounts.IAccountAuthenticatorResponse proxy=android.accounts.IAccountAuthenticatorResponse.Stub.asInterface(bridge);
        if(proxy==target||android.accounts.IAccountAuthenticatorResponse.Stub.asInterface(target)!=target)throw new AssertionError("Interface dispatch");
        android.os.Parcel parcel=android.os.Parcel.obtain();
        try{
            new android.accounts.AccountAuthenticatorResponse(proxy).writeToParcel(parcel,0);parcel.setDataPosition(0);
            android.accounts.AccountAuthenticatorResponse restored=android.accounts.AccountAuthenticatorResponse.CREATOR.createFromParcel(parcel);parcel.enforceNoDataAvail();
            android.os.Bundle result=new android.os.Bundle();result.putString("value","fixture");
            restored.onResult(result);restored.onRequestContinued();restored.onError(7,"fixture-error");
        }finally{parcel.recycle();}
        if(calls[0]!=1||calls[1]!=1||calls[2]!=1)throw new AssertionError("Callback delivery count");
        parcel=android.os.Parcel.obtain();
        try{
            parcel.writeInterfaceToken("wrong.interface");
            try{target.transact(2,parcel,null,1);throw new AssertionError("Wrong callback interface accepted");}catch(SecurityException expected){}
        }finally{parcel.recycle();}
        parcel=android.os.Parcel.obtain();
        try{
            parcel.writeInterfaceToken("android.accounts.IAccountAuthenticatorResponse");parcel.writeInt(99);
            try{target.transact(2,parcel,null,1);throw new AssertionError("Trailing callback data accepted");}catch(IllegalArgumentException expected){}
        }finally{parcel.recycle();}
        if(calls[1]!=1)throw new AssertionError("Malformed callback invoked");
    }
    public static void main(String[] args)throws Exception {
        if(args.length>1&&!args[1].startsWith("malformed")){AccountFutureSmoke.run();AuthenticatorActivitySmoke.run();}
        authenticatorResponse();
        AbstractAuthenticatorSmoke.run();
        shiraz.runtime.AuthenticatorEndpointSmoke.run();
        try(ServiceBrokerClient client=new ServiceBrokerClient(Integer.parseInt(args[0]))){
            if(args.length==2&&args[1].equals("job-recover")){JobBrokerSmoke.run(client,true);return;}
            if(args.length==2&&args[1].equals("job")){JobBrokerSmoke.run(client);return;}
            if(args.length==2&&args[1].equals("custom-token")){
                android.accounts.Account account=new android.accounts.Account("custom-user","test.accounts");
                ServiceBrokerClient.AccountSnapshot before=client.accountSnapshot();
                ServiceBrokerClient.AccountWatch watch=client.watchAccounts();
                if(!watch.initialRevision.equals(before.revision))throw new AssertionError("Initial account watch revision");
                client.accountOperation("add",account,null,null,null);
                ServiceBrokerClient.AccountSnapshot added=client.accountSnapshot();
                if(before.revision.equals(added.revision)||added.accounts().length!=before.accounts().length+1)throw new AssertionError("Account snapshot mutation");
                if(!watch.readRevision().equals(added.revision))throw new AssertionError("Account addition notice");
                android.accounts.Account[] isolated=added.accounts();isolated[0]=null;
                for(android.accounts.Account item:added.accounts())if(item==null)throw new AssertionError("Account snapshot exposed array ownership");

                ServiceBrokerClient.TokenPreparation prepared=client.prepareCustomToken(account,"fixture-type");
                if(!prepared.customTokens||prepared.token!=null||prepared.expiresMs!=null)throw new AssertionError("Custom miss");
                long expiry=System.currentTimeMillis()+60000;
                if(!client.commitCustomToken(account,"fixture-type","synthetic-custom",prepared,expiry)||client.commitCustomToken(account,"fixture-type","stale",prepared,expiry))throw new AssertionError("Custom commit revision");
                ServiceBrokerClient.TokenPreparation cached=client.prepareCustomToken(account,"fixture-type");
                if(!"synthetic-custom".equals(cached.token)||cached.expiresMs==null||cached.expiresMs!=expiry||!cached.customTokens)throw new AssertionError("Custom cached reply");
                if(client.prepareToken(account,"fixture-type").token!=null)throw new AssertionError("Custom token leaked to standard cache");
                client.accountOperation("invalidate",account,null,"synthetic-custom",null);
                if(client.prepareCustomToken(account,"fixture-type").token!=null)throw new AssertionError("Custom invalidation");
                if(!client.accountSnapshot().revision.equals(added.revision))throw new AssertionError("Token operations changed account snapshot");
                client.accountOperation("remove",account,null,null,null);
                ServiceBrokerClient.AccountSnapshot removed=client.accountSnapshot();
                if(removed.revision.equals(before.revision)||removed.accounts().length!=before.accounts().length)throw new AssertionError("Account snapshot removal");
                if(!watch.readRevision().equals(removed.revision))throw new AssertionError("Account removal notice");
                final ServiceBrokerClient.AccountWatch waiting=watch;
                final java.util.concurrent.CountDownLatch reading=new java.util.concurrent.CountDownLatch(1);
                final java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean();
                Thread reader=new Thread(new Runnable(){public void run(){reading.countDown();try{waiting.readRevision();}catch(java.io.IOException expected){stopped.set(true);}}});
                reader.start();reading.await();watch.close();watch.close();reader.join(3000);
                if(reader.isAlive()||!stopped.get())throw new AssertionError("Account watch close did not stop reader");
                try(ServiceBrokerClient.AccountWatch again=client.watchAccounts()){if(!again.initialRevision.equals(removed.revision))throw new AssertionError("Account watch re-admission");}
                AccountListenersSmoke.run(client);
                HandlerShutdownSmoke.run();
                System.out.println("ART_ACCOUNT_WATCH_OK");
                System.out.println("ART_ACCOUNT_SNAPSHOT_OK");
                System.out.println("ART_CUSTOM_TOKEN_CACHE_OK");return;
            }
            if(args.length==2&&args[1].startsWith("malformed-account-notice")){
                ServiceBrokerClient.AccountWatch watch=client.watchAccounts();
                try{watch.readRevision();throw new AssertionError("Malformed notice accepted");}catch(java.io.IOException expected){}
                try{watch.readRevision();throw new AssertionError("Closed watch reused");}catch(java.io.IOException expected){}
                watch.close();
                if(!client.accountSnapshot().revision.equals(watch.initialRevision))throw new AssertionError("Notice failure broke broker");
                System.out.println("ART_SERVICE_BROKER_MALFORMED_REJECTED");return;
            }
            if(args.length==2&&args[1].startsWith("malformed")){
                try{if(args[1].equals("malformed-provider"))client.resolveProvider("test.data","read");else if(args[1].equals("malformed-selected-sync"))client.listSelectedSyncAdapters();else if(args[1].equals("malformed-sync"))client.listSyncAdapters();else if(args[1].equals("malformed-job"))client.resolveJob("test.jobs","JobBrokerSmoke$Worker");else if(args[1].startsWith("malformed-account-watch"))client.watchAccounts();else if(args[1].equals("malformed-account-snapshot"))client.accountSnapshot();else if(args[1].equals("malformed-account"))client.accountOperation("get_password",new android.accounts.Account("fixture-user","test.accounts"),null,null,null);else if(args[1].equals("malformed-custom-token"))client.prepareCustomToken(new android.accounts.Account("fixture-user","test.accounts"),"fixture-token-type");else if(args[1].startsWith("malformed-activity-open"))client.openActivity(client.resolveActivity("test.auth","test.auth.Login"));else if(args[1].equals("malformed-activity"))client.resolveActivity("test.auth","test.auth.Login");else if(args[1].equals("malformed-token-preparation"))client.prepareToken(new android.accounts.Account("fixture-user","test.accounts"),"fixture-token-type");else if(args[1].equals("malformed-account-list"))client.accountOperation("list",null,"test.accounts",null,null);else if(args[1].equals("malformed-account-add"))client.accountOperation("add",new android.accounts.Account("fixture-user","test.accounts"),null,null,null);else if(args[1].equals("malformed-authenticator-open"))openAuthenticator(client);else if(args[1].equals("malformed-authenticator-resolution"))client.resolveAuthenticator("test.accounts");else if(args[1].equals("malformed-authenticators"))client.listAuthenticators();else if(args[1].equals("malformed-dynamic-open"))openDynamic(client);else if(args[1].equals("malformed-dynamic-query"))client.queryDynamicReceivers(new android.content.Intent("test.EVENT").setPackage("test.target"),null);else if(args[1].equals("malformed-dynamic"))dynamicReceiver(client,true);else if(args[1].equals("malformed-dynamic-remove"))dynamicReceiver(client,false);else if(args[1].equals("malformed-receiver-open"))openReceiver(client);else if(args[1].equals("malformed-receiver"))client.queryReceivers(new android.content.Intent("test.EVENT").setPackage("test.target"),null);else if(args[1].equals("malformed-permission"))client.checkPermission("test.PRIVATE","package","test.caller");else if(args[1].equals("malformed-signature"))client.compareSignatures("test.target","test.target");else if(args[1].equals("malformed-lookup"))client.lookupUid(10000);else client.resolve("test.target","test.target.Worker");throw new AssertionError("Malformed reply accepted");}
                catch(java.io.IOException expected){}
                try{client.resolve("test.target","test.target.Worker");throw new AssertionError("Malformed channel reused");}
                catch(java.io.IOException expected){}
                System.out.println("ART_SERVICE_BROKER_MALFORMED_REJECTED");return;
            }
            shiraz.runtime.SyncAdapterDiscovery.Declaration[] sync=client.listSyncAdapters();
            if(sync.length!=2||!sync[0].type.authority.equals("test.data")||!sync[0].type.accountType.equals("test.accounts")||!sync[0].type.getPackageName().equals("test.sync")||!sync[0].type.supportsUploading()||sync[0].type.isUserVisible()||!sync[0].component.equals("test.sync.Adapter")||!sync[0].process.equals(":sync")||!sync[0].apkSha256.matches("[0-9a-f]{64}"))throw new AssertionError("Sync declaration discovery");
            shiraz.runtime.SyncDiscoveryApiSmoke.run(client);
            ServiceBrokerClient.ProviderResolution provider=client.resolveProvider("test.data","read");
            if(!provider.authority.equals("test.data")||!provider.access.equals("read")||!provider.target.packageName.equals("test.provider")||!provider.target.component.equals("test.provider.Data")||!provider.target.process.equals(":data")||!provider.target.apkSha256.matches("[0-9a-f]{64}"))throw new AssertionError("Provider metadata");
            try{client.resolveProvider("test.data","write");throw new AssertionError("Protected provider write resolved");}catch(SecurityException expected){}
            if(!client.resolveProvider("test.data","read").target.apkSha256.equals(provider.target.apkSha256))throw new AssertionError("Provider denial broke channel");
            System.out.println("ART_PROVIDER_RESOLUTION_OK");
            ServiceBrokerClient.ActivityResolution activity=client.resolveActivity("test.auth","test.auth.Login");
            if(!activity.component.equals("test.auth.Login")||!activity.requestedComponent.equals(activity.component)||!activity.process.equals(":login")||activity.launchMode!=1||!activity.apkSha256.matches("[0-9a-f]{64}"))throw new AssertionError("Activity resolution");
            ServiceBrokerClient.ActivityResolution alias=client.resolveActivity("test.auth","test.auth.Alias");
            if(!alias.requestedComponent.equals("test.auth.Alias")||!alias.component.equals(activity.component)||alias.uid!=activity.uid||!alias.apkSha256.equals(activity.apkSha256))throw new AssertionError("Activity alias identity");
            try{client.resolveActivity("test.auth","test.auth.Private");throw new AssertionError("Private activity resolved");}catch(SecurityException expected){}
            if(!client.resolveActivity("test.auth","test.auth.Login").component.equals(activity.component))throw new AssertionError("Activity denial broke channel");
            ServiceBrokerClient.Resolution result=client.resolve(args[1],args[2]);
            if(!result.apkSha256.equals(args[3])||result.uid!=Integer.parseInt(args[4]))throw new AssertionError("Broker identity changed");
            try{client.resolve(args[1],args[2]+"Missing");throw new AssertionError("Missing service authorized");}
            catch(SecurityException expected){}
            if(client.resolve(args[1],args[2]).uid!=result.uid)throw new AssertionError("Denial corrupted channel");
            shiraz.runtime.PendingIntentCapability pending=client.obtainPendingIntent(0,77,0x04000000,new byte[]{1,2},new byte[]{3,4});
            if(!pending.packageName.equals("test.caller")||pending.uid!=client.lookupPackage("test.caller").uid)throw new AssertionError("PendingIntent creator provenance");
            if(!client.inspectPendingIntent(pending.token).token.equals(pending.token))throw new AssertionError("PendingIntent capability lookup");
            publicPendingIntent(client);
            client.cancelPendingIntent(pending.token);
            try{client.inspectPendingIntent(pending.token);throw new AssertionError("Canceled PendingIntent remained live");}catch(ServiceBrokerClient.PendingIntentCanceledException expected){}
            if(client.obtainPendingIntent(0,77,0x24000000,new byte[]{1,2},new byte[]{3,4})!=null)throw new AssertionError("NO_CREATE minted token");
            System.out.println("ART_PENDING_INTENT_AUTHORITY_OK");
            ServiceBrokerClient.PackageIdentity identity=client.lookupUid(result.uid);
            if(identity==null||!identity.packageName.equals(args[1])||client.lookupPackage(args[1]).uid!=result.uid)throw new AssertionError("Package identity lookup");
            if(client.lookupUid(0)!=null||client.lookupPackage("test.uninstalled")!=null)throw new AssertionError("Invented package");
            if(client.compareSignatures(result.uid,result.uid)!=0||client.compareSignatures(args[1],"test.other")!=-3||client.compareSignatures(0,result.uid)!=-4)throw new AssertionError("Verified signer comparison");
            if(client.checkPermission("test.PRIVATE","package","test.caller")!=0||client.checkPermission("test.PRIVATE","package","test.other")!=-1||client.checkPermission("test.PRIVATE","uid",Integer.valueOf(result.uid))!=-1)throw new AssertionError("Verified permission query");
            if(!client.queryReceivers(new android.content.Intent("test.EVENT").setPackage("test.uninstalled"),null).isEmpty())throw new AssertionError("Invented receiver");
            java.util.List<ServiceBrokerClient.DynamicResolution> dynamic=client.queryDynamicReceivers(new android.content.Intent("test.EVENT"),null);
            if(dynamic.size()!=1||!dynamic.get(0).packageName.equals(args[1])||dynamic.get(0).uid!=result.uid||!dynamic.get(0).apkSha256.equals(result.apkSha256)||!dynamic.get(0).process.equals(args[1]+":events")||dynamic.get(0).priority!=5)throw new AssertionError("Dynamic discovery identity");
            if(!client.queryDynamicReceivers(new android.content.Intent("test.EVENT").setPackage("test.uninstalled"),null).isEmpty())throw new AssertionError("Invented dynamic target");
            if(!client.queryDynamicReceivers(new android.content.Intent("test.EVENT"),"test.MISSING").isEmpty())throw new AssertionError("Dynamic recipient grant ignored");
            ServiceBrokerClient.AuthenticatorResolution authenticator=client.resolveAuthenticator("test.accounts");
            if(!authenticator.description.packageName.equals("test.auth")||!authenticator.description.type.equals("test.accounts")||!authenticator.component.equals("test.auth.Service")||!authenticator.process.equals("test.auth"))throw new AssertionError("Authenticator resolution routing");
            ServiceBrokerClient.PackageIdentity authIdentity=client.lookupPackage("test.auth");
            if(authenticator.uid!=authIdentity.uid)throw new AssertionError("Authenticator resolution UID");
            try{client.resolveAuthenticator("missing.type");throw new AssertionError("Unknown authenticator accepted");}catch(SecurityException expected){}
            if(client.resolveAuthenticator("test.accounts").uid!=authenticator.uid)throw new AssertionError("Denied resolution damaged channel");
            android.accounts.Account account=new android.accounts.Account("fixture-user","test.accounts");
            if(!Boolean.TRUE.equals(client.accountOperation("add",account,null,"fixture-pw",null)))throw new AssertionError("Account add");
            if(!Boolean.FALSE.equals(client.accountOperation("add",account,null,"ignored",null)))throw new AssertionError("Duplicate account");
            if(!"fixture-pw".equals(client.accountOperation("get_password",account,null,null,null)))throw new AssertionError("Account read");
            ServiceBrokerClient.TokenPreparation prepared=client.prepareToken(account,"fixture-token-type");
            if(prepared.customTokens||prepared.token!=null||prepared.ownerUid!=authenticator.uid||!prepared.ownerSha256.equals(authenticator.apkSha256))throw new AssertionError("Token preparation identity");
            if(!client.commitToken(account,"fixture-token-type","prepared-fixture-token",prepared)||client.commitToken(account,"fixture-token-type","stale-fixture-token",prepared))throw new AssertionError("Token compare-and-swap");
            ServiceBrokerClient.TokenPreparation cached=client.prepareToken(account,"fixture-token-type");
            if(!"prepared-fixture-token".equals(cached.token)||cached.revision.equals(prepared.revision))throw new AssertionError("Cached token preparation");
            client.accountOperation("set_token",account,"fixture-token-type","fixture-token",null);
            if(!"fixture-token".equals(client.accountOperation("get_token",account,"fixture-token-type",null,null)))throw new AssertionError("Account token");
            client.accountOperation("set_password",account,null,null,null);
            if(client.accountOperation("get_token",account,"fixture-token-type",null,null)!=null)throw new AssertionError("Password clears tokens");
            if(!Boolean.TRUE.equals(client.accountOperation("remove",account,null,null,null)))throw new AssertionError("Account remove");
            System.out.println("ART_SERVICE_BROKER_RESOLUTION_OK");
        }
    }
}
