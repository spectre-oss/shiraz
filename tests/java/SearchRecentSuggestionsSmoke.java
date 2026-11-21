// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.SearchRecentSuggestions;
import java.nio.file.Files;
import java.util.concurrent.*;
public final class SearchRecentSuggestionsSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static class Provider extends ContentProvider {
        SQLiteDatabase db;volatile CountDownLatch completed=new CountDownLatch(1);final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);volatile boolean block=true;
        Provider(SQLiteDatabase database){db=database;}
        public boolean onCreate(){return true;}
        public String getType(Uri uri){return null;}
        public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){return db.query("suggestions",projection,selection,args,null,null,order);}
        public Uri insert(Uri uri,ContentValues values){if(block){entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("blocked insert timeout");}catch(InterruptedException e){throw new AssertionError(e);}block=false;}check(db.insertWithOnConflict("suggestions",null,values,SQLiteDatabase.CONFLICT_REPLACE)>0,"insert failed");return uri;}
        public int delete(Uri uri,String selection,String[] args){int result=db.delete("suggestions",selection,args);completed.countDown();return result;}
        public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    }
    static int count(SQLiteDatabase db){try(Cursor rows=db.rawQuery("SELECT COUNT(*) FROM suggestions",null)){rows.moveToFirst();return rows.getInt(0);}}
    public static void main(String[] args)throws Exception{
        java.io.File file=Files.createTempFile("search-history",".db").toFile();SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(file,null);
        db.execSQL("CREATE TABLE suggestions (_id INTEGER PRIMARY KEY, date INTEGER, query TEXT UNIQUE, display1 TEXT, display2 TEXT)");
        Context context=new ContextWrapper(null){final ContentResolver resolver=new ContentResolver(this);public ContentResolver getContentResolver(){return resolver;}};
        Provider provider=new Provider(db);ProviderInfo info=new ProviderInfo();info.authority="fixture.search";provider.attachInfo(context,info);
        SearchRecentSuggestions history=new SearchRecentSuggestions(context,info.authority,3);
        history.saveRecentQuery("hello","second");check(provider.entered.await(5,TimeUnit.SECONDS),"background worker absent");check(count(db)==0,"save waited for provider");provider.release.countDown();check(provider.completed.await(5,TimeUnit.SECONDS),"save incomplete");
        try(Cursor rows=db.rawQuery("SELECT query,display1,display2,date FROM suggestions",null)){check(rows.moveToFirst()&&"hello".equals(rows.getString(0))&&"hello".equals(rows.getString(1))&&"second".equals(rows.getString(2))&&rows.getLong(3)>0,"stored payload");}
        for(int i=0;i<260;i++)db.execSQL("INSERT INTO suggestions(date,query,display1) VALUES(?,?,?)",new Object[]{(long)i,"old"+i,"old"+i});
        provider.completed=new CountDownLatch(1);history.saveRecentQuery("new",null);check(provider.completed.await(5,TimeUnit.SECONDS)&&count(db)==250,"bounded history");
        db.close();db=SQLiteDatabase.openOrCreateDatabase(file,null);provider.db=db;check(count(db)==250,"history not persisted");history.clearHistory();check(count(db)==0,"clear failed");
        SearchRecentSuggestions one=new SearchRecentSuggestions(context,info.authority,1);
        try{one.saveRecentQuery("query","invalid");throw new AssertionError("invalid second line accepted");}catch(IllegalArgumentException expected){}
        one.saveRecentQuery("",null);check(count(db)==0,"empty search stored");db.close();file.delete();System.out.println("SEARCH_RECENT_OK");
    }
}
