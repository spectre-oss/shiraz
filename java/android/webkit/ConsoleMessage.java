// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;
/** Console message from the placeholder WebView. */
public class ConsoleMessage {
 public enum MessageLevel {TIP,LOG,WARNING,ERROR,DEBUG}
 private final String message,sourceId;private final int lineNumber;private final MessageLevel level;
 public ConsoleMessage(String message,String sourceId,int lineNumber,MessageLevel level){this.message=message==null?"":message;this.sourceId=sourceId==null?"":sourceId;this.lineNumber=lineNumber;this.level=level==null?MessageLevel.LOG:level;}
 public String message(){return message;}
 public String sourceId(){return sourceId;}
 public int lineNumber(){return lineNumber;}
 public MessageLevel messageLevel(){return level;}
}
