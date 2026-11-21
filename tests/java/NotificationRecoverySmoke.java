// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import shiraz.runtime.NotificationBackend;
public final class NotificationRecoverySmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static final class Backend implements NotificationBackend {
  boolean fail;int posts,closes,cancels;long replacement;
  Backend(boolean fail){this.fail=fail;}
  public String[] capabilities(){return new String[]{"body"};}
  public long post(long replaces,String summary,String body,int urgency,int timeout,boolean quiet){posts++;replacement=replaces;if(fail)throw new IllegalStateException("Fixture offline");return 71;}
  public void cancel(long id){check(id==71,"wrong delivery canceled");cancels++;}
  public void close(){closes++;}
 }
 public static void main(String[] args){
  final Backend failed=new Backend(true),recovered=new Backend(false),replacement=new Backend(false);final int[] opens={0};
  NotificationManager manager=NotificationManager.shirazForProcess("fixture",new java.io.File("unused-notification-policy"),new NotificationManager.DeliveryFactory(){public NotificationBackend open(String app){int attempt=opens[0]++;return attempt==0?failed:attempt==1?recovered:replacement;}});
  Notification notification=new Notification();notification.extras.putCharSequence(Notification.EXTRA_TITLE,"Ongoing download");notification.flags=Notification.FLAG_FOREGROUND_SERVICE;
  try{
   manager.notify(7,notification);check(failed.posts==1&&failed.closes==1,"failed delivery not released");
   manager.notify(7,notification);check(opens[0]==1,"retry backoff ignored");
   check(manager.areNotificationsEnabled(),"backend recovery unavailable");
   manager.notify(7,notification);check(recovered.posts==1&&recovered.replacement==0,"identical notification not retried after recovery");
   manager.notify(7,notification);check(recovered.posts==1,"unchanged successful delivery duplicated");
   check(manager.shirazForegroundSummary().equals("Ongoing download"),"foreground ownership lost");
   manager.notify(8,notification);manager.notify(9,notification);
   recovered.fail=true;Notification changed=notification.clone();changed.extras.putCharSequence(Notification.EXTRA_TITLE,"Download updated");
   manager.notify(7,changed);check(recovered.closes==1,"established failed connection not closed");
   check(manager.getActiveNotifications().length==3,"connection loss erased local notifications");
   check(manager.areNotificationsEnabled(),"replacement backend unavailable");
   manager.cancel(8);check(replacement.cancels==0,"stale ID canceled on replacement connection");
   manager.notify(9,notification);check(replacement.posts==1&&replacement.replacement==0,"old successful ID reused after reconnect");
   manager.notify(9,notification);check(replacement.posts==1,"successful replacement duplicated");
   manager.cancelAll();check(replacement.cancels==1&&manager.getActiveNotifications().length==0,"replacement cancellation ownership");
  }finally{manager.shirazClose();}
  check(recovered.closes==1&&replacement.closes==1,"backend closed more than once or leaked");System.out.println("NOTIFICATION_RECOVERY_OK");
 }
}
