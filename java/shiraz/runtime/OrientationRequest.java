// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
/** Freeform desktop orientation preferences; manual window resizing remains available. */
public final class OrientationRequest {
 private final Thread owner=Thread.currentThread();
 private int requested=-1,aspect;private boolean pending,initialized;
 public OrientationRequest(int requested,int width,int height){request(requested,width,height);}
 private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Orientation changes require the activity thread");}
 public synchronized int get(){return requested;}
 // Worker threads publish preferences; only the activity thread consumes them.
 public synchronized void request(int value,int width,int height){
  if(value< -1||value>14)throw new IllegalArgumentException("Orientation range");
  if(value==3||value==4||value==8||value==9||value==10)throw new UnsupportedOperationException("Sensor/reverse/behind orientation is not implemented: "+value);
  if(width<1||height<1)throw new IllegalArgumentException("Orientation geometry");
  // Activity recreation and compositor refusal must not re-arm an unchanged
  // preference. A genuine mode change remains a fresh one-shot host request.
  if(initialized&&requested==value)return;
  initialized=true;
  requested=value;aspect=value==0||value==6||value==11?2:value==1||value==7||value==12?1:value==14?(width>height?2:1):0;pending=true;
 }
 public synchronized int[] take(int width,int height){thread();if(!pending)return null;pending=false;if(aspect==0||width==height||(width>height?2:1)==aspect)return null;return new int[]{height,width};}
}
