package chat.se;

import chat.*;
import chat.utils.UnreadInfo;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.utils.Vec;

import java.util.Objects;

public class SELiveView extends LiveView {
  private final SEChatroom r;
  
  public SELiveView(SEChatroom r) {
    super(r.m);
    this.r = r;
    createInput();
  }
  
  public void show() {
    for (SEChatEvent c : r.events) r.m.addMessage(c, false);
    super.show();
  }
  public void hide() {
    super.hide();
    for (SEChatEvent c : r.events) c.hide();
  }
  
  public MuteState muteState() { return r.muteState; }
  
  public UnreadInfo unreadInfo() { return r.unreadInfo(); }
  
  public ChatEvent prevMsg(ChatEvent msg, boolean mine) {
    return adjacent(msg, mine, -1);
  }
  
  public ChatEvent nextMsg(ChatEvent msg, boolean mine) {
    return adjacent(msg, mine, 1);
  }
  private ChatEvent adjacent(ChatEvent ev, boolean mine, int dir) {
    int i = r.events.indexOf((SEChatEvent) ev);
    if (i == -1) {
      if (dir == 1) return null;
      i = r.events.sz;
    }
    while (true) {
      i+= dir;
      if (i<0 || i>=r.events.size()) return dir==1? null : ev;
      SEChatEvent e = r.events.get(i);
      if (mine && !e.mine) continue;
      if (e.isDeleted()) continue;
      return e;
    }
  }
  
  public void older() {
    r.insertOlder(Vec.ofCollection(r.room.previousMessages().stream().map(msg -> new SEChatEvent(r, msg)).toList()));
  }
  
  public Node inputPlaceContent() {
    return input;
  }
  
  public boolean post(String raw, String replyTo) {
    final var message = Objects.isNull(replyTo) ? raw : ":" + replyTo + " " + raw;
    r.room.send(message);
    return true;
  }
  
  public boolean edit(ChatEvent m, String raw) {
    if (m instanceof SEChatEvent e) {
      if (e.message.room.roomId == r.room.roomId) {
        r.room.edit(e.message.id, raw);
        return true;
      }
    }
    return false;
  }
  
  public void upload() {
    // TODO image upload dialog
  }
  
  public Vec<Command> allCommands() {
    return Vec.of();
  }
  
  public void mentionUser(String uid) {
    input.append("@"+uid+" ");
  }
  
  public void markAsRead() {
    r.markAsRead();
  }
  
  public Chatroom room() {
    return r;
  }
  
  public String title() {
    return r.title();
  }
  
  public boolean contains(ChatEvent ev) {
    return ev instanceof SEChatEvent e && r.eventSet.contains(e);
  }
  
  public boolean navigationKey(Key key, KeyAction a) {
    return false;
  }
  
  public boolean typed(int codepoint) {
    return false;
  }
}
