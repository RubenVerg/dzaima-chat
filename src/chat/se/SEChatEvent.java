package chat.se;

import chat.*;
import chat.ui.MsgNode;
import chat.utils.HTMLParser;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.StringNode;
import dzaima.utils.Vec;
import libSE.SEMessage;

import java.time.Instant;
import java.util.*;

import static java.awt.SystemColor.text;

public class SEChatEvent extends ChatEvent {
  public final SEChatroom r;
  public final SEMessage message;
  
  protected SEChatEvent(SEChatroom r, SEMessage message) {
    super(Long.toString(message.id), r.u.id().equals(Long.toString(message.userId)), message.timeStamp, message.replyId.stream().mapToObj(Long::toString).findFirst().orElse(null));
    this.r = r;
    this.message = message;
  }
  
  public boolean userEq(ChatEvent o) {
    if (o instanceof SEChatEvent e) return message.userId == e.message.userId;
    return false;
  }
  
  public Chatroom room() {
    return r;
  }
  
  public MsgNode.MsgType type() {
    return MsgNode.MsgType.MSG;
  }
  
  public String senderID() {
    return Long.toString(message.userId);
  }
  
  public String senderDisplay() {
    return message.userName;
  }
  
  public boolean isDeleted() {
    return message.content == null;
  }
  
  public String getSrc() {
    return message.content;
  }
  
  public void updateBody(boolean newAtEnd, boolean ping) {
    Node body = HTMLParser.parse(r, message.content);
    r.m.updMessage(this, body, newAtEnd);
  }
  
  public void markRel(boolean on) {
    // what this is replying to
    SEChatEvent re = r.eventMap.get(target);
    if (re!=null && re.n!=null) re.n.setRelBg(on);
    
    // what replies to this
    Vec<String> replies = r.msgReplies.get(id);
    if (replies!=null) for (String c : replies) {
      SEChatEvent e = r.eventMap.get(c);
      if (e!=null && e.n!=null) e.n.setRelBg(on);
    }
  }
  
  public void rightClick(Click c, int x, int y) {
    
  }
  
  public HashMap<String, Integer> getReactions() { return null; }
  public HashSet<String> getReceipts(View view) { return null; }
  
  public boolean startsThread(View view) { return false; }
  public void toThread() { }
  
  public void toTarget() {
    SEChatEvent tgt = r.eventMap.get(target);
    if (tgt!=null) tgt.highlight(false);
  }
}
