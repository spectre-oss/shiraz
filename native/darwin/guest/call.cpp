// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/darwin/guest/call.hpp"
#include "shiraz/darwin/guest/closure.hpp"
#include "shiraz/darwin/guest/tls.hpp"
#include "shiraz/darwin/guest/admission_domain.hpp"
#include "shiraz/darwin/guest/admission_return.hpp"
#include <cstddef>
#include <cstring>
#include <cstdlib>
#include <optional>
#include <stdexcept>

namespace shiraz::guest {
extern "C" void shiraz_guest_invoke_asm(CallFrame*, void*, const void*, uint64_t);
extern "C" void shiraz_guest_invoke_admitted_asm(CallFrame*, void*, const void*, uint64_t,
                                                 AdmissionReturnScope*);
extern "C" void shiraz_guest_admission_arm(AdmissionReturnScope* scope, uintptr_t pc,
                                           uintptr_t sp) noexcept {
    try {
        scope->arm(pc, sp);
    } catch (...) {
        std::abort();
    }
}
static_assert(offsetof(CallFrame, vectors) == 64);
static_assert(offsetof(CallFrame, result_integers) == 192);
static_assert(offsetof(CallFrame, result_vectors) == 208);

Argument Argument::integer(int64_t value, ArgumentType type) {
    if (type == ArgumentType::Float || type == ArgumentType::Double)
        throw std::invalid_argument("integer argument requires integer type");
    return {type, uint64_t(value)};
}
Argument Argument::real(double value) {
    uint64_t bits;
    std::memcpy(&bits, &value, sizeof(value));
    return {ArgumentType::Double, bits};
}
Argument Argument::real(float value) {
    uint32_t bits;
    std::memcpy(&bits, &value, sizeof(value));
    return {ArgumentType::Float, bits};
}
double CallFrame::double_result() const {
    double value;
    std::memcpy(&value, result_vectors[0], sizeof(value));
    return value;
}
static CallFrame invoke_impl(const std::shared_ptr<AdmissionDomain>& domain, void* entry,
                             const std::vector<Argument>& arguments) {
    if (!entry || arguments.size() > 258)
        throw std::invalid_argument("invalid guest call size/entry");
    require_guest_tls_lifetime();
    CallFrame frame;
    std::vector<uint64_t> stack;
    unsigned integer = 0, vector = 0;
    for (const auto& argument : arguments) {
        bool floating =
            argument.type == ArgumentType::Float || argument.type == ArgumentType::Double;
        if (floating && vector < 8) {
            std::memcpy(frame.vectors[vector++], &argument.bits,
                        argument.type == ArgumentType::Float ? 4 : 8);
        } else if (!floating && integer < 8) {
            frame.integers[integer++] = argument.bits;
        } else {
            // AAPCS64 stack arguments occupy eight-byte slots, including narrow
            // values.
            stack.push_back(argument.bits);
        }
    }
    if (stack.size() % 2)
        stack.push_back(0);
    std::optional<AdmissionDomain::Invocation> admission;
    if (domain) {
        // A synchronous host callback can reenter the same domain. Its outer
        // invocation already owns the lease, including while close is draining.
        if (AdmissionDomain::current_invocation() != domain)
            admission.emplace(domain);
        domain->admit_entry(reinterpret_cast<uintptr_t>(entry));
    }
    CaptureBoundary transition;
    if (!guest_tls_prepared())
        prepare_guest_tls();
    uintptr_t sp;
    __asm__ volatile("mov %0, sp" : "=r"(sp));
    // The ordinary and admitted assembly entries reserve 32 and 48 bytes
    // respectively for saved registers before copying the
    // rounded argument area. This bounds that outgoing frame, not the guest's
    // later stack use or the host frames already entered to reach this check.
    const uint64_t reserved = (domain ? 48 : 32) + stack.size() * sizeof(uint64_t);
    if ((sp & 15) || reserved > sp || !prepared_guest_stack_contains(sp) ||
        !prepared_guest_stack_contains(sp - reserved))
        throw std::invalid_argument("guest call stack outside prepared native extent");
    if (domain) {
        AdmissionReturnScope scope(domain.get());
        shiraz_guest_invoke_admitted_asm(&frame, entry, stack.data(),
                                         stack.size() * sizeof(uint64_t), &scope);
    } else {
        shiraz_guest_invoke_asm(&frame, entry, stack.data(), stack.size() * sizeof(uint64_t));
    }
    return frame;
}
CallFrame invoke(void* entry, const std::vector<Argument>& arguments) {
    return invoke_impl({}, entry, arguments);
}
CallFrame invoke_admitted(std::shared_ptr<AdmissionDomain> domain, void* entry,
                          const std::vector<Argument>& arguments) {
    if (!domain)
        throw std::invalid_argument("guest admission domain is required");
    return invoke_impl(domain, entry, arguments);
}
} // namespace shiraz::guest
