#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Exercise real app startup in a disposable, sandboxed ART process."""

import argparse
import collections
import json
import os
import re
import resource
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

from art_smoke import HOST, ROOT

sys.path.insert(0, str(ROOT / "src"))
from shiraz.apk import Dex, inspect_apk  # noqa: E402
from shiraz.runtime import (  # noqa: E402
    application_metadata,
    art_execution_options,
    launch_command,
    package_timestamps,
    properties_text,
    runtime_inputs,
)


def quote(path):
    return json.dumps(str(Path(path).resolve()))


def diagnostic_instruction(data, dex_pc, descriptor, method_signature):
    """Read an instruction in an exact, bounded diagnostic method."""
    dex = Dex(data)
    signature = descriptor + "->" + method_signature
    if dex_pc < 0:
        raise ValueError("Negative DEX program counter")

    def read_uleb(pos):
        value = 0
        for shift in range(0, 35, 7):
            dex.bounds(pos, 1)
            byte = data[pos]
            pos += 1
            if shift == 28 and byte > 15:
                raise ValueError("Oversized DEX ULEB")
            value |= (byte & 127) << shift
            if byte < 128:
                return value, pos
        raise ValueError("Invalid DEX ULEB")

    matches = []
    for entry in dex.table(96, 32):
        if dex.index(dex.types, dex.u32(entry)) != descriptor:
            continue
        position = dex.u32(entry + 24)
        if not position:
            raise ValueError("Diagnostic class has no class data")
        counts = []
        for _ in range(4):
            count, position = read_uleb(position)
            if count > 1_000_000:
                raise ValueError("Oversized DEX class data")
            counts.append(count)
        for _ in range(counts[0] + counts[1]):
            _, position = read_uleb(position)
            _, position = read_uleb(position)
        for count in counts[2:]:
            index = 0
            for _ in range(count):
                delta, position = read_uleb(position)
                index += delta
                _, position = read_uleb(position)
                code, position = read_uleb(position)
                if dex.index(dex.methods, index) != signature:
                    continue
                if code < 112:
                    raise ValueError("Diagnostic method has no code item")
                dex.bounds(code, 16)
                size = dex.u32(code + 12)
                dex.bounds(code + 16, size * 2)
                if dex_pc >= size:
                    raise ValueError("DEX program counter outside method")
                word = dex.u16(code + 16 + dex_pc * 2)
                opcode = word & 255
                registers = "unavailable"
                if opcode in (0x93, 0x94, 0x9E, 0x9F) and dex_pc + 1 < size:
                    operands = dex.u16(code + 16 + (dex_pc + 1) * 2)
                    registers = f"dest={word >> 8},left={operands & 255},right={operands >> 8}"
                elif opcode in (0xB3, 0xB4, 0xBE, 0xBF):
                    registers = f"dest_left={(word >> 8) & 15},right={word >> 12}"
                spec_text = (
                    ROOT / "context/art_standalone/art/libdexfile/dex/dex_instruction_list.h"
                ).read_text()
                specifications = {
                    int(op, 16): (name, fmt)
                    for op, name, fmt in re.findall(
                        r'^\s*V\(0x([0-9A-Fa-f]{2}),\s*\w+,\s*"([^"]+)",\s*(k\w+),',
                        spec_text,
                        re.M,
                    )
                }
                widths = {
                    **dict.fromkeys(("k10t", "k10x", "k11n", "k11x", "k12x"), 1),
                    **dict.fromkeys(
                        (
                            "k20t",
                            "k21c",
                            "k21h",
                            "k21s",
                            "k21t",
                            "k22b",
                            "k22c",
                            "k22s",
                            "k22t",
                            "k22x",
                            "k23x",
                        ),
                        2,
                    ),
                    **dict.fromkeys(("k30t", "k31c", "k31i", "k31t", "k32x", "k35c", "k3rc"), 3),
                    **dict.fromkeys(("k45cc", "k4rcc"), 4),
                    "k51l": 5,
                }
                units = [dex.u16(code + 16 + i * 2) for i in range(size)]
                instructions = []
                pc = 0
                while pc < size:
                    instruction = units[pc]
                    op = instruction & 255
                    if op == 0 and instruction >> 8 in (1, 2, 3):
                        if pc + 4 > size:
                            raise ValueError("Truncated DEX payload")
                        kind = instruction >> 8
                        count = (
                            units[pc + 1] if kind != 3 else units[pc + 2] | (units[pc + 3] << 16)
                        )
                        width = (
                            4 + count * 2
                            if kind == 1
                            else 2 + count * 4
                            if kind == 2
                            else 4 + (units[pc + 1] * count + 1) // 2
                        )
                        name = "payload"
                    else:
                        if op not in specifications or specifications[op][1] not in widths:
                            raise ValueError("Unknown DEX instruction")
                        name, fmt = specifications[op]
                        width = widths[fmt]
                    if width < 1 or pc + width > size:
                        raise ValueError("Invalid DEX instruction width")
                    instructions.append((pc, instruction, name, width))
                    pc += width
                indices = [i for i, item in enumerate(instructions) if item[0] == dex_pc]
                if len(indices) != 1:
                    raise ValueError("DEX PC is not an instruction boundary")
                index = indices[0]
                context = []
                for address, instruction, name, width in instructions[
                    max(0, index - 18) : index + 1
                ]:
                    detail = ""
                    if (
                        instruction & 255 in (0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A)
                        and width == 2
                    ):
                        operands = units[address + 1]
                        detail = (
                            f" dest={instruction >> 8} array={operands & 255} index={operands >> 8}"
                        )
                    elif 0x52 <= instruction & 255 <= 0x5F and width == 2:
                        field = dex.index(dex.fields, units[address + 1])
                        allowed = (
                            "->asBinder:",
                            "->notify:",
                            "->ArtificialStackFrames:",
                            "->INotificationSideChannelDefault:",
                        )
                        if field.startswith(descriptor) and any(
                            token in field for token in allowed
                        ):
                            detail = " field=" + field.split("->", 1)[1].split(":", 1)[0]
                    elif instruction & 255 == 0xD8 and width == 2:
                        operands = units[address + 1]
                        literal = (operands >> 8) - (256 if operands & 0x8000 else 0)
                        detail = f" dest={instruction >> 8} source={operands & 255}"
                        if -4 <= literal <= 4:
                            detail += f" small_literal={literal}"
                    elif instruction & 255 in (0x6E, 0x6F, 0x70, 0x71, 0x72) and width == 3:
                        called = dex.index(dex.methods, units[address + 1])
                        if (
                            called
                            == "Lcom/google/internal/getMeteringPointsAe;->CoroutineDebuggingKt(I)I"
                        ):
                            detail = " target=interpreter"
                    elif instruction & 255 == 0x13 and width == 2:
                        literal = struct.unpack("<h", struct.pack("<H", units[address + 1]))[0]
                        if literal == 240:
                            detail = f" dest={instruction >> 8} case_literal=240"
                    context.append(f"pc={address} op={name}{detail}")
                switch_targets = []
                for address, instruction, name, width in instructions:
                    if name not in ("packed-switch", "sparse-switch"):
                        continue
                    offset = struct.unpack(
                        "<i", struct.pack("<HH", units[address + 1], units[address + 2])
                    )[0]
                    payload = address + offset
                    if payload < 0 or payload + 4 > size:
                        raise ValueError("Invalid DEX switch payload")
                    ident, count = units[payload], units[payload + 1]
                    if count > 10000:
                        raise ValueError("Oversized DEX switch")
                    if ident == 0x100 and name == "packed-switch":
                        if payload + 4 + count * 2 > size:
                            raise ValueError("Truncated packed switch")
                        first = struct.unpack(
                            "<i", struct.pack("<HH", units[payload + 2], units[payload + 3])
                        )[0]
                        keys = range(first, first + count)
                        target_base = payload + 4
                    elif ident == 0x200 and name == "sparse-switch":
                        if payload + 2 + count * 4 > size:
                            raise ValueError("Truncated sparse switch")
                        keys = [
                            struct.unpack(
                                "<i",
                                struct.pack(
                                    "<HH", units[payload + 2 + i * 2], units[payload + 3 + i * 2]
                                ),
                            )[0]
                            for i in range(count)
                        ]
                        target_base = payload + 2 + count * 2
                    else:
                        raise ValueError("DEX switch kind mismatch")
                    for i, key in enumerate(keys):
                        offset = struct.unpack(
                            "<i",
                            struct.pack(
                                "<HH", units[target_base + i * 2], units[target_base + i * 2 + 1]
                            ),
                        )[0]
                        target = address + offset
                        if 0 <= target <= dex_pc:
                            switch_targets.append((target, key, address))
                nearest = max(switch_targets) if switch_targets else None
                matches.append((opcode, registers, context, nearest))
    if len(matches) != 1:
        raise ValueError("Diagnostic method is missing or ambiguous")
    return matches[0]


