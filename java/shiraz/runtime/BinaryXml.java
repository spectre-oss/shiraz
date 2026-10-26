// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Bounded compiled Android XML elements/typed attributes, independent of resource-manager lifetime. */
public final class BinaryXml {
    public static final String ANDROID="http://schemas.android.com/apk/res/android";
    public static final class Value {
        public final int type,data; public final String text;
        public Value(int type,int data,String text) { this.type=type; this.data=data; this.text=text; }
    }
    public static final class Attribute {
        public final String namespace,name; public final Value value; public final int resourceId;
        Attribute(String namespace,String name,Value value,int resourceId) { this.namespace=namespace; this.name=name; this.value=value;this.resourceId=resourceId; }
    }
    public static final class Namespace {public final String prefix,uri;Namespace(String prefix,String uri){this.prefix=prefix;this.uri=uri;}}
    public static final class Node {
        public final String namespace,name;
        public int line,endLine,idIndex=-1,classIndex=-1,styleIndex=-1; public Node parent; public final List<Namespace> namespaces=new ArrayList<Namespace>();
        public final List<Attribute> attributes=new ArrayList<Attribute>();
        public final List<Node> children=new ArrayList<Node>();
        Node(String namespace,String name) { this.namespace=namespace; this.name=name; }
        public Value android(String name) { for (Attribute a:attributes) if (ANDROID.equals(a.namespace) && name.equals(a.name)) return a.value; return null; }
    }
    private final byte[] bytes; private String[] strings;
    private int characters,attributes;private int[] resourceIds;
    private BinaryXml(byte[] bytes) { this.bytes=bytes; }
    private IllegalArgumentException bad() { return new IllegalArgumentException("Malformed or oversized compiled XML"); }
    private void range(int offset,long count,int end) { if (offset < 0 || count < 0 || (long)offset+count > end || end > bytes.length) throw bad(); }
    private int u16(int offset,int end) { range(offset,2,end); return (bytes[offset]&255)|((bytes[offset+1]&255)<<8); }
    private int i32(int offset,int end) { range(offset,4,end); return (bytes[offset]&255)|((bytes[offset+1]&255)<<8)|((bytes[offset+2]&255)<<16)|(bytes[offset+3]<<24); }
    private String string(int index) { if (index == -1) return ""; if (strings == null || index < 0 || index >= strings.length) throw bad(); return strings[index]; }
    private String name(int index) { String value=string(index); if (value.isEmpty() || value.indexOf('\0') >= 0) throw bad(); return value; }
    private int length8(int[] cursor,int end) { range(cursor[0],1,end); int value=bytes[cursor[0]++]&255; if ((value&128) != 0) { range(cursor[0],1,end); value=((value&127)<<8)|(bytes[cursor[0]++]&255); } return value; }
    private int length16(int[] cursor,int end) { int value=u16(cursor[0],end); cursor[0]+=2; if ((value&32768) != 0) { value=((value&32767)<<16)|u16(cursor[0],end); cursor[0]+=2; } return value; }
    private String utf8(int start,int length) {
        StringBuilder out=new StringBuilder(length);int at=start,stop=start+length;
        while (at < stop) {
            int c=bytes[at++]&255;
            if (c < 0x80) out.append((char)c);
            else if ((c&0xE0) == 0xC0 && at < stop && (bytes[at]&0xC0) == 0x80) out.append((char)(((c&0x1F)<<6)|(bytes[at++]&0x3F)));
            else if ((c&0xF0) == 0xE0 && at+1 < stop && (bytes[at]&0xC0) == 0x80 && (bytes[at+1]&0xC0) == 0x80) { out.append((char)(((c&0x0F)<<12)|((bytes[at]&0x3F)<<6)|(bytes[at+1]&0x3F))); at+=2; }
            else if ((c&0xF8) == 0xF0 && at+2 < stop && (bytes[at]&0xC0) == 0x80 && (bytes[at+1]&0xC0) == 0x80 && (bytes[at+2]&0xC0) == 0x80) { int cp=((c&7)<<18)|((bytes[at]&0x3F)<<12)|((bytes[at+1]&0x3F)<<6)|(bytes[at+2]&0x3F); at+=3; if (cp > 0x10FFFF) throw bad(); out.appendCodePoint(cp); }
            else throw bad();
        }
        return out.toString();
    }
    private void pool(int start,int header,int end) {
        if (strings != null || header < 28) throw bad();
        int count=i32(start+8,end),styles=i32(start+12,end),flags=i32(start+16,end),offset=i32(start+20,end),styleOffset=i32(start+24,end);
        if (count < 0 || count > 65536 || styles != 0 || (flags&~0x101) != 0 || offset < header+(long)count*4 || (long)start+offset > end || styleOffset != 0) throw bad();
        range(start+header,(long)count*4,end); strings=new String[count];
        Charset charset=(flags&256) != 0 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16LE;
        for (int i=0;i<count;i++) {
            int relative=i32(start+header+i*4,end); long position=(long)start+offset+relative; if (relative < 0 || position > end) throw bad();
            int[] cursor={(int)position}; int units,length,terminator;
            if ((flags&256) != 0) { units=length8(cursor,end); length=length8(cursor,end); terminator=1; }
            else { units=length16(cursor,end); if (units < 0 || units > 65536) throw bad(); length=units*2; terminator=2; }
            if (units > 65536 || (long)characters+units > 2*1024*1024) throw bad();
            range(cursor[0],(long)length+terminator,end);
            for (int j=0;j<terminator;j++) if (bytes[cursor[0]+length+j] != 0) throw bad();
            if ((flags&256) != 0) strings[i]=utf8(cursor[0],length);
            else try { strings[i]=charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,cursor[0],length)).toString(); }
            catch (CharacterCodingException failure) { throw bad(); }
            if (strings[i].length() != units) throw bad(); characters+=units;
        }
    }
    public static Node parse(byte[] bytes) {
        if (bytes == null) throw new NullPointerException("compiled XML");
        if (bytes.length < 8 || bytes.length > 4*1024*1024) throw new IllegalArgumentException("Compiled XML length");
        return new BinaryXml(bytes).read();
    }
    private Node read() {
        if (u16(0,bytes.length) != 3 || u16(2,bytes.length) != 8 || i32(4,bytes.length) != bytes.length) throw bad();
        ArrayList<Node> stack=new ArrayList<Node>(); ArrayList<int[]> namespaces=new ArrayList<int[]>(); Node root=null; int nodes=0,chunks=0;
        for (int at=8;at<bytes.length;) {
            if (++chunks > 65536) throw bad(); range(at,8,bytes.length); int kind=u16(at,bytes.length),header=u16(at+2,bytes.length),size=i32(at+4,bytes.length);
            if (header < 8 || size < header) throw bad(); range(at,size,bytes.length); int end=at+size;
            if (kind == 1) pool(at,header,end);
            else if (kind == 0x180) { if (resourceIds!=null||nodes!=0||strings == null || header != 8 || (size-header)%4 != 0 || (size-header)/4 > strings.length) throw bad();resourceIds=new int[(size-header)/4];for(int i=0;i<resourceIds.length;i++)resourceIds[i]=i32(at+header+i*4,end); }
            else {
                if (header != 16 || strings == null) throw bad();
                if (kind == 0x100 || kind == 0x101) {
                    int prefix=i32(at+16,end),uri=i32(at+20,end); string(prefix); name(uri);
                    if (kind == 0x100) { if (namespaces.size() >= 64) throw bad(); namespaces.add(new int[]{prefix,uri}); }
                    else { if (namespaces.isEmpty()) throw bad(); int[] expected=namespaces.remove(namespaces.size()-1); if (expected[0] != prefix || expected[1] != uri) throw bad(); }
                } else if (kind == 0x102) {
                    if (++nodes > 4096 || stack.size() >= 64) throw bad(); Node node=new Node(string(i32(at+16,end)),name(i32(at+20,end)));
                    node.line=i32(at+8,end);node.parent=stack.isEmpty()?null:stack.get(stack.size()-1);for(int[] declaration:namespaces)node.namespaces.add(new Namespace(string(declaration[0]),string(declaration[1])));
                    int first=u16(at+24,end),stride=u16(at+26,end),count=u16(at+28,end);
                    node.idIndex=u16(at+30,end)-1;node.classIndex=u16(at+32,end)-1;node.styleIndex=u16(at+34,end)-1;if(node.idIndex>=count||node.classIndex>=count||node.styleIndex>=count)throw bad();
                    if (first < 20 || stride < 20 || count > 64 || (attributes+=count) > 65536) throw bad();
                    range(at+16+first,(long)stride*count,end);
                    for (int i=0;i<count;i++) {
                        int pos=at+16+first+i*stride;int nameIndex=i32(pos+4,end); String ns=string(i32(pos,end)),key=name(nameIndex); int raw=i32(pos+8,end); string(raw);
                        if (u16(pos+12,end) != 8 || bytes[pos+14] != 0) throw bad(); int type=bytes[pos+15]&255,data=i32(pos+16,end);
                        for (Attribute old:node.attributes) if (old.namespace.equals(ns) && old.name.equals(key)) throw bad();
                        node.attributes.add(new Attribute(ns,key,new Value(type,data,type == 3 ? string(data) : raw == -1 ? null : string(raw)),resourceIds!=null&&nameIndex<resourceIds.length?resourceIds[nameIndex]:0));
                    }
                    if (stack.isEmpty()) { if (root != null) throw bad(); root=node; } else stack.get(stack.size()-1).children.add(node); stack.add(node);
                } else if (kind == 0x103) {
                    if (stack.isEmpty()) throw bad(); Node node=stack.remove(stack.size()-1);node.endLine=i32(at+8,end);
                    if (!node.name.equals(name(i32(at+20,end))) || !node.namespace.equals(string(i32(at+16,end)))) throw bad();
                } else if (kind == 0x104) { if (!string(i32(at+16,end)).trim().isEmpty()) throw new UnsupportedOperationException("Compiled XML text nodes are not implemented"); }
                else throw bad();
            }
            at=end;
        }
        if (root == null || !stack.isEmpty() || !namespaces.isEmpty()) throw bad(); return root;
    }
}
