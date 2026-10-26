// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import android.graphics.Typeface;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import java.util.ArrayList;

/** Minimal HTML to Spanned conversion: b, strong, i, em, a, br, block breaks, list items and character entities. */
public class Html {
    private Html() {}

    private static final class Open {
        final String tag;
        final String href;
        final int start;
        Open(String tag, String href, int start) { this.tag = tag; this.href = href; this.start = start; }
    }

    public interface TagHandler {void handleTag(boolean opening,String tag,Editable output,org.xml.sax.XMLReader reader);}
    public interface ImageGetter {android.graphics.drawable.Drawable getDrawable(String source);}
    public static final int FROM_HTML_MODE_LEGACY=0,FROM_HTML_MODE_COMPACT=63;
    public static Spanned fromHtml(String source){return fromHtml(source,0,null,null);}
    public static Spanned fromHtml(String source,int flags){return fromHtml(source,flags,null,null);}
    public static Spanned fromHtml(String source,ImageGetter images,TagHandler tags){return fromHtml(source,0,images,tags);}
    public static Spanned fromHtml(String source,int flags,ImageGetter images,final TagHandler tags){
        final SpannableStringBuilder out=new SpannableStringBuilder();
        if(source==null)return out;if(source.length()>4*1024*1024)throw new IllegalArgumentException("HTML size limit");
        final ArrayList<Open> stack=new ArrayList<>();
        final org.xml.sax.helpers.XMLFilterImpl reader=new org.xml.sax.helpers.XMLFilterImpl();
        reader.setContentHandler(new org.xml.sax.helpers.DefaultHandler(){
            public void startElement(String uri,String local,String qname,org.xml.sax.Attributes attrs){
                String tag=local.toLowerCase(java.util.Locale.ROOT);String href=attrs.getValue("href");
                open(out,stack,tag,href==null?"":"href=\""+href.replace("\"","&quot;")+"\"",false);
                if(tags!=null&&!known(tag))tags.handleTag(true,local,out,reader);
            }
            public void endElement(String uri,String local,String qname){
                String tag=local.toLowerCase(java.util.Locale.ROOT);close(out,stack,tag);
                if(tags!=null&&!known(tag))tags.handleTag(false,local,out,reader);
            }
            public void characters(char[] text,int start,int count){appendText(out,new String(text,start,count));}
        });
        try{
            int i=0;String skip=null;
            while(i<source.length()){
                if(source.startsWith("<!--",i)){int end=source.indexOf("-->",i+4);i=end<0?source.length():end+3;continue;}
                if(source.charAt(i)=='<'){
                    int end=source.indexOf('>',i+1);if(end<0){emit(reader,source.substring(i));break;}
                    String body=source.substring(i+1,end).trim();i=end+1;
                    boolean closing=body.startsWith("/");if(closing)body=body.substring(1).trim();
                    boolean self=body.endsWith("/");if(self)body=body.substring(0,body.length()-1).trim();
                    int split=0;while(split<body.length()&&!Character.isWhitespace(body.charAt(split)))split++;
                    String tag=body.substring(0,split),lower=tag.toLowerCase(java.util.Locale.ROOT);if(tag.isEmpty())continue;
                    if(skip!=null){if(closing&&lower.equals(skip))skip=null;continue;}
                    if(!closing&&(lower.equals("script")||lower.equals("style"))){if(!self)skip=lower;continue;}
                    if(closing)reader.getContentHandler().endElement("",tag,tag);
                    else{
                        org.xml.sax.helpers.AttributesImpl attrs=new org.xml.sax.helpers.AttributesImpl();
                        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("([A-Za-z_:][A-Za-z0-9_:.-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s]+))").matcher(body.substring(split));
                        while(matcher.find()){String value=matcher.group(2)!=null?matcher.group(2):matcher.group(3)!=null?matcher.group(3):matcher.group(4);attrs.addAttribute("",matcher.group(1),matcher.group(1),"CDATA",decode(value));}
                        reader.getContentHandler().startElement("",tag,tag,attrs);
                        if(self||lower.equals("br")||lower.equals("img"))reader.getContentHandler().endElement("",tag,tag);
                    }
                }else{int end=source.indexOf('<',i);if(end<0)end=source.length();if(skip==null)emit(reader,decode(source.substring(i,end)));i=end;}
            }
        }catch(org.xml.sax.SAXException failure){throw new IllegalArgumentException("HTML handler failed",failure);}
        for(int k=stack.size()-1;k>=0;k--)apply(out,stack.get(k));
        return out;
    }
    private static boolean known(String tag){return java.util.Arrays.asList("html","body","b","strong","i","em","a","br","p","div","ul","ol","li","h1","h2","h3","h4","h5","h6","blockquote").contains(tag);}
    private static void emit(org.xml.sax.XMLReader reader,String text)throws org.xml.sax.SAXException{char[] chars=text.toCharArray();reader.getContentHandler().characters(chars,0,chars.length);}

