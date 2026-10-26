// SPDX-License-Identifier: GPL-3.0-only
package android.service.notification;
import android.app.Notification;
/** Snapshot of a notification owned by the current app. */
public final class StatusBarNotification {
 private final String pkg,tag;private final int id;private final Notification notification;private final long time;
 public StatusBarNotification(String pkg,String tag,int id,Notification notification,long time){this.pkg=pkg;this.tag=tag;this.id=id;this.notification=notification;this.time=time;}
 public String getPackageName(){return pkg;}public String getTag(){return tag;}public int getId(){return id;}public Notification getNotification(){return notification;}public long getPostTime(){return time;}
 public String getKey(){return pkg+"|"+id+"|"+tag;}public boolean isOngoing(){return (notification.flags&Notification.FLAG_ONGOING_EVENT)!=0;}public boolean isClearable(){return (notification.flags&(Notification.FLAG_NO_CLEAR|Notification.FLAG_ONGOING_EVENT))==0;}
}
