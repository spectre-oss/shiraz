// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Folder selection in the app window; no external desktop helper is required. */
public final class HostDocumentPicker {
    private HostDocumentPicker() { }
    public interface Result { void complete(int code, Intent data); }
    public static boolean handles(Intent intent) {
        Intent target=HostContentPicker.unwrap(intent);
        return target!=null&&target.getComponent()==null&&target.getPackage()==null
                &&"android.intent.action.OPEN_DOCUMENT_TREE".equals(target.getAction());
    }
    public static void launch(Activity activity, Result result) {
        DialogOverlay.requireMain();new Picker(activity,HostDocuments.forContext(activity),result).show();
    }
    private static final class Picker extends AlertDialog {
        final Activity activity;final HostDocuments documents;final Result result;
        File directory;LinearLayout entries;boolean completed;
        Picker(Activity activity,HostDocuments documents,Result result){
            super(activity);this.activity=activity;this.documents=documents;this.result=result;directory=documents.volume();
            setTitle("Choose folder");setMessage("App storage");setCanceledOnTouchOutside(false);
            setButton(BUTTON_POSITIVE,"Use folder",new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){
                try{Uri uri=Picker.this.documents.select(directory);int modes=Picker.this.documents.permitted(uri,2)?3:1;Intent data=new Intent().setData(uri).addFlags(modes|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);finish(Activity.RESULT_OK,data);}
                catch(IOException error){android.util.Log.w("ShirazDocuments","Cannot select folder",error);finish(Activity.RESULT_CANCELED,null);}
            }});
            setButton(BUTTON_NEGATIVE,"Cancel",new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){finish(Activity.RESULT_CANCELED,null);}});
            setOnDismissListener(new DialogInterface.OnDismissListener(){public void onDismiss(DialogInterface dialog){finish(Activity.RESULT_CANCELED,null);}});
        }
        private void finish(int code,Intent data){if(completed)return;completed=true;dismiss();if(!activity.isDestroyed())result.complete(code,data);}
        protected View createBody(){
            LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
            Button up=new Button(activity);up.setText("Up");body.addView(up,new LinearLayout.LayoutParams(-1,dp(40)));
            up.setOnClickListener(new View.OnClickListener(){public void onClick(View view){if(!directory.equals(documents.volume())){directory=directory.getParentFile();refresh();}}});
            ScrollView scroll=new ScrollView(activity);entries=new LinearLayout(activity);entries.setOrientation(LinearLayout.VERTICAL);scroll.addView(entries,new FrameLayout.LayoutParams(-1,-2));
            body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(180)));refresh();return body;
        }
        protected void onCreate(Bundle state){super.onCreate(state);CapturePermissions.styleDialog(this);}
        private void refresh(){
            entries.removeAllViews();String relative=documents.volume().toPath().relativize(directory.toPath()).toString();setMessage(relative.isEmpty()?"App storage":"App storage / "+relative);
            File[] children=directory.listFiles();if(children==null){setMessage("This folder is unavailable.");return;}Arrays.sort(children);int count=0;
            for(final File child:children){
                if(!child.isDirectory())continue;
                try{if(!child.getCanonicalFile().toPath().startsWith(documents.volume().toPath()))continue;}catch(IOException error){continue;}
                if(++count>128){TextView limit=new TextView(activity);limit.setText("More folders are not shown.");entries.addView(limit);break;}
                Button button=new Button(activity);button.setText(child.getName());button.setAllCaps(false);button.setTextColor(0xfff5f5f5);button.setBackgroundColor(0xff365b6b);entries.addView(button,new LinearLayout.LayoutParams(-1,dp(40)));
                button.setOnClickListener(new View.OnClickListener(){public void onClick(View view){try{File next=child.getCanonicalFile();if(next.toPath().startsWith(documents.volume().toPath())){directory=next;refresh();}}catch(IOException error){setMessage("This folder is unavailable.");}}});
            }
        }
    }
}
