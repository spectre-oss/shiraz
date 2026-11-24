// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"archive/zip"
	"bytes"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"unicode/utf16"
	"unicode/utf8"
)

const androidXMLNS = "http://schemas.android.com/apk/res/android"

var (
	intLiteralPattern   = regexp.MustCompile(`^-?(?:0x[0-9a-fA-F]+|[0-9]+)$`)
	floatLiteralPattern = regexp.MustCompile(`^[-+]?[0-9]+\.[0-9]+(?:[eE][-+]?[0-9]+)?$`)
	refTargetPattern    = regexp.MustCompile(`^(?:0x[0-9a-fA-F]+|[0-9]+)$`)
)

type xmlAttr struct {
	text   string
	kind   byte
	bits   uint32
	strVal string
	hasStr bool
}

type xmlNode struct {
	tag      string
	attrs    map[string]xmlAttr
	children []*xmlNode
}

func androidAttrKey(name string) string {
	return "{" + androidXMLNS + "}" + name
}

func (n *xmlNode) plainAttr(name string) string {
	if n == nil {
		return ""
	}
	return n.attrs[name].text
}

func (n *xmlNode) androidAttr(name string) string {
	if n == nil {
		return ""
	}
	return n.attrs[androidAttrKey(name)].text
}

func (n *xmlNode) androidAttrOK(name string) (string, bool) {
	if n == nil {
		return "", false
	}
	a, ok := n.attrs[androidAttrKey(name)]
	return a.text, ok
}

func (n *xmlNode) androidAttrRaw(name string) (xmlAttr, bool) {
	if n == nil {
		return xmlAttr{}, false
	}
	a, ok := n.attrs[androidAttrKey(name)]
	return a, ok
}

func (n *xmlNode) androidRef(name string) string {
	if n == nil {
		return ""
	}
	a, ok := n.attrs[androidAttrKey(name)]
	if ok && (a.kind == 1 || a.kind == 7) {
		return strconv.FormatUint(uint64(a.bits), 10)
	}
	return ""
}

