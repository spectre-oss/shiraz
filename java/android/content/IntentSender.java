// SPDX-License-Identifier: GPL-3.0-only
package android.content;
/** In-process pending intent completion target. */
public final class IntentSender {
 private final android.app.PendingIntent token;
 public IntentSender(android.app.PendingIntent token){this.token=token;}
 public void sendIntent(Context context,int code,Intent intent,OnFinished finished,android.os.Handler handler) throws SendIntentException {
  try{token.shirazSend(intent);if(finished!=null){Runnable r=new Runnable(){public void run(){finished.onSendFinished(IntentSender.this,intent,code,null,null);}};if(handler==null)r.run();else handler.post(r);}}
  catch(android.app.PendingIntent.CanceledException e){throw new SendIntentException(e);}
 }
 public interface OnFinished {void onSendFinished(IntentSender sender,Intent intent,int resultCode,String resultData,android.os.Bundle resultExtras);}
 public static class SendIntentException extends android.util.AndroidException {public SendIntentException(){}public SendIntentException(Exception e){super(e.toString());}}
}
