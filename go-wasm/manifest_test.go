// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"archive/zip"
	"bytes"
	"encoding/binary"
	"strings"
	"testing"
)

// Helper to build minimal Android binary XML chunks for manifest parser tests.
type axmlAttrSpec struct {
	ns       string
	name     string
	resID    uint32 // if non-zero, placed into XML_RESOURCE_MAP at name's pool index
	rawStr   string // if hasRaw, string value
	hasRaw   bool
	dataType byte
	dataBits uint32
	strVal   string // if dataType == 3, string pool entry whose index becomes dataBits
}

type axmlNodeSpec struct {
	ns       string
	tag      string
	attrs    []axmlAttrSpec
	children []axmlNodeSpec
}

func buildTestBinaryXML(root axmlNodeSpec) []byte {
	var pool []string
	var resMap []uint32
	// Reserve resource-mapped entries first so XML_RESOURCE_MAP stays compact and aligned.
	resIndex := map[uint32]uint32{}
	var collectRes func(n axmlNodeSpec)
	collectRes = func(n axmlNodeSpec) {
		for _, a := range n.attrs {
			if a.resID != 0 {
				if _, ok := resIndex[a.resID]; !ok {
					resIndex[a.resID] = uint32(len(pool))
					pool = append(pool, a.name)
					resMap = append(resMap, a.resID)
				}
			}
		}
		for _, c := range n.children {
			collectRes(c)
		}
	}
	collectRes(root)

	strIndex := map[string]uint32{}
	addStr := func(s string) uint32 {
		if idx, ok := strIndex[s]; ok {
			return idx
		}
		idx := uint32(len(pool))
		pool = append(pool, s)
		strIndex[s] = idx
		return idx
	}

	var intern func(n axmlNodeSpec)
	intern = func(n axmlNodeSpec) {
		if n.ns != "" {
			addStr(n.ns)
		}
		addStr(n.tag)
		for _, a := range n.attrs {
			if a.ns != "" {
				addStr(a.ns)
			}
			if a.resID == 0 {
				addStr(a.name)
			}
			if a.hasRaw {
				addStr(a.rawStr)
			}
			if a.dataType == 3 {
				addStr(a.strVal)
			}
		}
		for _, c := range n.children {
			intern(c)
		}
	}
	intern(root)

	// Build UTF-8 string pool chunk (kind=0x0001).
	var strData bytes.Buffer
	offsets := make([]uint32, len(pool))
	for i, s := range pool {
		offsets[i] = uint32(strData.Len())
		b := []byte(s)
		strData.WriteByte(byte(len(b)))
		strData.WriteByte(byte(len(b)))
		strData.Write(b)
		strData.WriteByte(0)
	}
	for strData.Len()%4 != 0 {
		strData.WriteByte(0)
	}
	strStart := uint32(28 + len(pool)*4)
	poolLen := strStart + uint32(strData.Len())
	var poolChunk bytes.Buffer
	_ = binary.Write(&poolChunk, binary.LittleEndian, uint16(0x0001))
	_ = binary.Write(&poolChunk, binary.LittleEndian, uint16(28))
	_ = binary.Write(&poolChunk, binary.LittleEndian, poolLen)
	_ = binary.Write(&poolChunk, binary.LittleEndian, uint32(len(pool)))
	_ = binary.Write(&poolChunk, binary.LittleEndian, uint32(0))     // styleCount
	_ = binary.Write(&poolChunk, binary.LittleEndian, uint32(0x100)) // UTF-8 flag
	_ = binary.Write(&poolChunk, binary.LittleEndian, strStart)
	_ = binary.Write(&poolChunk, binary.LittleEndian, uint32(0)) // stylesStart
	for _, off := range offsets {
		_ = binary.Write(&poolChunk, binary.LittleEndian, off)
	}
	poolChunk.Write(strData.Bytes())

	// Optional XML_RESOURCE_MAP chunk (kind=0x0180).
	var resChunk bytes.Buffer
	if len(resMap) > 0 {
		_ = binary.Write(&resChunk, binary.LittleEndian, uint16(0x0180))
		_ = binary.Write(&resChunk, binary.LittleEndian, uint16(8))
		_ = binary.Write(&resChunk, binary.LittleEndian, uint32(8+len(resMap)*4))
		for _, id := range resMap {
			_ = binary.Write(&resChunk, binary.LittleEndian, id)
		}
	}

	var body bytes.Buffer
	var emit func(n axmlNodeSpec)
	emit = func(n axmlNodeSpec) {
		nsIdx := uint32(0xffffffff)
		if n.ns != "" {
			nsIdx = addStr(n.ns)
		}
		tagIdx := addStr(n.tag)
		startLen := uint32(36 + len(n.attrs)*20)
		_ = binary.Write(&body, binary.LittleEndian, uint16(0x0102))
		_ = binary.Write(&body, binary.LittleEndian, uint16(16))
		_ = binary.Write(&body, binary.LittleEndian, startLen)
		_ = binary.Write(&body, binary.LittleEndian, uint32(1))          // line
		_ = binary.Write(&body, binary.LittleEndian, uint32(0xffffffff)) // comment
		_ = binary.Write(&body, binary.LittleEndian, nsIdx)
		_ = binary.Write(&body, binary.LittleEndian, tagIdx)
		_ = binary.Write(&body, binary.LittleEndian, uint16(20)) // attrStart
		_ = binary.Write(&body, binary.LittleEndian, uint16(20)) // attrSize
		_ = binary.Write(&body, binary.LittleEndian, uint16(len(n.attrs)))
		_ = binary.Write(&body, binary.LittleEndian, uint16(0)) // idIndex
		_ = binary.Write(&body, binary.LittleEndian, uint16(0)) // classIndex
		_ = binary.Write(&body, binary.LittleEndian, uint16(0)) // styleIndex
		for _, a := range n.attrs {
			aNS := uint32(0xffffffff)
			if a.ns != "" {
				aNS = addStr(a.ns)
			}
			var aName uint32
			if a.resID != 0 {
				aName = resIndex[a.resID]
			} else {
				aName = addStr(a.name)
			}
			raw := uint32(0xffffffff)
			if a.hasRaw {
				raw = addStr(a.rawStr)
			}
			bits := a.dataBits
			if a.dataType == 3 {
				bits = addStr(a.strVal)
			}
			_ = binary.Write(&body, binary.LittleEndian, aNS)
			_ = binary.Write(&body, binary.LittleEndian, aName)
			_ = binary.Write(&body, binary.LittleEndian, raw)
			_ = binary.Write(&body, binary.LittleEndian, uint16(8))
			body.WriteByte(0)
			body.WriteByte(a.dataType)
			_ = binary.Write(&body, binary.LittleEndian, bits)
		}
		for _, c := range n.children {
			emit(c)
		}
		_ = binary.Write(&body, binary.LittleEndian, uint16(0x0103))
		_ = binary.Write(&body, binary.LittleEndian, uint16(16))
		_ = binary.Write(&body, binary.LittleEndian, uint32(24))
		_ = binary.Write(&body, binary.LittleEndian, uint32(1))
		_ = binary.Write(&body, binary.LittleEndian, uint32(0xffffffff))
		_ = binary.Write(&body, binary.LittleEndian, nsIdx)
		_ = binary.Write(&body, binary.LittleEndian, tagIdx)
	}
	emit(root)

	total := uint32(8 + poolChunk.Len() + resChunk.Len() + body.Len())
	var out bytes.Buffer
	_ = binary.Write(&out, binary.LittleEndian, uint16(0x0003))
	_ = binary.Write(&out, binary.LittleEndian, uint16(8))
	_ = binary.Write(&out, binary.LittleEndian, total)
	out.Write(poolChunk.Bytes())
	out.Write(resChunk.Bytes())
	out.Write(body.Bytes())
	return out.Bytes()
}

