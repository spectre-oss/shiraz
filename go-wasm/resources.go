// SPDX-License-Identifier: GPL-3.0-only
package main

import (
	"archive/zip"
	"encoding/binary"
	"errors"
	"io"
	"path"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

const (
	maxResourceTable    = 64 << 20
	maxTypeChunksPerKey = 4096
	maxEntryCount       = 65536
	maxRefDepth         = 8
	maxIconVisits       = 4096
	maxIconXML          = 1 << 20
	maxIconImage        = 4 << 20
	resNoEntry          = 0xffffffff
	resDensityAny       = 0xfffe
	resDensityNone      = 0xffff
	resTypeReference    = 0x01
	resTypeString       = 0x03
	resTypeBoolean      = 0x12
)

var manifestProcessPattern = regexp.MustCompile(`^:?[A-Za-z_][A-Za-z0-9_.]*$`)

type resTypeKey struct {
	pkg  byte
	kind byte
}

type resTypeChunk struct {
	start  int
	header int
	end    int
}

type resourceValue struct {
	density  uint16
	dataType byte
	data     uint32
	language uint32
	config   []byte
}

type resourceTable struct {
	data    []byte
	strings []string
	chunks  map[resTypeKey][]resTypeChunk
}

func readResourceTableFromZip(archive *zip.Reader) (*resourceTable, error) {
	var found *zip.File
	count := 0
	for _, f := range archive.File {
		if f.Name == "resources.arsc" {
			found = f
			count++
		}
	}
	if count != 1 || found == nil {
		return nil, errors.New("missing or duplicate manifest resource table")
	}
	if found.Flags&1 != 0 || found.UncompressedSize64 > maxResourceTable {
		return nil, errors.New("manifest resource table exceeds limit")
	}
	stream, err := found.Open()
	if err != nil {
		return nil, err
	}
	data, err := io.ReadAll(io.LimitReader(stream, maxResourceTable+1))
	stream.Close()
	if err != nil {
		return nil, err
	}
	if len(data) > maxResourceTable {
		return nil, errors.New("manifest resource table exceeds limit")
	}
	return parseResourceTable(data)
}

func parseResourceTable(data []byte) (*resourceTable, error) {
	if len(data) > maxResourceTable {
		return nil, errors.New("resources.arsc exceeds size limit")
	}
	if len(data) < 12 {
		return nil, errors.New("invalid resources.arsc header")
	}
	kind := binary.LittleEndian.Uint16(data[0:])
	header := int(binary.LittleEndian.Uint16(data[2:]))
	total := int(binary.LittleEndian.Uint32(data[4:]))
	if kind != 0x0002 || header < 12 || total != len(data) {
		return nil, errors.New("invalid resources.arsc header")
	}
	table := &resourceTable{
		data:   data,
		chunks: map[resTypeKey][]resTypeChunk{},
	}
	hasStrings := false
	for pos := header; pos < total; {
		ckind, size, length, err := table.chunk(pos, total)
		if err != nil {
			return nil, err
		}
		if ckind == 0x0001 && !hasStrings {
			pool, err := xmlStringPool(data, pos, size, pos+length, true)
			if err != nil {
				return nil, err
			}
			table.strings = pool
			hasStrings = true
		} else if ckind == 0x0200 {
			if err := table.parsePackage(pos, size, pos+length); err != nil {
				return nil, err
			}
		}
		pos += length
	}
	return table, nil
}

func (t *resourceTable) chunk(pos, end int) (uint16, int, int, error) {
	if pos < 0 || pos+8 > end {
		return 0, 0, 0, errors.New("resources.arsc chunk outside parent")
	}
	kind := binary.LittleEndian.Uint16(t.data[pos:])
	size := int(binary.LittleEndian.Uint16(t.data[pos+2:]))
	length := int(binary.LittleEndian.Uint32(t.data[pos+4:]))
	if size < 8 || length < size || pos+length > end || length%4 != 0 {
		return 0, 0, 0, errors.New("resources.arsc chunk outside parent")
	}
	return kind, size, length, nil
}

func (t *resourceTable) parsePackage(start, header, end int) error {
	if header < 284 || start+header > end {
		return errors.New("short resources.arsc package header")
	}
	pkgID := binary.LittleEndian.Uint32(t.data[start+8:])
	if pkgID == 0 || pkgID >= 256 {
		return nil
	}
	for pos := start + header; pos < end; {
		kind, size, length, err := t.chunk(pos, end)
		if err != nil {
			return err
		}
		if kind == 0x0201 {
			if size < 24 || pos+size > pos+length {
				return errors.New("short resources.arsc type header")
			}
			typeID := t.data[pos+8]
			key := resTypeKey{pkg: byte(pkgID), kind: typeID}
			if len(t.chunks[key]) >= maxTypeChunksPerKey {
				return errors.New("too many resources.arsc type configurations")
			}
			t.chunks[key] = append(t.chunks[key], resTypeChunk{start: pos, header: size, end: pos + length})
		}
		pos += length
	}
	return nil
}

func (t *resourceTable) stringAt(index uint32) (string, error) {
	if int(index) >= len(t.strings) {
		return "", errors.New("resources.arsc string index outside pool")
	}
	return t.strings[index], nil
}

func (t *resourceTable) entryOffset(tablePos int, flags byte, count, entry uint32, end int) (uint32, bool, error) {
	if count > maxEntryCount {
		return 0, false, errors.New("resources.arsc entry count exceeds limit")
	}
	if flags&0x01 != 0 {
		low, high := uint32(0), count
		for low < high {
			middle := (low + high) / 2
			p := tablePos + int(middle)*4
			if p < tablePos || p+4 > end {
				return 0, false, errors.New("resources.arsc offset outside chunk")
			}
			idx := uint32(binary.LittleEndian.Uint16(t.data[p:]))
			off16 := binary.LittleEndian.Uint16(t.data[p+2:])
			if idx == entry {
				return uint32(off16) * 4, true, nil
			}
			if idx < entry {
				low = middle + 1
			} else {
				high = middle
			}
		}
		return 0, false, nil
	}
	if entry >= count {
		return 0, false, nil
	}
	if flags&0x02 != 0 {
		p := tablePos + int(entry)*2
		if p < tablePos || p+2 > end {
			return 0, false, errors.New("resources.arsc offset outside chunk")
		}
		off16 := binary.LittleEndian.Uint16(t.data[p:])
		if off16 == 0xffff {
			return 0, false, nil
		}
		return uint32(off16) * 4, true, nil
	}
	p := tablePos + int(entry)*4
	if p < tablePos || p+4 > end {
		return 0, false, errors.New("resources.arsc offset outside chunk")
	}
	off := binary.LittleEndian.Uint32(t.data[p:])
	if off == resNoEntry {
		return 0, false, nil
	}
	return off, true, nil
}

func (t *resourceTable) entryValue(pos, end int) (byte, uint32, bool, error) {
	if pos < 0 || pos+4 > end {
		return 0, 0, false, errors.New("resources.arsc entry outside chunk")
	}
	size := int(binary.LittleEndian.Uint16(t.data[pos:]))
	flags := binary.LittleEndian.Uint16(t.data[pos+2:])
	if flags&0x0008 != 0 {
		if pos+8 > end {
			return 0, 0, false, errors.New("resources.arsc compact entry outside chunk")
		}
		return byte(flags >> 8), binary.LittleEndian.Uint32(t.data[pos+4:]), true, nil
	}
	if flags&0x0001 != 0 {
		return 0, 0, false, nil
	}
	if size < 8 || pos+size+8 > end {
		return 0, 0, false, errors.New("resources.arsc value outside chunk")
	}
	return t.data[pos+size+3], binary.LittleEndian.Uint32(t.data[pos+size+4:]), true, nil
}

func (t *resourceTable) values(resID uint32) ([]resourceValue, error) {
	key := resTypeKey{pkg: byte(resID >> 24), kind: byte(resID >> 16)}
	entry := resID & 0xffff
	chunks := t.chunks[key]
	if len(chunks) == 0 {
		return nil, nil
	}
	var out []resourceValue
	for _, c := range chunks {
		if c.start+24 > c.end {
			return nil, errors.New("short resources.arsc type chunk")
		}
		flags := t.data[c.start+9]
		count := binary.LittleEndian.Uint32(t.data[c.start+12:])
		entriesStart := binary.LittleEndian.Uint32(t.data[c.start+16:])
		configSize := int(binary.LittleEndian.Uint32(t.data[c.start+20:]))
		if configSize < 4 || configSize > c.header-20 || c.start+20+configSize > c.end {
			return nil, errors.New("invalid resource configuration size")
		}
		config := t.data[c.start+20 : c.start+20+configSize]
		var language uint32
		var density uint16
		if configSize >= 16 && c.header >= 36 {
			language = binary.LittleEndian.Uint32(t.data[c.start+28:])
			density = binary.LittleEndian.Uint16(t.data[c.start+34:])
		}
		off, ok, err := t.entryOffset(c.start+c.header, flags, count, entry, c.end)
		if err != nil {
			return nil, err
		}
		if !ok {
			continue
		}
		entryPos := uint64(c.start) + uint64(entriesStart) + uint64(off)
		if entryPos > uint64(c.end) {
			return nil, errors.New("resources.arsc entry outside chunk")
		}
		dtype, val, ok, err := t.entryValue(int(entryPos), c.end)
		if err != nil {
			return nil, err
		}
		if ok {
			out = append(out, resourceValue{
				density:  density,
				dataType: dtype,
				data:     val,
				language: language,
				config:   config,
			})
		}
	}
	return out, nil
}

// selectManifestFlag mirrors src/shiraz/manifest_flags.py for API 27 normal UI.
func selectManifestFlag(table *resourceTable, ident uint32, sdk uint16, uiType byte) (byte, uint32, error) {
	vals, err := table.values(ident)
	if err != nil {
		return 0, 0, err
	}
	type rank struct {
		hasUI   int
		version uint16
	}
	type candidate struct {
		r        rank
		dataType byte
		data     uint32
	}
	var candidates []candidate
	for _, v := range vals {
		cfg := append([]byte(nil), v.config...)
		var version uint16
		if len(cfg) >= 28 {
			version = binary.LittleEndian.Uint16(cfg[24:26])
		}
		var mode byte
		if len(cfg) >= 32 {
			mode = cfg[29]
		}
		if version > sdk || (mode&15 != 0 && mode&15 != uiType) {
			continue
		}
		for i := 0; i < 4 && i < len(cfg); i++ {
			cfg[i] = 0
		}
		if len(cfg) >= 28 {
			cfg[24], cfg[25] = 0, 0
		}
		if len(cfg) >= 32 {
			cfg[29] &= 0xf0
		}
		for _, b := range cfg {
			if b != 0 {
				return 0, 0, errors.New("manifest flag uses unsupported configuration qualifiers")
			}
		}
		hasUI := 0
		if mode&15 != 0 {
			hasUI = 1
		}
		candidates = append(candidates, candidate{
			r:        rank{hasUI: hasUI, version: version},
			dataType: v.dataType,
			data:     v.data,
		})
	}
	if len(candidates) == 0 {
		return 0, 0, errors.New("manifest flag has no matching resource configuration")
	}
	best := candidates[0].r
	for _, c := range candidates[1:] {
		if c.r.hasUI > best.hasUI || (c.r.hasUI == best.hasUI && c.r.version > best.version) {
			best = c.r
		}
	}
	var chosenType byte
	var chosenData uint32
	chosen := false
	for _, c := range candidates {
		if c.r != best {
			continue
		}
		if !chosen {
			chosenType, chosenData, chosen = c.dataType, c.data, true
		} else if c.dataType != chosenType || c.data != chosenData {
			return 0, 0, errors.New("ambiguous manifest flag resource configuration")
		}
	}
	return chosenType, chosenData, nil
}

func resolveManifestFlagValue(table *resourceTable, ident uint32) (byte, uint32, error) {
	seen := map[uint32]bool{}
	for step := 0; step < 9; step++ {
		if seen[ident] {
			return 0, 0, errors.New("cyclic manifest flag resource")
		}
		seen[ident] = true
		kind, val, err := selectManifestFlag(table, ident, 27, 1)
		if err != nil {
			return 0, 0, err
		}
		if kind == resTypeReference {
			ident = val
			continue
		}
		return kind, val, nil
	}
	return 0, 0, errors.New("manifest flag reference depth exceeded")
}

func normalizeManifestFlags(archive *zip.Reader, facts map[string]string) error {
	type pendingFlag struct {
		key   string
		field string
		ident uint32
	}
	var pending []pendingFlag
	for _, group := range []string{"activity", "alias", "service", "receiver", "provider"} {
		count, _ := strconv.Atoi(facts[group+".count"])
		for i := 0; i < count; i++ {
			prefix := group + "." + strconv.Itoa(i) + "."
			for _, field := range []string{"enabled", "exported", "process"} {
				key := prefix + field
				val, ok := facts[key]
				if !ok || !strings.HasPrefix(val, "@") {
					continue
				}
				var ident uint64
				var err error
				if resStr := facts[key+"Res"]; resStr != "" {
					ident, err = strconv.ParseUint(resStr, 10, 32)
				} else {
					raw := val[1:]
					if strings.HasPrefix(raw, "0x") || strings.HasPrefix(raw, "0X") {
						ident, err = strconv.ParseUint(raw[2:], 16, 32)
					} else {
						ident, err = strconv.ParseUint(raw, 10, 32)
					}
				}
				if err != nil || ident == 0 || ident > 0xffffffff {
					return errors.New("invalid or unresolved manifest flag resource ID")
				}
				pending = append(pending, pendingFlag{key: key, field: field, ident: uint32(ident)})
			}
		}
	}
	if len(pending) == 0 {
		return nil
	}
	if len(pending) > 4096 {
		return errors.New("manifest resource flag count limit")
	}
	table, err := readResourceTableFromZip(archive)
	if err != nil {
		return err
	}
	type cacheKey struct {
		ident     uint32
		isProcess bool
	}
	cache := map[cacheKey]string{}
	for _, item := range pending {
		ck := cacheKey{ident: item.ident, isProcess: item.field == "process"}
		resolved, cached := cache[ck]
		if !cached {
			kind, val, err := resolveManifestFlagValue(table, item.ident)
			if err != nil {
				return err
			}
			if ck.isProcess {
				if kind != resTypeString {
					return errors.New("manifest process resource is not a string")
				}
				str, err := table.stringAt(val)
				if err != nil {
					return err
				}
				if len(str) < 1 || len(str) > 1024 || !manifestProcessPattern.MatchString(str) {
					return errors.New("invalid manifest process resource")
				}
				resolved = str
			} else {
				if kind != resTypeBoolean {
					return errors.New("manifest flag resource is not boolean")
				}
				if val != 0 {
					resolved = "true"
				} else {
					resolved = "false"
				}
			}
			cache[ck] = resolved
		}
		facts[item.key] = resolved
	}
	return nil
}

func resolveResourceLabel(table *resourceTable, resID uint32, literal string) string {
	if resID == 0 {
		if literal != "" && !strings.HasPrefix(literal, "@") {
			trimmed := strings.TrimSpace(literal)
			if len(trimmed) > 200 {
				trimmed = trimmed[:200]
			}
			return trimmed
		}
		return ""
	}
	if table == nil {
		return ""
	}
	cur := resID
	for depth := 0; depth < maxRefDepth; depth++ {
		vals, err := table.values(cur)
		if err != nil || len(vals) == 0 {
			return ""
		}
		sort.SliceStable(vals, func(i, j int) bool {
			return (vals[i].language == 0) && (vals[j].language != 0)
		})
		v := vals[0]
		if v.dataType == resTypeString {
			s, err := table.stringAt(v.data)
			if err != nil {
				return ""
			}
			trimmed := strings.TrimSpace(s)
			if len(trimmed) > 200 {
				trimmed = trimmed[:200]
			}
			return trimmed
		}
		if v.dataType != resTypeReference || v.data == 0 {
			return ""
		}
		cur = v.data
	}
	return ""
}

func resolveResourceIconPNG(archive *zip.Reader, table *resourceTable, iconResID uint32) ([]byte, bool) {
	if table == nil || iconResID == 0 {
		return nil, false
	}
	visits := 0
	type pngCandidate struct {
		rank int
		name string
	}
	var collect func(resID uint32, depth int) ([]pngCandidate, []string)
	collect = func(resID uint32, depth int) ([]pngCandidate, []string) {
		if depth > maxRefDepth {
			return nil, nil
		}
		vals, err := table.values(resID)
		if err != nil {
			return nil, nil
		}
		var pngs []pngCandidate
		var xmls []string
		for _, v := range vals {
			visits++
			if visits > maxIconVisits {
				return pngs, xmls
			}
			if v.dataType == resTypeReference && v.data != 0 {
				p, x := collect(v.data, depth+1)
				pngs = append(pngs, p...)
				xmls = append(xmls, x...)
			} else if v.dataType == resTypeString {
				name, err := table.stringAt(v.data)
				if err != nil || !strings.HasPrefix(name, "res/") || strings.Contains(name, "..") || strings.Contains(name, "\\") {
					continue
				}
				lower := strings.ToLower(name)
				if strings.HasSuffix(lower, ".png") && !strings.HasSuffix(lower, ".9.png") && v.density != resDensityAny {
					r := int(v.density)
					if v.density == 0 || v.density == resDensityNone {
						r = 160
					}
					pngs = append(pngs, pngCandidate{rank: r, name: name})
				} else if strings.HasSuffix(lower, ".xml") {
					xmls = append(xmls, name)
				}
			}
		}
		return pngs, xmls
	}

	readZipEntry := func(name string, limit uint64) ([]byte, bool) {
		clean := path.Clean(name)
		if !strings.HasPrefix(clean, "res/") {
			return nil, false
		}
		for _, f := range archive.File {
			if f.Name == clean && !f.FileInfo().IsDir() && f.Flags&1 == 0 && f.UncompressedSize64 > 0 && f.UncompressedSize64 <= limit {
				stream, err := f.Open()
				if err != nil {
					return nil, false
				}
				data, err := io.ReadAll(io.LimitReader(stream, int64(limit)+1))
				stream.Close()
				if err != nil || uint64(len(data)) > limit {
					return nil, false
				}
				return data, true
			}
		}
		return nil, false
	}

	pickBestPNG := func(candidates []pngCandidate) ([]byte, bool) {
		if len(candidates) == 0 {
			return nil, false
		}
		sort.SliceStable(candidates, func(i, j int) bool {
			if candidates[i].rank != candidates[j].rank {
				return candidates[i].rank > candidates[j].rank
			}
			return candidates[i].name > candidates[j].name
		})
		for _, c := range candidates {
			data, ok := readZipEntry(c.name, maxIconImage)
			if ok && len(data) >= 24 && string(data[:8]) == "\x89PNG\r\n\x1a\n" {
				return data, true
			}
		}
		return nil, false
	}

	pngs, xmls := collect(iconResID, 0)
	if data, ok := pickBestPNG(pngs); ok {
		return data, true
	}
	// If icon points to an <adaptive-icon> XML, try its <foreground> PNG bitmap.
	for _, xmlName := range xmls {
		raw, ok := readZipEntry(xmlName, maxIconXML)
		if !ok {
			continue
		}
		root, err := parseBinaryXML(raw)
		if err != nil || root.tag != "adaptive-icon" {
			continue
		}
		if fg := root.first("foreground"); fg != nil {
			if ref := fg.androidRefUint32("drawable"); ref != 0 {
				fgPNGs, _ := collect(ref, 1)
				if data, ok := pickBestPNG(fgPNGs); ok {
					return data, true
				}
			}
		}
	}
	return nil, false
}
