package chat.se;

import chat.*;
import chat.utils.UnreadInfo;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.utils.Vec;

public class SELiveView extends LiveView {
  private final SEChatroom room;
  
  public SELiveView(SEChatroom room) {
    super(room.m);
    this.room = room;
    createInput();
  }
  
  public MuteState muteState() { return room.muteState; }
  
  public UnreadInfo unreadInfo() { return room.unreadInfo(); }
  
  public ChatEvent prevMsg(ChatEvent msg, boolean mine) {
    return adjacent(msg, mine, -1);
  }
  
  public ChatEvent nextMsg(ChatEvent msg, boolean mine) {
    return adjacent(msg, mine, 1);
  }
  private ChatEvent adjacent(ChatEvent ev0, boolean mine, int dir) {
    if (!(ev0 instanceof SEChatEvent ev)) return null;
    int i = room.events.indexOf(ev);
    if (i==-1) return null;
    while (true) {
      if (i==0 || i==room.events.size()) return null;
      i+= dir;
      if (!mine || room.events.get(i).mine) return room.events.get(i);
    }
  }
  
  public void older() {
    // TODO insert messages from transcript
  }
  
  public Node inputPlaceContent() {
    return input;
  }
  
  public boolean post(String raw, String replyTo) {
    return false;
  }
  
  public boolean edit(ChatEvent m, String raw) {
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
    return room;
  }
  
  public String title() {
    return room.title();
  }
  
  public boolean contains(ChatEvent ev) {
    return ev instanceof SEChatEvent e && room.eventSet.contains(e);
  }
  
  public boolean navigationKey(Key key, KeyAction a) {
    return false;
  }
  
  public boolean typed(int codepoint) {
    return false;
  }
}
