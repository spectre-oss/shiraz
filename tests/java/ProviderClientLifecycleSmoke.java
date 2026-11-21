// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.content.res.AssetFileDescriptor;
import java.io.*;

public final class ProviderClientLifecycleSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static class Provider extends ContentProvider {
        int calls,queries;
        int opens;
        File file;
        public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
            opens++;if(!"r".equals(mode))throw new FileNotFoundException("read only");
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
        }
        public boolean onCreate(){return true;}
        public Bundle call(String method,String arg,Bundle extras){calls++;return new Bundle();}
        public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){queries++;return null;}
        public String getType(Uri uri){return "vnd.fixture/item";}
        public Uri insert(Uri uri,ContentValues values){return uri;}
        public int delete(Uri uri,String selection,String[] args){return 2;}
        public int update(Uri uri,ContentValues values,String selection,String[] args){return 3;}
    }
    static void fileAccess(Context context,ContentResolver resolver)throws Exception{
        File file=File.createTempFile("provider-client-", ".data");
        try{
            try(FileOutputStream out=new FileOutputStream(file)){out.write(new byte[]{11,22,33});}
            Provider provider=new Provider();provider.file=file;
            ProviderInfo info=new ProviderInfo();info.authority="fixture.files";provider.attachInfo(context,info);
            Uri uri=Uri.parse("content://fixture.files/item");
            ContentProviderClient client=resolver.acquireContentProviderClient(uri);
            CancellationSignal canceled=new CancellationSignal();canceled.cancel();
            try{client.openFile(uri,"r",canceled);throw new AssertionError("canceled file open");}catch(OperationCanceledException expected){}
            try{client.openAssetFile(uri,"r",canceled);throw new AssertionError("canceled asset open");}catch(OperationCanceledException expected){}
            check(provider.opens==0,"canceled open had side effects");
            try{resolver.openFileDescriptor(uri,"r",canceled);throw new AssertionError("resolver canceled file open");}catch(OperationCanceledException expected){}
            try{resolver.openAssetFileDescriptor(uri,"r",canceled);throw new AssertionError("resolver canceled asset open");}catch(OperationCanceledException expected){}
            try{resolver.openFileDescriptor(Uri.fromFile(file),"wt",canceled);throw new AssertionError("canceled file truncated");}catch(OperationCanceledException expected){}
            try{resolver.openAssetFileDescriptor(Uri.fromFile(file),"wt",canceled);throw new AssertionError("canceled asset truncated");}catch(OperationCanceledException expected){}
            check(provider.opens==0&&file.length()==3,"resolver cancellation had side effects");
            try{client.openFile(uri,"w");throw new AssertionError("provider failure swallowed");}catch(FileNotFoundException expected){}
            ParcelFileDescriptor descriptor=client.openFile(uri,"r");
            AssetFileDescriptor asset=client.openAssetFile(uri,"r");
            client.close();
            try{client.openFile(uri,"r");throw new AssertionError("released file open");}catch(IllegalStateException expected){}
            try{client.openAssetFile(uri,"r");throw new AssertionError("released asset open");}catch(IllegalStateException expected){}
            check(provider.opens==3,"released open reached provider");
            try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
                check(in.read()==11&&in.read()==22,"descriptor survived client close");
            }
            try(InputStream in=asset.createInputStream()){
                check(in.read()==11&&in.read()==22&&in.read()==33&&in.read()==-1,"independent asset descriptor");
            }
            final CancellationSignal forwarded=new CancellationSignal();
            Provider aware=new Provider(){
                public ParcelFileDescriptor openFile(Uri u,String mode,CancellationSignal signal){check(signal==forwarded,"file cancellation not forwarded");opens++;return null;}
                public AssetFileDescriptor openAssetFile(Uri u,String mode,CancellationSignal signal){check(signal==forwarded,"asset cancellation not forwarded");opens++;return null;}
            };
            info=new ProviderInfo();info.authority="fixture.aware";aware.attachInfo(context,info);
            try(ContentProviderClient awareClient=resolver.acquireContentProviderClient("fixture.aware")){
                Uri target=Uri.parse("content://fixture.aware/item");
                check(awareClient.openFile(target,"r",forwarded)==null,"nullable file result");
                check(awareClient.openAssetFile(target,"r",forwarded)==null,"nullable asset result");
                check(aware.opens==2,"cancellation-aware overload not called");
                check(resolver.openFileDescriptor(target,"r",forwarded)==null,"resolver nullable file result");
                check(resolver.openAssetFileDescriptor(target,"r",forwarded)==null,"resolver nullable asset result");
                check(aware.opens==4,"resolver cancellation-aware overload not called");
            }
        }finally{check(file.delete(),"temporary file cleanup");}
    }
    public static void main(String[] args)throws Exception{
        Context context=new ContextWrapper(null);ContentResolver resolver=new ContentResolver(context);
        Provider provider=new Provider();ProviderInfo info=new ProviderInfo();info.authority="fixture.client";provider.attachInfo(context,info);
        Uri uri=Uri.parse("content://fixture.client/items");
        ContentProviderClient first=resolver.acquireContentProviderClient(uri),second=resolver.acquireContentProviderClient(uri);
        check(first.getLocalContentProvider()==provider&&second.getLocalContentProvider()==provider,"local provider identity");
        first.call("probe",null,null);check(provider.calls==1,"call dispatch");
        check(first.release(),"first release");check(!first.release(),"duplicate release reported success");first.close();
        try{first.getLocalContentProvider();throw new AssertionError("released local provider access");}catch(IllegalStateException expected){}
        check(second.getLocalContentProvider()==provider,"release invalidated peer local handle");
        try{first.call("probe",null,null);throw new AssertionError("released client used provider");}catch(IllegalStateException expected){}
        try{first.query(uri,null,null,null,null,null);throw new AssertionError("released client queried provider");}catch(IllegalStateException expected){}
        check(provider.calls==1&&provider.queries==0,"released handle side effects");
        second.call("probe",null,null);check(provider.calls==2,"one release invalidated another handle");
        check("vnd.fixture/item".equals(second.getType(uri)),"type dispatch");
        check(uri.equals(second.insert(uri,new ContentValues()))&&second.bulkInsert(uri,new ContentValues[]{new ContentValues(),new ContentValues()})==2,"insert dispatch");
        check(second.update(uri,new ContentValues(),null,null)==3&&second.delete(uri,null,null)==2,"update/delete dispatch");
        CancellationSignal signal=new CancellationSignal();signal.cancel();
        try{second.query(uri,null,null,null,null,signal);throw new AssertionError("canceled query reached provider");}catch(OperationCanceledException expected){}
        check(provider.queries==0,"canceled query side effect");second.query(uri,null,null,null,null);check(provider.queries==1,"query overload");
        second.close();
        try{second.insert(uri,new ContentValues());throw new AssertionError("closed handle wrote data");}catch(IllegalStateException expected){}

        check(resolver.acquireContentProviderClient("missing.client")==null,"missing provider");
        fileAccess(context,resolver);
        System.out.println("PROVIDER_CLIENT_LIFECYCLE_OK");
    }
}
