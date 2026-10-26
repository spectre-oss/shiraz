// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Per-loaded-app camera/microphone policy and metadata-only access history. */
public final class CapturePermissions {
 public static final String CAMERA="android.permission.CAMERA", MICROPHONE="android.permission.RECORD_AUDIO";
 private static volatile CapturePermissions current;
 private String appName;
 private final Set<String> declared=new HashSet<String>(),once=new HashSet<String>();
 private final Properties decisions=new Properties();
 private final File state,history;
 private boolean foreground=true;
 private long nextLease=1,revision;
 private final Map<Long,Lease> active=new LinkedHashMap<Long,Lease>();
 private static final class Lease {final String permission;final Runnable stop;Lease(String p,Runnable s){permission=p;stop=s;}}
 public CapturePermissions(File data,String name,Properties metadata){
  appName=name;File directory=new File(data,"privacy");if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalStateException("Cannot create app privacy directory");
  directory.setReadable(false,false);directory.setWritable(false,false);directory.setExecutable(false,false);directory.setReadable(true,true);directory.setWritable(true,true);directory.setExecutable(true,true);
  state=new File(directory,"permissions.properties");history=new File(directory,"access-history.log");
  int count=Integer.parseInt(metadata.getProperty("permission.count","0"));if(count<0||count>1024)throw new IllegalArgumentException("Permission count");
  for(int i=0;i<count;i++)declared.add(metadata.getProperty("permission."+i,""));
  if(state.isFile()){if(state.length()>16384)throw new IllegalStateException("Permission state exceeds limit");try(InputStream in=new FileInputStream(state)){decisions.load(in);}catch(IOException e){throw new IllegalStateException("Cannot load app permissions",e);}}
 }
 public static synchronized void install(CapturePermissions value){if(current!=null&&current!=value)current.setForeground(false);current=value;}
 public static CapturePermissions current(){return current;}
 public synchronized void setAppName(CharSequence value){if(value!=null&&value.length()>0)appName=value.toString().substring(0,Math.min(256,value.length()));}
 public static void authorize(String permission){CapturePermissions policy=current;if(policy==null)throw new SecurityException("Capture consent is unavailable");policy.require(permission);}
 public static boolean supports(String p){return CAMERA.equals(p)||MICROPHONE.equals(p);}
 private static String label(String p){return "wifi".equals(p)?"Wi-Fi":CAMERA.equals(p)?"Camera":"Microphone";}
 public synchronized boolean canRequest(String p){return supports(p)&&declared.contains(p)&&foreground;}
 public synchronized boolean granted(String p){return canRequest(p)&&(once.contains(p)||"foreground".equals(decisions.getProperty(p)));}
 public synchronized boolean wasDenied(String p){return "denied".equals(decisions.getProperty(p));}
 private void save(){File temporary=new File(state.getParentFile(),"permissions.tmp");try(OutputStream out=new FileOutputStream(temporary)){decisions.store(out,"Shiraz per-app capture consent");}catch(IOException e){throw new IllegalStateException("Cannot save app permissions",e);}if(!temporary.renameTo(state))throw new IllegalStateException("Cannot replace app permissions");state.setReadable(false,false);state.setWritable(false,false);state.setReadable(true,true);state.setWritable(true,true);}
 public void wifiAccess(String action){
  if(!action.matches("(?:service:requested|(?:isWifiEnabled|getWifiState):(?:requested|enabled|disabled|unavailable)|(?:getConnectionInfo|getSSID):redacted|getIpAddress:unavailable)"))throw new IllegalArgumentException("Wi-Fi audit action");
  android.os.StrictMode.ThreadPolicy old=android.os.StrictMode.allowThreadDiskWrites();try{event("wifi",action);}finally{android.os.StrictMode.setThreadPolicy(old);}
 }
 private synchronized void event(String permission,String action){
  try{if(history.length()>512*1024){File previous=new File(history.getParentFile(),"access-history.previous.log");if(previous.exists()&&!previous.delete())throw new IOException("history rotation");if(!history.renameTo(previous))throw new IOException("history rotation");}
   try(Writer out=new OutputStreamWriter(new FileOutputStream(history,true),StandardCharsets.UTF_8)){out.write(System.currentTimeMillis()+"\t"+label(permission)+"\t"+action+"\n");}
   history.setReadable(false,false);history.setWritable(false,false);history.setReadable(true,true);history.setWritable(true,true);revision++;
  }catch(IOException e){throw new IllegalStateException("Cannot track capture access",e);}
 }
 private synchronized void decide(String p,String choice){
  if(!canRequest(p))throw new SecurityException("Capture permission is undeclared or app is in background");
  if(!"once".equals(choice)&&!"foreground".equals(choice)&&!"denied".equals(choice))throw new IllegalArgumentException("permission choice");
  once.remove(p);if("once".equals(choice)){once.add(p);decisions.remove(p);}else decisions.setProperty(p,choice);
  save();event(p,"decision:"+choice);
 }
 public void revoke(String p){if(!supports(p))throw new IllegalArgumentException("permission");synchronized(this){once.remove(p);decisions.setProperty(p,"denied");save();}stop(p);event(p,"revoked");}
 public synchronized void require(String p){if(!granted(p)){event(p,"access-denied");throw new SecurityException(label(p)+" permission has not been granted for foreground use");}}
 public synchronized long begin(String p,Runnable stop){require(p);if(active.size()>=16)throw new IllegalStateException("Capture device limit");long id=nextLease++;event(p,"capture-start");active.put(id,new Lease(p,stop));return id;}
 public synchronized void end(long id){Lease lease=active.remove(id);if(lease!=null)event(lease.permission,"capture-stop");}
 public void failure(String p){event(p,"device-unavailable");}
 private void stop(String permission){List<Map.Entry<Long,Lease>> leases;synchronized(this){leases=new ArrayList<Map.Entry<Long,Lease>>(active.entrySet());}for(Map.Entry<Long,Lease> e:leases)if(permission==null||permission.equals(e.getValue().permission)){try{e.getValue().stop.run();}finally{end(e.getKey());}}}
 public void setForeground(boolean value){synchronized(this){foreground=value;if(!value){for(String p:once)event(p,"one-time-expired");once.clear();}revision++;}if(!value)stop(null);}
 public synchronized long revision(){return revision;}
 public synchronized String activeSummary(){boolean camera=false,mic=false;for(Lease lease:active.values()){camera|=CAMERA.equals(lease.permission);mic|=MICROPHONE.equals(lease.permission);}return camera&&mic?"Camera + microphone in use":camera?"Camera in use":mic?"Microphone in use":"";}
 public synchronized String history(){
  ArrayDeque<String> lines=new ArrayDeque<String>();try{if(history.isFile())try(BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(history),StandardCharsets.UTF_8))){String line;while((line=in.readLine())!=null){if(lines.size()==12)lines.removeFirst();lines.addLast(line);}}}catch(IOException e){return "History unavailable";}
  StringBuilder out=new StringBuilder();for(String line:lines){String[] parts=line.split("\t");if(parts.length==3){try{out.append(new java.text.SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date(Long.parseLong(parts[0])))).append("  ").append(parts[1]).append("  ").append(eventLabel(parts[2])).append('\n');}catch(NumberFormatException ignored){}}}return out.length()==0?"No capture access yet.":out.toString();
 }
 private static String eventLabel(String event){if("service:requested".equals(event))return "Wi-Fi service requested";if("getSSID:redacted".equals(event))return "Network name requested (redacted)";if("getConnectionInfo:redacted".equals(event))return "Connection information requested (redacted)";if(event.startsWith("isWifiEnabled:")||event.startsWith("getWifiState:")){String result=event.substring(event.indexOf(':')+1);return "requested".equals(result)?"Wi-Fi status requested":"Wi-Fi status: "+result;}if("prompt".equals(event))return "Permission requested";if("decision:foreground".equals(event))return "Allowed while using app";if("decision:once".equals(event))return "Allowed once";if("decision:denied".equals(event))return "Denied";if("capture-start".equals(event))return "Capture started";if("capture-stop".equals(event))return "Capture stopped";if("revoked".equals(event))return "Access revoked";if("one-time-expired".equals(event))return "Permission expired";if("device-unavailable".equals(event))return "Device unavailable";if("access-denied".equals(event))return "Access denied";return "Permission event";}
 public AlertDialog prompt(final Activity owner,final String p,final Runnable completed){
  synchronized(this){if(!canRequest(p))throw new SecurityException("Capture permission unavailable");event(p,"prompt");}
  final boolean[] answered={false};
  final String[] choices={"foreground","once","denied"};final String[] labels={"While using app","Only this time","Don't allow"};
  final AlertDialog dialog=new AlertDialog(owner){
   private android.view.View card;
   @Override public boolean shirazAnimateEntrance(){return true;}
   protected void onCreate(Bundle state){super.onCreate(state);((android.view.ViewGroup)card.getParent()).removeView(card);setContentView(card,new android.view.ViewGroup.LayoutParams(-2,-2));getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));getWindow().setDimAmount(.32f);}
   protected android.view.View createBody(){
    android.widget.LinearLayout body=new android.widget.LinearLayout(owner);body.setOrientation(1);body.setPadding(dp(24),dp(24),dp(24),dp(16));
    android.view.View icon=new android.view.View(owner){protected void onDraw(Canvas canvas){int save=canvas.save();try{canvas.scale(getWidth()/48f,getHeight()/48f);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(0xff344b3f);canvas.drawCircle(24,24,24,paint);paint.setColor(0xffc1e9d2);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);
      if(CAMERA.equals(p)){canvas.drawRoundRect(new RectF(12,17,36,33),3,3,paint);canvas.drawCircle(24,25,5,paint);canvas.drawLine(19,17,21,13,paint);canvas.drawLine(21,13,27,13,paint);canvas.drawLine(27,13,29,17,paint);}
      else{canvas.drawRoundRect(new RectF(20,12,28,27),4,4,paint);Path cradle=new Path();cradle.moveTo(16,22);cradle.lineTo(16,25);cradle.cubicTo(16,36,32,36,32,25);cradle.lineTo(32,22);canvas.drawPath(cradle,paint);canvas.drawLine(24,33,24,37,paint);canvas.drawLine(20,37,28,37,paint);}
     }finally{canvas.restoreToCount(save);}}};icon.setContentDescription(label(p));android.widget.LinearLayout.LayoutParams iconParams=new android.widget.LinearLayout.LayoutParams(dp(48),dp(48));iconParams.gravity=android.view.Gravity.CENTER_HORIZONTAL;body.addView(icon,iconParams);
    body.addView(consentText(owner,"Shiraz permissions",12,0xffbdc9c0,16,0),new android.widget.LinearLayout.LayoutParams(-1,-2));
    body.addView(consentText(owner,"Allow "+appName+" to use your "+label(p).toLowerCase(Locale.ROOT)+"?",22,0xffe5ede5,8,0),new android.widget.LinearLayout.LayoutParams(-1,-2));
    android.widget.TextView explanation=consentText(owner,"Only while this window is in use.\n\nPermission decisions and access times appear in Privacy. Captured audio and images are never added to that history.",14,0xffbdc9c0,16,20);explanation.setGravity(android.view.Gravity.START);explanation.setLineSpacing(dp(3),1);body.addView(explanation,new android.widget.LinearLayout.LayoutParams(-1,-2));
    for(int i=0;i<choices.length;i++){final String choice=choices[i];android.widget.Button button=new android.widget.Button(owner);button.setText(labels[i]);button.setAllCaps(false);button.setTextSize(14);button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));button.setTextColor(i==0?0xff173b2a:0xffc1e9d2);button.setGravity(android.view.Gravity.CENTER);button.setPadding(dp(16),0,dp(16),0);button.setMinimumHeight(dp(48));
     android.graphics.drawable.GradientDrawable shape=consentShape(dp(24),i==0?0xffc1e9d2:Color.TRANSPARENT);if(i==1)shape.setStroke(dp(1),0xff63776a);android.graphics.drawable.GradientDrawable mask=consentShape(dp(24),Color.WHITE);button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(i==0?0x24173b2a:0x24c1e9d2),shape,mask));
     android.widget.LinearLayout.LayoutParams params=new android.widget.LinearLayout.LayoutParams(-1,dp(48));params.topMargin=i==0?0:dp(8);body.addView(button,params);button.setOnClickListener(new android.view.View.OnClickListener(){public void onClick(android.view.View view){if(answered[0])return;answered[0]=true;if(canRequest(p))decide(p,choice);dismiss();completed.run();}});
    }
    android.widget.ScrollView scroll=new android.widget.ScrollView(owner){protected void onMeasure(int widthSpec,int heightSpec){int cap=Math.max(1,getResources().getDisplayMetrics().heightPixels-dp(48));int size=android.view.View.MeasureSpec.getMode(heightSpec)==android.view.View.MeasureSpec.UNSPECIFIED?cap:Math.min(cap,android.view.View.MeasureSpec.getSize(heightSpec));int width=Math.min(dp(400),Math.max(1,getResources().getDisplayMetrics().widthPixels-dp(48)));if(android.view.View.MeasureSpec.getMode(widthSpec)!=android.view.View.MeasureSpec.UNSPECIFIED)width=Math.min(width,android.view.View.MeasureSpec.getSize(widthSpec));super.onMeasure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(size,android.view.View.MeasureSpec.AT_MOST));}};
    scroll.setBackground(consentShape(dp(28),0xff202a24));scroll.setClipToOutline(true);scroll.addView(body,new android.widget.FrameLayout.LayoutParams(-1,-2));card=scroll;return card;
   }
  };
  dialog.setOnCancelListener(new DialogInterface.OnCancelListener(){public void onCancel(DialogInterface d){if(!answered[0]){answered[0]=true;if(canRequest(p))decide(p,"denied");completed.run();}}});dialog.setCanceledOnTouchOutside(false);dialog.show();return dialog;
 }
 private String privacyText(){String summary=activeSummary();return (summary.isEmpty()?"Camera and microphone are idle.":summary)+"\n\nCamera: "+(granted(CAMERA)?"Allowed while in use":"Not allowed")+"\nMicrophone: "+(granted(MICROPHONE)?"Allowed while in use":"Not allowed")+"\n\nRecent access\n"+history();}
 public void showPrivacy(final Activity owner){
  AlertDialog dialog=new AlertDialog(owner){protected void onCreate(Bundle state){super.onCreate(state);styleDialog(this);}protected android.view.View createBody(){android.widget.LinearLayout body=new android.widget.LinearLayout(owner);body.setOrientation(1);for(final String permission:new String[]{CAMERA,MICROPHONE}){final android.widget.Button button=new android.widget.Button(owner);button.setText("Revoke "+label(permission).toLowerCase(Locale.ROOT)+" access");button.setEnabled(granted(permission));button.setAlpha(granted(permission)?1f:.4f);body.addView(button,new android.widget.LinearLayout.LayoutParams(-1,-2));button.setOnClickListener(new android.view.View.OnClickListener(){public void onClick(android.view.View v){revoke(permission);button.setEnabled(false);button.setAlpha(.4f);setMessage(privacyText());}});}return body;}};
  dialog.setTitle("Privacy — "+appName);dialog.setMessage(privacyText());dialog.setButton(-2,"Close",(DialogInterface.OnClickListener)null);dialog.show();
 }
 private boolean indicatorPointer;
 public boolean pointer(int action,float x,float y,int width,Activity owner){
  if(action==android.view.MotionEvent.ACTION_DOWN){String text=activeSummary();Paint paint=new Paint();paint.setTextSize(14);int w=Math.min(width,Math.round(paint.measureText(text))+24);indicatorPointer=!text.isEmpty()&&y>=0&&y<26&&x>=(width-w)/2&&x<(width+w)/2;}
  boolean owned=indicatorPointer;if(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL){indicatorPointer=false;if(owned&&action==android.view.MotionEvent.ACTION_UP)showPrivacy(owner);}return owned;
 }
 private static int consentDp(android.content.Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
 private static android.graphics.drawable.GradientDrawable consentShape(int radius,int color){android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(color);shape.setCornerRadius(radius);return shape;}
 private static android.widget.TextView consentText(android.content.Context context,String text,int size,int color,int top,int bottom){android.widget.TextView view=new android.widget.TextView(context);view.setText(text);view.setTextSize(size);view.setTextColor(color);view.setGravity(android.view.Gravity.CENTER);view.setPadding(0,consentDp(context,top),0,consentDp(context,bottom));return view;}
 public static void styleDialog(AlertDialog dialog){styleView(dialog.getWindow().getDecorView());}
 private static void styleView(android.view.View view){if(view instanceof android.widget.TextView)((android.widget.TextView)view).setTextColor(0xfff5f5f5);if(view instanceof android.widget.Button)view.setBackgroundColor(0xff365b6b);else if(view instanceof android.view.ViewGroup)view.setBackgroundColor(0xff20252b);if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)styleView(group.getChildAt(i));}}
 public void draw(Canvas canvas,int width){String text=activeSummary();if(text.isEmpty())return;Paint paint=new Paint();paint.setTextSize(14);int w=Math.min(width,Math.round(paint.measureText(text))+24),x=(width-w)/2;paint.setColor(0xff116b39);canvas.drawRect(x,0,x+w,26,paint);paint.setColor(0xffffffff);canvas.drawText(text,x+12,19,paint);}
}
