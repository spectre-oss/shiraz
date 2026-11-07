// SPDX-License-Identifier: GPL-3.0-only
import java.util.Arrays;
/**
 * Loads a library by absolute path (System.load, dlopen RTLD_GLOBAL in OpenJDK),
 * then runs another class's main. The macOS make rule loads ANGLE's
 * libGLESv2.dylib this way before GlesWindowFramebufferSmoke, standing in for
 * the Linux rule's LD_PRELOAD=libGLESv2.so.2 without DYLD_INSERT_LIBRARIES.
 */
public final class PreloadMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("usage: PreloadMain /abs/library main-class [args...]");
        System.load(args[0]);
        System.out.println("SHIRAZ_PRELOADED " + args[0]);
        Class.forName(args[1]).getMethod("main", String[].class).invoke(null, (Object) Arrays.copyOfRange(args, 2, args.length));
    }
}
