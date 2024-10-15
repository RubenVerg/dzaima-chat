package chat.se;

import chat.*;
import chat.ui.MsgNode;
import chat.utils.HTMLParser;
import dzaima.ui.gui.PartialMenu;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.utils.Vec;
import libSE.SEMessage;

import java.util.*;

public class SEChatEvent extends ChatEvent {
  public final SEChatroom r;
  public SEMessage message;
  
  protected SEChatEvent(SEChatroom r, SEMessage message) {
    super(Long.toString(message.id), r.u.id().equals(Long.toString(message.userId)), message.timeStamp, message.replyId.stream().mapToObj(Long::toString).findFirst().orElse(null));
    this.r = r;
    this.message = message;
  }

  public void edit(SEMessage newMessage) {
    edited = true;
    message = newMessage;
    updateBody(true, false);
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
    Node body = isDeleted()? removedBody() : HTMLParser.parse(r, message.content);
    if (visible) r.m.updMessage(this, body, newAtEnd);
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
    n.border.openMenu(true);
    final var pm = new PartialMenu(r.m.gc);
    final var lv = (SELiveView) r.m.liveView();
    pm.add(n.gc.getProp("chat.se.msgMenu.reply").gr(), "replyTo", () -> {
      if (Objects.nonNull(lv)) {
        lv.input.markReply(this);
        lv.input.focusMe();
      }
    });
    pm.add(n.gc.getProp("chat.se.msgMenu.mine").gr(), s -> {
      if (s.equals("delete")) {
        r.delete(this);
        return true;
      }
      if (s.equals("edit")) {
        if (Objects.nonNull(lv)) {
          if (Objects.isNull(lv.input.editing)) lv.input.setEdit(this);
          lv.input.focusMe();
        }
        return true;
      }
      return false;
    });
    pm.open(r.m.ctx, c, () -> {
      if (Objects.nonNull(n)) n.border.openMenu(false);
    });
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
