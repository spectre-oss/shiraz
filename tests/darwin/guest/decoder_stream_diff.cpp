// SPDX-License-Identifier: GPL-3.0-only
#include <capstone/capstone.h>
#include <cstdint>
#include <cstring>
#include <iostream>

extern "C" bool shiraz_cs_disasm_detail(csh, const uint8_t**, size_t*, uint64_t*, cs_insn*);

static uint64_t fingerprint = 1469598103934665603ull;
static void add(uint64_t value) {
    for (int i = 0; i < 8; ++i) {
        fingerprint = (fingerprint ^ uint8_t(value)) * 1099511628211ull;
        value >>= 8;
    }
}
static void add_text(const char* text) {
    for (; *text; ++text)
        add(uint8_t(*text));
    add(0xff);
}
int main() {
    csh handle;
    if (cs_open(CS_ARCH_ARM64, CS_MODE_ARM, &handle) || cs_option(handle, CS_OPT_DETAIL, CS_OPT_ON))
        return 2;
    auto* instruction = cs_malloc(handle);
    if (!instruction)
        return 3;
    uint64_t random = 0x123456789abcdef0ull;
    unsigned valid = 0;
    for (int index = 0; index < 500000; ++index) {
        random ^= random << 13;
        random ^= random >> 7;
        random ^= random << 17;
        uint32_t word = uint32_t(random);
        uint8_t bytes[4];
        std::memcpy(bytes, &word, sizeof(bytes));
        const uint8_t* input = bytes;
        size_t size = sizeof(bytes);
        uint64_t pc = uint64_t(index) * 4;
        const bool ok = shiraz_cs_disasm_detail(handle, &input, &size, &pc, instruction);
        add(ok);
        if (ok) {
            ++valid;
            add(instruction->id);
            add(instruction->size);
            auto& arm = instruction->detail->arm64;
            add(arm.op_count);
            add(arm.writeback);
            for (unsigned operand = 0; operand < arm.op_count; ++operand) {
                auto& value = arm.operands[operand];
                add(value.type);
                add(value.shift.type);
                add(value.shift.value);
                if (value.type == ARM64_OP_REG)
                    add(value.reg);
                else if (value.type == ARM64_OP_IMM)
                    add(uint64_t(value.imm));
                else if (value.type == ARM64_OP_MEM) {
                    add(value.mem.base);
                    add(value.mem.index);
                    add(uint64_t(value.mem.disp));
                }
            }
            cs_regs reads{}, writes{};
            uint8_t read_count = 0, write_count = 0;
            add(cs_regs_access(handle, instruction, reads, &read_count, writes, &write_count));
            add(write_count);
            for (unsigned item = 0; item < write_count; ++item)
                add(writes[item]);
        }
        input = bytes;
        size = sizeof(bytes);
        pc = uint64_t(index) * 4;
        const bool normal = cs_disasm_iter(handle, &input, &size, &pc, instruction);
        add(normal);
        if (normal) {
            add_text(instruction->mnemonic);
            add_text(instruction->op_str);
        }
    }
    cs_free(instruction, 1);
    cs_close(&handle);
    std::cout << "valid=" << valid << " hash=" << fingerprint << '\n';
}
