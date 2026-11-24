// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"bytes"
	"encoding/binary"
)

// Canonical GLib GVariant binary serializer for OSTree and Flatpak static delta structures.

type gvValue struct {
	sig   string
	align int
	fixed bool
	data  []byte
}

func gvAlignUp(offset, align int) int {
	if align <= 1 {
		return offset
	}
	rem := offset % align
	if rem == 0 {
		return offset
	}
	return offset + (align - rem)
}

func gvPadTo(buf *bytes.Buffer, align int) {
	target := gvAlignUp(buf.Len(), align)
	for buf.Len() < target {
		buf.WriteByte(0)
	}
}

func gvOffsetWidth(bodyLen, nOffsets int) int {
	bl := uint64(bodyLen)
	no := uint64(nOffsets)
	if bl+no <= 0xff {
		return 1
	}
	if bl+no*2 <= 0xffff {
		return 2
	}
	if bl+no*4 <= 0xffffffff {
		return 4
	}
	return 8
}

func gvAppendOffset(buf *bytes.Buffer, value int, width int) {
	v := uint64(value)
	var raw [8]byte
	binary.LittleEndian.PutUint64(raw[:], v)
	buf.Write(raw[:width])
}

func gvByte(v byte) gvValue {
	return gvValue{sig: "y", align: 1, fixed: true, data: []byte{v}}
}

func gvU32LE(v uint32) gvValue {
	b := make([]byte, 4)
	binary.LittleEndian.PutUint32(b, v)
	return gvValue{sig: "u", align: 4, fixed: true, data: b}
}

func gvU32BE(v uint32) gvValue {
	b := make([]byte, 4)
	binary.BigEndian.PutUint32(b, v)
	return gvValue{sig: "u", align: 4, fixed: true, data: b}
}

func gvU64LE(v uint64) gvValue {
	b := make([]byte, 8)
	binary.LittleEndian.PutUint64(b, v)
	return gvValue{sig: "t", align: 8, fixed: true, data: b}
}

func gvU64BE(v uint64) gvValue {
	b := make([]byte, 8)
	binary.BigEndian.PutUint64(b, v)
	return gvValue{sig: "t", align: 8, fixed: true, data: b}
}

func gvString(s string) gvValue {
	b := make([]byte, len(s)+1)
	copy(b, s)
	return gvValue{sig: "s", align: 1, fixed: false, data: b}
}

func gvBytes(raw []byte) gvValue {
	b := make([]byte, len(raw))
	copy(b, raw)
	return gvValue{sig: "ay", align: 1, fixed: false, data: b}
}

func gvVariant(child gvValue) gvValue {
	b := make([]byte, 0, len(child.data)+1+len(child.sig))
	b = append(b, child.data...)
	b = append(b, 0)
	b = append(b, child.sig...)
	return gvValue{sig: "v", align: 8, fixed: false, data: b}
}

func gvTupleSig(items []gvValue, sig string) gvValue {
	if len(items) == 0 {
		return gvValue{sig: "()", align: 1, fixed: true, data: []byte{0}}
	}
	align := 1
	fixed := true
	var sigBuf bytes.Buffer
	if sig == "" {
		sigBuf.WriteByte('(')
	}
	for _, item := range items {
		if item.align > align {
			align = item.align
		}
		if !item.fixed {
			fixed = false
		}
		if sig == "" {
			sigBuf.WriteString(item.sig)
		}
	}
	if sig == "" {
		sigBuf.WriteByte(')')
		sig = sigBuf.String()
	}
	var buf bytes.Buffer
	var offsets []int
	last := len(items) - 1
	for i, item := range items {
		gvPadTo(&buf, item.align)
		buf.Write(item.data)
		if !item.fixed && i < last {
			offsets = append(offsets, buf.Len())
		}
	}
	if fixed {
		gvPadTo(&buf, align)
	} else if len(offsets) > 0 {
		width := gvOffsetWidth(buf.Len(), len(offsets))
		for i := len(offsets) - 1; i >= 0; i-- {
			gvAppendOffset(&buf, offsets[i], width)
		}
	}
	return gvValue{sig: sig, align: align, fixed: fixed, data: buf.Bytes()}
}

func gvTuple(items ...gvValue) gvValue {
	return gvTupleSig(items, "")
}

func gvDictEntry(key, value gvValue) gvValue {
	return gvTupleSig([]gvValue{key, value}, "{"+key.sig+value.sig+"}")
}

func gvArray(elemSig string, elemAlign int, elemFixed bool, items []gvValue) gvValue {
	sig := "a" + elemSig
	if len(items) == 0 {
		return gvValue{sig: sig, align: elemAlign, fixed: false, data: nil}
	}
	var buf bytes.Buffer
	if elemFixed {
		for _, item := range items {
			buf.Write(item.data)
		}
		return gvValue{sig: sig, align: elemAlign, fixed: false, data: buf.Bytes()}
	}
	offsets := make([]int, 0, len(items))
	for _, item := range items {
		gvPadTo(&buf, elemAlign)
		buf.Write(item.data)
		offsets = append(offsets, buf.Len())
	}
	width := gvOffsetWidth(buf.Len(), len(offsets))
	for _, off := range offsets {
		gvAppendOffset(&buf, off, width)
	}
	return gvValue{sig: sig, align: elemAlign, fixed: false, data: buf.Bytes()}
}
