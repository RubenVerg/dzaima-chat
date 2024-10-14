package chat.se;

import chat.*;
import chat.ui.MsgNode;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.types.StringNode;

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
    
  }
  
  public void rightClick(Click c, int x, int y) {
    
  }
  
  public HashMap<String, Integer> getReactions() {
    return new HashMap<>();
  }
  
  public HashSet<String> getReceipts(View view) {
    return new HashSet<>();
  }
  
  public boolean startsThread(View view) { return false; }
  public void toThread() { }
  
  public void toTarget() {
    SEChatEvent tgt = r.eventMap.get(target);
    if (tgt!=null) tgt.highlight(false);
  }
}
