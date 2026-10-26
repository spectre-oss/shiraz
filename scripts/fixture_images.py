# SPDX-License-Identifier: GPL-3.0-only
"""Original tiny PNG test assets, generated only under ignored build directories."""
from pathlib import Path
import struct
import zlib

def chunk(kind,data):
    return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)

def image(width,height,rows,depth=8,color=6,extra=b'',interlace=0):
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,depth,color,0,0,interlace))+extra+chunk(b'IDAT',zlib.compress(rows))+chunk(b'IEND',b'')

def rgba(width,height,colors,interlace=False):
    passes=((0,0,8,8),(4,0,8,8),(0,4,4,8),(2,0,4,4),(0,2,2,4),(1,0,2,2),(0,1,1,2)) if interlace else ((0,0,1,1),)
    rows=bytearray()
    for ox,oy,dx,dy in passes:
        if ox >= width or oy >= height: continue
        for y in range(oy,height,dy):
            rows.append(0)
            for x in range(ox,width,dx):
                c=colors[y*width+x]
                rows.extend(((c>>16)&255,(c>>8)&255,c&255,(c>>24)&255))
    return image(width,height,rows,interlace=int(interlace))

def prepare(resources,assets):
    colors=[0xffff0000,0x8000ff00,0xff0000ff,0]
    files={
        'grid.png':rgba(2,2,colors),
        'interlaced.png':rgba(2,2,colors,True),
        'palette.png':image(2,2,b'\0\x10\0\xb0',depth=2,color=3,extra=chunk(b'PLTE',bytes((255,0,0,0,255,0,0,0,255,0,0,0)))+chunk(b'tRNS',bytes((255,128,255,0)))),
        'gray1.png':image(2,1,b'\0\x40',depth=1,color=0),
        'gray16.png':image(2,1,b'\0\0\0\xff\xff',depth=16,color=0),
        'oversized.png':rgba(8193,1,[0xff000000]*8193),
        'metadata.png':image(1,1,b'\0\xff\0\0\xff',extra=chunk(b'tEXt',b'Author\0fixture')+chunk(b'zTXt',b'Comment\0\0'+zlib.compress(b'bounded text'))+chunk(b'iTXt',b'Description\0\1\0\0\0'+zlib.compress('café'.encode()))),
        'metadata_bomb.png':image(1,1,b'\0\xff\0\0\xff',extra=chunk(b'zTXt',b'Comment\0\0'+zlib.compress(b'0'*(2*1024*1024)))),
        'profile.png':image(1,1,b'\0\0\0\0\xff',extra=chunk(b'iCCP',b'profile\0\0invalid')),
        'ninepatch.png':image(1,1,b'\0\0\0\0\xff',extra=chunk(b'npTc',b'unsupported')),
    }
    # Compiled 3x3 patch: one-pixel fixed borders, stretchable center.
    patch=bytes((0,2,2,9))+bytes(8)+struct.pack('>4i',1,1,1,1)+bytes(4)+struct.pack('>13i',1,2,1,2,*([1]*9))
    pixels=[0xffff0000,0xff00ff00,0xff0000ff,0xffffff00,0xff888888,0xff00ffff,0xffff00ff,0xff000000,0xffffffff]
    raw=rgba(3,3,pixels)
    files['patch.png']=raw[:33]+chunk(b'npTc',patch)+raw[33:]
    files['corrupt.png']=files['grid.png'][:-1]+bytes((files['grid.png'][-1]^1,))
    files['truncated.png']=files['grid.png'][:-8]
    directory=Path(assets)/'images'; directory.mkdir(parents=True,exist_ok=True)
    for name,data in files.items(): (directory/name).write_bytes(data)
    for folder,name,data in (
        ('drawable-mdpi','nine_patch_test.9.png',rgba(5,5,[0,0,0xff000000,0,0,0,*pixels[0:3],0,0xff000000,*pixels[3:6],0xff000000,0,*pixels[6:9],0,0,0,0xff000000,0,0])),
        ('drawable-mdpi','png_grid.png',files['grid.png']),
        ('drawable-mdpi','png_density.png',rgba(2,2,[0xff00ff00]*4)),
        ('drawable-xhdpi','png_density.png',rgba(4,4,[0xff00ff00]*16)),
        ('drawable-nodpi','png_noscale.png',rgba(2,2,[0xff0000ff]*4)),
    ):
        directory=Path(resources)/folder; directory.mkdir(parents=True,exist_ok=True); (directory/name).write_bytes(data)
