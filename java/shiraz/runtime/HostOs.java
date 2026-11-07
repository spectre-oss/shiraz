// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
/** Host kernel identity. ART reports os.name=Linux on every host, so this reads uname(2). */
public final class HostOs {
    private HostOs(){}
    private static final class Kernel {static final boolean DARWIN=detect();}
    // libcore.io.Linux natives exist only under ART. On a host JVM (the OpenJDK
    // lanes of the Linux smokes) Os.uname() throws UnsatisfiedLinkError, and there
    // os.name is accurate ("Linux", "Mac OS X"). Catch inside the computation so a
    // failed probe never poisons this class with NoClassDefFoundError.
    private static boolean detect(){
        try{return "Darwin".equals(android.system.Os.uname().sysname);}
        catch(LinkageError hostJvm){
            String name=System.getProperty("os.name","");
            return name.startsWith("Mac OS")||name.equals("Darwin");
        }
    }
    /** True on macOS (XNU), where /proc, memfd and Linux-only paths do not exist. */
    public static boolean darwin(){return Kernel.DARWIN;}
}
