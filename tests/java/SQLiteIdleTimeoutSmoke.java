// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.database.sqlite.*;
import java.io.File;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicLong;
public final class SQLiteIdleTimeoutSmoke {
 static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 static long nativeHandle(SQLiteDatabase db)throws Exception{java.lang.reflect.Field f=SQLiteDatabase.class.getDeclaredField("handle");f.setAccessible(true);return ((AtomicLong)f.get(db)).get();}
 static void idle(SQLiteDatabase db)throws Exception{long deadline=System.nanoTime()+3000000000L;while(nativeHandle(db)!=0&&System.nanoTime()<deadline)Thread.sleep(20);require(nativeHandle(db)==0,"Native connection did not become idle");require(db.isOpen(),"Logical database closed");}
 static long scalar(SQLiteDatabase db,String sql){try(SQLiteStatement s=db.compileStatement(sql)){return s.simpleQueryForLong();}}
 static final class Helper extends SQLiteOpenHelper{
  int opens,creates;
  Helper(Context context,String name){super(context,name,null,1);}
  public void onCreate(SQLiteDatabase db){creates++;db.execSQL("CREATE TABLE data(value INTEGER)");db.execSQL("INSERT INTO data VALUES(7)");}
  public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
  public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
  public void onOpen(SQLiteDatabase db){opens++;}
 }
 public static void main(String[] args)throws Exception{
  final Path root=Files.createTempDirectory("shiraz-sqlite-idle-");
  Context context=new ContextWrapper(null){public File getDatabasePath(String name){return root.resolve(name).toFile();}};
  try{
   try(Helper helper=new Helper(context,"test.db")){
    try{helper.setIdleConnectionTimeout(-1);throw new AssertionError("Negative timeout accepted");}catch(IllegalArgumentException expected){}
    helper.setIdleConnectionTimeout(30);helper.setWriteAheadLoggingEnabled(true);
    SQLiteDatabase db=helper.getWritableDatabase();idle(db);
    require(helper.getWritableDatabase()==db,"Helper identity changed");
    require(scalar(db,"SELECT value FROM data")==7,"Reopen lost data");
    require(scalar(db,"PRAGMA foreign_keys")==1,"Reopen lost configured foreign keys");
    require(helper.opens==1&&helper.creates==1,"Reopen reran helper callbacks");
    try{helper.setIdleConnectionTimeout(40);throw new AssertionError("Open timeout change accepted");}catch(IllegalStateException expected){}
    try(SQLiteStatement held=db.compileStatement("SELECT value FROM data")){
     Thread.sleep(130);require(nativeHandle(db)!=0,"Idle closed outstanding statement");require(held.simpleQueryForLong()==7,"Statement invalidated");
    }
    idle(db);db.beginTransaction();
    try{db.execSQL("INSERT INTO data VALUES(8)");Thread.sleep(130);require(nativeHandle(db)!=0,"Idle closed transaction");db.setTransactionSuccessful();}finally{db.endTransaction();}
    idle(db);require(scalar(db,"SELECT count(*) FROM data")==2,"Transaction not durable");
    db.execSQL("BEGIN");Thread.sleep(130);require(nativeHandle(db)!=0,"Idle closed manual transaction");db.execSQL("ROLLBACK");idle(db);
    helper.close();require(!db.isOpen(),"Explicit close ignored");
    try{scalar(db,"SELECT 1");throw new AssertionError("Closed DB reopened");}catch(IllegalStateException expected){}
    helper.setIdleConnectionTimeout(Long.MAX_VALUE);SQLiteDatabase reopened=helper.getWritableDatabase();Thread.sleep(130);require(nativeHandle(reopened)!=0,"Unlimited timeout closed connection");
   }
   try(Helper memory=new Helper(context,null)){memory.setIdleConnectionTimeout(0);SQLiteDatabase db=memory.getWritableDatabase();Thread.sleep(130);require(scalar(db,"SELECT value FROM data")==7,"In-memory DB lost");}
   System.out.println("SQLITE_IDLE_TIMEOUT_OK");
  }finally{SQLiteDatabase.shirazShutdown();for(File file:root.toFile().listFiles())Files.delete(file.toPath());Files.delete(root);}
 }
}
