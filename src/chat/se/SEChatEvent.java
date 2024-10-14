package chat.se;

import chat.*;
import chat.ui.MsgNode;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.types.StringNode;
import dzaima.utils.Vec;

import java.time.Instant;
import java.util.*;

public class SEChatEvent extends ChatEvent {
  public final SEChatroom r;
  private final String uid;
  private final String text;
  public boolean deleted;
  
  protected SEChatEvent(SEChatroom r, String id, String uid, Instant time, String target, String text) {
    super(id, r.u.id().equals(uid), time, target);
    this.r = r;
    this.uid = uid;
    this.text = text;
  }
  
  public boolean userEq(ChatEvent o) {
    return o instanceof SEChatEvent e && uid.equals(e.uid);
  }
  
  public Chatroom room() {
    return r;
  }
  
  public MsgNode.MsgType type() {
    return MsgNode.MsgType.MSG;
  }
  
  public String senderID() {
    return uid;
  }
  
  public String senderDisplay() {
    return r.getUsername(uid, false).best();
  }
  
  public boolean isDeleted() {
    return deleted;
  }
  
  public String getSrc() {
    return text;
  }
  
  public void updateBody(boolean newAtEnd, boolean ping) {
    StringNode body = new StringNode(m().ctx, text); // TODO use HTMLParser output or something
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