func buildTestAPK(t *testing.T, manifest []byte, extra map[string][]byte) []byte {
	t.Helper()
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	for name, content := range map[string][]byte{
		"AndroidManifest.xml": manifest,
		"classes.dex":         []byte("dex\n035\x00"),
	} {
		w, err := zw.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := w.Write(content); err != nil {
			t.Fatal(err)
		}
	}
	for name, content := range extra {
		w, err := zw.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := w.Write(content); err != nil {
			t.Fatal(err)
		}
	}
	if err := zw.Close(); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

// Helper to build a minimal resources.arsc table with one package (0x7f) and type chunks.
type arscEntrySpec struct {
	index    uint16
	dataType byte
	data     uint32
}

type arscTypeSpec struct {
	typeID  byte
	sdk     uint16
	uiMode  byte
	density uint16
	entries []arscEntrySpec
}

func buildTestARSC(poolStrings []string, types []arscTypeSpec) []byte {
	var strData bytes.Buffer
	offsets := make([]uint32, len(poolStrings))
	for i, s := range poolStrings {
		offsets[i] = uint32(strData.Len())
		b := []byte(s)
		strData.WriteByte(byte(len(b)))
		strData.WriteByte(byte(len(b)))
		strData.Write(b)
		strData.WriteByte(0)
	}
	for strData.Len()%4 != 0 {
		strData.WriteByte(0)
	}
	strStart := uint32(28 + len(poolStrings)*4)
	poolLen := strStart + uint32(strData.Len())
	var poolChunk bytes.Buffer
	_ = binary.Write(&poolChunk, binary.LittleEndian, uint16(0x0001))
	_ = binary.Write(&poolChunk, binary.LittleEndian, uint16(28))
	_ = binary.Write(&poolChunk, binary.LittleEndian, poolLen)
	_ = binary.Write(&poolChunk, binary.LittleEndian, uint32(len(poolStrings)))
	_ = binary.Write(&poolChunk, binary.LittleEndian, uint32(0))
	_ = binary.Write(&poolChunk, binary.LittleEndian, uint32(0x100))
	_ = binary.Write(&poolChunk, binary.LittleEndian, strStart)
	_ = binary.Write(&poolChunk, binary.LittleEndian, uint32(0))
	for _, off := range offsets {
		_ = binary.Write(&poolChunk, binary.LittleEndian, off)
	}
	poolChunk.Write(strData.Bytes())

	var pkgBody bytes.Buffer
	for _, ts := range types {
		maxIdx := uint16(0)
		byIdx := map[uint16]arscEntrySpec{}
		for _, e := range ts.entries {
			byIdx[e.index] = e
			if e.index >= maxIdx {
				maxIdx = e.index + 1
			}
		}
		cfg := make([]byte, 64)
		binary.LittleEndian.PutUint32(cfg[0:4], 64)
		binary.LittleEndian.PutUint16(cfg[14:16], ts.density)
		binary.LittleEndian.PutUint16(cfg[24:26], ts.sdk)
		cfg[29] = ts.uiMode

		headerSize := uint16(20 + len(cfg))
		entryOffsets := make([]uint32, maxIdx)
		var entriesBuf bytes.Buffer
		for i := uint16(0); i < maxIdx; i++ {
			e, ok := byIdx[i]
			if !ok {
				entryOffsets[i] = resNoEntry
				continue
			}
			entryOffsets[i] = uint32(entriesBuf.Len())
			// Standard non-compact ResTable_entry (8 bytes) + Res_value (8 bytes).
			_ = binary.Write(&entriesBuf, binary.LittleEndian, uint16(8))
			_ = binary.Write(&entriesBuf, binary.LittleEndian, uint16(0))
			_ = binary.Write(&entriesBuf, binary.LittleEndian, uint32(0))
			_ = binary.Write(&entriesBuf, binary.LittleEndian, uint16(8))
			entriesBuf.WriteByte(0)
			entriesBuf.WriteByte(e.dataType)
			_ = binary.Write(&entriesBuf, binary.LittleEndian, e.data)
		}
		entriesStart := uint32(headerSize) + uint32(maxIdx)*4
		chunkLen := entriesStart + uint32(entriesBuf.Len())
		_ = binary.Write(&pkgBody, binary.LittleEndian, uint16(0x0201))
		_ = binary.Write(&pkgBody, binary.LittleEndian, headerSize)
		_ = binary.Write(&pkgBody, binary.LittleEndian, chunkLen)
		pkgBody.WriteByte(ts.typeID)
		pkgBody.WriteByte(0)
		_ = binary.Write(&pkgBody, binary.LittleEndian, uint16(0))
		_ = binary.Write(&pkgBody, binary.LittleEndian, uint32(maxIdx))
		_ = binary.Write(&pkgBody, binary.LittleEndian, entriesStart)
		pkgBody.Write(cfg)
		for _, off := range entryOffsets {
			_ = binary.Write(&pkgBody, binary.LittleEndian, off)
		}
		pkgBody.Write(entriesBuf.Bytes())
	}

	pkgHeader := make([]byte, 288)
	binary.LittleEndian.PutUint16(pkgHeader[0:2], 0x0200)
	binary.LittleEndian.PutUint16(pkgHeader[2:4], uint16(len(pkgHeader)))
	binary.LittleEndian.PutUint32(pkgHeader[4:8], uint32(len(pkgHeader)+pkgBody.Len()))
	binary.LittleEndian.PutUint32(pkgHeader[8:12], 0x7f)

	total := uint32(12 + poolChunk.Len() + len(pkgHeader) + pkgBody.Len())
	var out bytes.Buffer
	_ = binary.Write(&out, binary.LittleEndian, uint16(0x0002))
	_ = binary.Write(&out, binary.LittleEndian, uint16(12))
	_ = binary.Write(&out, binary.LittleEndian, total)
	_ = binary.Write(&out, binary.LittleEndian, uint32(1))
	out.Write(poolChunk.Bytes())
	out.Write(pkgHeader)
	out.Write(pkgBody.Bytes())
	return out.Bytes()
}

func parsePropsMap(text string) map[string]string {
	out := map[string]string{}
	for _, line := range strings.Split(text, "\n") {
		if line == "" {
			continue
		}
		idx := strings.IndexByte(line, '=')
		if idx >= 0 {
			out[line[:idx]] = line[idx+1:]
		}
	}
	return out
}

func TestManifestForegroundServiceTypeAndResourceMap(t *testing.T) {
	// Exercise foregroundServiceType, XML_RESOURCE_MAP with omitted attribute names,
	// isolatedProcess, cleartext/networkSecurityConfig, reference vs resource metadata,
	// activity-alias, defined/ownSignaturePermission, provider read/writePermission,
	// uses-feature/uses-configuration, and signed intent-filter priority.
	manifest := buildTestBinaryXML(axmlNodeSpec{
		tag: "manifest",
		attrs: []axmlAttrSpec{
			{name: "package", hasRaw: true, rawStr: "net.example.maps", dataType: 3, strVal: "net.example.maps"},
			{resID: 0x0101021c, name: "", dataType: 3, strVal: "5.5.1"}, // versionName (omitted name)
			{ns: androidXMLNS, name: "compileSdkVersion", dataType: 0x10, dataBits: 36},
		},
		children: []axmlNodeSpec{
			{
				tag: "uses-sdk",
				attrs: []axmlAttrSpec{
					{ns: androidXMLNS, name: "minSdkVersion", dataType: 0x10, dataBits: 24},
					{ns: androidXMLNS, name: "targetSdkVersion", dataType: 0x10, dataBits: 36},
				},
			},
			{
				tag: "uses-permission",
				attrs: []axmlAttrSpec{
					{ns: androidXMLNS, name: "name", dataType: 3, strVal: "android.permission.FOREGROUND_SERVICE_DATA_SYNC"},
				},
			},
			{
				tag: "uses-permission",
				attrs: []axmlAttrSpec{
					{ns: androidXMLNS, name: "name", dataType: 3, strVal: "net.example.maps.OWN_SIG"},
				},
			},
			{
				tag: "uses-permission",
				attrs: []axmlAttrSpec{
					{ns: androidXMLNS, name: "name", dataType: 3, strVal: "net.example.maps.SIG_OR_SYS"},
				},
			},
			{
				tag: "permission",
				attrs: []axmlAttrSpec{
					{ns: androidXMLNS, name: "name", dataType: 3, strVal: "net.example.maps.OWN_SIG"},
					{ns: androidXMLNS, name: "protectionLevel", dataType: 0x11, dataBits: 2},
				},
			},
			{
				tag: "permission",
				attrs: []axmlAttrSpec{
					{ns: androidXMLNS, name: "name", dataType: 3, strVal: "net.example.maps.SIG_OR_SYS"},
					{ns: androidXMLNS, name: "protectionLevel", dataType: 0x11, dataBits: 3},
				},
			},
			{
				tag: "uses-feature",
				attrs: []axmlAttrSpec{
					{ns: androidXMLNS, name: "glEsVersion", dataType: 0x11, dataBits: 0x00020000},
				},
			},
			{
				tag: "application",
				attrs: []axmlAttrSpec{
					{ns: androidXMLNS, name: "name", dataType: 3, strVal: ".MapsApp"},
					{ns: androidXMLNS, name: "permission", dataType: 3, strVal: "net.example.maps.APP_PERM"},
					{resID: 0x010104ec, name: "", dataType: 0x12, dataBits: 1},          // usesCleartextTraffic
					{resID: 0x01010527, name: "", dataType: 0x01, dataBits: 0x7f15001d}, // networkSecurityConfig
				},
				children: []axmlNodeSpec{
					{
						tag: "meta-data",
						attrs: []axmlAttrSpec{
							{ns: androidXMLNS, name: "name", dataType: 3, strVal: "ref_val"},
							{ns: androidXMLNS, name: "value", dataType: 0x01, dataBits: 0x7f0b000d},
						},
					},
					{
						tag: "meta-data",
						attrs: []axmlAttrSpec{
							{ns: androidXMLNS, name: "name", dataType: 3, strVal: "res_val"},
							{ns: androidXMLNS, name: "resource", dataType: 0x01, dataBits: 0x7f0b000e},
						},
					},
					{
						tag: "meta-data",
						attrs: []axmlAttrSpec{
							{ns: androidXMLNS, name: "name", dataType: 3, strVal: "neg_int"},
							{ns: androidXMLNS, name: "value", dataType: 0x10, dataBits: 0xfffffff9}, // -7
						},
					},
					{
						tag: "activity",
						attrs: []axmlAttrSpec{
							{ns: androidXMLNS, name: "name", dataType: 3, strVal: ".MainActivity"},
						},
						children: []axmlNodeSpec{
							{
								tag: "intent-filter",
								attrs: []axmlAttrSpec{
									{ns: androidXMLNS, name: "priority", dataType: 0x10, dataBits: 0xffffff9c}, // -100
								},
								children: []axmlNodeSpec{
									{tag: "action", attrs: []axmlAttrSpec{{ns: androidXMLNS, name: "name", dataType: 3, strVal: "android.intent.action.MAIN"}}},
									{tag: "category", attrs: []axmlAttrSpec{{ns: androidXMLNS, name: "name", dataType: 3, strVal: "android.intent.category.LAUNCHER"}}},
								},
							},
						},
					},
					{
						tag: "activity-alias",
						attrs: []axmlAttrSpec{
							{ns: androidXMLNS, name: "name", dataType: 3, strVal: ".LaunchAlias"},
							{ns: androidXMLNS, name: "targetActivity", dataType: 3, strVal: ".MainActivity"},
						},
					},
					{
						tag: "service",
						attrs: []axmlAttrSpec{
							{resID: 0x01010003, name: "", dataType: 3, strVal: ".DownloadService"},
							{resID: 0x01010599, name: "", dataType: 0x11, dataBits: 1}, // foregroundServiceType=dataSync
							{resID: 0x010103a9, name: "", dataType: 0x12, dataBits: 1}, // isolatedProcess=true
						},
					},
					{
						tag: "service",
						attrs: []axmlAttrSpec{
							{ns: androidXMLNS, name: "name", dataType: 3, strVal: ".NavService"},
							{ns: androidXMLNS, name: "foregroundServiceType", dataType: 0x11, dataBits: 9}, // dataSync|location
						},
					},
					{
						tag: "provider",
						attrs: []axmlAttrSpec{
							{ns: androidXMLNS, name: "name", dataType: 3, strVal: ".MapProvider"},
							{ns: androidXMLNS, name: "authorities", dataType: 3, strVal: "net.example.maps.provider"},
						},
					},
				},
			},
		},
	})

	apk := buildTestAPK(t, manifest, nil)
	meta, err := inspectAPK(apk, "0.362.0", "x86_64")
	if err != nil {
		t.Fatalf("inspectAPK failed: %v", err)
	}
	got := parsePropsMap(meta.ApplicationProperties)
	want := map[string]string{
		"versionName":                       "5.5.1",
		"targetSdk":                         "36",
		"usesCleartextTraffic":              "true",
		"networkSecurityConfig":             "@0x7f15001d",
		"applicationInfo.compileSdkVersion": "36",
		"application.metadata.0.kind":       "reference",
		"application.metadata.0.value":      "2131427341",
		"application.metadata.1.kind":       "resource",
		"application.metadata.1.value":      "2131427342",
		"application.metadata.2.kind":       "integer",
		"application.metadata.2.value":      "-7",
		"ownSignaturePermission.count":      "1",
		"ownSignaturePermission.0":          "net.example.maps.OWN_SIG",
		"definedPermission.count":           "2",
		"definedPermission.0.protection":    "2",
		"definedPermission.1.protection":    "3",
		"launcher.0.name":                   "net.example.maps.MainActivity",
		"launcher.0.target":                 "net.example.maps.MainActivity",
		"alias.count":                       "1",
		"alias.0.name":                      "net.example.maps.LaunchAlias",
		"alias.0.target":                    "net.example.maps.MainActivity",
		"alias.0.exported":                  "false",
		"activity.0.filter.0.priority":      "-100",
		"activity.0.permission":             "net.example.maps.APP_PERM",
		"service.count":                     "2",
		"service.0.name":                    "net.example.maps.DownloadService",
		"service.0.foregroundServiceType":   "1",
		"service.0.isolated":                "true",
		"service.0.exported":                "false",
		"service.0.permission":              "net.example.maps.APP_PERM",
		"service.1.name":                    "net.example.maps.NavService",
		"service.1.foregroundServiceType":   "9",
		"provider.0.readPermission":         "net.example.maps.APP_PERM",
		"provider.0.writePermission":        "net.example.maps.APP_PERM",
		"configuration.count":               "1",
		"configuration.0.reqGlEsVersion":    "131072",
	}
	for k, expected := range want {
		if got[k] != expected {
			t.Errorf("property %s = %q, want %q", k, got[k], expected)
		}
	}
	if _, unwanted := got["configuration.0.reqTouchScreen"]; unwanted {
		t.Errorf("feature-derived configuration unexpectedly emitted reqTouchScreen")
	}
}

func TestManifestRejectsInvalidForegroundServiceType(t *testing.T) {
	manifest := buildTestBinaryXML(axmlNodeSpec{
		tag: "manifest",
		attrs: []axmlAttrSpec{
			{name: "package", hasRaw: true, rawStr: "net.example.bad", dataType: 3, strVal: "net.example.bad"},
		},
		children: []axmlNodeSpec{
			{
				tag: "application",
				children: []axmlNodeSpec{
					{
						tag:   "activity",
						attrs: []axmlAttrSpec{{ns: androidXMLNS, name: "name", dataType: 3, strVal: ".Main"}},
						children: []axmlNodeSpec{{
							tag: "intent-filter",
							children: []axmlNodeSpec{
								{tag: "action", attrs: []axmlAttrSpec{{ns: androidXMLNS, name: "name", dataType: 3, strVal: "android.intent.action.MAIN"}}},
								{tag: "category", attrs: []axmlAttrSpec{{ns: androidXMLNS, name: "name", dataType: 3, strVal: "android.intent.category.LAUNCHER"}}},
							},
						}},
					},
					{
						tag: "service",
						attrs: []axmlAttrSpec{
							{ns: androidXMLNS, name: "name", dataType: 3, strVal: ".BadService"},
							{ns: androidXMLNS, name: "foregroundServiceType", dataType: 0x11, dataBits: 0x80000000},
						},
					},
				},
			},
		},
	})
	apk := buildTestAPK(t, manifest, nil)
	if _, err := inspectAPK(apk, "0.362.0", "x86_64"); err == nil {
		t.Fatal("expected out-of-range foregroundServiceType to fail")
	}
}

func TestManifestFlagsAndResourceLabelIconResolution(t *testing.T) {
	arsc := buildTestARSC(
		[]string{":worker", "Example Maps", "res/obf_icon.png"},
		[]arscTypeSpec{
			// Type 1 (bool/string flags):
			// 0x7f010000 = bool true (default)
			// 0x7f010001 = bool false (default), overridden to true on SDK 33 (must pick false for API 27)
			// 0x7f010002 = string ":worker"
			{
				typeID: 1,
				entries: []arscEntrySpec{
					{index: 0, dataType: resTypeBoolean, data: 1},
					{index: 1, dataType: resTypeBoolean, data: 0},
					{index: 2, dataType: resTypeString, data: 0},
				},
			},
			{
				typeID: 1,
				sdk:    33,
				entries: []arscEntrySpec{
					{index: 1, dataType: resTypeBoolean, data: 1},
				},
			},
			// Type 2 (label string): 0x7f020000 = "Example Maps"
			{
				typeID: 2,
				entries: []arscEntrySpec{
					{index: 0, dataType: resTypeString, data: 1},
				},
			},
			// Type 3 (icon drawable): 0x7f030000 = "res/obf_icon.png"
			{
				typeID:  3,
				density: 320,
				entries: []arscEntrySpec{
					{index: 0, dataType: resTypeString, data: 2},
				},
			},
		},
	)

	manifest := buildTestBinaryXML(axmlNodeSpec{
		tag: "manifest",
		attrs: []axmlAttrSpec{
			{name: "package", hasRaw: true, rawStr: "net.example.flags", dataType: 3, strVal: "net.example.flags"},
		},
		children: []axmlNodeSpec{
			{
				tag: "application",
				attrs: []axmlAttrSpec{
					{ns: androidXMLNS, name: "label", dataType: 0x01, dataBits: 0x7f020000},
					{ns: androidXMLNS, name: "icon", dataType: 0x01, dataBits: 0x7f030000},
				},
				children: []axmlNodeSpec{
					{
						tag:   "activity",
						attrs: []axmlAttrSpec{{ns: androidXMLNS, name: "name", dataType: 3, strVal: ".Main"}},
						children: []axmlNodeSpec{{
							tag: "intent-filter",
							children: []axmlNodeSpec{
								{tag: "action", attrs: []axmlAttrSpec{{ns: androidXMLNS, name: "name", dataType: 3, strVal: "android.intent.action.MAIN"}}},
								{tag: "category", attrs: []axmlAttrSpec{{ns: androidXMLNS, name: "name", dataType: 3, strVal: "android.intent.category.LAUNCHER"}}},
							},
						}},
					},
					{
						tag: "service",
						attrs: []axmlAttrSpec{
							{ns: androidXMLNS, name: "name", dataType: 3, strVal: ".Worker"},
							{ns: androidXMLNS, name: "enabled", dataType: 0x01, dataBits: 0x7f010000},
							{ns: androidXMLNS, name: "exported", dataType: 0x01, dataBits: 0x7f010001},
							{ns: androidXMLNS, name: "process", dataType: 0x01, dataBits: 0x7f010002},
							{ns: androidXMLNS, name: "foregroundServiceType", dataType: 0x11, dataBits: 1},
						},
					},
				},
			},
		},
	})

	pngBytes := append([]byte("\x89PNG\r\n\x1a\n"), bytes.Repeat([]byte{0x11}, 32)...)
	apk := buildTestAPK(t, manifest, map[string][]byte{
		"resources.arsc":   arsc,
		"res/obf_icon.png": pngBytes,
	})

	meta, err := inspectAPK(apk, "0.362.0", "x86_64")
	if err != nil {
		t.Fatalf("inspectAPK with resources.arsc failed: %v", err)
	}
	got := parsePropsMap(meta.ApplicationProperties)
	if got["service.0.enabled"] != "true" {
		t.Errorf("service.0.enabled = %q, want true", got["service.0.enabled"])
	}
	if got["service.0.exported"] != "false" {
		t.Errorf("service.0.exported = %q, want false (SDK 33 override must not apply)", got["service.0.exported"])
	}
	if got["service.0.process"] != `\:worker` {
		t.Errorf("service.0.process = %q, want %q", got["service.0.process"], `\:worker`)
	}
	if meta.Label != "Example Maps" {
		t.Errorf("meta.Label = %q, want Example Maps", meta.Label)
	}

	// Verify Flatpak builder resolves obfuscated PNG icon and resource label cleanly.
	base := syntheticFlatpakBase(t, "x86_64")
	fp, err := buildFlatpakFromApk(base, apk, "0.362.0", flatpakBuildOptions{
		AllowNetwork: true,
		Timestamp:    1700000000,
	})
	if err != nil {
		t.Fatalf("buildFlatpakFromApk failed: %v", err)
	}
	if !bytes.Contains(fp.Bytes, []byte("Name=Example Maps (Shiraz)\n")) {
		t.Errorf("Flatpak desktop entry did not use resolved resource label")
	}
	if !bytes.Contains(fp.Bytes, pngBytes) {
		t.Errorf("Flatpak bundle did not embed obfuscated PNG icon resolved from resources.arsc")
	}
}
