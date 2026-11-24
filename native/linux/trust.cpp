// SPDX-License-Identifier: GPL-3.0-only
// Linux host system CA trust anchors (libshiraz-trust.so) for shiraz.runtime.HostTrustProvider.
// Reads public system PEM CA bundles only (never private keys or user identities)
// and decodes X.509 CERTIFICATE blocks into DER byte arrays matching native/darwin/trust.cpp.

#include <jni.h>

#include <cstdint>
#include <cstdlib>
#include <fstream>
#include <set>
#include <string>
#include <vector>

namespace {

constexpr size_t kMaxBundleBytes = 16 * 1024 * 1024;
constexpr size_t kMaxAnchorCount = 4096;
constexpr size_t kMaxCertBytes = 1024 * 1024;

constexpr const char* kBeginMarker = "-----BEGIN CERTIFICATE-----";
constexpr const char* kEndMarker = "-----END CERTIFICATE-----";

int base64Value(unsigned char c) {
    if (c >= 'A' && c <= 'Z')
        return c - 'A';
    if (c >= 'a' && c <= 'z')
        return 26 + (c - 'a');
    if (c >= '0' && c <= '9')
        return 52 + (c - '0');
    if (c == '+')
        return 62;
    if (c == '/')
        return 63;
    return -1;
}

bool decodeBase64(const std::string& input, std::vector<uint8_t>& out) {
    out.clear();
    out.reserve((input.size() * 3) / 4);
    uint32_t accum = 0;
    int bits = 0;
    bool sawPad = false;
    for (unsigned char c : input) {
        if (c == '\r' || c == '\n' || c == ' ' || c == '\t')
            continue;
        if (c == '=') {
            sawPad = true;
            continue;
        }
        if (sawPad)
            return false;
        const int v = base64Value(c);
        if (v < 0)
            return false;
        accum = (accum << 6) | static_cast<uint32_t>(v);
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            out.push_back(static_cast<uint8_t>((accum >> bits) & 0xFFu));
            if (out.size() > kMaxCertBytes)
                return false;
        }
    }
    return !out.empty() && out[0] == 0x30; // X.509 Certificate starts with ASN.1 SEQUENCE (0x30)
}

bool readPemBundle(const std::string& path, std::vector<std::vector<uint8_t>>& certs,
                   std::set<std::vector<uint8_t>>& seen) {
    std::ifstream in(path, std::ios::binary | std::ios::ate);
    if (!in.is_open())
        return false;
    const std::streamoff size = in.tellg();
    if (size <= 0 || static_cast<size_t>(size) > kMaxBundleBytes)
        return false;
    in.seekg(0, std::ios::beg);
    std::string content(static_cast<size_t>(size), '\0');
    if (!in.read(&content[0], size))
        return false;

    const size_t beginLen = std::char_traits<char>::length(kBeginMarker);
    const size_t endLen = std::char_traits<char>::length(kEndMarker);
    size_t pos = 0;
    bool addedAny = false;
    while (certs.size() < kMaxAnchorCount) {
        const size_t start = content.find(kBeginMarker, pos);
        if (start == std::string::npos)
            break;
        const size_t bodyStart = start + beginLen;
        const size_t end = content.find(kEndMarker, bodyStart);
        if (end == std::string::npos)
            break;
        std::vector<uint8_t> der;
        if (decodeBase64(content.substr(bodyStart, end - bodyStart), der)) {
            if (seen.insert(der).second) {
                certs.push_back(std::move(der));
                addedAny = true;
            }
        }
        pos = end + endLen;
    }
    return addedAny;
}

std::vector<std::vector<uint8_t>> loadLinuxSystemAnchors() {
    std::vector<std::vector<uint8_t>> certs;
    std::set<std::vector<uint8_t>> seen;

    if (const char* explicitBundle = std::getenv("SHIRAZ_SYSTEM_CA_BUNDLE")) {
        if (*explicitBundle) {
            readPemBundle(explicitBundle, certs, seen);
            return certs;
        }
    }

    if (const char* sslCertFile = std::getenv("SSL_CERT_FILE")) {
        if (*sslCertFile && readPemBundle(sslCertFile, certs, seen))
            return certs;
    }

    static const char* const kDefaultBundles[] = {
        "/etc/ssl/certs/ca-certificates.crt", // Debian, Ubuntu, Arch, Alpine, Flatpak
        "/etc/pki/tls/certs/ca-bundle.crt",   // Fedora, RHEL, CentOS, Rocky
        "/etc/ssl/ca-bundle.pem",             // openSUSE, SLES
        "/etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem", // p11-kit extracted bundle
        "/etc/ssl/cert.pem",                                 // Alpine, NixOS, Void
    };
    for (const char* path : kDefaultBundles) {
        if (readPemBundle(path, certs, seen))
            break;
    }
    return certs;
}

} // namespace

extern "C" JNIEXPORT jobjectArray JNICALL Java_shiraz_runtime_HostTrustProvider_anchors(JNIEnv* env,
                                                                                        jclass) {
    const auto certs = loadLinuxSystemAnchors();
    jclass bytesClass = env->FindClass("[B");
    if (!bytesClass)
        return nullptr;
    jobjectArray result =
        env->NewObjectArray(static_cast<jsize>(certs.size()), bytesClass, nullptr);
    env->DeleteLocalRef(bytesClass);
    if (!result)
        return nullptr;
    for (size_t i = 0; i < certs.size() && !env->ExceptionCheck(); ++i) {
        const auto& der = certs[i];
        jbyteArray encoded = env->NewByteArray(static_cast<jsize>(der.size()));
        if (!encoded)
            return nullptr;
        env->SetByteArrayRegion(encoded, 0, static_cast<jsize>(der.size()),
                                reinterpret_cast<const jbyte*>(der.data()));
        env->SetObjectArrayElement(result, static_cast<jsize>(i), encoded);
        env->DeleteLocalRef(encoded);
    }
    return env->ExceptionCheck() ? nullptr : result;
}
