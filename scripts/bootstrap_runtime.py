#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build pinned host dependencies in-tree; no root install or Android image."""
import argparse
import fcntl
import hashlib
import json
import os
import platform
import shutil
from pathlib import Path
import subprocess
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PREFIX = ROOT / '.local'
SOURCES = {
    'espeak-ng': ('https://github.com/espeak-ng/espeak-ng.git', '4870adfa25b1a32b4361592f1be8a40337c58d6c'),
    'wolfssl': ('https://github.com/wolfSSL/wolfssl.git', '3c5eead44904df64e6a5a1f4ebdce377d35a849a'),
    'art_standalone': ('https://gitlab.com/android_translation_layer/art_standalone.git', 'a1edfc40e8cea3644d80e4e776d3fa7fc397a1c2'),
    'bionic_translation': ('https://gitlab.com/android_translation_layer/bionic_translation.git', '6253dec7f24a57a94039a088cb436d880e92e3be'),
}
# Narrow host-port fixes; original AOSP license notices remain in dependency sources.
PATCHES = {
    'bionic_translation': [
        ('bionic-elf-exports.patch', {
            'meson.build': '5d51bbd2bfe76b9078c05e4bc32ebc41b17372cce24ab9f4dca2a561ea887fba',
            'libc/libc-elf-exports.c': '69b2058bee99a587431a565755fd66b242a1a81b3b82079e77abaf1b22e74b0b',
            'libc/guest-exports.map': '2c5d8c95c283e0e38ff74500962386fc8d3ae5f6c3ce24d2a0d181dfe239f0d7',
        }),
        ('bionic-dladdr-path.patch', {
            'linker/dlfcn.c': '86372bbb7474dd14aef781e948a20f8a51afd405cb02fe5b436fb32d3c004cd7',
        }),
        ('bionic-dirent-storage.patch', {
            'libc/libc.c': '96ffe75851d8d9a557c62057b252fb49edd09a74e5bfc69ad689eed87bebe949',
        }),
        ('bionic-art-sigchain.patch', {
            'libc/libc.c': 'df58c0aead9961d92549a2696fd9aec8f66f51e64bb203e8f6a90bf159a4cb15',
        }),
        ('bionic-signal-mask.patch', {
            'libc/libc.c': '9e7df7f13f4f31d5ee3504b7ba81d48636f6a927d7a29120e6e7ff2ab6454d76',
        }),
        ('bionic-static-sync-init.patch', {
            'pthread_wrapper/libpthread.c': '1a7551b88a17680256f290b3d55ae3686cfb26a1aefe216b75b6b8b5a593433c',
        }),
        ('bionic-xsi-strerror-r.patch', {
            'libc/libc-misc.c': '008af6c6f9250eb879a954193897f82a2f0b256d1a7d7e673bcf74bb09f71312',
        }),
        ('bionic-checked-memchr.patch', {
            'libc/libc-misc.c': 'd410c2c8ac872ef58bd4437ccf0d3ebf82a309bc0e9952263b2e0db8997c0204',
        }),
        ('bionic-app-home.patch', {
            'libc/libc-misc.c': '966d22748fce7bc4c9a7a652ce6ccb62491a85e8e6f8f74ee3df2f0cd953cd15',
        }),
        ('bionic-host-debug-init.patch', {
            'linker/linker.c': '120cbd7b332b69ca1a937dd269438dacbe520f204374fc2838081e6006a4f9f7',
        }),
    ],
    'art_standalone': [
        ('art-alt-signal-stack-ownership.patch', {
            'art/runtime/thread_linux.cc': '42e860f00f402d5168616728618df76fb5a0bb26321ec8b5e0ddb48926f033b3',
        }),
        ('art-dx-host-bash.patch', {
            'dalvik/dx/etc/dx': '3cf9906026221053b3ed99018f75939462a175d126dcc87924e6a24fc3278147',
        }),
        ('art-boot-resource-paths.patch', {
            'art/runtime/native/java_lang_VMClassLoader.cc': '8a15f9aea6260e662ed9cf09259a19411e0a923a728cd765c98f3fa037d1e088',
        }),
        ('art-imageless-dex-load.patch', {
            'art/runtime/oat_file_assistant.cc': '386decefc023334528af89dfbc43f10307c5cbd2f2ba766aae94b46777bdaad0',
        }),
        ('art-short-apk-path.patch', {
            'art/runtime/oat_file_assistant.cc': '34e52ba3ee39b00126ed72e04b767cf1188e2ca29e8d8941fbfab61872903b99',
        }),
        ('art-in-memory-dex-cookie.patch', {
            'libcore/dalvik/src/main/java/dalvik/system/DexFile.java': '0b6c3f76f11ac3c505027b20478bd300768c1d8fe2177c6b14f8c021a504f42a',
        }),
        ('art-linkage-diagnostics.patch', {
            'art/runtime/thread.cc': '2df7108d93021291d88116eb07694d39d0adb23b704ba7884c6c5bfc77d6c31b',
        }),
        ('art-managed-diagnostics.patch', {
            'art/runtime/interpreter/interpreter.cc': '6e52434cf9d8ee90abe781934433abb19d3122ae4e44fa50db0b1edf88453f2a',
        }),
        ('art-reflection-diagnostics.patch', {
            'art/runtime/reflection.cc': '1197d545b93db726a972043324b582a8ed9cb1ff610864ed174de60d3b846342',
        }),
        ('art-native-bridge-load-error.patch', {
            'art/runtime/jni/java_vm_ext.cc': '0c07a835e1ffdbf35843852f211e3666bbc5daf7a49eb24c239691bfaeb648e4',
        }),
        ('art-low4g-fallback.patch', {
            'art/libartbase/base/mem_map.cc': '34c6948b8fab5418d0f53fed77d55e44902792c7730a5b6f477fd8bb01250bee',
        }),
        ('art-host-trust-store.patch', {
            'external/wolfssljni/src/java/com/wolfssl/provider/jsse/WolfSSLTrustManager.java': '962f58b73551dfa46f315958aeee457a5a4b39b3abab73b7a6608fac37f4185e',
        }),
        ('art-tls-alpn-api.patch', {
            'libcore/ojluni/src/main/java/javax/net/ssl/SSLParameters.java': '5805b2daa1cb91329268aca39b6c9666fcda73d475d9ffd7a470835a75a90410',
            'libcore/ojluni/src/main/java/javax/net/ssl/SSLSocket.java': '18d505f4345953e2a66c964afd31338e515379a99613cdf095e6587848e2a0e6',
        }),
        ('art-tls-peer-chain-order.patch', {
            'external/wolfssljni/src/java/com/wolfssl/WolfSSLX509StoreCtx.java': 'df67d2ba23f85e35ffd144369f6219ff8061776380de53fa8b8a09149499c7a2',
        }),
        ('art-tls-cache-lifetime.patch', {
            'external/wolfssljni/src/java/com/wolfssl/provider/jsse/WolfSSLAuthStore.java': 'c21a8c88b6a8d8af08d29f574fc80339581045089d0bf76a024cd706ef04efd2',
            'external/wolfssljni/src/java/com/wolfssl/provider/jsse/WolfSSLImplementSSLSession.java': 'ac4558a6df6c22bf909f9399660a6679b31889e434fe42d6279ce4f53d5a446b',
        }),
        ('art-tls-stream-lifetime.patch', {
            'external/wolfssljni/src/java/com/wolfssl/provider/jsse/WolfSSLSocket.java': '06338beb576fff593cc422c1600aa852fafecde8d3f64d400cfe3ba4122d34e9',
        }),
        ('art-tls-verify-mask.patch', {
            'external/wolfssljni/src/java/com/wolfssl/provider/jsse/WolfSSLEngineHelper.java': '09dd5dc1da457c7cb43b4ecf0f1ad2f9a823e55bc02fc012a52365529e4afcd8',
        }),
        ('art-tls-optional-client-auth.patch', {
            'external/wolfssljni/src/java/com/wolfssl/provider/jsse/WolfSSLEngineHelper.java': '9e9bc3ce0622b4d898824b66a54597b695f50a3cbd40159cf0d4d9d17f7cff5e',
            'external/wolfssljni/src/java/com/wolfssl/provider/jsse/WolfSSLInternalVerifyCb.java': '5d1473e1915c9cce275373661b4b05634668c9a594cb40a8054cabc99cc721c2',
        }),
        ('art-tls-peer-chain-order.patch', {
            'external/wolfssljni/native/com_wolfssl_WolfSSLX509StoreCtx.c': 'b5dfcda9bd2b91a79cef950376936098061f792d4b0352e005e94b330b33ef5b',
        }),
        ('art-tls-eof.patch', {
            'external/wolfssljni/src/java/com/wolfssl/provider/jsse/WolfSSLSocket.java': 'd956fb49de6ad1628a38dfd9159d2e2c63115ab3276f6bc9309624f08cb62656',
        }),
        ('art-tls-stream-reads.patch', {
            'external/wolfssljni/src/java/com/wolfssl/provider/jsse/WolfSSLSocket.java': '13f4f126d86c40e12aa7e5e5462de59191660b115e505fcea96b87bed4d1a973',
        }),
        ('art-tls-ip-addresses.patch', {
            'external/okhttp/okhttp/src/main/java/com/squareup/okhttp/internal/tls/OkHostnameVerifier.java': '411bdc96937ff7988f42020519bf18873736b08b7a4aefb752dd0114fde3da4f',
        }),
        ('art-tls-darwin-sigpipe.patch', {
            'external/wolfssljni/native/com_wolfssl_WolfSSLSession.c': '4f75324e6fa86d31b4535937b83f0a871d4c4ce9aa30770347a1e74d9548c356',
        }),
        ('art-host-unload.patch', {
            'libcore/luni/src/main/native/Register.cpp':
                '1baba7f2fb38514ba57955287d7a28ca9478a985d3fd9bce411111837d36b9de',
        }),
        ('art-xml-entity-limits.patch', {
            'libcore/xml/src/main/java/org/kxml2/io/KXmlParser.java': '918eb49d67890adbe7b2ab843e9076a91de6b2e4cfde809110e42ed4452aa161',
        }),
        ('art-resource-lifetime.patch', {
            'libandroidfw/Android.mk': 'b50d13d2dfb926c6725cc5b1961807ae6aeaa796e0d0a54faf3e81d4b0808c34',
            'libandroidfw/androidfw_c_api.cpp': 'e908c08beae395e967633e611e2708b5e1903f93e1a7e6812dc62801c0332391',
            'libandroidfw/include/androidfw/androidfw_c_api.h': '4bbc4c38843b0c03ab12ced7ed600ac3fff7f2e3b4bb37a5605840efad3d5a5e',
        }),
        ('art-resource-size-configurations.patch', {
            'libandroidfw/androidfw_c_api.cpp': 'f87d482e950e560af8875ce58072e894f6225967fe7b55f764292b83d2fd996f',
            'libandroidfw/include/androidfw/androidfw_c_api.h': 'cd8dad2be0628312e1afb98093f4df828647ba88503dde093962957acdf23d77',
        }),
        ('art-resource-identifier-status.patch', {
            'libandroidfw/androidfw_c_api.cpp': '9754786dfa69284d3c3f9fa6f046b7b7b4f3961edb027c4025817576097218b0',
            'libandroidfw/include/androidfw/androidfw_c_api.h': 'e395f966983da83a84750d3ec03f08dfe93133a6128c7caaf06a6408e51c2473',
        }),
        ('art-resource-private-attribute-io.patch', {
            'libandroidfw/AssetManager2.cpp': 'e860b7656deb7cc7b9ee98587e15d8b363e405e58d03f1e66532bef5e5212402',
        }),
        ('art-liblog-c-compat.patch', {
            'system/core/liblog/fake_log_device.c': '244a9b7c548aa032dae9834308e58cd3c74288c00d1dd939e33cc7e18234bb7f',
        }),
    ],
}
# macOS-only patches, applied after PATCHES on Darwin hosts only (Linux trees are
# unaffected). Same (patch, {file: final sha256}) chain format as PATCHES.
PATCHES_DARWIN = {
    'art_standalone': [
        ('art-darwin-network.patch', {
            'external/okhttp/Android.mk': 'afc052111dca4ee147393c7b2463a974716f8b19b8bb8036fb44113abac6df36',
            'external/wolfssljni/Android.mk': 'a05aef517f6250f94656d85c62ec324567049fd92eb6d72c938e3a8eaa0aa9ca',
        }),
        ('art-darwin-build.patch', {  # 27156 bytes
            'art/build/Android.common_build.mk': '5e00dd05b95a1a716200ad7f276a82c5f675b566c1301fc61a8e92698aa48e0e',
            'art/dalvikvm/Android.mk': 'ff48dc36f8b850d976ab2bd93ec0e070ce6a204d86afb77335e6073704921fe8',
            'art/libartpalette/Android.mk': '4328fba0e10923eaf16e8b7d589569ec408e889eafb35af147d9d3e1b27493b6',
            'art/runtime/Android.mk': 'e8bb97ccf347a053bcf26bd4c0c83b092b2e36556555672026ca6dbfadb60180',
            'build/core/combo/HOST_darwin-arm64.mk': '0f2c24db7babe8d8cd3f6e392c4dfc08994fdbc74aa730f5809516b903066d26',
            'build/core/combo/include/arch/darwin-arm64/AndroidConfig.h': 'd14ab0865bb991624fbc8d3f7378f90ba7e31bfbbeaa3f9d36201b1b292d1c39',
            'build/core/envsetup.mk': '5149ae553aaa1aa42f2aade5dc1712a2db8db60f429002cefa9e70c7570517a4',
            'build/core/host_dalvik_java_library.mk': 'eedcad7c8c694d31d0c6c9a6b73ad3ab7be8b8604197b9385ee6ef74307e3dc9',
            'build/core/host_dalvik_static_java_library.mk': 'c41de9866724bc7a77dc201af5bff3a757bc381565cd713b5e0f4d9bee312fe1',
            'build/core/product_config.mk': 'aefb46d1ff2301de593e278f8a6ef9a217fbd75513abc18470108153906aae4e',
            'external/fdlibm/Android.mk': 'e87054689af8c7a7ab23634f45cd6d11deed95ac93acad47d7fe4c3d758a42d2',
            'libbase/Android.mk': '92b4ba08b54c90afb2ebc0f9d031dad65cb7b870d216100a3201b35dd226e13d',
            'libcore/JavaLibrary.mk': '82160ad1179ebf6801b27b19b1573c153e64637704b9cbecced3dc387b24ade7',
            'libcore/NativeCode.mk': 'f7b4f8184f2668e1b10fc242490ba755af715d9e8b04ff3f650104a50f83e54b',
            'libcore/luni/src/main/native/sub.mk': '0537268fc514fa7547db4279e8b008e6fddca19b6195e2b26f8f69fd656af94d',
            'libnativehelper/Android.mk': '266e70d67d56d91f1d4fa5b34031111889da5dfc349b18bce6ded85ed939336c',
            'system/core/libbacktrace/Android.mk': 'b9971d50c3bae29d443bc2787f8f0fe2b88addd2cdc36b53db275ca87173868d',
            'system/core/libcutils/Android.mk': '3958665f31c69c552f54cca6972afe000f12492c7812a98a993f9ccdff97d36b',
            'system/core/libnativebridge/Android.mk': '90efaa8abadf30173c9f2e83ed60281610da8ede440a09f559c51b026a4c5c33',
            'system/core/libutils/Android.mk': '5b6e389c2df0e78dee4f47807b76a7bb6e3dd1573f481467cc66160f9ad560e6',
        }),
        ('art-darwin-support.patch', {  # 25382 bytes
            'art/libartbase/base/time_utils.cc': 'cd92619e980d867ab0e5c0c670fad8384f965430b1502c4b81586e3df822536d',
            'art/sigchainlib/sigchain.cc': 'ff66e220f125f503a9acadd478c5e0ff2af88ec788a19b222f050af92bee7f49',
            'art/sigchainlib/sigchain.h': '2f4a3284abc715d44602d9fe1c313d634d031c02d4ae276b40b5276963523518',
            'libandroidfw/include/android/asset_manager.h': '1d05e599de4cc688e0436e15e0f259093a6f70dd51a121c7c8fec6570fb8a082',
            'libandroidfw/include/androidfw/androidfw_c_api.h': '3877a7ea59d070c4b19d57ce72a965c0c035916fb4d55016f9042dd42ec46b38',
            'libbase/logging.cpp': '66e76b46d644f185786dc997207a4c70df87ecc85ec475f2b4cd05a54dc02dfb',
            'libnativehelper/include/nativehelper/jni.h': '70f646bfa4edc7b27d500a5c52d6258cef0698cc8c88f4fb49b806c3cc9bbdf5',
            'libziparchive/zip_archive.cc': 'a0a0ad7199274e025ebbc4c463ae65386ae4d1c618f1280bb093691661ba08de',
            'system/core/libcutils/threads.c': '9b800baa7107081994ea3db3a8431069db175c3214e6576787729551cc821a8a',
            'system/core/liblog/logd_write.c': 'd2fc1e9e3bc212692433c2e385a6d85b524c05a0c6f013e8dd38af2a292f9462',
            'system/core/libnativebridge/native_bridge.cc': '262062cec65699e66061bab229b5d5adb4384e1338e604a41a963e7474b39e51',
            'system/core/libutils/Looper.cpp': '5e0cd53679ccde407f64bef40ca134dc91f5ca2c0607377d1ea09a4ce72c62ac',
            'system/core/libutils/include/utils/Compat.h': 'e7552020c4ea081eb2f7d6bf18abd537aa194753b9f46ceeca47a68b89924dc8',
        }),
        ('art-darwin-heap.patch', {  # 55158 bytes
            'art/libartbase/base/casts.h': '50348370f70069ce2370989d91827a49bf36ac563f5ecb9e3d61dcc8b00c3a05',
            'art/libartbase/base/globals.h': 'a662226bcc5fcdf08713cd22c1699d5e3a1eb8484c1f7f5b7c6bd59999f83a6d',
            'art/libartbase/base/mem_map.cc': 'b4e1c147a99325fd2d58d4cbdbef38a641e24f4a9511b9041852c511ba6853c8',
            'art/libartbase/base/mem_map.h': '9a1c7fff2c50716562747b21a6c2a3443f51eb4d0d16e57fefd8f9402f0a5a53',
            'art/libartbase/base/mem_map_unix.cc': '536905efa5b0884153abbecaf5678fbbb2656453b6bc5a764d0e608676799bf1',
            'art/libartbase/base/utils.h': 'a937895adc6f9dbde1fef0e6e888668e29f4b3db6a2f90a615f86ef7c6e4561d',
            'art/runtime/class_table-inl.h': '4392ae60380d129323e59db7441850fcdc6ec2e1d953a3681f2e5320d4140ef2',
            'art/runtime/gc/accounting/space_bitmap-inl.h': 'c6a810da8102921024aa80e5303ed1cd44f2f87ab9b436e6afd04d7773d2da6f',
            'art/runtime/gc/allocator/rosalloc.cc': '58df4708aee71d78a9c9a8ff9e4cffb01434ccf627b68014c304c36be5894ddb',
            'art/runtime/gc/heap.cc': '7352431af8ca4a86aa6ee3ea3d31789b1d221da854b18201d4d3cb00fd6d1f99',
            'art/runtime/gc/space/bump_pointer_space.cc': 'a35388904eff64c5d185370848541f9969cd33eaf86a97ea250f0c1a724461af',
            'art/runtime/gc/space/large_object_space.cc': '50345dc2b9a86053c22cd4908be652e1ecda9827e20653de19fdda0ca7b12cb2',
            'art/runtime/gc/space/rosalloc_space.cc': '3cd81b690b493d2fca1fd924fe1443142daba32808eb84056405b8b222a3255d',
            'art/runtime/gc/space/space.cc': '86db7278df7bcd19c074703ff20c21a00d7fb584756a319ab15981cad44b3264',
            'art/runtime/interpreter/interpreter.cc': 'afd0e4c76139d2cef6e9f78f33b58ccbcd7b2eee0a5f97187af0107e95ec6b96',
            'art/runtime/interpreter/interpreter_common.cc': 'ce6df15bb441d2a283a2d0d690a7b2fbb799dd87937007e350b983923444dcea',
            'art/runtime/lock_word-inl.h': 'b77e952394bfe4d003fd570be365687d37c0c87717d634fa3eabe7b6fecc481c',
            'art/runtime/lock_word.h': '2eb0cfb4430f5d369194d1c15ffd99462e436cc3f502ab4818752d1aac4dda47',
            'art/runtime/method_handles.cc': '95fd813913bb905cba57512b6a2c80029a8820cf852ca5f2710e7362c855cda9',
            'art/runtime/mirror/object_reference.h': '915ba4d3e7424602eeef3258300f061deb1eab4c93ee4de6bd2ad62aa8f55738',
            'art/runtime/mirror/var_handle.cc': '9efd7d98b28ec383d1ad03f8fb9165faa4582c625d11744c268006fddea91aeb',
            'art/runtime/monitor.cc': 'a39a751ecf49659280028809981d1a35d9250ae4218cd812d35dba0b63b0f19f',
            'art/runtime/monitor_pool.h': 'bc2b58a63eae19cbb2a7c7c44770bb147564975f18f2d18cdf5feaf7545a24dc',
            'art/runtime/obj_ptr-inl.h': '6438f4bb0900b3113e6cd95bc85ec987575384b73a0b87e87a37eb21662421c1',
            'art/runtime/obj_ptr.h': '656688d8c0775dcbc8eb88e448d2bea78427b48bb471e422a907074c6d2ad0e6',
            'art/runtime/quick_exception_handler.cc': '0464ffe06d8ad336c93eadfcd9c272432895b92dfe4b3bc7cb40142ab8baeacf',
            'art/runtime/stack.cc': '0e18bbb3c98b9fb36e0eab5f2ecec7490ba9be2826ba323df4ed143e3e6824e8',
        }),
        ('art-darwin-runtime.patch', {  # 155609 bytes
            'art/darwin/include/LICENSE.musl': 'f9bc4423732350eb0b3f7ed7e91d530298476f8fec0c6c427a1c04ade22655af',
            'art/darwin/include/elf.h': '70d3150b17e682299f4ff31e0311efa80468cf3fc6b747b61795dae7fa32125b',
            'art/runtime/arch/arm64/context_arm64.cc': '5c0b18a80cd3e486ea59561e4888130e3ac8eb2b121fbd88f6088ff6a5aeddd6',
            'art/runtime/arch/arm64/fault_handler_arm64.cc': 'da849e909b300e0fcebd84b6f78dc3409fb7bdc36709a64bafcaf7f4a2838ffb',
            'art/runtime/base/mutex-inl.h': '479ebcac24c29d607cf766c2d95d1ed942e4ca3a3212f4e2778add8042d0bee1',
            'art/runtime/base/mutex.h': '543e3f454e50916c1ba8c866ede2463c44370dc808a433124881423e6ff1a3f2',
            'art/runtime/darwin/dl_bio_darwin.cc': '723c3f8a016e041a66542b9c062de36d69c08083e5c938092f932fbc5c56bcf7',
            'art/runtime/darwin/futex_darwin.h': 'dd46d14cab87635ef2518f122f16a9ee7907df9097278fff07d059d283fca136',
            'art/runtime/entrypoints/quick/quick_alloc_entrypoints.cc': '0967e5996b2c8ce71a64e630adda0287020c59a2a0c8076c1e9fdccb4f2d79a1',
            'art/runtime/entrypoints/quick/quick_trampoline_entrypoints.cc': '71c387e6460bfee909ca77e0442bd5de157edefd8bb06b71b0c26b4b5e5e6210',
            'art/runtime/exec_utils.cc': 'b6bcc1febc044cb52c56ad85ef4c6d1532b7b2d382456b58ce6c765d0b3eec75',
            'art/runtime/interpreter/mterp/mterp.cc': '1f37d852e007dea263149e4cf0dbae90fcacf8ef8569eee2ba41c668d2b7c798',
            'art/runtime/jni/jni_internal.cc': '229c199b7056d7764a044e402c663a2682f7a672e25420f368ffdaea1d7adbfc',
            'art/runtime/parsed_options.cc': '1eb0a31325a2f683da6e7c06a7b5363088272ed78f5f6e499ac1cb2607b3e0e6',
            'art/runtime/runtime.cc': 'dd2eeca7a1ee16f43617b4f246b000b31f8958ebfe2737cb3d0f00d4abcf0f4c',
            'art/runtime/runtime.h': '83b59063cbfcba4e29380322edb9dda271e4078b5a4c6e487bab264712df1100',
            'art/runtime/runtime_common.cc': '2c7179c0c455a333a2a6cb1f07d5e8d44f90fc2e08505fc32d329d97f6870136',
            'art/runtime/thread.cc': '1eb8a474a258ef87bbafdb397d5be57783855b0883513b39ff050463aa9b2a77',
            'art/runtime/thread_list.cc': '3a4a849e27853ab4bf12578722812b13d08b013d8cdde8a0510cb838fc3964d2',
            'art/runtime/ti/agent.cc': 'd861248884917b394105d7fed1f755fdedddffcfccd74a8be5787c39b7b30d3e',
        }),
        ('art-darwin-asm.patch', {  # 81301 bytes
            'art/runtime/arch/arm64/asm_support_arm64.S': 'b4e87a3d72285362f921d9356ec741aa6683f0eb6badfc91855ec9d7159084fb',
            'art/runtime/arch/arm64/darwin_symbol_aliases.S': '50b1e70fc6bd2b8e68912ce9be7bf4431cb24411ad3c8787bac5a200428005fc',
            'art/runtime/arch/arm64/memcmp16_arm64.S': 'c0c54193ae1e2ac2ac15e9adb5ad5c8fd41b985d16f3204d6bdfdf809e52439e',
            'art/runtime/arch/arm64/quick_entrypoints_arm64.S': 'eb443974a0d6fb9d244ffe5927446e552ae6c610b600648d8c17170cd4cb00d3',
            'art/runtime/interpreter/mterp/arm64/control_flow.S': '08ae4cdbf592c9b612ff5772819cdc39fb8390589098b9e8e0edc85a5800acdf',
            'art/runtime/interpreter/mterp/arm64/main.S': 'b69f8ed52c3f26dc77d29b59136b1a43ad8194d8e6f9ebafb4fefd86d2cd1bc9',
            'art/runtime/interpreter/mterp/arm64/object.S': 'df05c5312a25ef923ab118e883f48229b1f2539dc6a8b2891582d7b6e90d95b2',
            'art/runtime/interpreter/mterp/common/gen_setup.py': 'c55d995d106624790fdfb13003edd2b7df7f7bdcd8310d8f74204959ed4e7534',
            'art/runtime/interpreter/mterp/darwin_symbol_aliases_arm64.S': '18131f5b5768a9437a73d8434e30417e3b6fb74db76f9d48bbe0db4a670bc851',
        }),
        ('art-darwin-libcore.patch', {  # 63461 bytes
            'art/openjdkjvm/OpenjdkJvm.cc': 'abe8d8e51a368b78d8d579e11c73ebc1670886f3f32741995c5aeadce1e6734f',
            'libcore/luni/src/main/native/DarwinSockoptSentinels.h': '01aff1d66faaeafdab9590425e08620ab7eb707fb8ced435c0ef16013ef55b2b',
            'libcore/luni/src/main/native/Portability.h': '2a4ce8f92919a545017ddfe3ce5196b91aafed75353f0fdfcdf685d5bea39fd8',
            'libcore/luni/src/main/native/Portability_darwin.h': '99e385eccd84eaf0d97f936bf2fa33a90eef89be3426d193fd3a9549d5bee8c3',
            'libcore/luni/src/main/native/android_system_OsConstants.cpp': '5633625f75271d80ae6245c1b46559d8dd7ba629c516de042433e0d7b5e539aa',
            'libcore/luni/src/main/native/libcore_io_Linux.cpp': '363e7b8ac32395e6fca93feb8c31418be73695f9fec855fc4eb3a0b44b9bffc5',
            'libcore/ojluni/src/main/native/EPoll.c': '99267e4fde5de5a57234883e52e3b172b0a1f7d9515f92cfdae56777a1f04d6b',
            'libcore/ojluni/src/main/native/LinuxNativeDispatcher.c': '3b384cd946d62a2cad594209de4efbe3101f7eb45005dc0725187cd5f4f38222',
            'libcore/ojluni/src/main/native/LinuxWatchService.c': 'fa72a428e33d2d01fb027e05309663936eb204bf948df63bc646396347cf4336',
            'libcore/ojluni/src/main/native/Net.c': '625598cb8ca91083dc93b6f2696b1ef7d7e7da496a2b9b7bfa037252584598ef',
            'libcore/ojluni/src/main/native/System.c': '1d1eb9280f361f430781bed4a3060478882c63263918aa54be5cb370c3e3510a',
            'libcore/ojluni/src/main/native/UNIXProcess_md.c': '15a87ec4566c90e5a77a5e497d7c6707c19215e15fe8cee9a6680342a80d3ac3',
            'libcore/ojluni/src/main/native/UnixNativeDispatcher.c': 'da6b5b3af24264725b1089f29aea38b0e8b7c65fb2684897aceb72c47c2db152',
            'libcore/ojluni/src/main/native/io_util_md.c': '5ce529d368275bd83ced819b6ee835c2d0f4ea3a3a1c09740aac54c5a03981d6',
            'libcore/ojluni/src/main/native/jvm_md.h': 'a8423dd9ea7b8ae1d7ee66c3196b79121cbfb4b03724f93ab106ffacf6e9048c',
            'libcore/ojluni/src/main/native/linux_close.cpp': '154424efddc69ac211134b1763f23efe82570797900652d931fea26ae2e35dda',
            'libnativehelper/AsynchronousCloseMonitor.cpp': '1a154047dbdfdb712666b64bf65cdd62c585f58c126667075724c944f2d5b290',
        }),
        # okhttp is not booted on macOS yet: java.net.URL falls back to
        # shiraz.net.http(s).Handler instead of throwing AssertionError.
        ('art-darwin-url-handlers.patch', {  # 2635 bytes
            'libcore/ojluni/src/main/java/java/net/URL.java': 'f9481fffcbf68eaee61bf99d1ca740f47816b8215e783367b157e0ba78bd2b32',
        }),
        ('art-darwin-guest-bridge.patch', {
            'system/core/libnativebridge/native_bridge.cc': 'f6e87865f6e35b89dbf7ef78870942ffa08465e9c453de13b2b9f60d261342e0',
            'art/runtime/native_bridge_art_interface.cc': '976f3fe572f3a85e2d7aad9d3194da3aa14d3c9e62a61686ae7f3f7577c88583',
        }),
    ],
}
BSD_VERSION = '0.12.2'
BSD_SHA256 = 'b88cc9163d0c652aaf39a99991d974ddba1c3a9711db8f1b5838af2a14731014'


