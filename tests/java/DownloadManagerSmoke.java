// SPDX-License-Identifier: GPL-3.0-only
import android.app.DownloadManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
public final class DownloadManagerSmoke {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static final class App extends ContextWrapper {
  final File root;DownloadManager manager;volatile long completion;
  App(File root){super(null);this.root=root;}
  File dir(String name){File f=new File(root,name);f.mkdirs();return f;}
  public String getPackageName(){return "org.shiraz.downloadtest";}
  public Context getApplicationContext(){return this;}
  public File getFilesDir(){return dir("files");}public File getCacheDir(){return dir("cache");}public File getNoBackupFilesDir(){return dir("no_backup");}
  public File getExternalFilesDir(String type){return dir("external/"+(type==null?"":type));}
  public Object getSystemService(String name){return DOWNLOAD_SERVICE.equals(name)?manager:null;}
  public void sendBroadcast(Intent intent){check(DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction()),"Completion action");completion=intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,0);}
 }
 public static void main(String[] args)throws Exception{
  java.util.Map<String,String> environment=new java.util.HashMap<String,String>();
  environment.put("SHIRAZ_PRESENT_SNAPSHOT","parent.ppm");environment.put("SHIRAZ_INPUT_SCRIPT","parent.script");environment.put("SHIRAZ_LIVE_INPUT","parent.input");environment.put("SHIRAZ_CANVAS","gpu");environment.put("XDG_DATA_HOME","private-parent");environment.put("SHIRAZ_HOST_XDG_DATA_HOME","host-data");
  java.lang.reflect.Method clean=shiraz.runtime.HostAppInstaller.class.getDeclaredMethod("cleanEnvironment",java.util.Map.class);clean.setAccessible(true);clean.invoke(null,environment);
  check(!environment.containsKey("SHIRAZ_PRESENT_SNAPSHOT")&&!environment.containsKey("SHIRAZ_INPUT_SCRIPT")&&!environment.containsKey("SHIRAZ_LIVE_INPUT"),"Installed child does not inherit parent capture/input");
  check("gpu".equals(environment.get("SHIRAZ_CANVAS"))&&"host-data".equals(environment.get("XDG_DATA_HOME")),"Child renderer preference and host XDG retained");
  Looper.prepareMainLooper();App app=new App(Files.createTempDirectory("download-api").toFile());app.manager=DownloadManager.shirazForHost(app);
  final ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));
  Thread response=new Thread(new Runnable(){public void run(){try(Socket socket=server.accept()){BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));while(!in.readLine().isEmpty()){}socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 4\r\n\r\ntest".getBytes("UTF-8"));}catch(IOException error){throw new RuntimeException(error);}}});response.setDaemon(true);response.start();
  try{
   check(app.getSystemService(DownloadManager.class)==app.manager,"Typed service name");
   File target=new File(app.getExternalFilesDir("Download"),"sample.txt");
   DownloadManager.Request request=new DownloadManager.Request(Uri.parse("http://127.0.0.1:"+server.getLocalPort()+"/file")).setTitle("sample").setDescription("test transfer").setDestinationUri(Uri.fromFile(target)).setAllowedOverMetered(false).setAllowedOverRoaming(false).setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
   long id=app.manager.enqueue(request),deadline=System.currentTimeMillis()+10000;
   while(app.completion==0&&System.currentTimeMillis()<deadline){Looper.getMainLooper().shirazDispatchDue(100);Thread.sleep(20);}
   check(app.completion==id,"Completion delivered on application loop");
   try(Cursor cursor=app.manager.query(new DownloadManager.Query().setFilterById(id).setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL))){check(cursor.moveToFirst()&&cursor.getCount()==1,"Success query");check(cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))==4,"Byte count");check("test transfer".equals(cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION))),"Description persisted");}
   Uri uri=app.manager.getUriForDownloadedFile(id);check(uri!=null&&"content".equals(uri.getScheme()),"Download content URI");
   ContentResolver resolver=new ContentResolver(app);check("text/plain".equals(resolver.getType(uri)),"Download MIME type");
   try(InputStream in=resolver.openInputStream(uri)){check(in.read()=='t'&&in.read()=='e'&&in.read()=='s'&&in.read()=='t'&&in.read()==-1,"Content URI bytes");}
   try(ParcelFileDescriptor descriptor=app.manager.openDownloadedFile(id)){check(descriptor.getStatSize()==4,"Owned downloaded descriptor");}
   check(app.manager.remove(id)==1&&app.manager.getUriForDownloadedFile(id)==null,"Removed download inaccessible");
   try{resolver.openInputStream(uri);throw new AssertionError("Removed URI opened");}catch(FileNotFoundException expected){}
  }finally{app.manager.shirazClose();server.close();Looper.shirazShutdownMain();}
  System.out.println("DOWNLOAD_MANAGER_API_HTTP_CONTENT_COMPLETION_OK");
 }
}
