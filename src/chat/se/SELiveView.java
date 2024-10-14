package chat.se;

import chat.*;
import chat.utils.UnreadInfo;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.utils.Vec;

import java.util.Random;

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
  private ChatEvent adjacent(ChatEvent ev0, boolean mine, int dir) {
    if (!(ev0 instanceof SEChatEvent ev)) return null;
    int i = r.events.indexOf(ev);
    if (i==-1) return null;
    while (true) {
      if (i==0 || i== r.events.size()) return null;
      i+= dir;
      if (!mine || r.events.get(i).mine) return r.events.get(i);
    }
  }
  
  public void older() {
    // TODO load proper transcript messages
    Vec<SEChatEvent> evs = new Vec<>();
    for (int i = 0; i < 10; i++) evs.add(r.randomMessage(new Random(), null, "transcript "+i));
    r.insertOlder(evs);
  }
  
  public Node inputPlaceContent() {
    return input;
  }
  
  public boolean post(String raw, String replyTo) {
    // TODO post message
    return false;
  }
  
  public boolean edit(ChatEvent m, String raw) {
    // TODO edit message
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