func (n *xmlNode) androidRefUint32(name string) uint32 {
	if n == nil {
		return 0
	}
	a, ok := n.attrs[androidAttrKey(name)]
	if ok && (a.kind == 1 || a.kind == 7) {
		return a.bits
	}
	return 0
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

func xmlStringPool(data []byte, start, header, end int, replaceInvalidUTF8 bool) ([]string, error) {
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
	if strEnd > end || strEnd < start+strStart {
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
			raw := data[p : p+length]
			if replaceInvalidUTF8 {
				out[i] = strings.ToValidUTF8(string(raw), "\ufffd")
			} else {
				if !utf8.Valid(raw) {
					return nil, errors.New("invalid UTF-8 XML string")
				}
				out[i] = string(raw)
			}
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

func parseBinaryXML(data []byte) (*xmlNode, error) {
	if len(data) < 8 || binary.LittleEndian.Uint16(data) != 3 || binary.LittleEndian.Uint16(data[2:]) != 8 || int(binary.LittleEndian.Uint32(data[4:])) != len(data) {
		return nil, errors.New("unsupported Android manifest XML")
	}
	var stringsPool []string
	var resourceIDs []uint32
	get := func(index uint32) (string, bool, error) {
		if index == 0xffffffff {
			return "", false, nil
		}
		if int(index) >= len(stringsPool) {
			return "", false, errors.New("XML string index outside pool")
		}
		return stringsPool[index], true, nil
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
		switch kind {
		case 1:
			pool, err := xmlStringPool(data, pos, header, end, false)
			if err != nil {
				return nil, err
			}
			stringsPool = pool
		case 0x180:
			if header != 8 || (length-header)%4 != 0 || (length-header)/4 > len(stringsPool) {
				return nil, errors.New("invalid XML resource map")
			}
			n := (length - header) / 4
			resourceIDs = make([]uint32, n)
			for i := 0; i < n; i++ {
				resourceIDs[i] = binary.LittleEndian.Uint32(data[pos+header+i*4:])
			}
		case 0x102:
			if header != 16 || pos+36 > end {
				return nil, errors.New("invalid XML element")
			}
			ns, _, err := get(binary.LittleEndian.Uint32(data[pos+16:]))
			if err != nil {
				return nil, err
			}
			local, hasLocal, err := get(binary.LittleEndian.Uint32(data[pos+20:]))
			if err != nil {
				return nil, err
			}
			if !hasLocal {
				return nil, errors.New("element without name")
			}
			tag := local
			if ns != "" {
				tag = "{" + ns + "}" + local
			}
			attrStart := int(binary.LittleEndian.Uint16(data[pos+24:]))
			attrSize := int(binary.LittleEndian.Uint16(data[pos+26:]))
			attrCount := int(binary.LittleEndian.Uint16(data[pos+28:]))
			first := pos + 16 + attrStart
			if attrStart < 20 || attrSize < 20 || attrCount > 256 || first+attrCount*attrSize > end {
				return nil, errors.New("invalid XML attributes")
			}
			node := &xmlNode{tag: tag, attrs: map[string]xmlAttr{}}
			for i := 0; i < attrCount; i++ {
				p := first + i*attrSize
				ansIdx := binary.LittleEndian.Uint32(data[p:])
				anameIdx := binary.LittleEndian.Uint32(data[p+4:])
				raw := binary.LittleEndian.Uint32(data[p+8:])
				valueSize := binary.LittleEndian.Uint16(data[p+12:])
				reserved := data[p+14]
				dtype := data[p+15]
				bits := binary.LittleEndian.Uint32(data[p+16:])
				if valueSize != 8 || reserved != 0 {
					return nil, errors.New("invalid XML attribute value")
				}
				ans, _, err := get(ansIdx)
				if err != nil {
					return nil, err
				}
				aname, hasName, err := get(anameIdx)
				if err != nil {
					return nil, err
				}
				if !hasName {
					return nil, errors.New("attribute without name")
				}
				// Android matches framework attributes by resource ID first; the
				// name and namespace strings are optional for mapped names.
				var ident uint32
				if int(anameIdx) < len(resourceIDs) {
					ident = resourceIDs[anameIdx]
				}
				if ident>>24 == 1 {
					ans = androidXMLNS
					if mapped, ok := androidAttributeNames[ident]; ok {
						aname = mapped
					} else if aname == "" {
						aname = fmt.Sprintf("@0x%08x", ident)
					}
				} else if ident != 0 && aname == "" {
					aname = fmt.Sprintf("@0x%08x", ident)
				}
				key := aname
				if ans != "" {
					key = "{" + ans + "}" + aname
				}
				if _, exists := node.attrs[key]; exists {
					// Lookup by ID returns the first match, matching Android and manifest.py.
					if ident != 0 {
						continue
					}
					return nil, errors.New("duplicate XML attribute")
				}
				var strVal string
				var hasStr bool
				if dtype == 3 {
					strVal, hasStr, err = get(bits)
					if err != nil {
						return nil, err
					}
				}
				value := ""
				if raw != 0xffffffff {
					value, _, err = get(raw)
				} else if dtype == 3 {
					value = strVal
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
				node.attrs[key] = xmlAttr{text: value, kind: dtype, bits: bits, strVal: strVal, hasStr: hasStr}
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
		case 0x103:
			if header != 16 || pos+24 > end {
				return nil, errors.New("invalid XML end element")
			}
			ns, _, err := get(binary.LittleEndian.Uint32(data[pos+16:]))
			if err != nil {
				return nil, err
			}
			local, hasLocal, err := get(binary.LittleEndian.Uint32(data[pos+20:]))
			if err != nil {
				return nil, err
			}
			if !hasLocal {
				return nil, errors.New("unbalanced XML")
			}
			tag := local
			if ns != "" {
				tag = "{" + ns + "}" + local
			}
			if len(stack) == 0 || stack[len(stack)-1].tag != tag {
				return nil, errors.New("unbalanced XML")
			}
			stack = stack[:len(stack)-1]
		}
		pos = end
	}
	if root == nil || len(stack) > 0 {
		return nil, errors.New("incomplete XML document")
	}
	return root, nil
}

func parseManifestXML(data []byte) (*xmlNode, error) {
	root, err := parseBinaryXML(data)
	if err != nil {
		return nil, err
	}
	if root.tag != "manifest" || root.plainAttr("package") == "" {
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

func parseInt32Literal(raw string) (int, error) {
	if !intLiteralPattern.MatchString(raw) {
		return 0, fmt.Errorf("invalid integer literal: %s", raw)
	}
	neg := strings.HasPrefix(raw, "-")
	body := raw
	if neg {
		body = raw[1:]
	}
	base := 10
	if strings.HasPrefix(body, "0x") || strings.HasPrefix(body, "0X") {
		base = 16
		body = body[2:]
	}
	u, err := strconv.ParseUint(body, base, 64)
	if err != nil {
		return 0, err
	}
	var v int64
	if neg {
		v = -int64(u)
	} else {
		v = int64(u)
	}
	if v < -0x80000000 || v > 0xffffffff {
		return 0, errors.New("integer out of range")
	}
	if v >= 0x80000000 {
		v -= 0x100000000
	}
	return int(v), nil
}

func enumValue(node *xmlNode, key string, names map[string]int, defaultValue int, flags bool) (int, error) {
	value, ok := node.androidAttrRaw(key)
	if !ok {
		return defaultValue, nil
	}
	if value.kind == 0x10 || value.kind == 0x11 {
		return int(int32(value.bits)), nil
	}
	if value.kind != 0 && value.kind != 3 {
		return 0, fmt.Errorf("unsupported %s attribute type: 0x%02x", key, value.kind)
	}
	raw := value.text
	if raw == "" {
		return 0, fmt.Errorf("empty %s value", key)
	}
	if n, err := parseInt32Literal(raw); err == nil {
		return n, nil
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

func foregroundServiceType(service *xmlNode) (int, error) {
	raw, ok := service.androidAttrOK("foregroundServiceType")
	if !ok {
		return 0, nil
	}
	if len(raw) > 256 {
		return 0, errors.New("foreground service type length")
	}
	if raw == "" {
		return 0, errors.New("invalid foreground service type")
	}
	// Accept decimal or 0x-hex literals (including binary XML typed 0x10/0x11,
	// which parseBinaryXML formats as unsigned decimal strings).
	isDec := true
	for i := 0; i < len(raw); i++ {
		if raw[i] < '0' || raw[i] > '9' {
			isDec = false
			break
		}
	}
	if isDec || strings.HasPrefix(raw, "0x") {
		var u uint64
		var err error
		if strings.HasPrefix(raw, "0x") {
			u, err = strconv.ParseUint(raw[2:], 16, 64)
		} else {
			u, err = strconv.ParseUint(raw, 10, 64)
		}
		if err != nil || u > 0x7fffffff {
			return 0, errors.New("invalid foreground service type")
		}
		return int(u), nil
	}
	flags := map[string]int{
		"none":            0,
		"dataSync":        1,
		"mediaPlayback":   2,
		"phoneCall":       4,
		"location":        8,
		"connectedDevice": 16,
		"mediaProjection": 32,
		"camera":          64,
		"microphone":      128,
		"health":          256,
		"remoteMessaging": 512,
		"systemExempted":  1024,
		"shortService":    2048,
		"specialUse":      0x40000000,
	}
	result := 0
	for _, part := range strings.Split(raw, "|") {
		bit, exists := flags[part]
		if !exists {
			return 0, errors.New("unsupported foreground service type")
		}
		result |= bit
	}
	return result, nil
}

func put(m map[string]string, key, value string) {
	if value != "" {
		m[key] = value
	}
}

type metaBudget struct {
	entries int
	bytes   int
}

func metadataValue(entry *xmlNode) (string, string, error) {
	resAttr, hasRes := entry.androidAttrRaw("resource")
	valAttr, hasVal := entry.androidAttrRaw("value")
	if !hasRes && !hasVal {
		return "", "", errors.New("metadata requires resource or value")
	}
	field := "value"
	attr := valAttr
	if hasRes {
		field = "resource"
		attr = resAttr
	}
	if attr.kind != 0 {
		if field == "resource" {
			if attr.kind == 1 {
				return "resource", strconv.FormatUint(uint64(attr.bits), 10), nil
			}
			return "unsupported", strconv.Itoa(int(attr.kind)), nil
		}
		switch {
		case attr.kind == 1:
			return "reference", strconv.FormatUint(uint64(attr.bits), 10), nil
		case attr.kind == 3:
			return "string", attr.strVal, nil
		case attr.kind == 4:
			return "float_bits", strconv.FormatUint(uint64(attr.bits), 10), nil
		case attr.kind == 0x12:
			return "boolean", strconv.FormatBool(attr.bits != 0), nil
		case attr.kind >= 0x10 && attr.kind <= 0x1f:
			return "integer", strconv.FormatInt(int64(int32(attr.bits)), 10), nil
		default:
			return "unsupported", strconv.Itoa(int(attr.kind)), nil
		}
	}
	raw := attr.text
	if strings.HasPrefix(raw, "@") {
		target := raw[1:]
		kind := "reference"
		if field == "resource" {
			kind = "resource"
		}
		if refTargetPattern.MatchString(target) {
			var n uint64
			var err error
			if strings.HasPrefix(target, "0x") || strings.HasPrefix(target, "0X") {
				n, err = strconv.ParseUint(target[2:], 16, 32)
			} else {
				n, err = strconv.ParseUint(target, 10, 32)
			}
			if err == nil {
				return kind, strconv.FormatUint(n, 10), nil
			}
		}
		return kind + "_name", target, nil
	}
	if field == "resource" {
		return "unsupported", "non-reference resource", nil
	}
	if raw == "true" || raw == "false" {
		return "boolean", raw, nil
	}
	if n, err := parseInt32Literal(raw); err == nil {
		return "integer", strconv.Itoa(n), nil
	}
	if floatLiteralPattern.MatchString(raw) {
		return "float", raw, nil
	}
	return "string", raw, nil
}

func emitMeta(m map[string]string, prefix string, node *xmlNode, budget *metaBudget) error {
	entries := node.nodes("meta-data")
	if len(entries) > 128 {
		return errors.New("component metadata limit")
	}
	m[prefix+"metadata.count"] = strconv.Itoa(len(entries))
	for i, entry := range entries {
		key := entry.androidAttr("name")
		if key == "" || len(key) > 4096 {
			return errors.New("invalid component metadata name")
		}
		kind, value, err := metadataValue(entry)
		if err != nil {
			return err
		}
		budget.entries++
		budget.bytes += len(key) + len(value)
		if budget.entries > 4096 || budget.bytes > 4*1024*1024 {
			return errors.New("component metadata budget exceeded")
		}
		p := prefix + "metadata." + strconv.Itoa(i) + "."
		m[p+"name"] = key
		put(m, p+"name_resource", entry.androidRef("name"))
		m[p+"kind"] = kind
		m[p+"value"] = value
	}
	return nil
}

func emitFilters(m map[string]string, prefix string, node *xmlNode) error {
	filters := node.nodes("intent-filter")
	if len(filters) > 128 {
		return errors.New("activity filter limit")
	}
	m[prefix+"filter.count"] = strconv.Itoa(len(filters))
	for i, filter := range filters {
		p := prefix + "filter." + strconv.Itoa(i) + "."
		priority := 0
		if attr, ok := filter.androidAttrRaw("priority"); ok {
			if attr.kind != 0 {
				if attr.kind != 0x10 && attr.kind != 0x11 {
					return errors.New("intent filter priority must be an integer")
				}
				priority = int(int32(attr.bits))
			} else {
				n, err := parseInt32Literal(attr.text)
				if err != nil || n < -0x80000000 || n > 0x7fffffff {
					return errors.New("invalid intent filter priority")
				}
				priority = n
			}
		}
		m[p+"priority"] = strconv.Itoa(priority)
		m[p+"unsupported"] = "false"
		for _, kind := range []string{"action", "category"} {
			items := filter.nodes(kind)
			if len(items) > 128 {
				return errors.New("activity filter entry limit")
			}
			m[p+kind+".count"] = strconv.Itoa(len(items))
			for j, x := range items {
				name := x.androidAttr("name")
				if name == "" || len(name) > 4096 {
					return errors.New("invalid activity filter name")
				}
				m[p+kind+"."+strconv.Itoa(j)] = name
			}
		}
		dataNodes := filter.nodes("data")
		if len(dataNodes) > 4096 {
			return errors.New("activity filter data limit")
		}
		schemeKey := androidAttrKey("scheme")
		mimeKey := androidAttrKey("mimeType")
		schemes := []string{}
		mimes := []string{}
		seenScheme := map[string]bool{}
		seenMime := map[string]bool{}
		for _, x := range dataNodes {
			if s, ok := x.androidAttrOK("scheme"); ok && !seenScheme[s] {
				schemes = append(schemes, s)
				seenScheme[s] = true
			}
			if mt, ok := x.androidAttrOK("mimeType"); ok && !seenMime[mt] {
				mimes = append(mimes, mt)
				seenMime[mt] = true
			}
			for attrKey := range x.attrs {
				if attrKey != schemeKey && attrKey != mimeKey {
					m[p+"unsupported"] = "true"
				}
			}
		}
		if len(schemes) > 128 || len(mimes) > 128 {
			return errors.New("activity filter data entry limit")
		}
		m[p+"scheme.count"] = strconv.Itoa(len(schemes))
		for j, v := range schemes {
			if len(v) > 4096 {
				return errors.New("activity filter scheme length")
			}
			m[p+"scheme."+strconv.Itoa(j)] = v
		}
		m[p+"mime.count"] = strconv.Itoa(len(mimes))
		for j, v := range mimes {
			if len(v) > 4096 {
				return errors.New("activity filter mime length")
			}
			m[p+"mime."+strconv.Itoa(j)] = v
		}
	}
	return nil
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
			case '\f':
				b.WriteString("\\f")
			default:
				if c < 32 {
					fmt.Fprintf(&b, "\\u%04x", c)
				} else {
					b.WriteRune(c)
				}
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

func featureRecord(item *xmlNode, grouped bool) (map[string]string, int, error) {
	req, hasReq := item.androidAttrOK("required")
	if hasReq && req != "true" && req != "false" {
		return nil, 0, errors.New("unsupported feature required value")
	}
	ver, err := enumValue(item, "version", nil, 0, false)
	if err != nil {
		return nil, 0, err
	}
	gles, err := enumValue(item, "glEsVersion", nil, 0, false)
	if err != nil {
		return nil, 0, err
	}
	flags := 0
	if grouped || !hasReq || req != "false" {
		flags = 1
	}
	rec := map[string]string{
		"version":        strconv.Itoa(ver),
		"reqGlEsVersion": strconv.Itoa(gles),
		"flags":          strconv.Itoa(flags),
	}
	if name, ok := item.androidAttrOK("name"); ok {
		rec["name"] = name
	}
	return rec, gles, nil
}

func inspectAPK(apk []byte, version string, architecture string) (metadata, error) {
	var result metadata
	abi, _, err := targetABI(architecture)
	if err != nil {
		return result, err
	}
	result.Architecture = architecture
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
	hasTargetABI := false
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
			if strings.HasPrefix(name, "lib/"+abi+"/") {
				hasTargetABI = true
			}
		}
		if file.Name == "AndroidManifest.xml" {
			manifest = file
		}
		if file.Name == "classes.dex" {
			hasDex = true
		}
	}
	if hasNative && !hasTargetABI {
		return result, fmt.Errorf("APK has no %s native library candidate", abi)
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
	pkg := root.plainAttr("package")
	app := root.first("application")
	if app == nil {
		return result, errors.New("APK has no application")
	}
	sdk := root.first("uses-sdk")
	min := sdk.androidAttr("minSdkVersion")
	if min == "" {
		min = "1"
	}
	n, err := strconv.Atoi(min)
	if err != nil || n > 27 {
		return result, errors.New("APK minSdk is unsupported")
	}
	if app.androidAttr("enabled") == "false" {
		return result, errors.New("APK application is disabled")
	}
	cleartext, hasCleartext := app.androidAttrOK("usesCleartextTraffic")
	if hasCleartext && cleartext != "true" && cleartext != "false" && !strings.HasPrefix(cleartext, "@") {
		return result, errors.New("invalid usesCleartextTraffic boolean")
	}

	appClass := qualify(pkg, app.androidAttr("name"))
	result.Package = pkg
	result.Application = appClass
	if result.Application == "" {
		result.Application = "android.app.Application"
	}
	result.Version = version
	result.IconRes = app.androidRefUint32("icon")

	revisionCode, err := enumValue(root, "revisionCode", nil, 0, false)
	if err != nil {
		return result, err
	}
	installLoc, err := enumValue(root, "installLocation", map[string]int{"auto": 0, "internalOnly": 1, "preferExternal": 2}, -1, false)
	if err != nil {
		return result, err
	}
	facts := map[string]string{
		"package":          pkg,
		"versionCode":      "0",
		"versionCodeMajor": "0",
		"sharedUserLabel":  strconv.FormatUint(uint64(root.androidRefUint32("sharedUserLabel")), 10),
		"baseRevisionCode": strconv.Itoa(revisionCode),
		"installLocation":  strconv.Itoa(installLoc),
		"minSdk":           min,
		"targetSdk":        min,
		"theme":            strconv.FormatUint(uint64(app.androidRefUint32("theme")), 10),
		"icon":             strconv.FormatUint(uint64(result.IconRes), 10),
		"logo":             strconv.FormatUint(uint64(app.androidRefUint32("logo")), 10),
		"banner":           strconv.FormatUint(uint64(app.androidRefUint32("banner")), 10),
	}
	put(facts, "applicationClass", appClass)
	put(facts, "sharedUserId", root.androidAttr("sharedUserId"))
	for _, pair := range [][2]string{{"versionName", "versionName"}, {"versionCode", "versionCode"}, {"versionCodeMajor", "versionCodeMajor"}} {
		put(facts, pair[0], root.androidAttr(pair[1]))
	}
	put(facts, "versionNameRes", root.androidRef("versionName"))
	if ts := sdk.androidAttr("targetSdkVersion"); ts != "" {
		facts["targetSdk"] = ts
	}
	for _, name := range []string{"enabled", "debuggable", "hasCode"} {
		put(facts, name, app.androidAttr(name))
	}
	if hasCleartext {
		facts["usesCleartextTraffic"] = cleartext
	}
	put(facts, "networkSecurityConfig", app.androidAttr("networkSecurityConfig"))
	if lbl, ok := app.androidAttrOK("label"); ok {
		facts["label"] = lbl
	}
	put(facts, "labelRes", app.androidRef("label"))

	// applicationInfo.*
	put(facts, "applicationInfo.backupAgent", qualify(pkg, app.androidAttr("backupAgent")))
	put(facts, "applicationInfo.manageSpaceActivity", qualify(pkg, app.androidAttr("manageSpaceActivity")))
	put(facts, "applicationInfo.appComponentFactory", qualify(pkg, app.androidAttr("appComponentFactory")))
	put(facts, "applicationInfo.permission", app.androidAttr("permission"))
	appProcess := app.androidAttr("process")
	if appProcess == "" {
		appProcess = pkg
	}
	if strings.HasPrefix(appProcess, ":") {
		facts["applicationInfo.processName"] = pkg + appProcess
	} else {
		facts["applicationInfo.processName"] = appProcess
	}
	if affinity, hasAffinity := app.androidAttrOK("taskAffinity"); !hasAffinity {
		facts["applicationInfo.taskAffinity"] = pkg
	} else if strings.HasPrefix(affinity, ":") {
		facts["applicationInfo.taskAffinity"] = pkg + affinity
	} else {
		facts["applicationInfo.taskAffinity"] = affinity
	}
	facts["applicationInfo.descriptionRes"] = strconv.FormatUint(uint64(app.androidRefUint32("description")), 10)
	uiOptions, err := enumValue(app, "uiOptions", map[string]int{"none": 0, "splitActionBarWhenNarrow": 1}, 0, false)
	if err != nil {
		return result, err
	}
	facts["applicationInfo.uiOptions"] = strconv.Itoa(uiOptions)
	appCategory, err := enumValue(app, "appCategory", map[string]int{
		"game": 0, "audio": 1, "video": 2, "image": 3, "social": 4,
		"news": 5, "maps": 6, "productivity": 7, "accessibility": 8,
	}, -1, false)
	if err != nil {
		return result, err
	}
	facts["applicationInfo.category"] = strconv.Itoa(appCategory)
	screens := root.first("supports-screens")
	for _, field := range []string{"requiresSmallestWidthDp", "compatibleWidthLimitDp", "largestWidthLimitDp"} {
		val := 0
		if screens != nil {
			val, err = enumValue(screens, field, nil, 0, false)
			if err != nil {
				return result, err
			}
		}
		facts["applicationInfo."+field] = strconv.Itoa(val)
	}
	targetSandbox, err := enumValue(root, "targetSandboxVersion", nil, 1, false)
	if err != nil {
		return result, err
	}
	facts["applicationInfo.targetSandboxVersion"] = strconv.Itoa(targetSandbox)
	compileSdk, err := enumValue(root, "compileSdkVersion", nil, 0, false)
	if err != nil {
		return result, err
	}
	facts["applicationInfo.compileSdkVersion"] = strconv.Itoa(compileSdk)
	put(facts, "applicationInfo.compileSdkVersionCodename", root.androidAttr("compileSdkVersionCodename"))

	budget := &metaBudget{}
	if err := emitMeta(facts, "application.", app, budget); err != nil {
		return result, err
	}

	// uses-permission (sorted, unique)
	requested := map[string]bool{}
	for _, x := range root.nodes("uses-permission") {
		if name := x.androidAttr("name"); name != "" {
			requested[name] = true
		}
	}
	if len(requested) > 1024 {
		return result, errors.New("permission declaration limit")
	}
	permissions := make([]string, 0, len(requested))
	for name := range requested {
		permissions = append(permissions, name)
	}
	sort.Strings(permissions)
	facts["permission.count"] = strconv.Itoa(len(permissions))
	for i, v := range permissions {
		facts["permission."+strconv.Itoa(i)] = v
	}

	// definedPermission & ownSignaturePermission
	permNodes := root.nodes("permission")
	if len(permNodes) > 1024 {
		return result, errors.New("defined permission limit")
	}
	facts["definedPermission.count"] = strconv.Itoa(len(permNodes))
	protectionLevels := map[string]int{"normal": 0, "dangerous": 1, "signature": 2, "signatureOrSystem": 3, "privileged": 16}
	ownSet := map[string]bool{}
	for i, permission := range permNodes {
		p := "definedPermission." + strconv.Itoa(i) + "."
		name := permission.androidAttr("name")
		put(facts, p+"name", name)
		protection, err := enumValue(permission, "protectionLevel", protectionLevels, 0, true)
		if err != nil {
			return result, err
		}
		facts[p+"protection"] = strconv.Itoa(protection)
		if name != "" && strings.HasPrefix(name, pkg+".") && requested[name] && protection&15 == 2 {
			ownSet[name] = true
		}
	}
	own := make([]string, 0, len(ownSet))
	for name := range ownSet {
		own = append(own, name)
	}
	sort.Strings(own)
	facts["ownSignaturePermission.count"] = strconv.Itoa(len(own))
	for i, name := range own {
		facts["ownSignaturePermission."+strconv.Itoa(i)] = name
	}

	// launcher activities (in document order across activity and activity-alias)
	type launcherEntry struct {
		name   string
		target string
	}
	launchers := []launcherEntry{}
	for _, child := range app.children {
		if child.tag != "activity" && child.tag != "activity-alias" {
			continue
		}
		for _, filter := range child.nodes("intent-filter") {
			mainAct, launcherCat := false, false
			for _, action := range filter.nodes("action") {
				mainAct = mainAct || action.androidAttr("name") == "android.intent.action.MAIN"
			}
			for _, category := range filter.nodes("category") {
				launcherCat = launcherCat || category.androidAttr("name") == "android.intent.category.LAUNCHER"
			}
			if mainAct && launcherCat {
				compName := qualify(pkg, child.androidAttr("name"))
				target := child.androidAttr("targetActivity")
				if target == "" {
					target = child.androidAttr("name")
				}
				launchers = append(launchers, launcherEntry{name: compName, target: qualify(pkg, target)})
			}
		}
	}
	if len(launchers) == 0 {
		return result, errors.New("APK has no launcher")
	}
	if len(launchers) > 256 {
		return result, errors.New("launcher declaration limit")
	}
	result.Activity = launchers[0].target
	facts["launcher.count"] = strconv.Itoa(len(launchers))
	for i, l := range launchers {
		facts["launcher."+strconv.Itoa(i)+".target"] = l.target
		name := l.name
		if name == "" {
			name = l.target
		}
		facts["launcher."+strconv.Itoa(i)+".name"] = name
	}

	// activities
	activities := app.nodes("activity")
	if len(activities) > 512 {
		return result, errors.New("activity declaration limit")
	}
	seenActivities := map[string]bool{}
	facts["activity.count"] = strconv.Itoa(len(activities))
	configBits := map[string]int{"mcc": 1, "mnc": 2, "locale": 4, "touchscreen": 8, "keyboard": 16, "keyboardHidden": 32, "navigation": 64, "orientation": 128, "screenLayout": 256, "uiMode": 512, "screenSize": 1024, "smallestScreenSize": 2048, "density": 4096, "layoutDirection": 8192, "colorMode": 16384, "fontScale": 0x40000000}
	launchModes := map[string]int{"standard": 0, "singleTop": 1, "singleTask": 2, "singleInstance": 3, "singleInstancePerTask": 4}
	orientations := map[string]int{"unspecified": -1, "landscape": 0, "portrait": 1, "user": 2, "behind": 3, "sensor": 4, "nosensor": 5, "sensorLandscape": 6, "sensorPortrait": 7, "reverseLandscape": 8, "reversePortrait": 9, "fullSensor": 10, "userLandscape": 11, "userPortrait": 12, "fullUser": 13, "locked": 14}
	appPermission, hasAppPermission := app.androidAttrOK("permission")
	appDirectBoot := app.androidAttr("directBootAware")
	appRawProcess := app.androidAttr("process")

	for i, activity := range activities {
		name := qualify(pkg, activity.androidAttr("name"))
		if name == "" || seenActivities[name] {
			return result, errors.New("invalid or duplicate activity declaration")
		}
		seenActivities[name] = true
		p := "activity." + strconv.Itoa(i) + "."
		facts[p+"name"] = name
		put(facts, p+"enabled", activity.androidAttr("enabled"))
		if exp, ok := activity.androidAttrOK("exported"); ok {
			facts[p+"exported"] = exp
		} else {
			facts[p+"exported"] = strconv.FormatBool(len(activity.nodes("intent-filter")) > 0)
		}
		directBoot := activity.androidAttr("directBootAware")
		if directBoot == "" {
			directBoot = appDirectBoot
		}
		put(facts, p+"directBootAware", directBoot)
		if perm, ok := activity.androidAttrOK("permission"); ok {
			put(facts, p+"permission", perm)
		} else if hasAppPermission {
			put(facts, p+"permission", appPermission)
		}
		if lbl, ok := activity.androidAttrOK("label"); ok {
			facts[p+"label"] = lbl
		}
		put(facts, p+"enabledRes", activity.androidRef("enabled"))
		put(facts, p+"exportedRes", activity.androidRef("exported"))
		put(facts, p+"labelRes", activity.androidRef("label"))
		facts[p+"theme"] = strconv.FormatUint(uint64(activity.androidRefUint32("theme")), 10)
		put(facts, p+"parentActivityName", qualify(pkg, activity.androidAttr("parentActivityName")))
		process := activity.androidAttr("process")
		if process == "" {
			process = appRawProcess
		}
		if process == "" {
			process = pkg
		}
		facts[p+"process"] = process
		mode, err := enumValue(activity, "launchMode", launchModes, 0, false)
		if err != nil || mode < 0 || mode > 4 {
			return result, errors.New("invalid activity launch mode")
		}
		changes, err := enumValue(activity, "configChanges", configBits, 0, true)
		if err != nil {
			return result, err
		}
		orientation, err := enumValue(activity, "screenOrientation", orientations, -1, false)
		if err != nil || orientation < -1 || orientation > 14 {
			return result, errors.New("invalid activity orientation")
		}
		facts[p+"launch_mode"] = strconv.Itoa(mode)
		facts[p+"config_changes"] = strconv.Itoa(changes)
		facts[p+"screen_orientation"] = strconv.Itoa(orientation)
		if err := emitFilters(facts, p, activity); err != nil {
			return result, err
		}
		if err := emitMeta(facts, p, activity, budget); err != nil {
			return result, err
		}
	}

	// activity-aliases
	aliases := app.nodes("activity-alias")
	if len(aliases) > 256 {
		return result, errors.New("activity alias declaration limit")
	}
	seenAliases := map[string]bool{}
	facts["alias.count"] = strconv.Itoa(len(aliases))
	for i, alias := range aliases {
		name := qualify(pkg, alias.androidAttr("name"))
		target := qualify(pkg, alias.androidAttr("targetActivity"))
		if name == "" || target == "" || seenAliases[name] || seenActivities[name] {
			return result, errors.New("invalid or duplicate activity alias")
		}
		seenAliases[name] = true
		p := "alias." + strconv.Itoa(i) + "."
		facts[p+"name"] = name
		facts[p+"target"] = target
		put(facts, p+"enabled", alias.androidAttr("enabled"))
		if exp, ok := alias.androidAttrOK("exported"); ok {
			facts[p+"exported"] = exp
		} else {
			facts[p+"exported"] = strconv.FormatBool(len(alias.nodes("intent-filter")) > 0)
		}
		put(facts, p+"permission", alias.androidAttr("permission"))
		if err := emitFilters(facts, p, alias); err != nil {
			return result, err
		}
	}

	// receivers
	receivers := app.nodes("receiver")
	if len(receivers) > 256 {
		return result, errors.New("receiver declaration limit")
	}
	facts["receiver.count"] = strconv.Itoa(len(receivers))
	for i, receiver := range receivers {
		name := qualify(pkg, receiver.androidAttr("name"))
		if name == "" {
			return result, errors.New("invalid receiver declaration")
		}
		p := "receiver." + strconv.Itoa(i) + "."
		facts[p+"name"] = name
		for _, field := range []string{"enabled", "exported", "permission", "directBootAware"} {
			put(facts, p+field, receiver.androidAttr(field))
		}
		process := receiver.androidAttr("process")
		if process == "" {
			process = appRawProcess
		}
		if process == "" {
			process = pkg
		}
		facts[p+"process"] = process
		put(facts, p+"enabledRes", receiver.androidRef("enabled"))
		put(facts, p+"exportedRes", receiver.androidRef("exported"))
		if err := emitFilters(facts, p, receiver); err != nil {
			return result, err
		}
		if err := emitMeta(facts, p, receiver, budget); err != nil {
			return result, err
		}
	}

	// uses-feature, feature-group, uses-configuration
	featureNodes := root.nodes("uses-feature")
	if len(featureNodes) > 256 {
		return result, errors.New("feature declaration limit")
	}
	facts["feature.count"] = strconv.Itoa(len(featureNodes))
	type configEntry map[string]string
	configs := []configEntry{}
	for _, item := range root.nodes("uses-configuration") {
		kb, hasKB := item.androidAttrOK("reqHardKeyboard")
		nav, hasNav := item.androidAttrOK("reqFiveWayNav")
		if (hasKB && kb != "true" && kb != "false") || (hasNav && nav != "true" && nav != "false") {
			return result, errors.New("unsupported configuration boolean")
		}
		touch, err := enumValue(item, "reqTouchScreen", map[string]int{"notouch": 1, "stylus": 2, "finger": 3}, 0, false)
		if err != nil {
			return result, err
		}
		kbType, err := enumValue(item, "reqKeyboardType", map[string]int{"nokeys": 1, "qwerty": 2, "twelvekey": 3}, 0, false)
		if err != nil {
			return result, err
		}
		navType, err := enumValue(item, "reqNavigation", map[string]int{"nonav": 1, "dpad": 2, "trackball": 3, "wheel": 4}, 0, false)
		if err != nil {
			return result, err
		}
		inputFlags := 0
		if kb == "true" {
			inputFlags |= 1
		}
		if nav == "true" {
			inputFlags |= 2
		}
		configs = append(configs, configEntry{
			"reqTouchScreen":   strconv.Itoa(touch),
			"reqKeyboardType":  strconv.Itoa(kbType),
			"reqNavigation":    strconv.Itoa(navType),
			"reqInputFeatures": strconv.Itoa(inputFlags),
			"reqGlEsVersion":   "0",
		})
	}
	for i, item := range featureNodes {
		rec, gles, err := featureRecord(item, false)
		if err != nil {
			return result, err
		}
		p := "feature." + strconv.Itoa(i) + "."
		for k, v := range rec {
			facts[p+k] = v
		}
		if _, hasName := rec["name"]; !hasName && gles != 0 {
			configs = append(configs, configEntry{"reqGlEsVersion": strconv.Itoa(gles)})
		}
	}
	if len(configs) > 256 {
		return result, errors.New("configuration declaration limit")
	}
	facts["configuration.count"] = strconv.Itoa(len(configs))
	for i, cfg := range configs {
		p := "configuration." + strconv.Itoa(i) + "."
		for k, v := range cfg {
			facts[p+k] = v
		}
	}
	groupNodes := root.nodes("feature-group")
	if len(groupNodes) > 64 {
		return result, errors.New("feature group limit")
	}
	facts["featureGroup.count"] = strconv.Itoa(len(groupNodes))
	groupedTotal := 0
	for i, group := range groupNodes {
		items := group.nodes("uses-feature")
		groupedTotal += len(items)
		if groupedTotal > 256 {
			return result, errors.New("feature group limit")
		}
		gp := "featureGroup." + strconv.Itoa(i)
		facts[gp+".count"] = strconv.Itoa(len(items))
		for j, item := range items {
			rec, _, err := featureRecord(item, true)
			if err != nil {
				return result, err
			}
			p := gp + "." + strconv.Itoa(j) + "."
			for k, v := range rec {
				facts[p+k] = v
			}
		}
	}

	// instrumentation
	instNodes := root.nodes("instrumentation")
	if len(instNodes) > 256 {
		return result, errors.New("instrumentation declaration limit")
	}
	seenInst := map[string]bool{}
	facts["instrumentation.count"] = strconv.Itoa(len(instNodes))
	for i, item := range instNodes {
		name := qualify(pkg, item.androidAttr("name"))
		target := item.androidAttr("targetPackage")
		if name == "" || target == "" || seenInst[name] {
			return result, errors.New("invalid or duplicate instrumentation declaration")
		}
		seenInst[name] = true
		p := "instrumentation." + strconv.Itoa(i) + "."
		facts[p+"name"] = name
		facts[p+"targetPackage"] = target
		for _, field := range []string{"targetProcesses", "handleProfiling", "functionalTest", "label"} {
			put(facts, p+field, item.androidAttr(field))
		}
		for _, field := range []string{"label", "icon", "logo", "banner"} {
			facts[p+field+"Res"] = strconv.FormatUint(uint64(item.androidRefUint32(field)), 10)
		}
		if err := emitMeta(facts, p, item, budget); err != nil {
			return result, err
		}
	}

	// services
	services := app.nodes("service")
	if len(services) > 512 {
		return result, errors.New("service declaration limit")
	}
	seenServices := map[string]bool{}
	facts["service.count"] = strconv.Itoa(len(services))
	for i, service := range services {
		name := qualify(pkg, service.androidAttr("name"))
		if name == "" || seenServices[name] {
			return result, errors.New("invalid or duplicate service declaration")
		}
		seenServices[name] = true
		fgType, err := foregroundServiceType(service)
		if err != nil {
			return result, err
		}
		p := "service." + strconv.Itoa(i) + "."
		facts[p+"name"] = name
		put(facts, p+"enabled", service.androidAttr("enabled"))
		put(facts, p+"enabledRes", service.androidRef("enabled"))
		put(facts, p+"exportedRes", service.androidRef("exported"))
		process := service.androidAttr("process")
		if process == "" {
			process = appRawProcess
		}
		if process == "" {
			process = pkg
		}
		facts[p+"process"] = process
		put(facts, p+"isolated", service.androidAttr("isolatedProcess"))
		if exp, ok := service.androidAttrOK("exported"); ok {
			facts[p+"exported"] = exp
		} else {
			facts[p+"exported"] = strconv.FormatBool(len(service.nodes("intent-filter")) > 0)
		}
		if perm, ok := service.androidAttrOK("permission"); ok {
			put(facts, p+"permission", perm)
		} else if hasAppPermission {
			put(facts, p+"permission", appPermission)
		}
		put(facts, p+"directBootAware", service.androidAttr("directBootAware"))
		facts[p+"foregroundServiceType"] = strconv.Itoa(fgType)
		for _, field := range []string{"stopWithTask", "externalService", "singleUser", "useAppZygote", "allowSharedIsolatedProcess"} {
			put(facts, p+field, service.androidAttr(field))
		}
		if err := emitFilters(facts, p, service); err != nil {
			return result, err
		}
		if err := emitMeta(facts, p, service, budget); err != nil {
			return result, err
		}
	}

	// providers
	providers := app.nodes("provider")
	if len(providers) > 256 {
		return result, errors.New("provider declaration limit")
	}
	facts["provider.count"] = strconv.Itoa(len(providers))
	for i, provider := range providers {
		name := qualify(pkg, provider.androidAttr("name"))
		if name == "" {
			return result, errors.New("invalid provider declaration")
		}
		p := "provider." + strconv.Itoa(i) + "."
		facts[p+"name"] = name
		for _, field := range []string{"authorities", "enabled", "exported", "grantUriPermissions", "multiprocess", "initOrder"} {
			put(facts, p+field, provider.androidAttr(field))
		}
		process := provider.androidAttr("process")
		if process == "" {
			process = appRawProcess
		}
		if process == "" {
			process = pkg
		}
		facts[p+"process"] = process
		perm, hasPerm := provider.androidAttrOK("permission")
		if !hasPerm && hasAppPermission {
			perm = appPermission
			hasPerm = true
		}
		if rp, ok := provider.androidAttrOK("readPermission"); ok {
			put(facts, p+"readPermission", rp)
		} else if hasPerm {
			put(facts, p+"readPermission", perm)
		}
		if wp, ok := provider.androidAttrOK("writePermission"); ok {
			put(facts, p+"writePermission", wp)
		} else if hasPerm {
			put(facts, p+"writePermission", perm)
		}
		if err := emitFilters(facts, p, provider); err != nil {
			return result, err
		}
		if err := emitMeta(facts, p, provider, budget); err != nil {
			return result, err
		}
	}

	// Resolve resource-backed component enabled/exported/process flags via resources.arsc.
	if err := normalizeManifestFlags(archive, facts); err != nil {
		return result, err
	}

	// Best-effort resolved label via resources.arsc (for desktop entries and Flatpak metadata).
	labelRes := app.androidRefUint32("label")
	if labelRes != 0 {
		if table, err := readResourceTableFromZip(archive); err == nil {
			result.Label = resolveResourceLabel(table, labelRes, app.androidAttr("label"))
		}
	} else {
		result.Label = resolveResourceLabel(nil, 0, app.androidAttr("label"))
	}

	result.ApplicationProperties = props(facts)
	return result, nil
}
