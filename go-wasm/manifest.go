// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"archive/zip"
	"bytes"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"sort"
	"strconv"
	"strings"
	"unicode/utf16"
)

type xmlAttr struct {
	text string
	kind byte
	bits uint32
}
type xmlNode struct {
	tag      string
	attrs    map[string]xmlAttr
	children []*xmlNode
}

func (n *xmlNode) attr(name string) string {
	if n == nil {
		return ""
	}
	return n.attrs[name].text
}
func (n *xmlNode) ref(name string) string {
	if n == nil {
		return ""
	}
	a := n.attrs[name]
	if a.kind == 1 || a.kind == 7 {
		return strconv.FormatUint(uint64(a.bits), 10)
	}
	return ""
}
func (n *xmlNode) nodes(name string) []*xmlNode {
	var out []*xmlNode
	if n != nil {
		for _, c := range n.children {
			if c.tag == name {
				out = append(out, c)
			}
		}
	}
	return out
}
func (n *xmlNode) first(name string) *xmlNode {
	for _, c := range n.nodes(name) {
		return c
	}
	return nil
}

func xmlStringPool(data []byte, start, header, end int) ([]string, error) {
	if header < 28 || start+28 > end {
		return nil, errors.New("short XML string pool")
	}
	count := int(binary.LittleEndian.Uint32(data[start+8:]))
	styles := int(binary.LittleEndian.Uint32(data[start+12:]))
	flags := binary.LittleEndian.Uint32(data[start+16:])
	strStart := int(binary.LittleEndian.Uint32(data[start+20:]))
	styleStart := int(binary.LittleEndian.Uint32(data[start+24:]))
	if count > 1000000 || count < 0 || styles > 1000000 || start+header+(count+styles)*4 > end || strStart < header+(count+styles)*4 {
		return nil, errors.New("invalid XML string pool")
	}
	strEnd := end
	if styleStart != 0 {
		strEnd = start + styleStart
	}
	if strEnd > end {
		return nil, errors.New("invalid XML string range")
	}
	read8 := func(p *int) (int, error) {
		if *p >= strEnd {
			return 0, io.ErrUnexpectedEOF
		}
		v := int(data[*p])
		*p++
		if v&128 != 0 {
			if *p >= strEnd {
				return 0, io.ErrUnexpectedEOF
			}
			v = (v&127)<<8 | int(data[*p])
			*p++
		}
		return v, nil
	}
	read16 := func(p *int) (int, error) {
		if *p+2 > strEnd {
			return 0, io.ErrUnexpectedEOF
		}
		v := int(binary.LittleEndian.Uint16(data[*p:]))
		*p += 2
		if v&0x8000 != 0 {
			if *p+2 > strEnd {
				return 0, io.ErrUnexpectedEOF
			}
			v = (v&0x7fff)<<16 | int(binary.LittleEndian.Uint16(data[*p:]))
			*p += 2
		}
		return v, nil
	}
	out := make([]string, count)
	for i := 0; i < count; i++ {
		offset := int(binary.LittleEndian.Uint32(data[start+header+i*4:]))
		p := start + strStart + offset
		if p < start || p >= strEnd {
			return nil, errors.New("XML string offset outside pool")
		}
		if flags&0x100 != 0 {
			if _, err := read8(&p); err != nil {
				return nil, err
			}
			length, err := read8(&p)
			if err != nil {
				return nil, err
			}
			if length > 1<<20 || p+length+1 > strEnd || data[p+length] != 0 {
				return nil, errors.New("invalid UTF-8 XML string")
			}
			out[i] = string(data[p : p+length])
		} else {
			length, err := read16(&p)
			if err != nil {
				return nil, err
			}
			if length > 1<<20 || p+length*2+2 > strEnd {
				return nil, errors.New("invalid UTF-16 XML string")
			}
			units := make([]uint16, length)
			for j := 0; j < length; j++ {
				units[j] = binary.LittleEndian.Uint16(data[p+j*2:])
			}
			if data[p+length*2] != 0 || data[p+length*2+1] != 0 {
				return nil, errors.New("unterminated UTF-16 XML string")
			}
			out[i] = string(utf16.Decode(units))
		}
	}
	return out, nil
}