def application_diagnostic_overlay(
    work,
    env,
    *,
    exact_trace,
    interpreter_state,
    interpreter_array_length,
    text_observe,
    resource_observe,
    api27_keycodes,
    mock_external,
    app_argv_name,
):
    """Compile a disposable runner overlay for post-failure diagnostics."""
    original = (ROOT / "java/shiraz/runtime/ActivityRunner.java").read_text(encoding="utf-8")
    needle = "ProviderHost.attach(context, metadata); app.onCreate();"
    trace_start = (
        """File traceDirectory=new File(args[6],"cache");
            if(!traceDirectory.isDirectory()&&!traceDirectory.mkdirs())throw new java.io.IOException("Cannot create trace directory");
            dalvik.system.VMDebug.startMethodTracing(new File(traceDirectory,"shiraz-methods.trace").getAbsolutePath(),64*1024*1024,0,false,0);"""
        if exact_trace
        else ""
    )
    report_state = (
        """try {
                System.err.println("M5_PLATFORM_LEADER_IDENTITY same="+(android.os.Process.myTid()==android.os.Process.myPid()));
                System.err.println("M5_PLATFORM_THREAD_PRIORITY value="+android.os.Process.getThreadPriority(0));
                System.err.println("M5_PLATFORM_CPU_TIME positive="+(android.os.Process.getElapsedCpuTime()>0));
            } catch(Throwable diagnostic){System.err.println("M5_PLATFORM_THREAD_STATUS_UNAVAILABLE");}
            try {
                Class<?> vmClass=Class.forName("com.google.internal.getMeteringPointsAe",false,app.getClass().getClassLoader());
                java.lang.reflect.Field traceField=Throwable.class.getDeclaredField("backtrace");traceField.setAccessible(true);
                Object traceValue=traceField.get(failure);
                if(traceValue instanceof Object[]){
                    Object[] stackFrames=(Object[])traceValue;
                    if(stackFrames.length>1&&stackFrames[0] instanceof long[]){
                        long[] traceData=(long[])stackFrames[0];int depth=traceData.length/2;
                        if(depth<=256&&stackFrames.length==depth+1){
                            for(int i=0;i<depth;i++){
                                if(stackFrames[i+1]==vmClass)
                                    System.err.println("M5_INTERPRETER_DEX_PC value="+(traceData[depth+i]&0xffffffffL));
                                if(stackFrames[i+1]==app.getClass())
                                    System.err.println("M5_APPLICATION_DEX_PC value="+(traceData[depth+i]&0xffffffffL));
                            }
                        }
                    }
                }
                long count=dalvik.system.VMDebug.countInstancesOfClass(vmClass,false);
                System.err.println("M5_INTERPRETER_HEAP count="+count);
                if(count>0&&count<=64){
                    Object[] instances=dalvik.system.VMDebug.getInstancesOfClasses(new Class[]{vmClass},false)[0];
                    java.lang.reflect.Field position=vmClass.getDeclaredField("notify");position.setAccessible(true);
                    java.lang.reflect.Field values=vmClass.getDeclaredField("asBinder");values.setAccessible(true);
                    java.lang.reflect.Field objects=vmClass.getDeclaredField("INotificationSideChannelStubProxy");objects.setAccessible(true);
                    int limit=Math.min(instances.length,64);
                    for(int i=0;i<limit;i++){
                        int p=position.getInt(instances[i]);int[] stack=(int[])values.get(instances[i]);
                        if(stack==null||p<0||p>=stack.length)continue;
                        String below=p>0?diagnosticIntKind(stack[p-1]):"outside";
                        String top=diagnosticIntKind(stack[p]);
                        String above=p+1<stack.length?diagnosticIntKind(stack[p+1]):"outside";
                        System.err.println("M5_INTERPRETER_STATE index="+i+" position="+p+" stack_length="+stack.length+" below="+below+" top="+top+" above="+above);
                        Object[] objectStack=(Object[])objects.get(instances[i]);
                        if(objectStack!=null&&objectStack.length<=128){
                            for(int slot=0;slot<objectStack.length;slot++)if(objectStack[slot] instanceof int[]){
                                int[] code=(int[])objectStack[slot];int occurrences=0;
                                if(code.length<=65536)for(int word:code)if(word==240)occurrences++;
                                System.err.println("M5_INTERPRETER_ARRAY instance="+i+" slot="+slot+" length="+code.length+" case_occurrences="+occurrences);
                            }
                            for(int slot=0;slot<objectStack.length;slot++)if(objectStack[slot] instanceof java.util.ArrayList){
                                java.util.ArrayList<?> list=(java.util.ArrayList<?>)objectStack[slot];
                                int strings=0,classes=0,methods=0,other=0;
                                if(list.size()<=128)for(Object value:list){
                                    if(value instanceof String)strings++;
                                    else if(value instanceof Class)classes++;
                                    else if(value instanceof java.lang.reflect.Method)methods++;
                                    else other++;
                                }
                                System.err.println("M5_INTERPRETER_LIST instance="+i+" slot="+slot+" size="+list.size()+" strings="+strings+" classes="+classes+" methods="+methods+" other="+other);
                            }
                        }
                    }
                }
            } catch(Throwable diagnostic){System.err.println("M5_INTERPRETER_HEAP_UNAVAILABLE");}"""
        if interpreter_state
        else ""
    )
    trace_stop = "dalvik.system.VMDebug.stopMethodTracing();" if exact_trace else ""
    if interpreter_array_length:
        report_state += """try {
                long total = dalvik.system.VMDebug.countInstancesOfClass(int[].class, false);
                int matched = 0; int[] candidate = null;
                if (total >= 0 && total <= 20000) {
                    Object[] arrays = dalvik.system.VMDebug.getInstancesOfClasses(new Class[]{int[].class}, false)[0];
                    if (arrays.length <= 20000) {
                        for (Object array : arrays)
                            if (((int[]) array).length == ARRAY_LENGTH) { matched++; candidate = (int[]) array; }
                    } else total = -1;
                }
                System.err.println("M5_INTERPRETER_ARRAY_SCAN total="+total+" requested_length=ARRAY_LENGTH matches="+matched);
                if (matched == 1) {
                    long stringArraysTotal = dalvik.system.VMDebug.countInstancesOfClass(String[].class, false);
                    int sizedArrays = 0, matchingArrays = 0;
                    if (stringArraysTotal >= 0 && stringArraysTotal <= 20000) {
                        Object[] arrays = dalvik.system.VMDebug.getInstancesOfClasses(new Class[]{String[].class}, false)[0];
                        if (arrays.length <= 20000) for (Object item : arrays) {
                            String[] parts = (String[]) item;
                            if (parts.length != candidate.length) continue;
                            sizedArrays++; boolean same = true;
                            for (int j=0; j<parts.length; j++) try {
                                if (parts[j] == null || Integer.parseInt(parts[j]) != candidate[j]) { same=false; break; }
                            } catch (NumberFormatException invalid) { same=false; break; }
                            if (same) matchingArrays++;
                        } else stringArraysTotal = -1;
                    }
                    System.err.println("M5_INTERPRETER_SPLIT_SCAN total="+stringArraysTotal+" sized="+sizedArrays+" exact="+matchingArrays);
                    long stringsTotal = dalvik.system.VMDebug.countInstancesOfClass(String.class, false);
                    int compared = 0, equal = 0; long examined = 0;
                    if (stringsTotal >= 0 && stringsTotal <= 100000) {
                        Object[] strings = dalvik.system.VMDebug.getInstancesOfClasses(new Class[]{String.class}, false)[0];
                        java.util.HashMap<Integer,Integer> tokens = new java.util.HashMap<Integer,Integer>();
                        if (strings.length <= 100000) for (Object item : strings) {
                            String s = (String) item;
                            if (s.length() > 0 && s.length() <= 11) try {
                                int parsed = Integer.parseInt(s);
                                Integer previous = tokens.get(parsed);
                                tokens.put(parsed, previous == null ? 1 : previous + 1);
                            } catch (NumberFormatException ignored) { }
                            if (s.length() < ARRAY_LENGTH || s.length() > 32768) continue;
                            if (examined + s.length() > 16 * 1024 * 1024) break;
                            examined += s.length(); compared++;
                            if (diagnosticProgramMatches(s, candidate)) equal++;
                        }
                        else stringsTotal = -1;
                        int tokenMatches = 0;
                        if (stringsTotal >= 0) for (int value : candidate) {
                            Integer remaining = tokens.get(value);
                            if (remaining != null && remaining > 0) {
                                tokenMatches++; tokens.put(value, remaining - 1);
                            }
                        }
                        System.err.println("M5_INTERPRETER_TOKEN_SCAN matched="+tokenMatches+" requested_length=ARRAY_LENGTH");
                    }
                    System.err.println("M5_INTERPRETER_TEXT_SCAN total="+stringsTotal+" compared="+compared+" matched="+equal);
                }
            } catch (Throwable diagnostic) { System.err.println("M5_INTERPRETER_ARRAY_SCAN unavailable=1"); }
""".replace("ARRAY_LENGTH", str(interpreter_array_length))
    mock_setup = (
        '''ExternalLauncher.setRunner(new ExternalLauncher.Runner() {
                public void run(java.util.List<String> command) {
                    if(command.size()!=2)throw new IllegalStateException("Unexpected external command");
                    System.err.println("M5_PROBE_EXTERNAL_OPEN mocked=1");
                }
            });'''
        if mock_external
        else ""
    )
    replacement = (
        mock_setup
        + """ProviderHost.attach(context, metadata);
            """
        + ("android.content.res.Resources.shirazProbeEnable();" if resource_observe else "")
        + trace_start
        + """
            try { app.onCreate(); }
            catch(Throwable failure) { """
        + report_state
        + """ throw failure; }
            finally { """
        + trace_stop
        + """ }
"""
    )
    if original.count(needle) != 1:
        raise ValueError("ActivityRunner application trace insertion point changed")
    source = work / "overlay-source/shiraz/runtime/ActivityRunner.java"
    source.parent.mkdir(parents=True)
    transformed = original.replace(needle, replacement)
    if app_argv_name:
        process_marker = "Application.shirazSetProcessName(name);ServiceProcess.configure(args);"
        if transformed.count(process_marker) != 1:
            raise ValueError("ActivityRunner process name insertion point changed")
        transformed = transformed.replace(
            process_marker,
            process_marker
            + 'android.os.Process.setArgV0(name);System.err.println("M5_PROBE_APP_ARGV_NAME set=1");',
        )
    if interpreter_state:
        marker = "    public static void main(final String[] args) {"
        helper = """    private static String diagnosticIntKind(int value) {
        return value == 0 ? "zero" : value == 1 ? "one" : value == -1 ? "minus_one" : "other";
    }
"""
        if interpreter_array_length:
            helper += """    private static boolean diagnosticProgramMatches(String text, int[] values) {
        int i=0, n=0;
        while (i<text.length()) {
            while (i<text.length() && !(text.charAt(i)>='0' && text.charAt(i)<='9') && text.charAt(i)!='-') i++;
            if (i==text.length()) break;
            int start=i; if (text.charAt(i)=='-') i++;
            int digits=i; while (i<text.length() && text.charAt(i)>='0' && text.charAt(i)<='9') i++;
            if (digits==i) continue;
            if (n==values.length) return false;
            try { if (Integer.parseInt(text.substring(start,i))!=values[n++]) return false; }
            catch (NumberFormatException invalid) { return false; }
        }
        return n==values.length;
    }
"""
        if transformed.count(marker) != 1:
            raise ValueError("ActivityRunner diagnostic helper insertion point changed")
        transformed = transformed.replace(marker, helper + marker)
    source.write_text(transformed, encoding="utf-8")
    sources = [str(source)]
    if resource_observe:
        resource_source = (ROOT / "java/android/content/res/Resources.java").read_text(
            encoding="utf-8"
        )
        marker = "    public synchronized String getString(int id) {"
        wrapper = '''    private static final java.util.concurrent.atomic.AtomicInteger shirazResourceObservations = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile boolean shirazResourceProbeEnabled = false;
    public static void shirazProbeEnable() { shirazResourceProbeEnabled = true; }
    public synchronized String getString(int id) {
        String value = shirazObservedString(id);
        if (shirazResourceProbeEnabled && shirazResourceObservations.getAndIncrement() < 32)
            System.err.println("SHIRAZ_PROBE_RESOURCE_STRING id="+Integer.toHexString(id)+" length="+value.length());
        return value;
    }
    private synchronized String shirazObservedString(int id) {'''
        if resource_source.count(marker) != 1:
            raise ValueError("Resources diagnostic insertion point changed")
        resource_source = resource_source.replace(marker, wrapper)
        resource_path = work / "overlay-source/android/content/res/Resources.java"
        resource_path.parent.mkdir(parents=True, exist_ok=True)
        resource_path.write_text(resource_source, encoding="utf-8")
        sources.append(str(resource_path))
    if text_observe:
        text_source = (ROOT / "java/android/text/TextUtils.java").read_text(encoding="utf-8")
        marker = "    public static int getOffsetBefore(CharSequence text, int offset) {"
        wrapper = '''    private static int shirazTextObservations = 0;
    public static int getOffsetBefore(CharSequence text, int offset) {
        int result = shirazObservedOffsetBefore(text, offset);
        if (shirazTextObservations++ < 32)
            System.err.println("SHIRAZ_PROBE_TEXT_BEFORE empty="+(text != null && text.length() == 0)+" offset_kind="+(offset==0?"zero":offset==1?"one":offset<0?"negative":offset<=text.length()?"in_range":"out_of_range")+" result_kind="+(result==0?"zero":result==1?"one":"other"));
        return result;
    }
    private static int shirazObservedOffsetBefore(CharSequence text, int offset) {'''
        if text_source.count(marker) != 1:
            raise ValueError("TextUtils diagnostic insertion point changed")
        text_source = text_source.replace(marker, wrapper)
        search_marker = "    public static int indexOf(CharSequence text, char value, int start) {"
        search_wrapper = '''    private static int shirazTextSearchObservations = 0;
    public static int indexOf(CharSequence text, char value, int start) {
        boolean comparable = text != null && text.getClass() == String.class;
        int expected = comparable ? ((String) text).indexOf(value, start) : 0;
        if (comparable && shirazTextSearchObservations++ == 0)
            System.err.println("SHIRAZ_PROBE_TEXT_STRING_SEARCH observed=1");
        try {
            int result = shirazObservedStringIndex(text, value, start);
            if (comparable && result != expected)
                System.err.println("SHIRAZ_PROBE_TEXT_STRING_SEARCH mismatch=result");
            return result;
        } catch (RuntimeException failure) {
            if (comparable) System.err.println("SHIRAZ_PROBE_TEXT_STRING_SEARCH mismatch=exception");
            throw failure;
        }
    }
    private static int shirazObservedStringIndex(CharSequence text, char value, int start) {'''
        if text_source.count(search_marker) != 1:
            raise ValueError("TextUtils string search diagnostic insertion point changed")
        text_source = text_source.replace(search_marker, search_wrapper)
        text_path = work / "overlay-source/android/text/TextUtils.java"
        text_path.parent.mkdir(parents=True)
        text_path.write_text(text_source, encoding="utf-8")
        sources.append(str(text_path))
    if api27_keycodes:
        key_source = (ROOT / "java/android/view/KeyEvent.java").read_text(encoding="utf-8")
        marker = "    public static int getMaxKeyCode() { return Names.max; }"
        if key_source.count(marker) != 1:
            raise ValueError("KeyEvent diagnostic insertion point changed")
        key_source = key_source.replace(
            marker,
            "    private static int shirazProbeKeyEvents;\n"
            "    public static int getMaxKeyCode() { if(shirazProbeKeyEvents++ < 32) "
            'System.err.println("SHIRAZ_PROBE_API27_MAX_KEYCODE used=1"); return KEYCODE_ALL_APPS; }',
        )
        key_path = work / "overlay-source/android/view/KeyEvent.java"
        key_path.parent.mkdir(parents=True, exist_ok=True)
        key_path.write_text(key_source, encoding="utf-8")
        sources.append(str(key_path))
    classes = work / "overlay-classes"
    classes.mkdir()
    core = (
        ROOT
        / "context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar"
    )
    subprocess.run(
        [
            "javac",
            "--release",
            "8",
            "-cp",
            str(ROOT / "build/framework/classes") + ":" + str(core),
            "-d",
            str(classes),
            *sources,
        ],
        env=env,
        check=True,
        timeout=60,
        capture_output=True,
    )
    dex = work / "overlay.dex"
    subprocess.run(
        [
            str(HOST / "bin/dx"),
            "--dex",
            "--min-sdk-version=27",
            "--output=" + str(dex),
            str(classes),
        ],
        env=env,
        check=True,
        timeout=60,
        capture_output=True,
    )
    jar = work / "trace-overlay.jar"
    with zipfile.ZipFile(jar, "w") as archive:
        entry = zipfile.ZipInfo("classes.dex")
        padding = (-(30 + len(entry.filename) + 4)) % 4
        entry.extra = struct.pack("<HH", 0xFFFF, padding) + b"\0" * padding
        archive.writestr(entry, dex.read_bytes())
    return jar


