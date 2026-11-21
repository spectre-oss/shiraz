// SPDX-License-Identifier: GPL-3.0-only
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import shiraz.runtime.ServiceBrokerClient;
public final class JobReplySmoke {
 static Method parser,queryParser;
 static android.app.job.JobInfo query(String text)throws Exception{
  try{return (android.app.job.JobInfo)queryParser.invoke(null,text.getBytes(StandardCharsets.UTF_8),"test.jobs",17);}catch(InvocationTargetException error){throw (Exception)error.getCause();}
 }
 static void rejectQuery(String text)throws Exception{try{query(text);throw new AssertionError("Malformed query accepted");}catch(java.io.IOException|IllegalArgumentException expected){}}

 static Object parse(String text,String op)throws Exception{
  try{return parser.invoke(null,text.getBytes(StandardCharsets.UTF_8),op);}catch(InvocationTargetException error){throw (Exception)error.getCause();}
 }
 static void reject(String text,String op)throws Exception{try{parse(text,op);throw new AssertionError("Malformed reply accepted: "+text);}catch(java.io.IOException expected){}}
 public static void main(String[] args)throws Exception{
  parser=ServiceBrokerClient.class.getDeclaredMethod("parseJobReply",byte[].class,String.class);parser.setAccessible(true);
  String revision="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  ServiceBrokerClient.JobMutation result=(ServiceBrokerClient.JobMutation)parse("{\"ok\":true,\"revision\":\""+revision+"\",\"cleanup_pending\":true}","schedule");
  if(!result.cleanupPending||!revision.equals(result.revision))throw new AssertionError("Committed pending cleanup");
  int[] ids=(int[])parse("{\"ok\":true,\"ids\":[-2147483648,2147483647]}","list");
  if(ids.length!=2||ids[0]!=Integer.MIN_VALUE||ids[1]!=Integer.MAX_VALUE)throw new AssertionError("IDs");
  try{parse("{\"ok\":false,\"error\":\"denied\"}","cancel");throw new AssertionError("Denial accepted");}catch(SecurityException expected){}
  for(String text:new String[]{"{}","{\"ok\":true}","{\"ok\":true,\"ids\":[1,1]}","{\"ok\":true,\"ids\":[1.0]}","{\"ok\":true,\"ids\":[2147483648]}","{\"ok\":true,\"ids\":[\"1\"]}","{\"ok\":\"true\",\"ids\":[]}","{\"ok\":true,\"ids\":[],\"ok\":true}","{\"ok\":false,\"ids\":[]}","{\"ok\":false,\"error\":\"x\",\"ids\":[]}","{\"ok\":true,\"ids\":[]}{}","{\"ok\":true,\"ids\":[],\"unknown\":0}"})reject(text,"list");
  reject("{\"ok\":true,\"revision\":\"bad\",\"cleanup_pending\":false}","schedule");
  reject("{\"ok\":true,\"removed\":true,\"cleanup_pending\":null}","cancel");
  reject("{\"ok\":true,\"removed\":true,\"cleanup_pending\":false}","schedule");
  StringBuilder many=new StringBuilder("{\"ok\":true,\"ids\":[");for(int i=0;i<101;i++){if(i>0)many.append(',');many.append(i);}reject(many+"]}","list");
  queryParser=shiraz.runtime.JobDefinition.class.getDeclaredMethod("decodeQueryReply",byte[].class,String.class,int.class);queryParser.setAccessible(true);
  String definition=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("tests/fixtures/job-definition-v1.json")),StandardCharsets.UTF_8);
  String reply="{\"ok\":true,\"definition\":"+definition+"}";
  android.app.job.JobInfo recovered=query(reply);
  if(recovered.getId()!=17||recovered.getExtras().getLong("long")!=Long.MAX_VALUE)throw new AssertionError("Query precision");
  if(query("{\"ok\":true,\"definition\":null}")!=null)throw new AssertionError("Absent job");
  rejectQuery(reply.replace("\"id\":17","\"id\":18"));
  if(query(reply.replace("\"persisted\":true","\"persisted\":false")).isPersisted())throw new AssertionError("Transient flag lost");
  rejectQuery(reply.replace("\"ok\":true","\"ok\":true,\"ok\":true"));
  rejectQuery(reply.replace("\"ok\":true","\"ok\":true,\"error\":\"x\""));
  rejectQuery(reply+"{}");rejectQuery("{\"ok\":true}");rejectQuery("{\"ok\":false,\"definition\":null}");
  rejectQuery(reply.replace("9223372036854775807","9223372036854775808"));
  try{query("{\"ok\":false,\"error\":\"denied\"}");throw new AssertionError("Query denial accepted");}catch(SecurityException expected){}
  System.out.println("JOB_REPLY_OK");
 }
}
