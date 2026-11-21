// SPDX-License-Identifier: GPL-3.0-only
package android.provider;
import android.content.*;
import android.net.Uri;
import java.util.concurrent.*;
/** Recent-search client; storage remains owned by the application's content provider. */
public class SearchRecentSuggestions {
    public static final String[] QUERIES_PROJECTION_1LINE={"_id","date","query","display1"};
    public static final String[] QUERIES_PROJECTION_2LINE={"_id","date","query","display1","display2"};
    public static final int QUERIES_PROJECTION_DATE_INDEX=1,QUERIES_PROJECTION_QUERY_INDEX=2,QUERIES_PROJECTION_DISPLAY1_INDEX=3,QUERIES_PROJECTION_DISPLAY2_INDEX=4;
    private static final ThreadPoolExecutor writes=new ThreadPoolExecutor(0,1,1,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(128),new ThreadFactory(){public Thread newThread(Runnable action){Thread worker=new Thread(action,"Shiraz-recent-search");worker.setDaemon(true);return worker;}},new ThreadPoolExecutor.AbortPolicy());
    private final Context context;
    private final Uri uri;
    private final boolean twoLines;
    public SearchRecentSuggestions(Context context,String authority,int mode){
        this.context=java.util.Objects.requireNonNull(context);
        if(authority==null||authority.isEmpty()||authority.length()>1024||!authority.matches("[A-Za-z0-9_.$-]+"))throw new IllegalArgumentException("Invalid suggestions authority");
        if((mode&1)==0||(mode&~3)!=0)throw new IllegalArgumentException("Unsupported suggestions mode");
        twoLines=(mode&2)!=0;uri=new Uri.Builder().scheme("content").authority(authority).appendPath("suggestions").build();
    }
    public void saveRecentQuery(final String query,final String line2){
        if(query==null||query.isEmpty())return;
        if(!twoLines&&line2!=null&&!line2.isEmpty())throw new IllegalArgumentException("Second line requires two-line mode");
        if(query.length()>32768||line2!=null&&line2.length()>32768)throw new IllegalArgumentException("Search history text limit");
        writes.execute(new Runnable(){public void run(){
            ContentResolver resolver=context.getContentResolver();
            ContentValues values=new ContentValues();values.put("display1",query);values.put("query",query);values.put("date",System.currentTimeMillis());if(twoLines)values.put("display2",line2);
            try{if(resolver.insert(uri,values)==null)throw new IllegalStateException("Search history provider rejected insert");}
            catch(RuntimeException failure){android.util.Log.e("SearchRecentSuggestions","Search history write failed");return;}
            truncateHistory(resolver,250);
        }});
    }
    public void clearHistory(){truncateHistory(context.getContentResolver(),0);}
    protected void truncateHistory(ContentResolver resolver,int maxEntries){
        if(maxEntries<0)throw new IllegalArgumentException("Negative history size");
        String selection=maxEntries==0?null:"_id IN (SELECT _id FROM suggestions ORDER BY date DESC LIMIT -1 OFFSET "+maxEntries+")";
        resolver.delete(uri,selection,null);
    }
}
