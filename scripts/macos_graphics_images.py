#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Original, deterministic image assets for the macOS graphics fixture APK.

Nothing here is committed as a binary: build_macos_fixture.py calls write()
into an ignored build directory. The encoders are deliberately minimal and
independent of the codecs Shiraz links (libpng, libwebp, libjpeg-turbo), so the
fixture decodes bytes that Shiraz did not produce itself:

- PNG: 8-bit RGBA, one IDAT (scripts/fixture_images.py).
- WebP: VP8L lossless with no transforms, no colour cache, one prefix-code
  group and "simple" prefix codes (at most two symbols per channel). Every
  channel of QUADRANTS_WEBP therefore takes at most two values.
- JPEG: baseline sequential, 4:4:4 YCbCr, all-ones quantisation, a flat
  colour so every block is DC-only (each AC run is a single EOB).

The colours and geometry below are the single source for the smoke checks in
scripts/macos_runtime_smoke.py; tests/macos-apk-graphics/GraphicsActivity.java
repeats them as Java constants.
"""
import argparse
from pathlib import Path
import struct
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from fixture_images import rgba  # noqa: E402

# 8x8 images split into four 4x4 quadrants: top-left, top-right, bottom-left, bottom-right.
SIZE = 8
QUADRANTS_PNG = (0xff3366cc, 0xffcc6633, 0xff66cc33, 0xffcccc00)
# Two values per channel: R in {20,e0}, G in {40,c0}, B in {10,f0}; all opaque.
QUADRANTS_WEBP = (0xffe04010, 0xff20c010, 0xff2040f0, 0xffe0c0f0)
JPEG_SIZE = 16
JPEG_RGB = 0x3090d0
ASSETS = {'quadrants.png', 'quadrants.webp', 'flat.jpg'}


def roundtrip_pixels():
    """GraphicsActivity.roundtripPixels(): 4x4, pixel 5 transparent, the rest opaque."""
    return [0 if i == 5 else 0xff000000 | ((i * 16 + 8) << 16) | ((255 - i * 16) << 8) | ((i * 37 + 11) & 0xff)
            for i in range(16)]


def quadrant_pixels(colours, size=SIZE):
    half = size // 2
    return [colours[(y >= half) * 2 + (x >= half)] for y in range(size) for x in range(size)]


class BitWriter:
    """LSB-first bit packing (VP8L)."""

    def __init__(self):
        self.value, self.count, self.data = 0, 0, bytearray()

    def write(self, value, bits):
        if value >> bits:
            raise ValueError(f'{value} does not fit in {bits} bits')
        self.value |= value << self.count
        self.count += bits
        while self.count >= 8:
            self.data.append(self.value & 0xff)
            self.value >>= 8
            self.count -= 8

    def finish(self):
        if self.count:
            self.data.append(self.value & 0xff)
        return bytes(self.data)


def _simple_code(bits, symbols):
    """Write a VP8L simple prefix code; canonical order gives the smaller symbol code 0."""
    bits.write(1, 1)
    bits.write(len(symbols) - 1, 1)
    first = symbols[0]
    if first < 2:
        bits.write(0, 1)
        bits.write(first, 1)
    else:
        bits.write(1, 1)
        bits.write(first, 8)
    if len(symbols) == 2:
        bits.write(symbols[1], 8)


def webp_lossless(width, height, pixels):
    """VP8L bitstream in a RIFF container for opaque or translucent ARGB pixels."""
    if not (1 <= width <= 16384 and 1 <= height <= 16384) or len(pixels) != width * height:
        raise ValueError('WebP dimensions')
    channels = [sorted({(pixel >> shift) & 0xff for pixel in pixels}) for shift in (8, 16, 0, 24)]  # G, R, B, A
    if any(len(values) > 2 for values in channels):
        raise ValueError('simple VP8L codes carry at most two values per channel')
    bits = BitWriter()
    bits.write(0x2f, 8)
    bits.write(width - 1, 14)
    bits.write(height - 1, 14)
    bits.write(int(channels[3] != [0xff]), 1)  # alpha_is_used hint
    bits.write(0, 3)  # version
    bits.write(0, 1)  # no transforms
    bits.write(0, 1)  # no colour cache
    bits.write(0, 1)  # no meta prefix codes: one group
    for values in channels:
        _simple_code(bits, values)
    _simple_code(bits, [0])  # distance code, never used
    for pixel in pixels:
        for values, shift in zip(channels, (8, 16, 0, 24)):
            if len(values) == 2:
                bits.write(values.index((pixel >> shift) & 0xff), 1)
    payload = bits.finish()
    chunk = b'VP8L' + struct.pack('<I', len(payload)) + payload + (b'\0' if len(payload) % 2 else b'')
    return b'RIFF' + struct.pack('<I', 4 + len(chunk)) + b'WEBP' + chunk


class JpegBits:
    """MSB-first entropy-coded segment with 0xff byte stuffing."""

    def __init__(self):
        self.value, self.count, self.data = 0, 0, bytearray()

    def write(self, value, bits):
        for shift in range(bits - 1, -1, -1):
            self.value = (self.value << 1) | ((value >> shift) & 1)
            self.count += 1
            if self.count == 8:
                self.data.append(self.value)
                if self.value == 0xff:
                    self.data.append(0)
                self.value, self.count = 0, 0

    def finish(self):
        if self.count:
            self.write((1 << (8 - self.count)) - 1, 8 - self.count)
        return bytes(self.data)


# ITU-T T.81 Annex K.3 table K.3 (luminance DC): code lengths 1..16 and symbols.
DC_BITS = (0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0)
DC_SYMBOLS = tuple(range(12))
# Flat blocks need one AC symbol only: EOB (0x00), one code of length 1.
AC_BITS = (1,) + (0,) * 15
AC_SYMBOLS = (0x00,)


def _huffman_codes(lengths, symbols):
    codes, code, index = {}, 0, 0
    for length, count in enumerate(lengths, 1):
        for _ in range(count):
            codes[symbols[index]] = (code, length)
            code += 1
            index += 1
        code <<= 1
    return codes


def _segment(marker, body):
    return b'\xff' + bytes([marker]) + struct.pack('>H', len(body) + 2) + body


def jpeg_flat(width, height, rgb):
    """Baseline 4:4:4 JPEG of one flat colour (JFIF RGB->YCbCr, quantiser 1)."""
    if not (1 <= width <= 65535 and 1 <= height <= 65535):
        raise ValueError('JPEG dimensions')
    r, g, b = (rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff
    samples = (0.299 * r + 0.587 * g + 0.114 * b,
               128 - 0.168736 * r - 0.331264 * g + 0.5 * b,
               128 + 0.5 * r - 0.418688 * g - 0.081312 * b)
    # A flat 8x8 block's DC coefficient is 8 * (sample - 128); quantiser 1 keeps it.
    dc = [round(8 * (sample - 128)) for sample in samples]
    dc_codes, ac_codes = _huffman_codes(DC_BITS, DC_SYMBOLS), _huffman_codes(AC_BITS, AC_SYMBOLS)
    bits, previous = JpegBits(), [0, 0, 0]
    blocks = ((width + 7) // 8) * ((height + 7) // 8)
    for _ in range(blocks):
        for component in range(3):
            diff = dc[component] - previous[component]
            previous[component] = dc[component]
            category = abs(diff).bit_length()
            code, length = dc_codes[category]
            bits.write(code, length)
            if category:
                bits.write(diff if diff > 0 else diff + (1 << category) - 1, category)
            code, length = ac_codes[0x00]
            bits.write(code, length)
    tables = (bytes([0x00]) + bytes(DC_BITS) + bytes(DC_SYMBOLS)
              + bytes([0x10]) + bytes(AC_BITS) + bytes(AC_SYMBOLS))
    return (b'\xff\xd8'
            + _segment(0xe0, b'JFIF\0\x01\x01\x00' + struct.pack('>HH', 1, 1) + b'\0\0')
            + _segment(0xdb, b'\x00' + b'\x01' * 64)
            + _segment(0xc0, b'\x08' + struct.pack('>HH', height, width) + b'\x03'
                       + b'\x01\x11\x00' + b'\x02\x11\x00' + b'\x03\x11\x00')
            + _segment(0xc4, tables)
            + _segment(0xda, b'\x03' + b'\x01\x00' + b'\x02\x00' + b'\x03\x00' + b'\x00\x3f\x00')
            + bits.finish() + b'\xff\xd9')


def images():
    """Asset name -> encoded bytes."""
    return {'quadrants.png': rgba(SIZE, SIZE, quadrant_pixels(QUADRANTS_PNG)),
            'quadrants.webp': webp_lossless(SIZE, SIZE, quadrant_pixels(QUADRANTS_WEBP)),
            'flat.jpg': jpeg_flat(JPEG_SIZE, JPEG_SIZE, JPEG_RGB)}


def write(directory):
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    for name, data in images().items():
        (directory / name).write_bytes(data)
    return sorted(directory / name for name in ASSETS)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    for path in write(parser.parse_args().directory):
        print(path)


if __name__ == '__main__':
    main()