func parseManifestXML(data []byte) (*xmlNode, error) {
	if len(data) < 8 || binary.LittleEndian.Uint16(data) != 3 || binary.LittleEndian.Uint16(data[2:]) != 8 || int(binary.LittleEndian.Uint32(data[4:])) != len(data) {
		return nil, errors.New("unsupported Android manifest XML")
	}
	var stringsPool []string
	get := func(index uint32) (string, error) {
		if index == 0xffffffff {
			return "", nil
		}
		if int(index) >= len(stringsPool) {
			return "", errors.New("XML string index outside pool")
		}
		return stringsPool[index], nil
	}
	var root *xmlNode
	var stack []*xmlNode
	countNodes := 0
	for pos := 8; pos < len(data); {
		if pos+8 > len(data) {
			return nil, io.ErrUnexpectedEOF
		}
		kind := binary.LittleEndian.Uint16(data[pos:])
		header := int(binary.LittleEndian.Uint16(data[pos+2:]))
		length := int(binary.LittleEndian.Uint32(data[pos+4:]))
		end := pos + length
		if header < 8 || length < header || end > len(data) {
			return nil, errors.New("invalid XML chunk")
		}
		if kind == 1 {
			pool, err := xmlStringPool(data, pos, header, end)
			if err != nil {
				return nil, err
			}
			stringsPool = pool
		}
		if kind == 0x102 {
			if header != 16 || pos+36 > end {
				return nil, errors.New("invalid XML element")
			}
			name, err := get(binary.LittleEndian.Uint32(data[pos+20:]))
			if err != nil {
				return nil, err
			}
			attrStart := int(binary.LittleEndian.Uint16(data[pos+24:]))
			attrSize := int(binary.LittleEndian.Uint16(data[pos+26:]))
			attrCount := int(binary.LittleEndian.Uint16(data[pos+28:]))
			first := pos + 16 + attrStart
			if attrStart < 20 || attrSize < 20 || attrCount > 256 || first+attrCount*attrSize > end {
				return nil, errors.New("invalid XML attributes")
			}
			node := &xmlNode{tag: name, attrs: map[string]xmlAttr{}}
			for i := 0; i < attrCount; i++ {
				p := first + i*attrSize
				key, err := get(binary.LittleEndian.Uint32(data[p+4:]))
				if err != nil {
					return nil, err
				}
				raw := binary.LittleEndian.Uint32(data[p+8:])
				valueSize := binary.LittleEndian.Uint16(data[p+12:])
				dtype := data[p+15]
				bits := binary.LittleEndian.Uint32(data[p+16:])
				if valueSize != 8 {
					return nil, errors.New("invalid XML attribute value")
				}
				value := ""
				if raw != 0xffffffff {
					value, err = get(raw)
				} else if dtype == 3 {
					value, err = get(bits)
				} else if dtype == 0x12 {
					value = strconv.FormatBool(bits != 0)
				} else if dtype == 0x10 || dtype == 0x11 {
					value = strconv.FormatUint(uint64(bits), 10)
				} else {
					value = fmt.Sprintf("@0x%08x", bits)
				}
				if err != nil {
					return nil, err
				}
				if _, exists := node.attrs[key]; exists {
					return nil, errors.New("duplicate XML attribute")
				}
				node.attrs[key] = xmlAttr{value, dtype, bits}
			}
			if len(stack) > 0 {
				stack[len(stack)-1].children = append(stack[len(stack)-1].children, node)
			} else if root == nil {
				root = node
			} else {
				return nil, errors.New("multiple XML roots")
			}
			stack = append(stack, node)
			countNodes++
			if len(stack) > 256 || countNodes > 20000 {
				return nil, errors.New("XML manifest exceeds limits")
			}
		}
		if kind == 0x103 {
			if len(stack) == 0 {
				return nil, errors.New("unbalanced XML")
			}
			stack = stack[:len(stack)-1]
		}
		pos = end
	}
	if root == nil || len(stack) > 0 || root.tag != "manifest" || root.attr("package") == "" {
		return nil, errors.New("invalid manifest root")
	}
	return root, nil
}

