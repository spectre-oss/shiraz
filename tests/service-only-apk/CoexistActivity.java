// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.servicefixture;
import android.app.Activity;
import android.content.*;
import android.os.*;
import java.io.*;
public final class CoexistActivity extends Activity {
 private boolean connected;private Intent worker;
 private final ServiceConnection connection=new ServiceConnection(){
  public void onServiceConnected(ComponentName name,IBinder binder){
   Parcel input=Parcel.obtain(),output=Parcel.obtain();try{
    input.writeInterfaceToken("shiraz.fixture.service");if(!binder.transact(8,input,output,0))throw new AssertionError("Worker state method");output.readException();int pid=output.readInt(),instance=output.readInt(),start=output.readInt();output.enforceNoDataAvail();
    if(pid==android.os.Process.myPid()||start!=1)throw new AssertionError("Start and bind did not share remote worker");
    System.out.println("SAME_PACKAGE_STARTED_BOUND "+pid+" "+instance);connected=true;
   }catch(RemoteException error){throw new AssertionError(error);}finally{input.recycle();output.recycle();}
  }
  public void onServiceDisconnected(ComponentName name){throw new AssertionError("Worker disconnected before Activity cleanup");}
 };
 private File ready,done;private final Handler handler=new Handler(Looper.getMainLooper());
 protected void onCreate(Bundle state){
  super.onCreate(state);android.widget.TextView view=new android.widget.TextView(this);view.setText("Activity and service coexistence");setContentView(view);
  ready=new File(getFilesDir(),"coexist-ready");done=new File(getFilesDir(),"coexist-done");done.delete();
  worker=new Intent(this,Worker.class);if(startService(worker)==null||!bindService(worker,connection,BIND_AUTO_CREATE))throw new AssertionError("Worker start/bind failed");
  publish();
  handler.postDelayed(new Runnable(){public void run(){if(connected&&done.isFile()){System.out.println("SAME_PACKAGE_ACTIVITY_SERVICE_OK");finish();}else{publish();handler.postDelayed(this,20);}}},20);
 }
 private void publish(){
  File pending=new File(getFilesDir(),"coexist-ready.tmp");try(FileOutputStream out=new FileOutputStream(pending)){out.write(Integer.toString(android.os.Process.myPid()).getBytes("UTF-8"));}catch(IOException error){throw new AssertionError(error);}if(!pending.renameTo(ready))throw new AssertionError("Publish Activity marker");

 }
 protected void onDestroy(){if(worker!=null){if(!stopService(worker))throw new AssertionError("Worker stop failed");unbindService(connection);worker=null;}if(ready!=null)ready.delete();if(done!=null)done.delete();super.onDestroy();}
}
