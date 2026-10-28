import android.graphics.FontFamily;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
public final class FontRegistrationSmoke {
 public static void main(String[] args)throws Exception{
  byte[] bytes=Files.readAllBytes(Paths.get(args[0]));
  java.lang.reflect.Field field=FontFamily.class.getDeclaredField("id");field.setAccessible(true);int first=0;
  for(int i=0;i<100;i++){
   FontFamily family=new FontFamily();
   if(!family.addFontFromBuffer(ByteBuffer.wrap(bytes),0,null,400,0)||!family.freeze())throw new AssertionError("registration "+i);
   int id=field.getInt(family);if(i==0)first=id;else if(id!=first)throw new AssertionError("duplicate font id");
  }
  System.out.println("FONT_REGISTRATION_DEDUP_OK");
 }
}
