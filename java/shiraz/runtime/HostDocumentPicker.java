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

/** File and folder selection in the app window; no external desktop helper is required. */
public final class HostDocumentPicker {
    private HostDocumentPicker() { }
    public interface Result { void complete(int code, Intent data); }
    public static boolean handles(Intent intent) {
        Intent target=HostContentPicker.unwrap(intent);
        return target!=null&&target.getComponent()==null&&target.getPackage()==null
                &&(Intent.ACTION_OPEN_DOCUMENT_TREE.equals(target.getAction())
                    ||Intent.ACTION_OPEN_DOCUMENT.equals(target.getAction())||Intent.ACTION_GET_CONTENT.equals(target.getAction()));
    }
    public static android.content.pm.ResolveInfo resolve(Context context,Intent intent){
        if(!handles(intent))return null;
        android.content.pm.ResolveInfo result=new android.content.pm.ResolveInfo();result.activityInfo=new android.content.pm.ActivityInfo();
        result.activityInfo.packageName=context.getPackageName();result.activityInfo.name=HostDocumentPicker.class.getName();result.activityInfo.enabled=true;result.activityInfo.exported=false;return result;
    }
    public static boolean accepts(Intent intent,String mime){
        Intent target=HostContentPicker.unwrap(intent);String[] types=target.getStringArrayExtra("android.intent.extra.MIME_TYPES");
        if(types==null||types.length==0)types=new String[]{target.getType()==null?"*/*":target.getType()};
        for(String type:types)if(type!=null&&(type.equals("*/*")||type.equalsIgnoreCase(mime)||type.endsWith("/*")&&mime.regionMatches(true,0,type,0,type.length()-1)))return true;
        return false;
    }
    public static void launch(Activity activity, Intent intent, Result result) {
        DialogOverlay.requireMain();new Picker(activity,HostDocuments.forContext(activity),HostContentPicker.unwrap(intent),result).show();
    }
    private static final class Picker extends AlertDialog {
        final Activity activity;final HostDocuments documents;final Result result;final Intent request;final boolean folders;
        File directory;LinearLayout entries;boolean completed;
        Picker(Activity activity,HostDocuments documents,Intent request,Result result){
            super(activity);this.activity=activity;this.documents=documents;this.request=request;this.result=result;directory=documents.volume();folders=Intent.ACTION_OPEN_DOCUMENT_TREE.equals(request.getAction());
            setTitle(folders?"Choose folder":"Choose file");setMessage("App storage");setCanceledOnTouchOutside(false);
            if(folders)setButton(BUTTON_POSITIVE,"Use folder",new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){
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
                if(!child.isDirectory()&&(folders||!child.isFile()||!accepts(request,HostDocuments.mime(child))))continue;
                try{if(!child.getCanonicalFile().toPath().startsWith(documents.volume().toPath()))continue;}catch(IOException error){continue;}
                if(++count>128){TextView limit=new TextView(activity);limit.setText("More items are not shown.");entries.addView(limit);break;}
                Button button=new Button(activity);button.setText(child.getName());button.setAllCaps(false);button.setTextSize(14);button.setMaxLines(2);button.setEllipsize(android.text.TextUtils.TruncateAt.END);button.setTextColor(0xfff5f5f5);button.setBackgroundColor(0xff365b6b);entries.addView(button,new LinearLayout.LayoutParams(-1,dp(child.isDirectory()?40:64)));
                button.setOnClickListener(new View.OnClickListener(){public void onClick(View view){try{File next=child.getCanonicalFile();if(next.toPath().startsWith(documents.volume().toPath())){if(next.isDirectory()){directory=next;refresh();}else{Uri uri=documents.selectFile(next);finish(Activity.RESULT_OK,new Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}}}catch(IOException error){setMessage("This item is unavailable.");}}});
            }
        }
    }
}
