// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.cert.Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import android.security.keystore.KeyGenParameterSpec;

/** App-private software keys. Does not claim hardware isolation or user authentication.
 * Returned software keys are exportable; Android per-operation authorization is unavailable. */
public final class HostKeyStoreProvider extends Provider {
    private static Path file;
    private static final Properties keys=new Properties();
    private HostKeyStoreProvider(){super("AndroidKeyStore",1.0,"Shiraz app-private software key storage");put("KeyStore.AndroidKeyStore",Store.class.getName());put("KeyGenerator.AES",AES.class.getName());}
    public static synchronized void install(File directory)throws IOException {
        Path next=new File(directory,"shiraz-keystore.properties").toPath();
        if(next.equals(file))return;
        keys.clear();if(Files.exists(next))try(InputStream in=Files.newInputStream(next)){keys.load(in);}
        file=next;if(Security.getProvider("AndroidKeyStore")==null)Security.addProvider(new HostKeyStoreProvider());
    }
    private static synchronized void persist()throws IOException {
        if(file==null)throw new IOException("No application key store");
        Files.createDirectories(file.getParent());Path tmp=Files.createTempFile(file.getParent(),".keystore-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {try(OutputStream out=Files.newOutputStream(tmp)){keys.store(out,"Shiraz software key storage");}Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}
    }
    private static String alias(String name){if(name==null)throw new NullPointerException("alias");return Base64.getEncoder().encodeToString(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static synchronized void save(String name,Key key)throws KeyStoreException {
        if(!(key instanceof SecretKey)||key.getEncoded()==null)throw new KeyStoreException("Only encodable software secret keys are supported");
        String id=alias(name),old=keys.getProperty(id),date=keys.getProperty(id+".date");
        keys.setProperty(id,key.getAlgorithm()+":"+Base64.getEncoder().encodeToString(key.getEncoded()));keys.setProperty(id+".date",Long.toString(System.currentTimeMillis()));
        try{persist();}catch(IOException e){if(old==null)keys.remove(id);else keys.setProperty(id,old);if(date==null)keys.remove(id+".date");else keys.setProperty(id+".date",date);throw new KeyStoreException("Cannot persist key",e);}
    }
    public static final class Store extends KeyStoreSpi {
        public Key engineGetKey(String name,char[] password)throws UnrecoverableKeyException {synchronized(HostKeyStoreProvider.class){String value=keys.getProperty(alias(name));if(value==null)return null;int split=value.indexOf(':');try{return new SecretKeySpec(Base64.getDecoder().decode(value.substring(split+1)),value.substring(0,split));}catch(RuntimeException e){throw new UnrecoverableKeyException("Invalid software key record");}}}
        public Certificate[] engineGetCertificateChain(String name){return null;}public Certificate engineGetCertificate(String name){return null;}
        public Date engineGetCreationDate(String name){synchronized(HostKeyStoreProvider.class){String date=keys.getProperty(alias(name)+".date");return date==null?null:new Date(Long.parseLong(date));}}
        public void engineSetKeyEntry(String name,Key key,char[] password,Certificate[] chain)throws KeyStoreException {if(chain!=null&&chain.length!=0)throw new KeyStoreException("Certificate chains unsupported");save(name,key);}
        public void engineSetKeyEntry(String name,byte[] key,Certificate[] chain)throws KeyStoreException {throw new KeyStoreException("Encoded protected key import unsupported");}
        public void engineSetCertificateEntry(String name,Certificate cert)throws KeyStoreException {throw new KeyStoreException("Certificate storage unsupported");}
        public void engineDeleteEntry(String name)throws KeyStoreException {synchronized(HostKeyStoreProvider.class){String id=alias(name);Object old=keys.remove(id),date=keys.remove(id+".date");try{persist();}catch(IOException e){if(old!=null)keys.put(id,old);if(date!=null)keys.put(id+".date",date);throw new KeyStoreException(e);}}}
        public Enumeration<String> engineAliases(){synchronized(HostKeyStoreProvider.class){ArrayList<String> names=new ArrayList<String>();for(String id:keys.stringPropertyNames())if(!id.endsWith(".date"))names.add(new String(Base64.getDecoder().decode(id),java.nio.charset.StandardCharsets.UTF_8));return Collections.enumeration(names);}}
        public boolean engineContainsAlias(String name){synchronized(HostKeyStoreProvider.class){return keys.containsKey(alias(name));}}
        public int engineSize(){return Collections.list(engineAliases()).size();}public boolean engineIsKeyEntry(String name){return engineContainsAlias(name);}public boolean engineIsCertificateEntry(String name){return false;}public String engineGetCertificateAlias(Certificate cert){return null;}
        public void engineStore(OutputStream out,char[] password)throws IOException {throw new IOException("App key store cannot be exported");}
        public void engineLoad(InputStream in,char[] password)throws IOException {if(in!=null||password!=null)throw new IOException("App key store requires null stream and password");if(file==null)throw new IOException("No application key store");}
    }
    public static final class AES extends KeyGeneratorSpi {
        private KeyGenParameterSpec spec;private SecureRandom random;
        protected void engineInit(SecureRandom r){throw new InvalidParameterException("KeyGenParameterSpec required");}
        protected void engineInit(int size,SecureRandom r){throw new InvalidParameterException("KeyGenParameterSpec required");}
        protected void engineInit(AlgorithmParameterSpec params,SecureRandom r)throws InvalidAlgorithmParameterException {
            spec=null;
            if(!(params instanceof KeyGenParameterSpec))throw new InvalidAlgorithmParameterException("KeyGenParameterSpec required");KeyGenParameterSpec s=(KeyGenParameterSpec)params;
            if(s.isStrongBoxBacked()||s.isUserAuthenticationRequired()||s.getAttestationChallenge()!=null||s.getKeyValidityStart()!=null||s.getKeyValidityForOriginationEnd()!=null)throw new InvalidAlgorithmParameterException("Hardware, authentication, attestation and validity policies unavailable in software key store");
            int size=s.getKeySize();if(size!=-1&&size!=128&&size!=192&&size!=256)throw new InvalidAlgorithmParameterException("Invalid AES key size");spec=s;random=r;
        }
        protected SecretKey engineGenerateKey(){if(spec==null)throw new IllegalStateException("Generator not initialized");try{
            KeyGenerator generator=null;for(Provider p:Security.getProviders())if(!p.getName().equals("AndroidKeyStore")){try{generator=KeyGenerator.getInstance("AES",p);break;}catch(NoSuchAlgorithmException ignored){}}
            if(generator==null)throw new NoSuchAlgorithmException("No software AES generator");generator.init(spec.getKeySize()==-1?256:spec.getKeySize(),random==null?new SecureRandom():random);SecretKey key=generator.generateKey();save(spec.getKeystoreAlias(),key);return key;
        }catch(GeneralSecurityException e){throw new ProviderException("Cannot generate software key",e);}}
    }
}
