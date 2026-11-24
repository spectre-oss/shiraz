// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/properties.hpp"
#include "shiraz/darwin/guest/admission_domain.hpp"
#include <algorithm>
namespace shiraz::guest {
PropertyMap api27_properties() {
    // Matches java/android/os/Build.java's nominal API-27 profile. This is
    // compatibility metadata, not a claim that all API-27 features exist.
    return {{"ro.build.version.sdk", "27"},
            {"ro.build.version.preview_sdk", "0"},
            {"ro.build.version.release", "8.1"},
            {"ro.build.version.codename", "REL"},
            {"ro.product.cpu.abi", "arm64-v8a"},
            {"ro.product.cpu.abilist", "arm64-v8a"},
            {"ro.product.cpu.abilist64", "arm64-v8a"},
            {"ro.product.cpu.abilist32", ""}};
}
std::vector<ProviderExport> property_exports(PropertyMap properties,
                                             std::function<bool(const void*)> validate) {
    if (properties.size() > 4096)
        throw ElfError("guest property count limit");
    for (const auto& entry : properties)
        if (entry.first.empty() || entry.first.size() >= 4096 ||
            entry.first.find('\0') != std::string::npos || entry.second.size() >= 92 ||
            entry.second.find('\0') != std::string::npos)
            throw ElfError("invalid guest property name/value");
    auto values = std::make_shared<const PropertyMap>(std::move(properties));
    void* entry = closure([values](Capture& capture) {
        CallCursor cursor(capture, Convention::Aapcs64);
        const char* name = cursor.read<const char*>();
        char* output = cursor.read<char*>();
        HostErrno preserve;
        // Bionic requires a PROP_VALUE_MAX-byte output. Missing properties return
        // an empty string and zero; no host environment is consulted.
        if (!name || !output || strnlen(name, 4096) == 4096) {
            if (output)
                output[0] = 0;
            return_value(capture, -1);
            return;
        }
        auto found = values->find(name);
        if (found == values->end()) {
            output[0] = 0;
            return_value(capture, 0);
            return;
        }
        std::memcpy(output, found->second.c_str(), found->second.size() + 1);
        return_value(capture, int(found->second.size()));
    });
    auto nth = closure([values](Capture& capture) {
        CallCursor cursor(capture, Convention::Aapcs64);
        unsigned index = cursor.read<unsigned>();
        auto it = values->begin();
        if (index >= values->size()) {
            return_value(capture, uintptr_t(0));
            return;
        }
        std::advance(it, index);
        return_value(capture, reinterpret_cast<uintptr_t>(&*it));
    });
    auto read = closure([values](Capture& capture) {
        CallCursor cursor(capture, Convention::Aapcs64);
        const void* handle = cursor.read<const void*>();
        char* name = cursor.read<char*>();
        char* value = cursor.read<char*>();
        HostErrno preserve;
        auto it = std::find_if(values->begin(), values->end(),
                               [&](const auto& item) { return &item == handle; });
        if (it == values->end() || !value) {
            *guest_errno_location() = errno_to_guest(EINVAL);
            return_value(capture, -1);
            return;
        }
        if (name) {
            size_t length = std::min(size_t(31), it->first.size());
            std::memcpy(name, it->first.data(), length);
            name[length] = 0;
        }
        std::memcpy(value, it->second.c_str(), it->second.size() + 1);
        return_value(capture, int(it->second.size()));
    });
    auto foreach = closure([values, validate](Capture& capture) {
        CallCursor cursor(capture, Convention::Aapcs64);
        void* callback = cursor.read<void*>();
        void* cookie = cursor.read<void*>();
        HostErrno preserve;
        if (!callback || !validate || !validate(callback)) {
            *guest_errno_location() = errno_to_guest(EINVAL);
            return_value(capture, -1);
            return;
        }
        try {
            auto domain = AdmissionDomain::current_invocation();
            for (const auto& item : *values) {
                std::vector<Argument> args{
                    Argument::integer(reinterpret_cast<uintptr_t>(&item), ArgumentType::Int64),
                    Argument::integer(reinterpret_cast<uintptr_t>(cookie), ArgumentType::Int64)};
                if (domain)
                    (void)invoke_admitted(domain, callback, args);
                else
                    (void)invoke(callback, args);
            }
            return_value(capture, 0);
        } catch (...) {
            *guest_errno_location() = errno_to_guest(EINVAL);
            return_value(capture, -1);
        }
    });
    auto read_callback = closure([values, validate = std::move(validate)](Capture& capture) {
        CallCursor cursor(capture, Convention::Aapcs64);
        const void* handle = cursor.read<const void*>();
        void* callback = cursor.read<void*>();
        void* cookie = cursor.read<void*>();
        HostErrno preserve;
        auto it = std::find_if(values->begin(), values->end(),
                               [&](const auto& item) { return &item == handle; });
        if (it == values->end() || !callback || !validate || !validate(callback))
            return;
        try {
            auto domain = AdmissionDomain::current_invocation();
            std::vector<Argument> args{
                Argument::integer(reinterpret_cast<uintptr_t>(cookie), ArgumentType::Int64),
                Argument::integer(reinterpret_cast<uintptr_t>(it->first.c_str()),
                                  ArgumentType::Int64),
                Argument::integer(reinterpret_cast<uintptr_t>(it->second.c_str()),
                                  ArgumentType::Int64),
                Argument::integer(0, ArgumentType::Int32)};
            if (domain)
                (void)invoke_admitted(domain, callback, args);
            else
                (void)invoke(callback, args);
        } catch (...) {
        }
    });
    return {{"__system_property_get", "LIBC", reinterpret_cast<uintptr_t>(entry),
             SymbolKind::Function, "properties/explicit-api-profile", false},
            {"__system_property_find_nth", "LIBC", reinterpret_cast<uintptr_t>(nth),
             SymbolKind::Function, "properties/opaque-index", false},
            {"__system_property_read", "LIBC", reinterpret_cast<uintptr_t>(read),
             SymbolKind::Function, "properties/opaque-read", false},
            {"__system_property_foreach", "LIBC", reinterpret_cast<uintptr_t>(foreach),
             SymbolKind::Function, "properties/validated-callback", false},
            {"__system_property_read_callback", "LIBC", reinterpret_cast<uintptr_t>(read_callback),
             SymbolKind::Function, "properties/validated-callback", false}};
}
} // namespace shiraz::guest
