package chat.se;

import chat.*;
import chat.mx.MediaThread;
import chat.ui.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.utils.*;

import java.util.*;
import java.util.function.*;

public class SEChatUser extends ChatUser {
  public final JSON.Obj data;
  
  public final Vec<SEChatroom> rooms = new Vec<>();
  public final HashMap<String, SEChatroom> roomMap = new HashMap<>();
  public final Collection<SEChatroom> roomSet = roomMap.values();
  
  public SEChatUser(ChatMain m, JSON.Obj data) {
    super(m);
    this.data = data;
    setUsername("hello i am user"); // TODO proper username
    setServer("SE");
    
    roomListNode.startLoad();
    addRoom(new SEChatroom(this, "se-123", "room 1"));
    addRoom(new SEChatroom(this, "se-456", "room 2"));
    roomListChanged();
  }
  
  private void addRoom(SEChatroom r) { // must call roomListChanged() at some later point
    preRoomListChange();
    rooms.add(r);
    roomMap.put(r.id, r);
    roomListNode.add(r.node);
  }
  
  public Vec<SEChatroom> rooms() {
    return rooms;
  }
  
  public void saveRooms() { }
  
  public void tick() {
    Random r = new Random();
    for (SEChatroom c : rooms) {
      if (r.nextFloat()>0.97) {
        String target = null;
        if (c.events.sz>0 && r.nextFloat()>0.5) target = c.events.get(r.nextInt(c.events.sz)).id;
        SEChatEvent e = c.randomMessage(r, target, "hello");
        c.pushMsg(e, e.hasPing);
      }
    }
  }
  
  public void close() {
    
  }
  
  public String id() {
    return data.str("userid");
  }
  
  public JSON.Obj data() {
    return data;
  }
  
  public URIInfo parseURI(String src, JSON.Obj info) {
    return new URIInfo(src, info, false, false) {
      public MediaThread.MediaRequest requestFull() {
        return null;
      }
      
      public MediaThread.MediaRequest requestThumbnail() { throw new IllegalStateException(); }
    };
  }
  
  public void loadImg(URIInfo info, boolean acceptThumbnail, Consumer<Node> loaded, BiFunction<Ctx, byte[], ImageNode> ctor, Supplier<Boolean> stillNeeded) {
    loaded.accept(null); // TODO inline images
  }
  
  public void openLink(String url, Extras.LinkInfo info) {
    m.gc.openLink(url); // TODO inline m.stack viewing?
  }
}