    private static void open(SpannableStringBuilder out, ArrayList<Open> stack, String tag, String attributes, boolean selfClosing) {
        switch (tag) {
            case "br":
                out.append('\n');
                return;
            case "p": case "div": case "ul": case "ol": case "h1": case "h2": case "h3": case "h4": case "h5": case "h6": case "blockquote":
                breakLine(out);
                break;
            case "li":
                breakLine(out);
                out.append("\u2022 ");
                return;
            default:
                break;
        }
        if (selfClosing) return;
        if (tag.equals("b") || tag.equals("strong") || tag.equals("i") || tag.equals("em") || tag.equals("a")
                || tag.equals("h1") || tag.equals("h2") || tag.equals("h3") || tag.equals("h4") || tag.equals("h5") || tag.equals("h6")) {
            stack.add(new Open(tag, tag.equals("a") ? attribute(attributes, "href") : null, out.length()));
        }
    }

    private static void close(SpannableStringBuilder out, ArrayList<Open> stack, String tag) {
        switch (tag) {
            case "p": case "div": case "ul": case "ol": case "h1": case "h2": case "h3": case "h4": case "h5": case "h6": case "blockquote": case "li":
                breakLine(out);
                break;
            default:
                break;
        }
        for (int k = stack.size() - 1; k >= 0; k--) {
            if (stack.get(k).tag.equals(tag)) {
                apply(out, stack.remove(k));
                return;
            }
        }
    }

    private static void apply(SpannableStringBuilder out, Open open) {
        int end = out.length();
        if (end <= open.start) return;
        switch (open.tag) {
            case "b": case "strong": case "h1": case "h2": case "h3": case "h4": case "h5": case "h6":
                out.setSpan(new StyleSpan(Typeface.BOLD), open.start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case "i": case "em":
                out.setSpan(new StyleSpan(Typeface.ITALIC), open.start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            case "a":
                if (open.href != null) out.setSpan(new URLSpan(open.href), open.start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
            default:
                break;
        }
    }

    private static void breakLine(SpannableStringBuilder out) {
        int n = out.length();
        if (n > 0 && out.charAt(n - 1) != '\n') out.append('\n');
    }

    private static void appendText(SpannableStringBuilder out, String text) {
        StringBuilder collapsed = new StringBuilder(text.length());
        int n = out.length();
        char last = n > 0 ? out.charAt(n - 1) : '\n';
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                if (last != ' ' && last != '\n') {
                    collapsed.append(' ');
                    last = ' ';
                }
            } else {
                collapsed.append(c);
                last = c;
            }
        }
        out.append(collapsed);
    }

    private static String attribute(String attributes, String name) {
        String lower = attributes.toLowerCase();
        int at = 0;
        while ((at = lower.indexOf(name, at)) >= 0) {
            boolean boundary = at == 0 || Character.isWhitespace(lower.charAt(at - 1));
            int p = at + name.length();
            while (p < lower.length() && Character.isWhitespace(lower.charAt(p))) p++;
            if (boundary && p < lower.length() && lower.charAt(p) == '=') {
                p++;
                while (p < attributes.length() && Character.isWhitespace(attributes.charAt(p))) p++;
                if (p >= attributes.length()) return null;
                char quote = attributes.charAt(p);
                if (quote == '"' || quote == '\'') {
                    int end = attributes.indexOf(quote, p + 1);
                    return end < 0 ? null : decode(attributes.substring(p + 1, end));
                }
                int end = p;
                while (end < attributes.length() && !Character.isWhitespace(attributes.charAt(end))) end++;
                return decode(attributes.substring(p, end));
            }
            at = p;
        }
        return null;
    }

    private static String decode(String value) {
        if (value.indexOf('&') < 0) return value;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            int semi = c == '&' ? value.indexOf(';', i + 1) : -1;
            String decoded = semi > 0 && semi - i <= 10 ? entity(value.substring(i + 1, semi)) : null;
            if (decoded != null) {
                out.append(decoded);
                i = semi + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static String entity(String name) {
        switch (name) {
            case "amp": return "&";
            case "lt": return "<";
            case "gt": return ">";
            case "quot": return "\"";
            case "apos": return "'";
            case "nbsp": return "\u00a0";
            default: break;
        }
        if (name.length() > 1 && name.charAt(0) == '#') {
            try {
                int code = name.charAt(1) == 'x' || name.charAt(1) == 'X' ? Integer.parseInt(name.substring(2), 16) : Integer.parseInt(name.substring(1));
                if (code > 0 && code <= 0x10ffff && !(code >= 0xd800 && code <= 0xdfff)) return new String(Character.toChars(code));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
