package chat.se;

import chat.*;
import chat.ui.RoomListNode;
import chat.utils.*;
import dzaima.ui.gui.io.Click;
import dzaima.utils.*;
import libSE.SEMessage;
import libSE.SERoom;

import java.util.*;

public class SEChatroom extends Chatroom {
  public final SEChatUser u;
  public final SELiveView view = new SELiveView(this);
  public final String id;
  public final SERoom room;
  
  public final HashMap<String, Username> users = new HashMap<>();
  
  public final Vec<SEChatEvent> events = new Vec<>();
  public final HashMap<String, SEChatEvent> eventMap = new HashMap<>();
  public final HashSet<SEChatEvent> eventSet = new HashSet<>();
  
  public final HashMap<String, Vec<String>> msgReplies = new HashMap<>(); // id → ids of messages replying to it
  
  public UnreadInfo unread = UnreadInfo.NONE;
  
  protected SEChatroom(SEChatUser u, SERoom room) {
    super(u);
    this.room = room;
    room.register(new SERoom.MessageHandler() {
      @Override
      public void onMessage(SEMessage message) {
        pushMsg(new SEChatEvent(SEChatroom.this, message), message.isMention);
      }

      @Override
      public void onEdit(SEMessage oldMessage, SEMessage newMessage) {
        // TODO
      }

      @Override
      public void onDelete(SEMessage message) {
        // TODO
      }

      @Override
      public void onMention(SEMessage message) {
        // TODO
      }

      @Override
      public void onLoadOldMessage(SEMessage message) {
        // TODO
      }
    });
    this.id = Long.toString(room.roomId);
    this.u = u;
    room.getInfo();
    setOfficialName(room.roomName);
  }
  
  public void insertOlder(Vec<SEChatEvent> evs) {
    events.insert(0, evs);
    for (SEChatEvent c : evs) insertedEvent(c);
    if (view.open) m.insertMessages(false, evs);
  }
  protected void insertedEvent(SEChatEvent e) { // need to still manually add into events
    eventMap.put(e.id, e);
    if (e.target!=null) msgReplies.computeIfAbsent(e.target, id -> new Vec<>()).add(e.id); // TODO update if edited
  }
  public void pushMsg(SEChatEvent e, boolean ping) { // returns the event object if it's visible on the timeline
    events.add(e);
    insertedEvent(e);
    if (view.open) m.addMessage(e, ping);
    if (ping) unread = new UnreadInfo(unread.unread, true);
    unread = new UnreadInfo(unread.unread+1, unread.ping);
    unreadChanged();
  }
  
  public void markAsRead() {
    unread = UnreadInfo.NONE;
    unreadChanged();
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
    Vec<UserRes> res = new Vec<>();
    for (final var user : room.pingable()) {
      if (user.b.startsWith(prefix)) res.add(new UserRes(user.b, "@" + user.b.replaceAll("\\s", "")));
    }
    return res;
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
    room.delete(Long.parseLong(m.id));
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
    return unread;
  }
}