def run(argv, cwd=ROOT, env=None):
    if argv[0] == "make":
        bash = shutil.which("bash")
        if not bash:
            raise RuntimeError("Bash is required to build the runtime")
        argv = [*argv, "SHELL=" + bash]
    subprocess.run([str(x) for x in argv], cwd=cwd, env=env, check=True)


def sources():
    for name, (url, revision) in SOURCES.items():
        directory = ROOT / 'context' / name
        if not directory.exists():
            directory.mkdir(parents=True)
            run(['git', 'init', '-q', directory])
            run(['git', 'remote', 'add', 'origin', url], cwd=directory)
            run(['git', 'fetch', '--depth', '1', 'origin', revision], cwd=directory)
            run(['git', 'checkout', '--detach', 'FETCH_HEAD'], cwd=directory)
        actual = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=directory, text=True).strip()
        if actual != revision:
            raise SystemExit(f'{name}: local revision differs from pin; refusing to overwrite')
        dirty = subprocess.check_output(['git', 'diff', '--no-ext-diff', '--name-only', 'HEAD'], cwd=directory, text=True)
        patches = PATCHES.get(name, [])
        if platform.system() == 'Darwin':
            patches = patches + PATCHES_DARWIN.get(name, [])
        expected_files = {file: checksum for _, files in patches for file, checksum in files.items()}
        changed = set(dirty.splitlines())
        if not changed.issubset(expected_files):
            raise SystemExit(f'{name}: unrecognized tracked changes; refusing to build')
        def digest(file):
            path = directory / file
            return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None

        current = {file: digest(file) for file in expected_files}
        if current == expected_files:
            continue  # Final recipe, including files superseded by overlapping patches.

        # `git clean` removes patch-created (untracked) files while tracked files keep
        # their final content. Recreate only those files, from the last patch that
        # writes each one, when everything else already matches the final recipe.
        def in_head(file):
            return subprocess.run(['git', 'cat-file', '-e', f'HEAD:{file}'], cwd=directory,
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0

        missing = [file for file, checksum in expected_files.items()
                   if current[file] is None and checksum is not None and not in_head(file)]
        if missing and all(current[file] == checksum for file, checksum in expected_files.items()
                           if file not in missing):
            for file in missing:
                patch = [patch for patch, files in patches if file in files][-1]
                run(['git', 'apply', f'--include={file}', ROOT / 'patches' / patch], cwd=directory)
                if digest(file) != expected_files[file]:
                    raise SystemExit(f'{name}: restored source differs from recorded checksum: {file}')
            continue

        # Independent patches may have been installed in any order (new fixes can be
        # prepended). Overlapping patches form a chain whose complete intermediate
        # snapshots must match before any further mutation is permitted.
        components = []
        for index, (_, files) in enumerate(patches):
            joined = {index}
            touched = set(files)
            remaining = []
            for indices, paths in components:
                if touched.intersection(paths):
                    joined.update(indices)
                    touched.update(paths)
                else:
                    remaining.append((indices, paths))
            components = remaining + [(joined, touched)]
        pending = set()
        for indices, paths in components:
            state = {}
            for file in paths:
                original = subprocess.run(['git', 'show', f'HEAD:{file}'], cwd=directory,
                                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                state[file] = hashlib.sha256(original.stdout).hexdigest() if original.returncode == 0 else None
            ordered = sorted(indices)
            snapshots = [dict(state)]
            for index in ordered:
                state.update(patches[index][1])
                snapshots.append(dict(state))
            matches = [step for step, snapshot in enumerate(snapshots)
                       if all(current[file] == checksum for file, checksum in snapshot.items())]
            if not matches:
                raise SystemExit(f'{name}: modified or partially applied dependency patch chain; '
                                 f'to rebuild it from the pin run: git -C {directory} checkout -- . '
                                 f'&& git -C {directory} clean -fdx -e out')
            pending.update(ordered[max(matches):])
        for index, (patch, files) in enumerate(patches):
            if index not in pending:
                continue
            run(['git', 'apply', '--check', ROOT / 'patches' / patch], cwd=directory)
            run(['git', 'apply', ROOT / 'patches' / patch], cwd=directory)
            for file, checksum in files.items():
                if digest(file) != checksum:
                    raise SystemExit(f'{name}: patched source differs from recorded checksum: {file}')
        for file, checksum in expected_files.items():
            if digest(file) != checksum:
                raise SystemExit(f'{name}: patched source differs from recorded checksum: {file}')



def build_bsd(env):
    deps = ROOT / '.deps'
    deps.mkdir(exist_ok=True)
    archive = deps / f'libbsd-{BSD_VERSION}.tar.xz'
    if not archive.exists():
        urllib.request.urlretrieve(f'https://libbsd.freedesktop.org/releases/libbsd-{BSD_VERSION}.tar.xz', archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != BSD_SHA256:
        raise SystemExit('libbsd source checksum mismatch')
    source = deps / f'libbsd-{BSD_VERSION}'
    if not source.exists():
        with tarfile.open(archive) as tar:
            tar.extractall(deps, filter='data')
    build = ROOT / 'build' / 'libbsd'
    build.mkdir(parents=True, exist_ok=True)
    run([source / 'configure', '--prefix=' + str(PREFIX), '--enable-shared', '--enable-static', '--with-pic'], cwd=build, env=env)
    run(['make', '-j2'], cwd=build, env=env)
    run(['make', 'install'], cwd=build, env=env)


def build_wolfssl(env):
    source = ROOT / 'context/wolfssl'
    if not (source / 'configure').is_file():
        run(['autoreconf', '-fi'], cwd=source, env=env)
    build = ROOT / 'build/wolfssl'
    build.mkdir(parents=True, exist_ok=True)
    run([source / 'configure', '--prefix=' + str(PREFIX), '--enable-shared', '--disable-static',
         '--enable-jni', '--disable-examples', '--disable-crypttests'], cwd=build, env=env)
    run(['make', '-j2'], cwd=build, env=env)
    run(['make', 'install'], cwd=build, env=env)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=['sources', 'bridges', 'art', 'resources', 'xml', 'network', 'speech', 'memory'], default='bridges', nargs='?')
    args = parser.parse_args()
    (ROOT / '.deps').mkdir(exist_ok=True)
    lock = (ROOT / '.deps/runtime-build.lock').open('a')
    try:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        parser.exit(1, 'Another Shiraz runtime bootstrap owns the build lock. Wait for it to finish.\n')
    sources()
    if args.stage == 'sources':
        return
    env = dict(os.environ)
    for name, suffix in [('PKG_CONFIG_PATH', 'lib/pkgconfig'), ('LIBRARY_PATH', 'lib'),
                         ('LD_LIBRARY_PATH', 'lib'), ('CPATH', 'include')]:
        env[name] = str(PREFIX / suffix) + (':' + env[name] if env.get(name) else '')
    if platform.system() == 'Darwin':
        # macOS builds in-tree ICU/lz4/xz and ART without libbsd or bionic_translation.
        import bootstrap_darwin
        bootstrap_darwin.run_stage(args.stage)
        return
    if args.stage == 'memory':
        run(['make', '-j2', 'libartbase', '____PREFIX=' + str(PREFIX), '____LIBDIR=lib'], cwd=ROOT / 'context/art_standalone', env=env)
        return
    if args.stage == 'speech':
        source = ROOT / 'context/espeak-ng'
        if not (source / 'configure').is_file():
            run(['sh', 'autogen.sh'], cwd=source, env=env)
        # Upstream phoneme generation assumes an in-source build.
        build = source
        run([source / 'configure', '--prefix=' + str(PREFIX), '--without-pcaudiolib', '--without-sonic', '--without-mbrola', '--without-async', '--disable-static'], cwd=build, env=env)
        run(['make', '-j2'], cwd=build, env=env)
        run(['make', 'install'], cwd=build, env=env)
        return
    if args.stage == 'network':
        from bootstrap_boot import desugar_boot, NETWORK_MODULES, MODULES
        build_wolfssl(env)
        run(['make', '-j2', *MODULES, *NETWORK_MODULES, '____PREFIX=' + str(PREFIX), '____LIBDIR=lib'], cwd=ROOT / 'context/art_standalone', env=env)
        desugar_boot(ROOT, MODULES + NETWORK_MODULES)
        return
    if args.stage == 'xml':
        if not (ROOT / 'context/art_standalone/out/host/linux-x86/framework/core-libart-hostdex.jar').is_file():
            raise SystemExit('Host ART Java runtime is missing. Run make runtime-art first.')
        run(['make', '-j2', 'core-libart-hostdex', '____PREFIX=' + str(PREFIX), '____LIBDIR=lib'], cwd=ROOT / 'context/art_standalone', env=env)
        from bootstrap_boot import desugar_boot
        desugar_boot(ROOT)
        return
    build_bsd(env)
    build = ROOT / 'build' / 'bionic'
    setup = ['meson', 'setup']
    if (build / 'meson-private/coredata.dat').exists():
        setup.append('--reconfigure')
    run(setup + [build, ROOT / 'context/bionic_translation', '--prefix=' + str(PREFIX), '--libdir=lib'], env=env)
    run(['meson', 'compile', '-C', build, '-j', '2'], env=env)
    run(['meson', 'install', '-C', build], env=env)
    if args.stage == 'art':
        run(['make', '-j2', 'dalvikvm', 'dex2oat', 'libjavacore', 'libopenjdk', 'libopenjdkjvm',
             'core-oj-hostdex', 'core-libart-hostdex', '____PREFIX=' + str(PREFIX), '____LIBDIR=lib'], cwd=ROOT / 'context/art_standalone', env=env)
        from bootstrap_boot import desugar_boot
        desugar_boot(ROOT)
    if args.stage == 'resources':
        run(['make', '-j2', 'libandroidfw', '____PREFIX=' + str(PREFIX), '____LIBDIR=lib'], cwd=ROOT / 'context/art_standalone', env=env)
    # This only records successful build steps, not a claim of a working ART boot.
    (ROOT / 'build/runtime-dependencies.json').write_text(json.dumps({'stage': args.stage, 'sources': SOURCES, 'patches': PATCHES, 'libbsd': BSD_VERSION}, indent=2) + '\n')


if __name__ == '__main__':
    main()
