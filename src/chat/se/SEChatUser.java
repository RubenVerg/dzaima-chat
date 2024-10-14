package chat.se;

import chat.*;
import chat.mx.MediaThread;
import chat.ui.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.utils.*;
import libSE.SEAccount;
import libSE.SEMessage;

import java.time.Instant;
import java.util.*;
import java.util.function.*;

public class SEChatUser extends ChatUser {
  public final JSON.Obj data;
  public final SEAccount account;
  
  public final Vec<SEChatroom> rooms = new Vec<>();
  public final HashMap<String, SEChatroom> roomMap = new HashMap<>();
  public final Collection<SEChatroom> roomSet = roomMap.values();
  
  public SEChatUser(ChatMain m, JSON.Obj data) {
    super(m);

    this.data = data;
    SEAccount.DEBUG = true;

    account = new SEAccount(data.str("server", SEAccount.ServerSites.STACK_EXCHANGE.site), true);
    account.authenticate(data.str("email"), data.str("password"), data.str("loginServer"));

    setUsername(account.userName()); // TODO proper username
    setServer(account.server);

    final var favorites = account.favorites();
    
    roomListNode.startLoad();
    for (final var fav : favorites)
      addRoom(new SEChatroom(this, account.joinRoom(fav)));
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

  public void tick() { }
  
  public void close() {
    account.close();
  }
  
  public String id() {
    return Long.toString(account.userId);
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
