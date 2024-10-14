package chat.se;

import chat.*;
import chat.ui.RoomListNode;
import chat.utils.*;
import dzaima.ui.gui.io.*;
import dzaima.utils.*;

import java.util.*;

public class SEChatroom extends Chatroom {
  public final SEChatUser u;
  public final SELiveView view = new SELiveView(this);
  public final String id;
  
  public final HashMap<String, Username> users = new HashMap<>();
  
  public final Vec<SEChatEvent> events = new Vec<>();
  public final HashMap<String, SEChatEvent> eventMap = new HashMap<>();
  public final HashSet<SEChatEvent> eventSet = new HashSet<>();
  
  protected SEChatroom(SEChatUser u, String id, String title0) {
    super(u);
    this.id = id;
    this.u = u;
    setOfficialName(title0);
  }
  
  public LiveView mainView() {
    return view;
  }
  
  public void muteStateChanged() { }
  public void cfgUpdated() {
    if (m.gc.getProp("chat.preview.enabled").b()) view.input.setLang(MDLang.makeLanguage(m, view.input));
    else view.input.setLang(m.gc.langs().defLang);
  }
  
  public Username getUsername(String uid, boolean requestForFuture) {
    return users.computeIfAbsent(uid, s -> {
      String name = "user-" + uid;
      return new Username(name, Promise.resolved(name));
    }); 
  }
  
  public void retryOnFullUserList(Runnable then) { }
  
  public Vec<UserRes> autocompleteUsers(String prefix) {
    return new Vec<>();
  }
  
  public void userMenu(Click c, int x, int y, String uid) {
    // TODO user profile right click
  }
  
  public void viewProfile(String uid) {
    // TODO viewing user profile info
  }
  
  public void viewRoomInfo() {
    // TODO viewing room info
  }
  
  public RoomListNode.ExternalDirInfo asDir() { return null; }
  
  public ChatUser user() {
    return u;
  }
  
  public void delete(ChatEvent m) {
    // TODO send message delete event
  }
  
  public ChatEvent find(String id) {
    return eventMap.get(id);
  }
  
  public String asCodeblock(String s) {
    return s.indent(4);
  }
  
  public Pair<Boolean, Integer> highlight(String s) {
    return new Pair<>(s.indexOf('\n')==-1, 0);
  }
  
  public UnreadInfo unreadInfo() {
    return UnreadInfo.NONE;
  }
}
