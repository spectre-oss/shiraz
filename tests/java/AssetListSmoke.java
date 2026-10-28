// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.*;
import java.util.Arrays;
public final class AssetListSmoke {
 private static void equal(AssetManager assets,String path,String... expected)throws Exception{String[] actual=assets.list(path);if(!Arrays.equals(actual,expected))throw new AssertionError(path+": "+Arrays.toString(actual));}
 public static void main(String[] args)throws Exception{
  Resources resources=Resources.shirazOpen(args[0],100,100);AssetManager assets=resources.getAssets();
  equal(assets,"","A.txt","dir","empty","file.txt","حافظ","🌍");equal(assets,"dir","a.txt","deep","z.txt");equal(assets,"dir/deep","one.bin");equal(assets,"empty");equal(assets,"missing");equal(assets,"file.txt");equal(assets,"حافظ","نام.txt");equal(assets,"🌍","earth.txt");
  String[] copy=assets.list("dir");copy[0]="changed";equal(assets,"dir","a.txt","deep","z.txt");
  equal(assets,"dir/","a.txt","deep","z.txt");equal(assets,"dir/deep/","one.bin");equal(assets,"empty/");
  try(java.io.InputStream in=assets.open("dir/a.txt")){if(in.read()!=65)throw new AssertionError("listed file can be opened");}
  for(String path:new String[]{"/","//","../dir","dir/../empty","dir/.","dir//deep","dir//","dir/../","dir\\deep","dir\u0000"}){try{assets.list(path);throw new AssertionError("unsafe path accepted: "+path);}catch(IllegalArgumentException expected){}}
  try{assets.list(null);throw new AssertionError("null accepted");}catch(NullPointerException expected){}
  assets.close();try{assets.list("");throw new AssertionError("closed manager accepted");}catch(IllegalStateException expected){}resources.shirazClose();
  Resources other=Resources.shirazOpen(args[0],100,100);AssetManager retained=other.getAssets();other.shirazClose();try{retained.list("");throw new AssertionError("closed resources accepted");}catch(IllegalStateException expected){}
  System.out.println("ASSET_LIST_ZIP_DIRECTORIES_OK");
 }
}
