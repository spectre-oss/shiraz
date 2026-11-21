# SPDX-License-Identifier: GPL-3.0-only
.PHONY: macos-network network-probe-smoke
macos-network:
	uv run --no-project python scripts/bootstrap_macos_network.py

ifeq ($(shell uname -s),Darwin)
build/macos/libshiraz-trust.so: native/darwin/trust.cpp
	@mkdir -p build/macos
	$(CXX) -std=c++17 -O2 -Wall -Wextra -Werror $(MACOS_JNI_CFLAGS) -fPIC -dynamiclib -install_name @rpath/libshiraz-trust.so $< -o $@ -framework Security -framework CoreFoundation
macos-runtime-libs: build/macos/libshiraz-trust.so build/macos/libshiraz-network.so
build/macos/libshiraz-network.so: native/darwin/network_jni.cpp native/darwin/job_network.cpp
	@mkdir -p build/macos
	$(CXX) -std=c++17 -O2 -Wall -Wextra -Werror -fblocks $(MACOS_JNI_CFLAGS) -fPIC -dynamiclib -install_name @rpath/libshiraz-network.so $^ -framework Network -o $@
macos-jvm-native: build/macos/libshiraz-network.so

.PHONY: darwin-connectivity-smoke
darwin-connectivity-smoke: macos-framework build/macos/libshiraz-network.so
	@mkdir -p build/macos/jvm-native
	@ln -sfn ../libshiraz-network.so build/macos/jvm-native/libshiraz-network.dylib
	uv run --no-project python scripts/java_framework_smoke.py shiraz.runtime.DarwinConnectivitySmoke --marker DARWIN_CONNECTIVITY_OK
network-probe-smoke: macos-framework build/macos/libshiraz-trust.so build/macos/libshiraz-host.so
else
network-probe-smoke: framework build/libshiraz-host.so
endif
network-probe-smoke:
	uv run --no-project python scripts/network_probe_smoke.py

ifeq ($(shell uname -s),Darwin)
# A completed network bootstrap must also supply the default trust bridge.
macos-network: build/macos/libshiraz-trust.so
endif

.PHONY: network-policy-smoke network-trust-smoke
ifeq ($(shell uname -s),Darwin)
network-policy-smoke network-trust-smoke: macos-framework build/macos/libshiraz-trust.so build/macos/libshiraz-host.so
else
network-policy-smoke network-trust-smoke: framework build/libshiraz-host.so
endif
network-policy-smoke:
	uv run --no-project python scripts/network_policy_smoke.py

network-trust-smoke:
	uv run --no-project python scripts/network_trust_smoke.py
