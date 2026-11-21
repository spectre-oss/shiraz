// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.accounts.AuthenticatorDescription;
/** Account discovery uses host enrollment; it grants no account or token access. */
public final class HostAccounts {
 private HostAccounts(){}
 public static ServiceBrokerClient.AccountWatch watch()throws java.io.IOException{
  ServiceBrokerClient broker=RuntimeBroker.client();if(broker==null)throw new UnsupportedOperationException("Account watches require a registered host session");
  return broker.watchAccounts();
 }
 public static ServiceBrokerClient.AccountSnapshot snapshot()throws java.io.IOException{
  ServiceBrokerClient broker=RuntimeBroker.client();if(broker==null)throw new UnsupportedOperationException("Account snapshots require a registered host session");
  return broker.accountSnapshot();
 }
 public static boolean registered(){return RuntimeBroker.client()!=null;}
 public static Object operation(String action,android.accounts.Account account,String key,String value,android.os.Bundle extras){
  ServiceBrokerClient broker=RuntimeBroker.client();if(broker==null)throw new IllegalStateException("Account broker unavailable");
  try{return broker.accountOperation(action,account,key,value,extras);}catch(java.io.IOException error){throw new IllegalStateException("Account operation failed",error);}
 }
 public static ServiceBrokerClient.TokenPreparation prepareToken(android.accounts.Account account,String type)throws java.io.IOException{
  ServiceBrokerClient broker=RuntimeBroker.client();if(broker==null)throw new UnsupportedOperationException("Token preparation requires a registered host session");
  return broker.prepareToken(account,type);
 }
 public static boolean commitToken(android.accounts.Account account,String type,String token,ServiceBrokerClient.TokenPreparation expected)throws java.io.IOException{
  ServiceBrokerClient broker=RuntimeBroker.client();if(broker==null)throw new UnsupportedOperationException("Token commit requires a registered host session");
  return broker.commitToken(account,type,token,expected);
 }
 public static ServiceBrokerClient.TokenPreparation prepareCustomToken(android.accounts.Account account,String type)throws java.io.IOException{
  ServiceBrokerClient broker=RuntimeBroker.client();if(broker==null)throw new UnsupportedOperationException("Token preparation requires a registered host session");
  return broker.prepareCustomToken(account,type);
 }
 public static boolean commitCustomToken(android.accounts.Account account,String type,String token,ServiceBrokerClient.TokenPreparation expected,long expiry)throws java.io.IOException{
  ServiceBrokerClient broker=RuntimeBroker.client();if(broker==null)throw new UnsupportedOperationException("Token commit requires a registered host session");
  return broker.commitCustomToken(account,type,token,expected,expiry);
 }
 public static ServiceBrokerClient.Connection open(String accountType)throws java.io.IOException{
  return open(accountType,false);
 }
 public static ServiceBrokerClient.Connection open(String accountType,boolean addOnly)throws java.io.IOException{
  ServiceBrokerClient broker=RuntimeBroker.client();if(broker==null)throw new UnsupportedOperationException("Authenticator admission requires a registered host session");
  return broker.openAuthenticator(addOnly?broker.resolveAddAccount(accountType):broker.resolveAuthenticator(accountType));
 }
 public static ServiceBrokerClient.AuthenticatorResolution resolve(String accountType){
  ServiceBrokerClient broker=RuntimeBroker.client();if(broker==null)throw new UnsupportedOperationException("Authenticator resolution requires a registered host session");
  try{return broker.resolveAuthenticator(accountType);}catch(java.io.IOException error){throw new IllegalStateException("Authenticator resolution failed",error);}
 }
 public static AuthenticatorDescription[] authenticators(){
  ServiceBrokerClient broker=RuntimeBroker.client();
  if(broker==null)throw new UnsupportedOperationException("Authenticator discovery requires a registered host session");
  try{return broker.listAuthenticators();}catch(java.io.IOException error){throw new IllegalStateException("Authenticator discovery failed",error);}
 }
}
