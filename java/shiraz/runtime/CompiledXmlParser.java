// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.res.XmlResourceParser;
import android.util.TypedValue;
import org.xmlpull.v1.*;
import java.util.*;

/** Owned compiled-resource pull cursor and typed AttributeSet; resource references remain references. */
public final class CompiledXmlParser implements XmlResourceParser {
    private static final class Event {final BinaryXml.Node node;final int type,depth;Event(BinaryXml.Node n,int t,int d){node=n;type=t;depth=d;}}
    private final ArrayList<Event> events=new ArrayList<Event>();private int at,resource;private final int encodedSize;private boolean closed,started;
    public CompiledXmlParser(byte[] bytes,int resource){this.resource=resource;encodedSize=bytes.length;BinaryXml.Node root=BinaryXml.parse(bytes);events.add(new Event(null,START_DOCUMENT,0));flatten(root,1);events.add(new Event(null,END_DOCUMENT,0));}
    private final Object sourceKey=new Object();
    private Object sharedKey;
    private CompiledXmlParser(CompiledXmlParser source){resource=source.resource;encodedSize=source.encodedSize;events.addAll(source.events);sharedKey=source.sharedKey==null?source.sourceKey:source.sharedKey;}
    /** A fresh cursor over resource-owned immutable compiled events. */
    public CompiledXmlParser shirazFork(){event();return new CompiledXmlParser(this);}
    public Object shirazSourceKey(){return sharedKey==null?sourceKey:sharedKey;}
    public int shirazPosition(){event();return at;}
    private void flatten(BinaryXml.Node n,int depth){events.add(new Event(n,START_TAG,depth));for(BinaryXml.Node child:n.children)flatten(child,depth+1);events.add(new Event(n,END_TAG,depth));}
    private Event event(){if(closed)throw new IllegalStateException("Compiled XML parser is closed");return events.get(at);}
    public int shirazEncodedSize(){return encodedSize;}
    public BinaryXml.Node shirazNode(){return event().node;}
    private BinaryXml.Attribute attribute(int index){Event e=event();if(e.type!=START_TAG||index<0||index>=e.node.attributes.size())throw new IndexOutOfBoundsException("XML attribute");return e.node.attributes.get(index);}
    private int index(String ns,String name){Event e=event();if(e.type!=START_TAG)return -1;for(int i=0;i<e.node.attributes.size();i++){BinaryXml.Attribute a=e.node.attributes.get(i);if(a.name.equals(name)&&(ns==null||a.namespace.equals(ns)))return i;}return -1;}
    public void close(){closed=true;events.clear();at=0;}
    public int getEventType(){return event().type;}
    public int next(){event();if(!started){started=true;return START_DOCUMENT;}if(at<events.size()-1)at++;return event().type;}
    public int nextToken(){return next();}
    public int getDepth(){return event().depth;}
    public String getName(){return event().node==null?null:event().node.name;}
    public String getNamespace(){return event().node==null?null:event().node.namespace;}
    private String prefix(String uri){if(uri.isEmpty())return null;List<BinaryXml.Namespace> ns=event().node.namespaces;for(int i=ns.size()-1;i>=0;i--)if(ns.get(i).uri.equals(uri))return ns.get(i).prefix.isEmpty()?null:ns.get(i).prefix;return null;}
    public String getPrefix(){return event().node==null?null:prefix(event().node.namespace);}
    public int getLineNumber(){return event().node==null?-1:event().type==END_TAG?event().node.endLine:event().node.line;}
    public int getColumnNumber(){return -1;}
    public String getPositionDescription(){return "Binary XML resource #"+Integer.toHexString(resource)+" line "+getLineNumber();}
    public int getNamespaceCount(int depth)throws XmlPullParserException{if(depth<0||depth>getDepth())throw new XmlPullParserException("Invalid namespace depth");if(depth==0)return 0;BinaryXml.Node n=event().node;for(int d=getDepth();d>depth;d--)n=n.parent;return n.namespaces.size();}
    public String getNamespacePrefix(int pos){BinaryXml.Namespace n=event().node.namespaces.get(pos);return n.prefix.isEmpty()?null:n.prefix;}
    public String getNamespaceUri(int pos){return event().node.namespaces.get(pos).uri;}
    public String getNamespace(String prefix){if(prefix==null)prefix="";if(prefix.equals("xml"))return "http://www.w3.org/XML/1998/namespace";if(prefix.equals("xmlns"))return "http://www.w3.org/2000/xmlns/";if(event().node==null)return null;List<BinaryXml.Namespace> list=event().node.namespaces;for(int i=list.size()-1;i>=0;i--)if(list.get(i).prefix.equals(prefix))return list.get(i).uri;return null;}
    public boolean isEmptyElementTag(){event();return false;}
    public String getText(){event();return null;}
    public char[] getTextCharacters(int[] holder){event();if(holder==null||holder.length<2)throw new IllegalArgumentException("Text holder");holder[0]=holder[1]=0;return null;}
    public boolean isWhitespace()throws XmlPullParserException{throw new XmlPullParserException("Current event is not text");}
    public String getInputEncoding(){return null;}
    public void setFeature(String name,boolean enabled)throws XmlPullParserException{if(FEATURE_PROCESS_NAMESPACES.equals(name)&&enabled)return;if((FEATURE_REPORT_NAMESPACE_ATTRIBUTES.equals(name)||FEATURE_VALIDATION.equals(name)||FEATURE_PROCESS_DOCDECL.equals(name))&&!enabled)return;throw new XmlPullParserException("Compiled XML feature "+name);}
    public boolean getFeature(String name){return FEATURE_PROCESS_NAMESPACES.equals(name);}
    public void setProperty(String name,Object value)throws XmlPullParserException{throw new XmlPullParserException("Compiled XML properties are not configurable");}
    public Object getProperty(String name){return null;}
    public void setInput(java.io.Reader reader)throws XmlPullParserException{throw new XmlPullParserException("Resource parser input is owned");}
    public void setInput(java.io.InputStream in,String encoding)throws XmlPullParserException{throw new XmlPullParserException("Resource parser input is owned");}
    public void defineEntityReplacementText(String name,String text)throws XmlPullParserException{throw new XmlPullParserException("Compiled XML has no entity substitutions");}
    public void require(int type,String ns,String name)throws XmlPullParserException{if(getEventType()!=type||ns!=null&&!ns.equals(getNamespace())||name!=null&&!name.equals(getName()))throw new XmlPullParserException("Unexpected event at "+getPositionDescription());}
    public int nextTag()throws XmlPullParserException{int type=next();if(type!=START_TAG&&type!=END_TAG)throw new XmlPullParserException("Expected tag");return type;}
    public String nextText()throws XmlPullParserException{require(START_TAG,null,null);if(next()!=END_TAG)throw new XmlPullParserException("Element contains child tags");return "";}
    public int getAttributeCount(){Event e=event();return e.type==START_TAG?e.node.attributes.size():-1;}
    public String getAttributeName(int index){return attribute(index).name;}
    public String getAttributeNamespace(int index){return attribute(index).namespace;}
    public String getAttributePrefix(int index){return prefix(attribute(index).namespace);}
    public String getAttributeType(int index){attribute(index);return "CDATA";}
    public boolean isAttributeDefault(int index){attribute(index);return false;}
    public int getAttributeNameResource(int index){return attribute(index).resourceId;}
    public String getAttributeValue(int index){BinaryXml.Value v=attribute(index).value;return v.type==3?v.text:TypedValue.coerceToString(v.type,v.data);}
    public String getAttributeValue(String ns,String name){int i=index(ns,name);return i<0?null:getAttributeValue(i);}
    public int getAttributeListValue(int index,String[] options,int fallback){BinaryXml.Value v=attribute(index).value;if(v.type!=3)return v.data;for(int i=0;i<options.length;i++)if(options[i].equals(v.text))return i;return fallback;}
    public boolean getAttributeBooleanValue(int index,boolean fallback){BinaryXml.Value v=attribute(index).value;return v.type>=16&&v.type<=31?v.data!=0:fallback;}
    public int getAttributeResourceValue(int index,int fallback){BinaryXml.Value v=attribute(index).value;return v.type==1?v.data:fallback;}
    public int getAttributeIntValue(int index,int fallback){BinaryXml.Value v=attribute(index).value;return v.type>=16&&v.type<=31?v.data:fallback;}
    public int getAttributeUnsignedIntValue(int index,int fallback){return getAttributeIntValue(index,fallback);}
    public float getAttributeFloatValue(int index,float fallback){BinaryXml.Value v=attribute(index).value;if(v.type!=4)throw new RuntimeException("Attribute is not a float");return Float.intBitsToFloat(v.data);}
    public int getAttributeListValue(String ns,String name,String[] options,int fallback){int i=index(ns,name);return i<0?fallback:getAttributeListValue(i,options,fallback);}
    public boolean getAttributeBooleanValue(String ns,String name,boolean fallback){int i=index(ns,name);return i<0?fallback:getAttributeBooleanValue(i,fallback);}
    public int getAttributeResourceValue(String ns,String name,int fallback){int i=index(ns,name);return i<0?fallback:getAttributeResourceValue(i,fallback);}
    public int getAttributeIntValue(String ns,String name,int fallback){int i=index(ns,name);return i<0?fallback:getAttributeIntValue(i,fallback);}
    public int getAttributeUnsignedIntValue(String ns,String name,int fallback){return getAttributeIntValue(ns,name,fallback);}
    public float getAttributeFloatValue(String ns,String name,float fallback){int i=index(ns,name);return i<0?fallback:getAttributeFloatValue(i,fallback);}
    public String getIdAttribute(){int i=event().node==null?-1:event().node.idIndex;return i<0?null:attribute(i).value.text;}
    public String getClassAttribute(){int i=event().node==null?-1:event().node.classIndex;return i<0?null:attribute(i).value.text;}
    public int getIdAttributeResourceValue(int fallback){int i=event().node==null?-1:event().node.idIndex;return i<0?fallback:getAttributeResourceValue(i,fallback);}
    public int getStyleAttribute(){int i=event().node==null?-1:event().node.styleIndex;if(i<0)return 0;BinaryXml.Value v=attribute(i).value;return v.type==1||v.type==2?v.data:0;}
}