func qualify(pkg, name string) string {
	if name == "" {
		return ""
	}
	if strings.HasPrefix(name, ".") {
		return pkg + name
	}
	if !strings.Contains(name, ".") {
		return pkg + "." + name
	}
	return name
}
func enumValue(node *xmlNode, key string, names map[string]int, defaultValue int, flags bool) (int, error) {
	raw := node.attr(key)
	if raw == "" {
		return defaultValue, nil
	}
	value := node.attrs[key]
	if value.kind == 0x10 || value.kind == 0x11 {
		return int(int32(value.bits)), nil
	}
	if n, err := strconv.ParseInt(raw, 0, 32); err == nil {
		return int(n), nil
	}
	parts := []string{raw}
	if flags {
		parts = strings.Split(raw, "|")
	}
	result := 0
	for _, part := range parts {
		number, ok := names[part]
		if !ok {
			return 0, fmt.Errorf("unsupported %s value: %s", key, part)
		}
		result |= number
	}
	return result, nil
}
func put(m map[string]string, key, value string) {
	if value != "" {
		m[key] = value
	}
}
func fields(m map[string]string, prefix string, node *xmlNode, names []string) {
	for _, name := range names {
		put(m, prefix+name, node.attr(name))
	}
}
func emitMeta(m map[string]string, prefix string, node *xmlNode) {
	entries := node.nodes("meta-data")
	m[prefix+"metadata.count"] = strconv.Itoa(len(entries))
	for i, entry := range entries {
		p := prefix + "metadata." + strconv.Itoa(i) + "."
		put(m, p+"name", entry.attr("name"))
		put(m, p+"name_resource", entry.ref("name"))
		field := "value"
		if entry.attr("resource") != "" {
			field = "resource"
		}
		v := entry.attrs[field]
		kind := "string"
		value := v.text
		if field == "resource" || v.kind == 1 {
			kind = "resource"
			value = strconv.FormatUint(uint64(v.bits), 10)
		} else if v.kind == 0x12 {
			kind = "boolean"
		} else if v.kind == 0x10 || v.kind == 0x11 {
			kind = "integer"
		}
		put(m, p+"kind", kind)
		put(m, p+"value", value)
	}
}
func emitFilters(m map[string]string, prefix string, node *xmlNode) {
	filters := node.nodes("intent-filter")
	m[prefix+"filter.count"] = strconv.Itoa(len(filters))
	for i, filter := range filters {
		p := prefix + "filter." + strconv.Itoa(i) + "."
		value := filter.attr("priority")
		if value == "" {
			value = "0"
		}
		m[p+"priority"] = value
		m[p+"unsupported"] = "false"
		for _, kind := range []string{"action", "category", "scheme", "mime"} {
			values := []string{}
			if kind == "action" || kind == "category" {
				for _, x := range filter.nodes(kind) {
					if x.attr("name") != "" {
						values = append(values, x.attr("name"))
					}
				}
			} else {
				key := kind
				seenValues := map[string]bool{}
				if kind == "mime" {
					key = "mimeType"
				}
				for _, x := range filter.nodes("data") {
					if x.attr(key) != "" && !seenValues[x.attr(key)] {
						values = append(values, x.attr(key))
						seenValues[x.attr(key)] = true
					}
					for attr := range x.attrs {
						if attr != "scheme" && attr != "mimeType" {
							m[p+"unsupported"] = "true"
						}
					}
				}
			}
			m[p+kind+".count"] = strconv.Itoa(len(values))
			for j, v := range values {
				m[p+kind+"."+strconv.Itoa(j)] = v
			}
		}
	}
}
func props(m map[string]string) string {
	keys := make([]string, 0, len(m))
	for key := range m {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	escape := func(s string) string {
		var b strings.Builder
		for _, c := range s {
			switch c {
			case '\\', ' ', '=', ':', '#', '!':
				b.WriteByte('\\')
				b.WriteRune(c)
			case '\n':
				b.WriteString("\\n")
			case '\r':
				b.WriteString("\\r")
			case '\t':
				b.WriteString("\\t")
			default:
				b.WriteRune(c)
			}
		}
		return b.String()
	}
	var b strings.Builder
	for _, key := range keys {
		b.WriteString(escape(key))
		b.WriteByte('=')
		b.WriteString(escape(m[key]))
		b.WriteByte('\n')
	}
	return b.String()
}

func inspectAPK(apk []byte, version string) (metadata, error) {
	var result metadata
	if !versionName.MatchString(version) {
		return result, errors.New("invalid Shiraz version")
	}
	archive, err := zip.NewReader(bytes.NewReader(apk), int64(len(apk)))
	if err != nil {
		return result, fmt.Errorf("APK ZIP data is invalid or incomplete (%d bytes): %w", len(apk), err)
	}
	var manifest *zip.File
	hasDex := false
	seenEntries := map[string]bool{}
	hasNative := false
	hasX8664 := false
	var total uint64
	if len(archive.File) > 100000 {
		return result, errors.New("APK has too many entries")
	}
	for _, file := range archive.File {
		name := file.Name
		if seenEntries[name] || strings.HasPrefix(name, "/") || strings.Contains(name, "\\") || strings.Contains(name, "\x00") || strings.Contains(name, "../") || strings.HasPrefix(name, "../") {
			return result, errors.New("unsafe APK entry")
		}
		seenEntries[name] = true
		total += file.UncompressedSize64
		if total > 768<<20 || file.Flags&1 != 0 {
			return result, errors.New("APK exceeds processing limits")
		}
		if strings.HasPrefix(name, "lib/") && strings.HasSuffix(name, ".so") {
			hasNative = true
			if strings.HasPrefix(name, "lib/x86_64/") {
				hasX8664 = true
			}
		}
		if file.Name == "AndroidManifest.xml" {
			manifest = file
		}
		if file.Name == "classes.dex" {
			hasDex = true
		}
	}
	if hasNative && !hasX8664 {
		return result, errors.New("APK has no x86_64 native library candidate")
	}
	if manifest == nil || !hasDex {
		return result, errors.New("APK needs AndroidManifest.xml and classes.dex")
	}
	if manifest.UncompressedSize64 > 16<<20 {
		return result, errors.New("manifest exceeds limit")
	}
	stream, err := manifest.Open()
	if err != nil {
		return result, err
	}
	data, err := io.ReadAll(io.LimitReader(stream, 16<<20+1))
	stream.Close()
	if err != nil {
		return result, err
	}
	root, err := parseManifestXML(data)
	if err != nil {
		return result, err
	}
	pkg := root.attr("package")
	app := root.first("application")
	if app == nil {
		return result, errors.New("APK has no application")
	}
	sdk := root.first("uses-sdk")
	min := sdk.attr("minSdkVersion")
	if min == "" {
		min = "1"
	}
	n, err := strconv.Atoi(min)
	if err != nil || n > 27 {
		return result, errors.New("APK minSdk is unsupported")
	}
	if app.attr("enabled") == "false" {
		return result, errors.New("APK application is disabled")
	}
	result.Package = pkg
	result.Application = qualify(pkg, app.attr("name"))
	if result.Application == "" {
		result.Application = "android.app.Application"
	}
	result.Version = version
	facts := map[string]string{"package": pkg, "applicationClass": result.Application, "versionCode": "0", "versionCodeMajor": "0", "minSdk": min, "targetSdk": min, "theme": "0"}
	for _, pair := range [][2]string{{"versionName", "versionName"}, {"versionCode", "versionCode"}, {"versionCodeMajor", "versionCodeMajor"}} {
		put(facts, pair[0], root.attr(pair[1]))
	}
	put(facts, "versionNameRes", root.ref("versionName"))
	if sdk.attr("targetSdkVersion") != "" {
		facts["targetSdk"] = sdk.attr("targetSdkVersion")
	}
	for _, name := range []string{"enabled", "debuggable", "hasCode"} {
		put(facts, name, app.attr(name))
	}
	put(facts, "label", app.attr("label"))
	put(facts, "labelRes", app.ref("label"))
	if app.ref("theme") != "" {
		facts["theme"] = app.ref("theme")
	}
	emitMeta(facts, "application.", app)
	permissions := []string{}
	for _, x := range root.nodes("uses-permission") {
		if x.attr("name") != "" {
			permissions = append(permissions, x.attr("name"))
		}
	}
	sort.Strings(permissions)
	facts["permission.count"] = strconv.Itoa(len(permissions))
	for i, v := range permissions {
		facts["permission."+strconv.Itoa(i)] = v
	}
	own := []string{}
	requested := map[string]bool{}
	for _, name := range permissions {
		requested[name] = true
	}
	for _, permission := range root.nodes("permission") {
		name := permission.attr("name")
		protection := permission.attr("protectionLevel")
		if strings.HasPrefix(name, pkg+".") && requested[name] && (strings.Contains(protection, "signature") || permission.attrs["protectionLevel"].bits&15 == 2) {
			own = append(own, name)
		}
	}
	sort.Strings(own)
	facts["ownSignaturePermission.count"] = strconv.Itoa(len(own))
	for i, name := range own {
		facts["ownSignaturePermission."+strconv.Itoa(i)] = name
	}
	launchers := []string{}
	activities := app.nodes("activity")
	aliases := app.nodes("activity-alias")
	for _, activity := range append(append([]*xmlNode{}, activities...), aliases...) {
		for _, filter := range activity.nodes("intent-filter") {
			main, launcher := false, false
			for _, action := range filter.nodes("action") {
				main = main || action.attr("name") == "android.intent.action.MAIN"
			}
			for _, category := range filter.nodes("category") {
				launcher = launcher || category.attr("name") == "android.intent.category.LAUNCHER"
			}
			if main && launcher {
				target := activity.attr("targetActivity")
				if target == "" {
					target = activity.attr("name")
				}
				launchers = append(launchers, qualify(pkg, target))
			}
		}
	}
	if len(launchers) == 0 {
		return result, errors.New("APK has no launcher")
	}
	result.Activity = launchers[0]
	facts["launcher.count"] = strconv.Itoa(len(launchers))
	for i, v := range launchers {
		facts["launcher."+strconv.Itoa(i)+".target"] = v
	}
	facts["activity.count"] = strconv.Itoa(len(activities))
	configBits := map[string]int{"mcc": 1, "mnc": 2, "locale": 4, "touchscreen": 8, "keyboard": 16, "keyboardHidden": 32, "navigation": 64, "orientation": 128, "screenLayout": 256, "uiMode": 512, "screenSize": 1024, "smallestScreenSize": 2048, "density": 4096, "layoutDirection": 8192, "colorMode": 16384, "fontScale": 0x40000000}
	launchModes := map[string]int{"standard": 0, "singleTop": 1, "singleTask": 2, "singleInstance": 3, "singleInstancePerTask": 4}
	orientations := map[string]int{"unspecified": -1, "landscape": 0, "portrait": 1, "user": 2, "behind": 3, "sensor": 4, "nosensor": 5, "sensorLandscape": 6, "sensorPortrait": 7, "reverseLandscape": 8, "reversePortrait": 9, "fullSensor": 10, "userLandscape": 11, "userPortrait": 12, "fullUser": 13, "locked": 14}
	for i, activity := range activities {
		p := "activity." + strconv.Itoa(i) + "."
		put(facts, p+"name", qualify(pkg, activity.attr("name")))
		for _, name := range []string{"enabled", "exported", "directBootAware", "permission", "label"} {
			put(facts, p+name, activity.attr(name))
		}
		if _, hasLabel := activity.attrs["label"]; hasLabel {
			facts[p+"label"] = activity.attr("label")
		}
		put(facts, p+"enabledRes", activity.ref("enabled"))
		put(facts, p+"exportedRes", activity.ref("exported"))
		put(facts, p+"labelRes", activity.ref("label"))
		facts[p+"theme"] = "0"
		if activity.ref("theme") != "" {
			facts[p+"theme"] = activity.ref("theme")
		}
		if activity.attr("exported") == "" {
			facts[p+"exported"] = strconv.FormatBool(len(activity.nodes("intent-filter")) > 0)
		}
		put(facts, p+"parentActivityName", qualify(pkg, activity.attr("parentActivityName")))
		process := activity.attr("process")
		if process == "" {
			process = app.attr("process")
		}
		if process == "" {
			process = pkg
		}
		facts[p+"process"] = process
		mode, err := enumValue(activity, "launchMode", launchModes, 0, false)
		if err != nil {
			return result, err
		}
		changes, err := enumValue(activity, "configChanges", configBits, 0, true)
		if err != nil {
			return result, err
		}
		orientation, err := enumValue(activity, "screenOrientation", orientations, -1, false)
		if err != nil {
			return result, err
		}
		facts[p+"launch_mode"] = strconv.Itoa(mode)
		facts[p+"config_changes"] = strconv.Itoa(changes)
		facts[p+"screen_orientation"] = strconv.Itoa(orientation)
		emitFilters(facts, p, activity)
		emitMeta(facts, p, activity)
	}
	for _, kind := range []string{"receiver", "service", "provider"} {
		nodes := app.nodes(kind)
		facts[kind+".count"] = strconv.Itoa(len(nodes))
		for i, node := range nodes {
			p := kind + "." + strconv.Itoa(i) + "."
			put(facts, p+"name", qualify(pkg, node.attr("name")))
			fields(facts, p, node, []string{"enabled", "exported", "permission", "directBootAware", "isolated", "stopWithTask", "externalService", "singleUser", "useAppZygote", "allowSharedIsolatedProcess", "authorities", "grantUriPermissions", "multiprocess", "initOrder"})
			process := node.attr("process")
			if process == "" {
				process = app.attr("process")
			}
			if process == "" {
				process = pkg
			}
			facts[p+"process"] = process
			put(facts, p+"enabledRes", node.ref("enabled"))
			put(facts, p+"exportedRes", node.ref("exported"))
			emitFilters(facts, p, node)
			emitMeta(facts, p, node)
		}
	}
	result.ApplicationProperties = props(facts)
	return result, nil
}
