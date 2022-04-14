package chat.mx;

import chat.*;
import dzaima.ui.node.types.StringNode;
import dzaima.utils.*;
import dzaima.utils.Tools;
import dzaima.utils.JSON.*;
import libMx.*;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import static chat.mx.MxChatroom.DEFAULT_MSGS;

public class MxChatUser extends ChatUser {
  
  public final Obj data;
  public final MxServer s;
  public final MxLogin u;
  
  public MxSync2 sync;
  
  public Vec<MxChatroom> roomList;
  public HashMap<String, MxChatroom> roomMap = new HashMap<>();
  
  private final ConcurrentLinkedQueue<Runnable> primary = new ConcurrentLinkedQueue<>();
  private final LinkedBlockingDeque<Runnable> network = new LinkedBlockingDeque<>();
  public void queueNetwork(Runnable r) { network.add(r); }
  @FunctionalInterface public interface Request<T> { T get() throws Throwable; }
  
  // calling again with the same counter will cancel the previous request if it wasn't already invoked on the main thread
  public <T> void queueRequest(Counter c, Request<T> network, Consumer<T> primary) {
    int v = c==null? 0 : ++c.value;
    queueNetwork(() -> {
      T r;
      try { r = network.get(); }
      catch (Throwable e) { e.printStackTrace(); r = null; }
      T finalR = r;
      this.primary.add(() -> { if (c==null || v==c.value) primary.accept(finalR); });
    });
  }
  
  public Thread networkThread = Tools.thread(() -> {
    while (true) {
      try {
        network.take().run();
      } catch (InterruptedException e) {
        return;
      } catch (Throwable e) {
        e.printStackTrace();
      }
    }
  });
  
  public MxChatUser(ChatMain m, Obj dataIn) {
    super(m);
    this.data = dataIn;
    s = MxServer.of(new MxLoginMgr() {
      public String getServer()   { return data.str("server"); }
      public String getUserID()   { return data.str("userid"); }
      public String getPassword() { return data.str("password"); }
      public String getToken()    { return data.str("token", null); }
      public void updateToken(String token) {
        data.put("token", new JSON.Str(token));
        m.requestSave();
      }
    });
    u = s.primaryLogin;
  
    node.ctx.id("name").replace(0, new StringNode(node.ctx, u.user().name()));
    node.ctx.id("server").replace(0, new StringNode(node.ctx, s.url.replaceFirst("^https?://", "")));
    roomList = new Vec<>();
    
    queueRequest(null, () -> u.s.getJ("_matrix/client/r0/sync?filter={\"room\":{\"timeline\":{\"limit\":"+DEFAULT_MSGS+"}}}&access_token=" + u.token), j -> {
      HashMap<String, MxChatroom> todoRooms = new HashMap<>();
      for (Entry e : j.obj("rooms", Obj.E).obj("join", Obj.E).entries()) {
        MxChatroom r = new MxChatroom(this, e.k, e.v.obj());
        roomMap.put(e.k, r);
        todoRooms.put(e.k, r);
      }

      boolean updatedRooms = false;
      for (String o : data.arr("roomOrder", Arr.E).strs()) {
        MxChatroom r = todoRooms.get(o);
        if (r!=null) {
          roomList.add(r);
          todoRooms.remove(o);
        } else updatedRooms = true;
      }

      updatedRooms|= todoRooms.size()>0;
      if (updatedRooms) for (MxChatroom c : todoRooms.values()) roomList.add(c);
      roomOrderChanged(updatedRooms);

      sync = new MxSync2(s, j.str("next_batch"));
      sync.start();
    });
  }
  public void roomOrderChanged(boolean save) {
    Val[] order = new Val[roomList.sz];
    listNode.clearCh();
    for (int i = 0; i < order.length; i++) {
      MxChatroom c = roomList.get(i);
      order[i] = new JSON.Str(c.r.rid);
      listNode.add(c.node);
    }
    data.put("roomOrder", new Arr(order));
    if (save) m.requestSave();
  }
  public Vec<Chatroom> rooms() {
    Vec<Chatroom> r = new Vec<>();
    for (MxChatroom c : roomList) r.add(c);
    return r;
  }
  public void reorderRooms(Vec<Chatroom> rs) {
    if (rs.sz!=roomList.sz) {
      ChatMain.warn("bad room reordering length");
      return;
    }
    Vec<MxChatroom> l = new Vec<>();
    for (Chatroom c : rs) {
      if (roomMap.get(((MxChatroom) c).r.rid)==null) {
        ChatMain.warn("bad room reordering");
        return;
      }
      l.add((MxChatroom) c);
    }
    roomList = l;
    roomOrderChanged(true);
  }
  
  public void tick() {
    while (true) {
      Runnable c = primary.poll(); if(c==null) break;
      try {
        c.run();
      } catch (Throwable t) { t.printStackTrace(); }
    }
    
    if (sync==null) return;
    
    while (true) {
      boolean newRooms = false;
      Obj m = sync.poll(); if (m==null) break;
      Obj crs = m.obj("rooms", Obj.E).obj("join", Obj.E);
      for (JSON.Entry k : crs.entries()) {
        MxChatroom room = roomMap.get(k.k);
        if (room==null) {
          MxChatroom r = new MxChatroom(this, k.k, k.v.obj());
          roomMap.put(k.k, r);
          roomList.add(r);
          newRooms = true;
        } else room.update(k.v.obj());
      }
      if (newRooms) roomOrderChanged(true);
    }
    
    for (MxChatroom c : roomList) c.tick();
  }
  
  public void close() {
    if (sync!=null) sync.stop(); // will wait for max 30s more but ¯\_(ツ)_/¯
    networkThread.interrupt();
  }
  
  public String id() {
    return u.uid;
  }
  
  public Obj data() {
    return data;
  }
  
  public MxChatroom findRoom(String name) {
    if (name.startsWith("!")) {
      for (MxChatroom c : roomList) if (c.r.rid.equals(name)) return c;
    } else if (name.startsWith("#")) {
      for (MxChatroom c : roomList) if (name.equals(c.canonicalAlias)) return c;
      for (MxChatroom c : roomList) for (String a : c.altAliases) if (name.equals(a)) return c;
    }
    return null;
  }
  public void openLink(String url) {
    if (url.startsWith("https://matrix.to/#/")) {
      try {
        URI u = new URI(url);
        String fr = u.getFragment().substring(1);
        int pos = fr.indexOf('?');
        if (pos!=-1) fr = fr.substring(0, pos);
        String[] parts = Tools.split(fr, '/');
        int n = parts.length;
        while (n>0 && parts[n-1].isEmpty()) n--;
        if (n==1) {
          MxChatroom r = findRoom(parts[0]);
          if (r!=null) {
            m.toRoom(r);
            return;
          }
        }
        if (n==2) {
          MxChatroom r = findRoom(parts[0]);
          String msgId = parts[1];
          if (r!=null) {
            MxChatEvent ev = r.log.get(msgId);
            if (ev!=null) {
              m.toRoom(r, ev);
              return;
            }
            r.openTranscript(msgId, v -> {
              if (!v) m.gc.openLink(url);
            });
            return;
          }
        }
      } catch (URISyntaxException ignored) { }
    }
    
    m.gc.openLink(url);
  }
}