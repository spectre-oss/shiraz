// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;
import shiraz.runtime.HostDownloads;
import shiraz.runtime.HostAppInstaller;

/** App-scoped downloads on Shiraz's host-backed storage. Queued work resumes when the app runs again. */
public class DownloadManager {
 public static final String ACTION_DOWNLOAD_COMPLETE="android.intent.action.DOWNLOAD_COMPLETE",ACTION_NOTIFICATION_CLICKED="android.intent.action.DOWNLOAD_NOTIFICATION_CLICKED",ACTION_VIEW_DOWNLOADS="android.intent.action.VIEW_DOWNLOADS",EXTRA_DOWNLOAD_ID="extra_download_id",EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS="extra_click_download_ids";
 public static final String COLUMN_ID="_id",COLUMN_TITLE="title",COLUMN_DESCRIPTION="description",COLUMN_URI="uri",COLUMN_MEDIA_TYPE="media_type",COLUMN_TOTAL_SIZE_BYTES="total_size",COLUMN_LOCAL_URI="local_uri",COLUMN_LOCAL_FILENAME="local_filename",COLUMN_STATUS="status",COLUMN_REASON="reason",COLUMN_BYTES_DOWNLOADED_SO_FAR="bytes_so_far",COLUMN_LAST_MODIFIED_TIMESTAMP="last_modified_timestamp",COLUMN_MEDIAPROVIDER_URI="mediaprovider_uri";
 public static final int STATUS_PENDING=1,STATUS_RUNNING=2,STATUS_PAUSED=4,STATUS_SUCCESSFUL=8,STATUS_FAILED=16;
 public static final int ERROR_UNKNOWN=1000,ERROR_FILE_ERROR=1001,ERROR_UNHANDLED_HTTP_CODE=1002,ERROR_HTTP_DATA_ERROR=1004,ERROR_TOO_MANY_REDIRECTS=1005,ERROR_INSUFFICIENT_SPACE=1006,ERROR_DEVICE_NOT_FOUND=1007,ERROR_CANNOT_RESUME=1008,ERROR_FILE_ALREADY_EXISTS=1009;
 public static final int PAUSED_WAITING_TO_RETRY=1,PAUSED_WAITING_FOR_NETWORK=2,PAUSED_QUEUED_FOR_WIFI=3,PAUSED_UNKNOWN=4;
 private final Context context;private final HostDownloads queue;private final Handler main;private volatile boolean closed;
 private static final String CHANNEL="shiraz.downloads";
 private DownloadManager(Context context){
  this.context=context;main=new Handler(Looper.getMainLooper());
  try{queue=new HostDownloads(new File(context.getNoBackupFilesDir(),"downloads"),context.getFilesDir().getCanonicalFile().getParentFile(),new HostDownloads.Listener(){public void finished(final HostDownloads.Record record){main.post(new Runnable(){public void run(){if(closed)return;notice(record);context.sendBroadcast(new Intent(ACTION_DOWNLOAD_COMPLETE).setPackage(context.getPackageName()).putExtra(EXTRA_DOWNLOAD_ID,record.id));}});}});}
  catch(IOException error){throw new IllegalStateException("Cannot open download queue",error);}
 }
 public static DownloadManager shirazForHost(Context context){return new DownloadManager(context);}
 private void live(){if(closed)throw new IllegalStateException("Download manager closed");}
 public void shirazClose(){closed=true;queue.close();}
 public static class Request {
  public static final int NETWORK_MOBILE=1,NETWORK_WIFI=2,VISIBILITY_VISIBLE=0,VISIBILITY_VISIBLE_NOTIFY_COMPLETED=1,VISIBILITY_HIDDEN=2,VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION=3;
  private final Uri uri;private File destination;private String title="",description="",mime="";private int visibility=0,networks=-1;private boolean charging,idle;
  private final Map<String,String> headers=new LinkedHashMap<String,String>();
  public Request(Uri uri){if(uri==null)throw new NullPointerException("uri");if(!"http".equals(uri.getScheme())&&!"https".equals(uri.getScheme()))throw new IllegalArgumentException("HTTP(S) URI required");this.uri=uri;}
  public Request setDestinationUri(Uri value){if(value==null||!"file".equals(value.getScheme())||value.getPath()==null||value.getAuthority()!=null&&!value.getAuthority().isEmpty())throw new IllegalArgumentException("Local file URI required");destination=new File(value.getPath());return this;}
  private Request destination(File root,String path){if(root==null)throw new IllegalStateException("External storage unavailable");if(path==null)throw new NullPointerException("subPath");try{File result=new File(root,path).getCanonicalFile();if(!result.toPath().startsWith(root.getCanonicalFile().toPath())||result.equals(root.getCanonicalFile()))throw new IllegalArgumentException("Destination escapes directory");destination=result;return this;}catch(IOException error){throw new IllegalArgumentException("Destination",error);}}
  public Request setDestinationInExternalFilesDir(Context context,String type,String path){return destination(context.getExternalFilesDir(type),path);}
  public Request setDestinationInExternalPublicDir(String type,String path){return destination(Environment.getExternalStoragePublicDirectory(type),path);}
  public Request setTitle(CharSequence value){title=value==null?"":value.toString();return this;}
  public Request setDescription(CharSequence value){description=value==null?"":value.toString();return this;}
  public Request setMimeType(String value){mime=value==null?"":value;return this;}
  public Request setNotificationVisibility(int value){if(value<0||value>3)throw new IllegalArgumentException("Visibility");visibility=value;return this;}
  public Request addRequestHeader(String name,String value){if(name==null)throw new NullPointerException("header");if(name.indexOf(':')>=0)throw new IllegalArgumentException("Header name");String old=headers.get(name);headers.put(name,old==null?(value==null?"":value):old+", "+(value==null?"":value));return this;}
  public Request setAllowedNetworkTypes(int value){networks=value;return this;}
  // The host connection is represented as non-roaming and non-metered by ConnectivityManager.
  public Request setAllowedOverRoaming(boolean value){return this;}
  public Request setAllowedOverMetered(boolean value){return this;}
  public Request setRequiresCharging(boolean value){charging=value;return this;}
  public Request setRequiresDeviceIdle(boolean value){idle=value;return this;}
 }
 public static class Query {
  public static final int ORDER_ASCENDING=1,ORDER_DESCENDING=2;
  private long[] ids;private int status;private String order=COLUMN_LAST_MODIFIED_TIMESTAMP;private int direction=ORDER_DESCENDING;
  public Query(){}
  public Query setFilterById(long... ids){this.ids=ids==null?null:ids.clone();return this;}
  public Query setFilterByStatus(int flags){status=flags;return this;}
  public Query orderBy(String column,int direction){if(!COLUMN_LAST_MODIFIED_TIMESTAMP.equals(column)&&!COLUMN_TOTAL_SIZE_BYTES.equals(column))throw new IllegalArgumentException("Download sort column");if(direction!=1&&direction!=2)throw new IllegalArgumentException("Download sort direction");order=column;this.direction=direction;return this;}
  boolean accepts(HostDownloads.Record record){if(status!=0&&(status&record.status)==0)return false;if(ids==null)return true;for(long id:ids)if(id==record.id)return true;return false;}
 }
 public long enqueue(Request request){live();if(request==null)throw new NullPointerException("request");if(request.charging||request.idle||(request.networks&Request.NETWORK_WIFI)==0)throw new UnsupportedOperationException("Requested download scheduling constraint is unavailable on this host");
  File target=request.destination==null?new File(new File(context.getCacheDir(),"downloads"),UUID.randomUUID().toString()):request.destination;
  try{long id=queue.enqueue(request.uri.toString(),target,request.title,request.mime,new LinkedHashMap<String,String>(request.headers),request.description,request.visibility);HostDownloads.Record record=queue.get(id);if(record!=null)notice(record);return id;}catch(IOException error){throw new IllegalStateException("Cannot enqueue download",error);}
 }
 private void notice(HostDownloads.Record record){
  NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager==null)return;
  boolean terminal=record.status==STATUS_SUCCESSFUL||record.status==STATUS_FAILED;
  if(record.visibility==Request.VISIBILITY_HIDDEN)return;
  if(terminal&&record.visibility==Request.VISIBILITY_VISIBLE){manager.cancel("download:"+record.id,0);return;}
  if(!terminal&&record.visibility==Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION)return;
  manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Downloads",NotificationManager.IMPORTANCE_LOW));
  Notification.Builder builder=new Notification.Builder(context,CHANNEL).setContentTitle(record.title.isEmpty()?"Download":record.title).setContentText(terminal?(record.status==STATUS_SUCCESSFUL?"Download complete":"Download failed"):record.description).setOngoing(!terminal).setOnlyAlertOnce(true);
  if(!terminal)builder.setProgress(0,0,true);manager.notify("download:"+record.id,0,builder.build());
 }
 public Cursor query(final Query query){live();if(query==null)throw new NullPointerException("query");try{
  List<HostDownloads.Record> records=queue.query();Collections.sort(records,new Comparator<HostDownloads.Record>(){public int compare(HostDownloads.Record a,HostDownloads.Record b){long x=COLUMN_TOTAL_SIZE_BYTES.equals(query.order)?a.total:a.modified,y=COLUMN_TOTAL_SIZE_BYTES.equals(query.order)?b.total:b.modified;return Long.compare(x,y)*(query.direction==1?1:-1);}});
  MatrixCursor cursor=new MatrixCursor(new String[]{COLUMN_ID,COLUMN_TITLE,COLUMN_DESCRIPTION,COLUMN_URI,COLUMN_MEDIA_TYPE,COLUMN_TOTAL_SIZE_BYTES,COLUMN_LOCAL_URI,COLUMN_LOCAL_FILENAME,COLUMN_STATUS,COLUMN_REASON,COLUMN_BYTES_DOWNLOADED_SO_FAR,COLUMN_LAST_MODIFIED_TIMESTAMP,COLUMN_MEDIAPROVIDER_URI});
  for(HostDownloads.Record record:records)if(query.accepts(record))cursor.addRow(new Object[]{record.id,record.title,record.description,record.url,record.mime,record.total,Uri.fromFile(record.destination).toString(),record.destination.getPath(),record.status,record.reason,record.bytes,record.modified,null});return cursor;
 }catch(IOException error){throw new IllegalStateException("Cannot query downloads",error);}}
 public int remove(long... ids){live();if(ids==null||ids.length==0)throw new IllegalArgumentException("Download ids");int removed=0;try{for(long id:ids)if(queue.remove(id)){removed++;NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager!=null)manager.cancel("download:"+id,0);}return removed;}catch(IOException error){throw new IllegalStateException("Cannot remove download",error);}}
 public Uri getUriForDownloadedFile(long id){live();try{HostDownloads.Record record=queue.get(id);return record!=null&&record.status==STATUS_SUCCESSFUL?Uri.parse("content://downloads/my_downloads/"+id):null;}catch(IOException error){throw new IllegalStateException("Cannot query download",error);}}
 public String getMimeTypeForDownloadedFile(long id){live();try{HostDownloads.Record record=queue.get(id);return record==null?null:record.mime;}catch(IOException error){throw new IllegalStateException("Cannot query download",error);}}
 public ParcelFileDescriptor openDownloadedFile(long id)throws FileNotFoundException{live();try{HostDownloads.Record record=queue.get(id);if(record==null||record.status!=STATUS_SUCCESSFUL)throw new FileNotFoundException("Download unavailable");HostAppInstaller.recordAccess(context.getPackageName()+":download-open:"+id);return ParcelFileDescriptor.open(record.destination,ParcelFileDescriptor.MODE_READ_ONLY);}catch(IOException error){FileNotFoundException failure=new FileNotFoundException("Download unavailable");failure.initCause(error);throw failure;}}
 public ContentProvider shirazProvider(){return new ContentProvider(){
  private long id(Uri uri){if(!"downloads".equals(uri.getAuthority())||uri.getPathSegments().size()!=2||!"my_downloads".equals(uri.getPathSegments().get(0)))throw new IllegalArgumentException("Download URI");return Long.parseLong(uri.getLastPathSegment());}
  public boolean onCreate(){return true;}
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){if(projection!=null||selection!=null||order!=null)throw new UnsupportedOperationException("Download provider query options");return DownloadManager.this.query(new Query().setFilterById(id(uri)));}
  public String getType(Uri uri){return getMimeTypeForDownloadedFile(id(uri));}
  public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Download is read-only");return openDownloadedFile(id(uri));}
  public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Use DownloadManager.enqueue");}
  public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Use DownloadManager.remove");}
  public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Download provider update");}
 };}
}
