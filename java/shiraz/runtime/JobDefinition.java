// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.app.job.JobInfo;
import android.content.ComponentName;
import android.os.PersistableBundle;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import java.io.Writer;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded versioned host job data. Parsing conveys no scheduling authority. */
public final class JobDefinition {
 private JobDefinition() {}
 private static final String NAME="[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*";
 private static final Set<String> FIELDS=new HashSet<String>(Arrays.asList("version","id","component","persisted","minimum_latency_ms","deadline_ms","interval_ms","flex_ms","initial_backoff_ms","backoff_policy","extras"));
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid job definition");}
 private static final class NumberValue {final String text;NumberValue(String text){this.text=text;}}
 private static final class BoundedWriter extends Writer {
  final StringBuilder text=new StringBuilder();
  public void write(char[] value,int offset,int count){if(count<0||count>32768-text.length())throw invalid();text.append(value,offset,count);}
  @Override public void write(String value,int offset,int count){if(count<0||count>32768-text.length())throw invalid();text.append(value,offset,offset+count);}
  public void flush(){}public void close(){}
 }
 /** Serialize data only. The authenticated broker still supplies scheduling authority. */
 public static byte[] encode(JobInfo job,String packageName)throws IOException {
  if(job==null||packageName==null||!packageName.matches(NAME)||job.getService()==null||!packageName.equals(job.getService().getPackageName()))throw invalid();
  if(!job.shirazDefinitionSupported())throw new UnsupportedOperationException("Unsupported job execution constraints");
  String component=string(job.getService().getClassName(),1024);if(!component.matches(NAME))throw invalid();
  BoundedWriter sink=new BoundedWriter();JsonWriter writer=new JsonWriter(sink);
  writer.beginObject().name("version").value(job.isRequireStorageNotLow()?2:1).name("id").value(job.getId()).name("component").value(component)
   .name("persisted").value(job.isPersisted()).name("minimum_latency_ms").value(job.getMinLatencyMillis()).name("deadline_ms");
  if(job.shirazHasDeadline())writer.value(job.getMaxExecutionDelayMillis());else writer.nullValue();
  writer.name("interval_ms").value(job.getIntervalMillis()).name("flex_ms").value(job.getFlexMillis())
   .name("initial_backoff_ms").value(job.getInitialBackoffMillis()).name("backoff_policy").value(job.getBackoffPolicy()).name("extras");
  writeBundle(writer,job.getExtras(),0,new int[]{4096},new IdentityHashMap<PersistableBundle,Boolean>());
  if(job.isRequireStorageNotLow())writer.name("requires_storage_not_low").value(true);
  writer.endObject();writer.close();byte[] bytes=sink.text.toString().getBytes(StandardCharsets.UTF_8);
  if(bytes.length>32768)throw invalid();
  decode(bytes,packageName,component); // Keep encoder semantics aligned with the strict reader.
  return bytes;
 }
 private static void writeBundle(JsonWriter writer,PersistableBundle value,int depth,int[] budget,IdentityHashMap<PersistableBundle,Boolean> active)throws IOException {
  if(depth>8||value.size()>1024||active.put(value,Boolean.TRUE)!=null)throw invalid();
  try{
   writer.beginObject();
   for(String key:value.keySet()){
    if(--budget[0]<0)throw invalid();string(key,1024);Object item=value.get(key);
    // BaseBundle stores null without a type tag; string-null preserves its
    // public getter/containsKey semantics for nullable arrays and bundles too.
    String type=item==null||item instanceof String?"string":item instanceof Boolean?"boolean":item instanceof Integer?"int":item instanceof Long?"long":item instanceof Double?"double":item instanceof PersistableBundle?"bundle":item instanceof boolean[]?"boolean[]":item instanceof int[]?"int[]":item instanceof long[]?"long[]":item instanceof double[]?"double[]":item instanceof String[]?"string[]":null;
    if(type==null)throw invalid();writer.name(key).beginObject().name("type").value(type).name("value");
    if(item instanceof PersistableBundle)writeBundle(writer,(PersistableBundle)item,depth+1,budget,active);
    else if(type.endsWith("[]")){
     int n=java.lang.reflect.Array.getLength(item);if(n>1024||(budget[0]-=n)<0)throw invalid();
     writer.beginArray();for(int i=0;i<n;i++)writeScalar(writer,java.lang.reflect.Array.get(item,i));writer.endArray();
    }else writeScalar(writer,item);
    writer.endObject();
   }
   writer.endObject();
  }finally{active.remove(value);}
 }
 private static void writeScalar(JsonWriter writer,Object value)throws IOException {
  if(value==null)writer.nullValue();
  else if(value instanceof String)writer.value(string(value,32768));
  else if(value instanceof Boolean)writer.value((Boolean)value);
  else if(value instanceof Integer||value instanceof Long)writer.value(((Number)value).longValue());
  else if(value instanceof Double){double number=(Double)value;if(Double.isNaN(number)||Double.isInfinite(number))throw invalid();writer.value(number);}
  else throw invalid();
 }
 public static JobInfo decode(byte[] bytes,String packageName,String component)throws IOException {
  if(bytes==null||bytes.length>32768||packageName==null||!packageName.matches(NAME)||component==null||!component.matches(NAME))throw invalid();
  JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()));
  Object tree=read(reader,0,new int[]{16384});
  if(reader.peek()!=JsonToken.END_DOCUMENT)throw invalid();
  return decodeTree(tree,packageName,component);
 }
 /** Host-only invocation metadata, deliberately outside the app job schema. */
 public static final class Launch {
  public final JobInfo job;public final boolean deadlineExpired;
  private Launch(JobInfo job,boolean deadlineExpired){this.job=job;this.deadlineExpired=deadlineExpired;}
 }
 public static Launch decodeLaunch(byte[] bytes,String packageName,String component)throws IOException {
  if(bytes==null||bytes.length>65536||packageName==null||!packageName.matches(NAME)||component==null||!component.matches(NAME))throw invalid();
  try(JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()))){
   Map<String,Object> value=object(read(reader,0,new int[]{16400}));
   if(reader.peek()!=JsonToken.END_DOCUMENT||!value.keySet().equals(new HashSet<String>(Arrays.asList("version","definition","deadline_expired"))))throw invalid();
   integer(value.get("version"),1,1);boolean expired=bool(value.get("deadline_expired"));
   JobInfo job=decodeTree(value.get("definition"),packageName,component);
   encode(job,packageName); // Keep the definition's independent byte bound.
   if(expired&&!job.shirazHasDeadline())throw invalid();
   return new Launch(job,expired);
  }
 }
 /** Decode an authenticated query; the package is supplied by the local app context. */
 static JobInfo decodeQueryReply(byte[] bytes,String packageName,int id)throws IOException {
  if(bytes==null||bytes.length>65536||packageName==null||!packageName.matches(NAME))throw invalid();
  try(JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()))){
   Map<String,Object> reply=object(read(reader,0,new int[]{16392}));
   if(reader.peek()!=JsonToken.END_DOCUMENT)throw invalid();
   boolean ok=bool(reply.get("ok"));
   if(!ok){
    if(!reply.keySet().equals(new HashSet<String>(Arrays.asList("ok","error"))))throw invalid();
    throw new SecurityException("Persisted job denied: "+string(reply.get("error"),32768));
   }
   if(!reply.keySet().equals(new HashSet<String>(Arrays.asList("ok","definition"))))throw invalid();
   if(reply.get("definition")==null)return null;
   Object definition=reply.get("definition");
   JobInfo job=decodeTree(definition,packageName,string(object(definition).get("component"),1024));
   if(job.getId()!=id)throw invalid();
   encode(job,packageName); // Enforce the standalone definition byte bound too.
   return job;
  }
 }
 private static JobInfo decodeTree(Object tree,String packageName,String component){
  Map<String,Object> value=object(tree);
  long version=integer(value.get("version"),1,2);
  Set<String> fields=new HashSet<String>(FIELDS);
  if(version==2)fields.add("requires_storage_not_low");
  if(!value.keySet().equals(fields))throw invalid();
  boolean storage=version==2&&bool(value.get("requires_storage_not_low"));
  int id=(int)integer(value.get("id"),Integer.MIN_VALUE,Integer.MAX_VALUE);
  String name=string(value.get("component"),1024);
  if(!name.matches(NAME)||!name.equals(component))throw invalid();
  boolean persisted=bool(value.get("persisted"));
  long latency=integer(value.get("minimum_latency_ms"),0,Long.MAX_VALUE);
  Long deadline=value.get("deadline_ms")==null?null:integer(value.get("deadline_ms"),0,Long.MAX_VALUE);
  long interval=integer(value.get("interval_ms"),0,Long.MAX_VALUE),flex=integer(value.get("flex_ms"),0,Long.MAX_VALUE);
  long backoff=integer(value.get("initial_backoff_ms"),10000,Long.MAX_VALUE);
  int policy=(int)integer(value.get("backoff_policy"),0,1);
  if(interval!=0){if(interval<900000||flex<Math.max(300000,interval/20)||flex>interval||latency!=0||deadline!=null)throw invalid();}
  else if(flex!=0)throw invalid();
  PersistableBundle extras=bundle(value.get("extras"),0,new int[]{4096});
  JobInfo.Builder builder=new JobInfo.Builder(id,new ComponentName(packageName,name)).setPersisted(persisted).setRequiresStorageNotLow(storage).setExtras(extras).setBackoffCriteria(backoff,policy);
  if(interval!=0)builder.setPeriodic(interval,flex);else{builder.setMinimumLatency(latency);if(deadline!=null)builder.setOverrideDeadline(deadline);}
  return builder.build();
 }
 private static Object read(JsonReader reader,int depth,int[] budget)throws IOException {
  if(depth>24||--budget[0]<0)throw invalid();
  switch(reader.peek()){
   case BEGIN_OBJECT:
    Map<String,Object> map=new LinkedHashMap<String,Object>();reader.beginObject();
    while(reader.hasNext()){String key=string(reader.nextName(),1024);if(map.containsKey(key)||map.size()>=1024)throw invalid();map.put(key,read(reader,depth+1,budget));}reader.endObject();return map;
   case BEGIN_ARRAY:
    List<Object> list=new ArrayList<Object>();reader.beginArray();while(reader.hasNext()){if(list.size()>=1024)throw invalid();list.add(read(reader,depth+1,budget));}reader.endArray();return list;
   case STRING:return string(reader.nextString(),32768);
   case NUMBER:return new NumberValue(reader.nextString());
   case BOOLEAN:return reader.nextBoolean();
   case NULL:reader.nextNull();return null;
   default:throw invalid();
  }
 }
 @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value){if(!(value instanceof Map))throw invalid();return (Map<String,Object>)value;}
 private static String string(Object value,int limit){
  if(!(value instanceof String))throw invalid();String text=(String)value;if(text.codePointCount(0,text.length())>limit)throw invalid();
  for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw invalid();}else if(Character.isLowSurrogate(c))throw invalid();}
  return text;
 }
 private static boolean bool(Object value){if(!(value instanceof Boolean))throw invalid();return (Boolean)value;}
 private static long integer(Object value,long low,long high){
  if(!(value instanceof NumberValue)||!((NumberValue)value).text.matches("-?(0|[1-9][0-9]*)"))throw invalid();
  long result;try{result=Long.parseLong(((NumberValue)value).text);}catch(NumberFormatException error){throw invalid();}
  if(result<low||result>high)throw invalid();return result;
 }
 private static Object scalar(String type,Object value){
  if(type.equals("boolean"))return bool(value);
  if(type.equals("int"))return (int)integer(value,Integer.MIN_VALUE,Integer.MAX_VALUE);
  if(type.equals("long"))return integer(value,Long.MIN_VALUE,Long.MAX_VALUE);
  if(type.equals("string"))return value==null?null:string(value,32768);
  if(type.equals("double")){if(!(value instanceof NumberValue))throw invalid();double result=Double.parseDouble(((NumberValue)value).text);if(Double.isNaN(result)||Double.isInfinite(result))throw invalid();return result;}
  throw invalid();
 }
 private static PersistableBundle bundle(Object value,int depth,int[] budget){
  if(depth>8)throw invalid();Map<String,Object> map=object(value);PersistableBundle out=new PersistableBundle();
  for(Map.Entry<String,Object> entry:map.entrySet()){
   if(--budget[0]<0)throw invalid();String key=entry.getKey();Map<String,Object> node=object(entry.getValue());
   if(node.size()!=2||!node.containsKey("type")||!node.containsKey("value"))throw invalid();
   String type=string(node.get("type"),16);Object item=node.get("value");
   if(type.equals("bundle")){out.putPersistableBundle(key,item==null?null:bundle(item,depth+1,budget));continue;}
   if(type.endsWith("[]")){
    String base=type.substring(0,type.length()-2);
    if(!Arrays.asList("boolean","int","long","double","string").contains(base))throw invalid();
    if(item!=null&&!(item instanceof List))throw invalid();List<?> list=(List<?>)item;
    int n=list==null?0:list.size();budget[0]-=n;if(budget[0]<0)throw invalid();
    if(base.equals("boolean")){boolean[] a=list==null?null:new boolean[n];for(int i=0;i<n;i++)a[i]=(Boolean)scalar(base,list.get(i));out.putBooleanArray(key,a);}
    else if(base.equals("int")){int[] a=list==null?null:new int[n];for(int i=0;i<n;i++)a[i]=(Integer)scalar(base,list.get(i));out.putIntArray(key,a);}
    else if(base.equals("long")){long[] a=list==null?null:new long[n];for(int i=0;i<n;i++)a[i]=(Long)scalar(base,list.get(i));out.putLongArray(key,a);}
    else if(base.equals("double")){double[] a=list==null?null:new double[n];for(int i=0;i<n;i++)a[i]=(Double)scalar(base,list.get(i));out.putDoubleArray(key,a);}
    else{String[] a=list==null?null:new String[n];for(int i=0;i<n;i++)a[i]=(String)scalar(base,list.get(i));out.putStringArray(key,a);}
   }else{
    Object decoded=scalar(type,item);
    if(type.equals("boolean"))out.putBoolean(key,(Boolean)decoded);else if(type.equals("int"))out.putInt(key,(Integer)decoded);
    else if(type.equals("long"))out.putLong(key,(Long)decoded);else if(type.equals("double"))out.putDouble(key,(Double)decoded);else out.putString(key,(String)decoded);
   }
  }
  return out;
 }
}
