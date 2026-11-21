// SPDX-License-Identifier: GPL-3.0-only
import android.app.job.JobInfo;
import android.os.PersistableBundle;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import shiraz.runtime.JobDefinition;

public final class JobDefinitionSmoke {
 static String fixture;
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static JobInfo decode(String text)throws Exception{return JobDefinition.decode(text.getBytes(StandardCharsets.UTF_8),"test.jobs","test.jobs.Worker");}
 static void reject(String text)throws Exception{try{decode(text);throw new AssertionError("malformed definition accepted");}catch(IllegalArgumentException|java.io.IOException expected){}}
 static String minimal(String extras){return "{\"version\":1,\"id\":1,\"component\":\"test.jobs.Worker\",\"persisted\":true,\"minimum_latency_ms\":0,\"deadline_ms\":null,\"interval_ms\":0,\"flex_ms\":0,\"initial_backoff_ms\":30000,\"backoff_policy\":1,\"extras\":"+extras+"}";}
 static JobInfo build(PersistableBundle extras){return new JobInfo.Builder(17,new android.content.ComponentName("test.jobs","test.jobs.Worker")).setPersisted(true).setExtras(extras).build();}
 static void rejectEncode(PersistableBundle extras)throws Exception{try{JobDefinition.encode(build(extras),"test.jobs");throw new AssertionError("encode accepted invalid extras");}catch(IllegalArgumentException expected){}}
 public static void main(String[] args)throws Exception{
  fixture=new String(Files.readAllBytes(Paths.get("tests/fixtures/job-definition-v1.json")),StandardCharsets.UTF_8);
  JobInfo job=decode(fixture);PersistableBundle b=job.getExtras();
  check(job.getId()==17&&job.isPersisted()&&!job.shirazSupported()&&job.shirazExecutionSupported(),"identity/persistence");
  check(b.getInt("int")==Integer.MIN_VALUE&&b.getLong("long")==Long.MAX_VALUE,"integer precision");
  check(b.getDouble("double")==1.25&&b.getBoolean("boolean")&&"سلام😀".equals(b.getString("string")),"scalars");
  check(b.containsKey("null")&&b.getString("null")==null&&b.containsKey("bundle-null")&&b.getPersistableBundle("bundle-null")==null,"nulls");
  check(b.getBooleanArray("boolean-array")[0]&&!b.getBooleanArray("boolean-array")[1],"boolean array");
  check(b.getIntArray("int-array")[1]==Integer.MAX_VALUE&&b.getLongArray("long-array")[0]==Long.MIN_VALUE,"integer arrays");
  check(b.getDoubleArray("double-array")[0]==-1.25&&b.getStringArray("string-array")[1]==null,"other arrays");
  for(String kind:new String[]{"boolean","int","long","double","string"})check(b.containsKey(kind+"-null")&&b.get(kind+"-null")==null,"null array");
  check(b.getPersistableBundle("nested").getStringArray("text")[1]==null,"nested null");
  b.getLongArray("long-array")[0]=0;check(job.getExtras().getLongArray("long-array")[0]==Long.MIN_VALUE,"mutable decode snapshot");
  for(String[] change:new String[][]{{"\"version\":1","\"version\":2"},{"\"id\":17","\"id\":17.0"},{"\"id\":17","\"id\":\"17\""},{"\"id\":17","\"id\":true"},{"\"persisted\":true","\"persisted\":1"},{"9223372036854775807","9223372036854775808"},{"1.25","1e999"},{"test.jobs.Worker","test.other.Worker"},{"\"flex_ms\":0","\"flex_ms\":1"},{"\"initial_backoff_ms\":30000","\"initial_backoff_ms\":9999"},{"\"backoff_policy\":1","\"backoff_policy\":2"}})reject(fixture.replace(change[0],change[1]));
  reject(fixture.replace("\"version\":1","\"version\":1,\"version\":1"));
  reject(fixture.replace("\"version\":1","\"uid\":10000,\"version\":1"));
  reject(fixture+"{}");reject(minimal("{\"x\":{\"type\":\"string\",\"value\":\"\\ud800\"}}"));
  reject(minimal("{\"x\":{\"type\":\"parcelable\",\"value\":null}}"));
  reject(minimal("{\"x\":{\"type\":\"int\",\"value\":null}}"));
  reject(minimal("{\"x\":{\"type\":\"string\",\"value\":null,\"value\":null}}"));
  String nested="{}";for(int i=0;i<8;i++)nested="{\"x\":{\"type\":\"bundle\",\"value\":"+nested+"}}";
  decode(minimal(nested));reject(minimal("{\"x\":{\"type\":\"bundle\",\"value\":"+nested+"}}"));
  StringBuilder array=new StringBuilder("[");for(int i=0;i<1024;i++){if(i>0)array.append(',');array.append('0');}array.append(']');
  decode(minimal("{\"x\":{\"type\":\"int[]\",\"value\":"+array+"}}"));
  reject(minimal("{\"x\":{\"type\":\"int[]\",\"value\":"+array.toString().replace("]",",0]")+"}}"));
  StringBuilder total=new StringBuilder("{");
  for(int i=0;i<4;i++){if(i>0)total.append(',');total.append('"').append(i).append("\":{\"type\":\"int[]\",\"value\":").append(array).append('}');}total.append('}');
  reject(minimal(total.toString())); // Four arrays plus their nodes exceed 4096.
  decode(minimal(total.toString().replace(",0]","]"))); // Exactly 4096 semantic elements.
  reject(minimal("{\"x\":{\"type\":\"unknown[]\",\"value\":null}}"));
  String periodic=minimal("{}").replace("\"interval_ms\":0","\"interval_ms\":900000").replace("\"flex_ms\":0","\"flex_ms\":300000");
  check(decode(periodic).isPeriodic(),"periodic");reject(periodic.replace("\"deadline_ms\":null","\"deadline_ms\":0"));
  try{JobDefinition.decode(new byte[]{(byte)0xc3,0x28},"test.jobs","test.jobs.Worker");throw new AssertionError("invalid UTF8");}catch(java.io.IOException expected){}
  try{JobDefinition.decode(new byte[32769],"test.jobs","test.jobs.Worker");throw new AssertionError("size limit");}catch(IllegalArgumentException expected){}
  byte[] encoded=JobDefinition.encode(job,"test.jobs");
  JobInfo round=JobDefinition.decode(encoded,"test.jobs","test.jobs.Worker");
  check(round.getExtras().getLong("long")==Long.MAX_VALUE&&round.getExtras().getLongArray("long-array")[0]==Long.MIN_VALUE,"encode precision");
  check(round.getExtras().getPersistableBundle("nested").getStringArray("text")[1]==null,"encode nested values");
  for(String key:job.getExtras().keySet())check(round.getExtras().containsKey(key),"encode preserved key");
  check(java.util.Arrays.equals(encoded,JobDefinition.encode(round,"test.jobs")),"stable normalized round trip");
  Files.write(Paths.get("build/job-definition-encoded.json"),encoded);
  JobInfo storage=new JobInfo.Builder(17,job.getService()).setExtras(job.getExtras()).setRequiresStorageNotLow(true).build();
  byte[] storageBytes=JobDefinition.encode(storage,"test.jobs");
  JobInfo storageRound=JobDefinition.decode(storageBytes,"test.jobs","test.jobs.Worker");
  check(storageRound.isRequireStorageNotLow()&&!storageRound.shirazExecutionSupported(),"storage data must not authorize execution");
  check(storageRound.getExtras().getLong("long")==Long.MAX_VALUE,"storage extras precision");
  check(java.util.Arrays.equals(storageBytes,JobDefinition.encode(storageRound,"test.jobs")),"storage stable round trip");
  Files.write(Paths.get("build/job-definition-storage-encoded.json"),storageBytes);
  String storageText=new String(storageBytes,StandardCharsets.UTF_8);
  for(String bad:new String[]{"null","1","\"true\""})reject(storageText.replace("\"requires_storage_not_low\":true","\"requires_storage_not_low\":"+bad));
  reject(storageText.replace("\"version\":2","\"version\":1"));
  check(!decode(storageText.replace("\"requires_storage_not_low\":true","\"requires_storage_not_low\":false")).isRequireStorageNotLow(),"explicit false storage");
  JobInfo deadline=new JobInfo.Builder(1,job.getService()).setOverrideDeadline(Long.MAX_VALUE).build();
  check(JobDefinition.decode(JobDefinition.encode(deadline,"test.jobs"),"test.jobs","test.jobs.Worker").shirazHasDeadline(),"explicit maximum deadline lost");
  check(!round.shirazHasDeadline(),"absent deadline became explicit");
  JobInfo periodicJob=decode(periodic);check(JobDefinition.decode(JobDefinition.encode(periodicJob,"test.jobs"),"test.jobs","test.jobs.Worker").getFlexMillis()==300000,"encode periodic flex");
  try{JobDefinition.encode(job,"test.other");throw new AssertionError("foreign package encoded");}catch(IllegalArgumentException expected){}
  try{JobDefinition.encode(new JobInfo.Builder(1,job.getService()).setRequiredNetworkType(1).build(),"test.jobs");throw new AssertionError("constraint silently dropped");}catch(UnsupportedOperationException expected){}
  PersistableBundle invalid=new PersistableBundle();invalid.putDouble("n",Double.NaN);rejectEncode(invalid);
  invalid.putDouble("n",Double.POSITIVE_INFINITY);rejectEncode(invalid);
  invalid.clear();invalid.putString("x","\ud800");rejectEncode(invalid);
  invalid.clear();invalid.putIntArray("x",new int[1025]);rejectEncode(invalid);
  invalid.clear();for(int i=0;i<4;i++)invalid.putIntArray("a"+i,new int[1024]);rejectEncode(invalid);
  invalid.clear();for(int i=0;i<1025;i++)invalid.putInt("k"+i,0);rejectEncode(invalid);
  invalid.clear();char[] big=new char[17000];java.util.Arrays.fill(big,'é');invalid.putString("x",new String(big));rejectEncode(invalid);
  invalid=new PersistableBundle();for(int i=0;i<9;i++){PersistableBundle parent=new PersistableBundle();parent.putPersistableBundle("x",invalid);invalid=parent;}rejectEncode(invalid);
  String launchDefinition=minimal("{}").replace("\"deadline_ms\":null","\"deadline_ms\":0");
  for(boolean expired:new boolean[]{false,true}){
   String launch="{\"version\":1,\"definition\":"+launchDefinition+",\"deadline_expired\":"+expired+"}";
   JobDefinition.Launch decoded=JobDefinition.decodeLaunch(launch.getBytes(StandardCharsets.UTF_8),"test.jobs","test.jobs.Worker");
   check(decoded.deadlineExpired==expired&&decoded.job.shirazHasDeadline(),"launch metadata");
  }
  String launch="{\"version\":1,\"definition\":"+launchDefinition+",\"deadline_expired\":true}";
  for(String invalidLaunch:new String[]{launch.replace("\"deadline_expired\":true","\"deadline_expired\":1"),launch.replace("\"deadline_ms\":0","\"deadline_ms\":null"),launch.replace("\"deadline_expired\":true","\"deadline_expired\":true,\"deadline_expired\":false"),launch.replace("\"deadline_expired\":true","\"deadline_expired\":true,\"uid\":10000"),minimal("{}")}){
   try{JobDefinition.decodeLaunch(invalidLaunch.getBytes(StandardCharsets.UTF_8),"test.jobs","test.jobs.Worker");throw new AssertionError("invalid launch accepted");}catch(IllegalArgumentException|java.io.IOException expected){}
  }
  System.out.println("JOB_DEFINITION_OK");
 }
}