def match_dex_class_fingerprint(dex, fingerprint):
    """Count ASCII DEX strings/descriptors matching a lookup, without printing them."""
    if len(dex) < 112 or dex[:4] != b"dex\n":
        raise ValueError("Invalid diagnostic DEX")
    count, offset = struct.unpack_from("<II", dex, 56)
    if count > 1_000_000 or offset > len(dex) or count > (len(dex) - offset) // 4:
        raise ValueError("Invalid diagnostic DEX string table")
    matches = 0
    for i in range(count):
        pos = struct.unpack_from("<I", dex, offset + i * 4)[0]
        for _ in range(5):
            if pos >= len(dex):
                raise ValueError("Invalid diagnostic DEX string")
            byte = dex[pos]
            pos += 1
            if byte < 128:
                break
        else:
            raise ValueError("Invalid diagnostic DEX string length")
        end = dex.find(b"\0", pos, min(len(dex), pos + 1024))
        if end < 0:
            continue
        value = dex[pos:end]
        candidates = [value]
        if value.startswith(b"L") and value.endswith(b";"):
            candidates.append(value[1:-1].replace(b"/", b"."))
        for candidate in candidates:
            result = 14695981039346656037
            for byte in candidate:
                result = ((result ^ byte) * 1099511628211) & 0xFFFFFFFFFFFFFFFF
            if result == fingerprint:
                matches += 1
    return matches


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--trace-methods", action="store_true")
    parser.add_argument("--trace-application-exact", action="store_true")
    parser.add_argument("--trace-context-events", type=int, default=120)
    parser.add_argument("--observe-interpreter-state", action="store_true")
    parser.add_argument("--observe-interpreter-array-length", type=int, default=0)
    parser.add_argument("--inspect-interpreter-dex-pc", action="store_true")
    parser.add_argument("--observe-class-lookup", action="store_true")
    parser.add_argument("--observe-gid", action="store_true")
    parser.add_argument("--observe-text", action="store_true")
    parser.add_argument("--observe-resources", action="store_true")
    parser.add_argument("--test-api27-keycodes", action="store_true")
    parser.add_argument("--mock-external", action="store_true")
    parser.add_argument("--observe-guest-fs", action="store_true")
    parser.add_argument("--observe-file-syscalls", action="store_true")
    parser.add_argument("--test-opath-missing", action="store_true")
    parser.add_argument("--observe-map-capabilities", action="store_true")
    parser.add_argument("--observe-cache-text", action="store_true")
    parser.add_argument("--observe-cache-files", action="store_true")
    parser.add_argument("--test-app-argv-name", action="store_true")
    parser.add_argument("--test-proc-cmdline-snapshot", action="store_true")
    parser.add_argument("--test-proc-cmdline-live", action="store_true")
    parser.add_argument("--test-empty-maps", action="store_true")
    parser.add_argument("--probe-small-thread-id", action="store_true")
    parser.add_argument("--verify-thread-id", action="store_true")
    parser.add_argument("--capture-class", help="test-only exact class to inspect in temporary DEX")
    parser.add_argument("--capture-start", type=int, default=0)
    parser.add_argument("--capture-limit", type=int, default=180)
    parser.add_argument("--capture-fallback", action="store_true")
    parser.add_argument("--scan-dispatch-bound", action="store_true")
    parser.add_argument(
        "--match-class-fingerprint", help="compare a lookup to temporary DEX strings"
    )
    parser.add_argument(
        "--capture-pattern", help="show a bounded instruction context around this identifier"
    )
    args = parser.parse_args()
    if args.test_proc_cmdline_snapshot and args.test_proc_cmdline_live:
        raise SystemExit("Choose one command-line diagnostic")
    if (args.observe_cache_text or args.observe_cache_files) and not args.observe_map_capabilities:
        raise SystemExit("Cache coverage requires --observe-map-capabilities")
    if not 1 <= args.trace_context_events <= 2000:
        raise SystemExit("Trace context must be between 1 and 2000 events")
    if args.trace_methods and args.trace_application_exact:
        raise SystemExit("Choose one method tracing mode")
    if not 0 <= args.observe_interpreter_array_length <= 65536 or (
        args.observe_interpreter_array_length and not args.observe_interpreter_state
    ):
        raise SystemExit("Array observation requires interpreter state and a bounded length")
    if args.inspect_interpreter_dex_pc and (
        not args.observe_interpreter_state
        or args.capture_class != "com.google.internal.getMeteringPointsAe"
    ):
        raise SystemExit("DEX PC inspection requires the interpreter state and exact class capture")
    if args.scan_dispatch_bound and (
        args.capture_class != "com.google.internal.ExperimentalMirrorMode"
        or not args.capture_fallback
    ):
        raise SystemExit("Dispatch bound scan requires the exact fallback application capture")
    if (
        args.probe_small_thread_id or args.verify_thread_id or args.observe_gid
    ) and not args.observe_class_lookup:
        raise SystemExit("Thread ID diagnostics require the lookup observer")
    if args.capture_class and not re.fullmatch(
        r"[A-Za-z_$][A-Za-z0-9_$.]{0,200}", args.capture_class
    ):
        raise SystemExit("Invalid diagnostic class name")
    if not 0 <= args.capture_start <= 20000 or not 1 <= args.capture_limit <= 300:
        raise SystemExit("Invalid diagnostic source range")
    if args.capture_pattern and not re.fullmatch(
        r"[A-Za-z_$][A-Za-z0-9_$.]{0,80}", args.capture_pattern
    ):
        raise SystemExit("Invalid diagnostic pattern")
    if args.match_class_fingerprint and (
        not args.capture_class or not re.fullmatch(r"[0-9a-f]{16}", args.match_class_fingerprint)
    ):
        raise SystemExit("A class capture and 16-digit lowercase fingerprint are required")
    apk = args.apk.resolve(strict=True)
    report = inspect_apk(apk)
    manifest = report["manifest"]
    package = manifest["package"]
    application = manifest.get("application_class") or "android.app.Application"
    activity = manifest["launcher_activities"][0]["target"]
    app_lib = ROOT / "build/runs" / report["apk_sha256"] / "app_lib"
    if not app_lib.is_dir() or any(p.is_symlink() for p in app_lib.iterdir()):
        raise SystemExit("Expected an existing non-symlink native library directory")
    vm, framework, resources, required, _ = runtime_inputs("Darwin", ROOT, HOST, "interpreter")
    if any(not p.is_file() for p in required):
        raise SystemExit("Required runtime input missing")
    bridge = ROOT / "build/macos/m5/libshiraz-guest-isolated-app-probe.so"
    if not bridge.is_file():
        raise SystemExit("Isolated NativeBridge is missing")
    with tempfile.TemporaryDirectory(prefix="shiraz-app-probe-", dir="/private/tmp") as name:
        work = Path(name).resolve()
        data = work / "app-data"
        for directory in (
            data,
            work / "cache",
            work / "tmp",
            work / "home",
            work / "android-data/dalvik-cache",
        ):
            directory.mkdir(parents=True, exist_ok=True)
        metadata = work / "application.properties"
        facts = application_metadata(manifest, apk, data)
        facts["nativeLibraryDir"] = str(app_lib)
        facts.update(package_timestamps(data, report["apk_sha256"]))
        metadata.write_text(properties_text(facts), encoding="utf-8")
        safe_env = {
            "PATH": "/usr/bin:/bin:/usr/sbin:/sbin",
            "HOME": str(work / "home"),
            "TMPDIR": str(work / "tmp"),
            "SHIRAZ_HOST_SDL_VIDEODRIVER": "dummy",
        }
        if args.trace_methods:
            safe_env["SHIRAZ_PROFILE_METHODS"] = "1"
        guard = work / "sandbox-guard"
        subprocess.run(
            [
                "xcrun",
                "clang",
                "-std=c11",
                "-Wall",
                "-Wextra",
                "-Werror",
                str(ROOT / "tests/darwin/guest/isolated_sandbox_guard.c"),
                "-o",
                str(guard),
            ],
            env=safe_env,
            check=True,
            timeout=60,
        )
        classpath = str(framework) + ":" + str(apk)
        if (
            args.trace_application_exact
            or args.observe_interpreter_state
            or args.observe_resources
            or args.observe_text
            or args.test_api27_keycodes
            or args.mock_external
            or args.test_app_argv_name
        ):
            overlay = application_diagnostic_overlay(
                work,
                safe_env,
                exact_trace=args.trace_application_exact,
                interpreter_state=args.observe_interpreter_state,
                interpreter_array_length=args.observe_interpreter_array_length,
                text_observe=args.observe_text,
                resource_observe=args.observe_resources,
                api27_keycodes=args.test_api27_keycodes,
                mock_external=args.mock_external,
                app_argv_name=args.test_app_argv_name,
            )
            classpath = str(overlay) + ":" + classpath
        command, env = launch_command(
            "Darwin",
            root=ROOT,
            host=HOST,
            environ=safe_env,
            vm=vm,
            classpath=classpath,
            framework_resources=resources,
            work=work,
            data=data,
            app_lib=app_lib,
            metadata=metadata,
            package=package,
            app=application,
            activity=activity,
            width=480,
            height=800,
            frames=1,
            snapshot=None,
            log_level="WARN",
            execution_options=art_execution_options("interpreter"),
            device_profile=None,
            profile_values={},
            digitalis=None,
            version=(ROOT / "VERSION").read_text().strip(),
            gles=False,
            guest_bridge=bridge,
        )
        env["SHIRAZ_LOCAL_SOCKET_DIR"] = str(work / "sockets")
        env["SHIRAZ_SANDBOX_SENTINEL"] = str(ROOT / "VERSION")
        env["SHIRAZ_GUEST_TRACE_IMAGES"] = "1"
        if args.observe_guest_fs:
            env["SHIRAZ_PROBE_FS"] = "1"
        if args.observe_file_syscalls:
            env["SHIRAZ_PROBE_FILE_SYSCALLS"] = "1"
        if args.test_opath_missing:
            env["SHIRAZ_PROBE_OPATH_MISSING"] = "1"
        if args.observe_map_capabilities:
            env["SHIRAZ_PROBE_MAP_CAPABILITIES"] = "1"
        if args.test_proc_cmdline_snapshot or args.test_proc_cmdline_live:
            env[
                "SHIRAZ_PROBE_CMDLINE_LIVE"
                if args.test_proc_cmdline_live
                else "SHIRAZ_PROBE_CMDLINE_SNAPSHOT"
            ] = "1"
            env["SHIRAZ_PROBE_EXPECTED_PROCESS_NAME"] = package
        if args.test_empty_maps:
            env["SHIRAZ_PROBE_EMPTY_MAPS"] = "1"
        if args.observe_class_lookup:
            env["SHIRAZ_PROBE_CLASS_LOOKUP"] = "1"
            env["SHIRAZ_PROBE_APP_PACKAGE"] = package
        if args.probe_small_thread_id:
            env["SHIRAZ_PROBE_SMALL_TID"] = "1"
        if args.verify_thread_id:
            env["SHIRAZ_PROBE_VERIFY_TID"] = "1"
        if args.observe_gid:
            env["SHIRAZ_PROBE_OBSERVE_GID"] = "1"
        if args.capture_class:
            env["SHIRAZ_PROBE_DEX_CLASS"] = "L" + args.capture_class.replace(".", "/") + ";"
            env["SHIRAZ_PROBE_DEX_OUTPUT"] = str(work / "diagnostic.dex")
        allowed = [
            HOST,
            apk,
            framework,
            resources,
            app_lib,
            ROOT / ".local/lib",
            ROOT / ".local/share/shiraz/fonts",
            bridge,
        ]
        allowed += [
            p
            for p in (ROOT / "build/macos").iterdir()
            if p.is_file() and p.suffix in (".so", ".dylib")
        ]
        font_conf = Path(env["FONTCONFIG_FILE"])
        allowed.append(font_conf)
        reads = " ".join(
            ("(subpath " if p.is_dir() else "(literal ") + quote(p) + ")" for p in allowed
        )
        profile = (
            "(version 1) (allow default) (deny network*) (deny file-write*) "
            "(deny file-read-data (subpath \"/Users\") (subpath \"/Volumes\") "
            "(subpath \"/private/tmp\") (subpath \"/private/var/folders\") "
            "(subpath \"/Library/Keychains\")) "
            "(allow file-read-data " + reads + " (subpath " + quote(work) + ")) "
            "(allow file-write* (subpath " + quote(work) + ")) "
            "(deny process-fork) (deny process-exec*) (allow process-exec (literal "
            + quote(vm)
            + ") (literal "
            + quote(guard)
            + ")) (deny signal (target others)) "
            "(deny mach-lookup (global-name \"com.apple.pasteboard.1\") "
            "(global-name \"com.apple.securityd\") (global-name \"com.apple.securityd.xpc\") "
            "(global-name \"com.apple.cfprefsd.agent\") (global-name \"com.apple.cfprefsd.daemon\")) "
            "(deny iokit-open)"
        )
        with tempfile.NamedTemporaryFile(
            prefix="sandbox-sentinel-", dir=ROOT / "build/macos/m5"
        ) as sentinel:
            sentinel.write(b"sentinel")
            sentinel.flush()
            check = subprocess.run(
                [
                    "/usr/bin/sandbox-exec",
                    "-p",
                    profile,
                    str(guard),
                    sentinel.name,
                    str(apk),
                    str(work / "guard"),
                ],
                env=safe_env,
                capture_output=True,
                text=True,
                timeout=20,
            )
            print(check.stdout + check.stderr, end="", flush=True)
            if check.returncode or "M5_OUTER_SANDBOX_PASS" not in check.stdout:
                raise SystemExit("Outer sandbox preflight failed")

        def no_core():
            resource.setrlimit(resource.RLIMIT_CORE, (0, 0))

        catalog_fds = []

        def prepare_catalog(command, record_size, limit, variable):
            catalog = subprocess.run(command, env=safe_env, capture_output=True, timeout=30)
            if (
                catalog.returncode
                or len(catalog.stdout) < 20
                or len(catalog.stdout) > 20 + limit * record_size
            ):
                raise SystemExit("Cache helper failed or returned an invalid catalog")
            count = struct.unpack_from("<I", catalog.stdout, 16)[0]
            if not count or count > limit or len(catalog.stdout) != 20 + count * record_size:
                raise SystemExit("Cache helper returned a malformed catalog")
            print(catalog.stderr.decode("utf-8", errors="replace"), end="", flush=True)
            with tempfile.NamedTemporaryFile(dir=work / "tmp", delete=False) as temporary:
                temporary.write(catalog.stdout)
                temporary_name = temporary.name
            try:
                fd = os.open(temporary_name, os.O_RDONLY | os.O_NOFOLLOW)
                catalog_fds.append(fd)
            finally:
                os.unlink(temporary_name)
            env[variable] = str(fd)

        try:
            if args.observe_cache_text:
                helper = ROOT / "build/macos/m5/dyld-text-catalog"
                if not helper.is_file():
                    raise SystemExit(
                        "Build the cache helper with make macos-guest-dyld-text-catalog"
                    )
                prepare_catalog([str(helper)], 16, 65536, "SHIRAZ_PROBE_CACHE_CATALOG_FD")
            if args.observe_cache_files:
                prepare_catalog(
                    [sys.executable, "-I", str(ROOT / "scripts/darwin_cache_mapping_catalog.py")],
                    40,
                    4096,
                    "SHIRAZ_PROBE_CACHE_FILE_CATALOG_FD",
                )
            result = subprocess.run(
                ["/usr/bin/sandbox-exec", "-p", profile, *map(str, command)],
                cwd=data,
                env=env,
                capture_output=True,
                text=True,
                errors="replace",
                timeout=40,
                preexec_fn=no_core,
                pass_fds=tuple(catalog_fds),
            )
        except subprocess.TimeoutExpired:
            print("M5_ISOLATED_APP_TIMEOUT")
            raise SystemExit(1)
        finally:
            for fd in catalog_fds:
                os.close(fd)
        print("M5_ISOLATED_APP_RESULT returncode=" + str(result.returncode), flush=True)
        print(result.stdout + result.stderr)
        captured = work / "diagnostic.dex"
        if args.capture_class and captured.is_file():
            if args.inspect_interpreter_dex_pc:
                pcs = re.findall(r"^M5_INTERPRETER_DEX_PC value=(\d+)$", result.stderr, re.M)
                if len(pcs) != 1:
                    raise SystemExit("Expected exactly one interpreter DEX PC")
                dex_data = captured.read_bytes()
                opcode, registers, context, nearest = diagnostic_instruction(
                    dex_data,
                    int(pcs[0]),
                    "Lcom/google/internal/getMeteringPointsAe;",
                    "CoroutineDebuggingKt(I)I",
                )
                print(f"M5_INTERPRETER_INSTRUCTION opcode=0x{opcode:02x} {registers}")
                if nearest:
                    print(
                        f"M5_INTERPRETER_NEAREST_SWITCH target_pc={nearest[0]} case={nearest[1]} switch_pc={nearest[2]}"
                    )
                for instruction in context:
                    print("M5_INTERPRETER_INSTRUCTION_CONTEXT " + instruction)
                app_pcs = re.findall(r"^M5_APPLICATION_DEX_PC value=(\d+)$", result.stderr, re.M)
                if len(app_pcs) == 1:
                    opcode, registers, context, nearest = diagnostic_instruction(
                        dex_data,
                        int(app_pcs[0]),
                        "Lcom/google/internal/ExperimentalMirrorMode;",
                        "onCreate()V",
                    )
                    print(f"M5_APPLICATION_INSTRUCTION opcode=0x{opcode:02x} {registers}")
                    if nearest:
                        print(
                            f"M5_APPLICATION_NEAREST_SWITCH target_pc={nearest[0]} case={nearest[1]} switch_pc={nearest[2]}"
                        )
                    for instruction in context:
                        print("M5_APPLICATION_INSTRUCTION_CONTEXT " + instruction)
            if args.match_class_fingerprint:
                matches = match_dex_class_fingerprint(
                    captured.read_bytes(), int(args.match_class_fingerprint, 16)
                )
                print("M5_PROBE_DEX_FINGERPRINT_MATCHES count=" + str(matches))
            source = work / "diagnostic.java"
            decompiler_env = dict(
                safe_env,
                JADX_CONFIG_DIR=str(work / "jadx-config"),
                JADX_CACHE_DIR=str(work / "jadx-cache"),
                JADX_TMP_DIR=str(work / "tmp"),
            )
            decompile_command = [
                "/opt/homebrew/bin/jadx",
                "-r",
                "-j",
                "1",
                "--rename-flags",
                "none",
                "--single-class",
                args.capture_class,
                "--single-class-output",
                str(source),
                str(captured),
            ]
            if args.capture_fallback:
                decompile_command[1:1] = ["--decompilation-mode", "fallback"]
            decompiler = subprocess.run(
                decompile_command,
                env=decompiler_env,
                cwd=work,
                capture_output=True,
                text=True,
                timeout=60,
            )
            print("M5_PROBE_CLASS_DECOMPILE exit=" + str(decompiler.returncode))
            if source.is_file() and source.stat().st_size <= 4 * 1024 * 1024:
                text = source.read_text(encoding="utf-8", errors="replace")
                text = re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', '"<redacted>"', text)
                if args.scan_dispatch_bound:
                    sanitized = re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.S)
                    marker = "public void onCreate() {"
                    start = sanitized.find(marker)
                    count = -1
                    if start >= 0:
                        opening = sanitized.find("{", start)
                        depth = 0
                        for end in range(opening, len(sanitized)):
                            if sanitized[end] == "{":
                                depth += 1
                            elif sanitized[end] == "}":
                                depth -= 1
                                if depth == 0:
                                    body = sanitized[opening:end]
                                    count = len(re.findall(r"\b(?:8192|0x2000)\b", body, re.I))
                                    matching = [
                                        line
                                        for line in body.splitlines()
                                        if re.search(r"\b(?:8192|0x2000)\b", line, re.I)
                                    ]
                                    direct = sum(
                                        bool(re.search(r"=\s*(?:8192|0x2000)\b", line, re.I))
                                        for line in matching
                                    )
                                    print(
                                        f"M5_PROBE_ONCREATE_8192_LINES lines={len(matching)} direct_assignments={direct}"
                                    )
                                    if len(matching) == 1:
                                        target = matching[0].strip()
                                        original_lines = text.splitlines()
                                        positions = [
                                            i
                                            for i, line in enumerate(original_lines)
                                            if line.split("//", 1)[0].strip() == target
                                        ]
                                        if len(positions) == 1:
                                            print(
                                                "M5_PROBE_ONCREATE_8192_SOURCE_LINE index="
                                                + str(positions[0])
                                            )
                                    break
                    print("M5_PROBE_ONCREATE_8192_LITERAL count=" + str(count))
                if not args.capture_fallback:
                    text = re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.S)
                text = re.sub(r"\b(?:0x[0-9a-fA-F]+|\d+)[lLfFdD]?\b", "N", text)
                lines = text.splitlines()
                print("M5_PROBE_CLASS_LINES total=" + str(len(lines)))
                selected = range(
                    args.capture_start, min(len(lines), args.capture_start + args.capture_limit)
                )
                if args.capture_pattern:
                    matches = [
                        i
                        for i, line in enumerate(lines)
                        if i >= args.capture_start
                        and (
                            (args.capture_pattern == "__division__" and re.search(r"\s/\s", line))
                            or (
                                args.capture_pattern != "__division__"
                                and args.capture_pattern in line
                            )
                        )
                    ]
                    for i in matches:
                        if re.search(r"\b(?:public|private|protected)\s+static\b.*\(", lines[i]):
                            print(
                                "M5_PROBE_CLASS_DECL index=" + str(i) + " " + lines[i].strip()[:220]
                            )
                    selected = sorted(
                        {j for i in matches for j in range(max(0, i - 12), min(len(lines), i + 13))}
                    )[: args.capture_limit]
                    print("M5_PROBE_CLASS_MATCHES count=" + str(len(matches)))
                for index in selected:
                    line = lines[index]
                    if line.strip():
                        print("M5_PROBE_CLASS_LINE index=" + str(index) + " " + line.strip()[:220])
        elif args.capture_class:
            print("M5_PROBE_DEX_UNAVAILABLE")
        trace = data / "cache/shiraz-methods.trace"
        if (args.trace_methods or args.trace_application_exact) and trace.is_file():
            raw = trace.read_bytes()
            end = raw.find(b"*end\n")
            if end >= 0:
                header = raw[:end].decode("utf-8", errors="replace")
                methods = {}
                threads = {}
                section = ""
                for line in header.splitlines():
                    if line.startswith("*"):
                        section = line
                    elif section == "*methods":
                        parts = line.split("\t")
                        if len(parts) >= 3:
                            try:
                                methods[int(parts[0], 0)] = parts[1] + "." + parts[2]
                            except ValueError:
                                pass
                    elif section == "*threads":
                        parts = line.split("\t", 1)
                        if len(parts) == 2 and parts[0].isdigit():
                            threads[int(parts[0]) & 0xFFFF] = parts[1]
                offset = end + len(b"*end\n")
                if len(raw) >= offset + 18 and raw[offset : offset + 4] == b"SLOW":
                    version, data_offset = struct.unpack_from("<HH", raw, offset + 4)
                    record_size = (
                        10 if version == 2 else struct.unpack_from("<H", raw, offset + 16)[0]
                    )
                    events = collections.deque(maxlen=80)
                    context = collections.deque(maxlen=args.trace_context_events)
                    interpreter_unwind = []
                    recent_platform = collections.OrderedDict()
                    platform_before_interpreter_unwind = []
                    filesystem_calls = collections.Counter()
                    conversion_calls = collections.Counter()
                    conversion_first = []
                    conversion_steps = []
                    before_first_dispatch = collections.Counter()
                    first_dispatch_seen = False
                    interpreter_actions = collections.Counter()
                    event_count = 0
                    if version in (2, 3) and record_size in (10, 14):
                        for pos in range(
                            offset + data_offset, len(raw) - record_size + 1, record_size
                        ):
                            tid, method = struct.unpack_from("<HI", raw, pos)
                            method_name = methods.get(method & ~3, "")
                            if threads.get(tid) == "main" and method_name.startswith(
                                (
                                    "android.",
                                    "java.",
                                    "dalvik.",
                                    "libcore.io.",
                                    "o.",
                                    "com.google.internal.",
                                )
                            ):
                                event = (method & 3, method_name)
                                events.append(event)
                                context.append(event)
                                event_count += 1
                                if (
                                    method_name
                                    == "com.google.internal.getMeteringPointsAe.CoroutineDebuggingKt"
                                ):
                                    interpreter_actions[event[0]] += 1
                                if event[0] == 0 and not first_dispatch_seen:
                                    if (
                                        method_name
                                        == "com.google.internal.getMeteringPointsAe.CoroutineDebuggingKt"
                                    ):
                                        first_dispatch_seen = True
                                    elif method_name in (
                                        "android.content.res.Resources.getString",
                                        "java.lang.String.split",
                                        "java.lang.Integer.parseInt",
                                        "java.lang.Long.parseLong",
                                        "java.lang.reflect.Method.invoke",
                                    ):
                                        before_first_dispatch[method_name] += 1
                                if event[0] == 0 and method_name in (
                                    "android.content.res.Resources.getString",
                                    "java.lang.String.split",
                                    "java.lang.Integer.parseInt",
                                    "java.lang.Long.parseLong",
                                    "java.lang.reflect.Method.invoke",
                                    "com.google.internal.getMeteringPointsAe.CoroutineDebuggingKt",
                                ):
                                    if len(conversion_steps) < 100 and (
                                        not conversion_steps or conversion_steps[-1] != method_name
                                    ):
                                        conversion_steps.append(method_name)
                                if event[0] == 0 and method_name.startswith(
                                    (
                                        "java.lang.String.",
                                        "java.lang.Integer.",
                                        "java.lang.Long.",
                                        "java.lang.Character.",
                                        "java.util.regex.",
                                    )
                                ):
                                    conversion_calls[method_name] += 1
                                    if len(conversion_first) < 80:
                                        conversion_first.append(method_name)
                                if event[0] == 0 and method_name.startswith(
                                    (
                                        "java.io.File.",
                                        "java.io.UnixFileSystem.",
                                        "android.system.Os.",
                                        "libcore.io.",
                                    )
                                ):
                                    filesystem_calls[method_name] += 1
                                if event[0] == 0 and method_name.startswith(
                                    (
                                        "android.",
                                        "dalvik.",
                                        "java.io.File.",
                                        "java.lang.Runtime.",
                                        "java.lang.System.",
                                        "java.net.",
                                        "java.security.",
                                    )
                                ):
                                    recent_platform.pop(method_name, None)
                                    recent_platform[method_name] = None
                                    if len(recent_platform) > 80:
                                        recent_platform.popitem(last=False)
                                if event[0] == 2 and method_name.startswith(
                                    "com.google.internal.getMeteringPointsAe."
                                ):
                                    interpreter_unwind = list(context)
                                    platform_before_interpreter_unwind = list(recent_platform)
                    print(
                        "M5_METHOD_TRACE sampled="
                        + str(int(args.trace_methods))
                        + " main_events="
                        + str(event_count)
                        + " overflow="
                        + str("data-file-overflow=true" in header).lower()
                        + " thread_names="
                        + ",".join(sorted(set(threads.values()))[:20])
                        + " methods="
                        + str(len(methods))
                    )
                    for action, method_name in interpreter_unwind:
                        print(
                            "M5_METHOD_TRACE_INTERPRETER_CONTEXT action="
                            + str(action)
                            + " method="
                            + method_name
                        )
                    for method_name in platform_before_interpreter_unwind:
                        print("M5_METHOD_TRACE_PLATFORM method=" + method_name)
                    for method_name in conversion_first:
                        print("M5_METHOD_TRACE_CONVERSION_FIRST method=" + method_name)
                    for method_name in conversion_steps:
                        print("M5_METHOD_TRACE_CONVERSION_STEP method=" + method_name)
                    print("M5_METHOD_TRACE_FIRST_DISPATCH seen=" + str(first_dispatch_seen).lower())
                    print(
                        "M5_METHOD_TRACE_DISPATCH_ACTIONS enter="
                        + str(interpreter_actions[0])
                        + " exit="
                        + str(interpreter_actions[1])
                        + " unwind="
                        + str(interpreter_actions[2])
                    )
                    for method_name, count in sorted(before_first_dispatch.items()):
                        print(f"M5_METHOD_TRACE_BEFORE_DISPATCH method={method_name} count={count}")
                    for method_name, count in conversion_calls.most_common(80):
                        print(
                            f"M5_METHOD_TRACE_CONVERSION_COUNT method={method_name} count={count}"
                        )
                    for method_name, count in filesystem_calls.most_common(40):
                        print("M5_METHOD_TRACE_FS method=" + method_name + " count=" + str(count))
                    for action, method_name in events:
                        print(
                            "M5_METHOD_TRACE_EVENT action=" + str(action) + " method=" + method_name
                        )
        elif args.trace_methods or args.trace_application_exact:
            print("M5_METHOD_TRACE_UNAVAILABLE")
        if result.returncode:
            raise SystemExit(1)


if __name__ == "__main__":
    main()
