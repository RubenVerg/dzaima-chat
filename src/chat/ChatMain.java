package chat;

import chat.mx.MxChatUser;
import chat.ui.*;
import dzaima.ui.eval.*;
import dzaima.ui.gui.*;
import dzaima.ui.gui.config.GConfig;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.*;
import dzaima.ui.node.prop.*;
import dzaima.ui.node.types.*;
import dzaima.utils.*;
import dzaima.utils.JSON.*;
import io.github.humbleui.skija.*;
import libMx.MxServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChatMain extends NodeWindow {
  public static final String DEFAULT_PROFILE = "accounts/profile.json";
  public static final Path LOCAL_CFG = Paths.get("local.dzcfg");
  
  
  public Path profilePath;
  private final Node msgs;
  public final ScrollNode msgsScroll;
  public final ChatTextArea input;
  public final Vec<ChatUser> users = new Vec<>();
  public final Node accountNode;
  public final Node actionbar, infobar;
  public View view;
  public ChatEvent editing;
  public ChatEvent replying;
  
  public boolean globalHidden = false;
  
  public Chatroom room() {
    return view==null? null : view.room();
  }
  
  public ChatMain(GConfig gc, Ctx pctx, String profilePath, PNodeGroup g) {
    super(gc, pctx, g, new WindowInit("chat"));
    if (Tools.DBG) MxServer.LOG = true;
    msgs = base.ctx.id("msgs");
    accountNode = base.ctx.id("accounts");
    actionbar = base.ctx.id("actionbar");
    infobar = base.ctx.id("infobar");
    msgsScroll = (ScrollNode) base.ctx.id("msgsScroll");
    Node inputPlace = base.ctx.id("inputPlace");
    input = new ChatTextArea(this, inputPlace.ctx, new String[]{"family", "numbering"}, new Prop[]{new StrProp("Arial"), EnumProp.FALSE}) {
      public boolean keyF2(Key key, int scancode, KeyAction a) {
        String name = gc.keymap(key, a, "chat");
        switch (name) {
          case "editUp": case "editDn":
            if (view instanceof Chatroom && (editing==null? getAll().length()==0 : getAll().equals(editing.getSrc())&&um.us.sz==0)) {
              Chatroom room = (Chatroom) view;
              setEdit(name.equals("editUp")? room.prevMsg(editing, true) : room.nextMsg(editing, true));
              return true;
            }
            break;
          case "replyUp": case "replyDn":
            if (view instanceof Chatroom && editing==null) {
              Chatroom room = (Chatroom) view;
              markReply(name.equals("replyUp")? room.prevMsg(replying, false) : room.nextMsg(replying, false));
              return true;
            }
            break;
          case "deleteMsg":
            Chatroom room = room();
            if (room!=null && editing!=null) {
              ChatEvent toDel = editing;
              setEdit(null);
              room.delete(toDel);
              removeAll(); um.clear();
            }
          return true;
        }
        return super.keyF2(key, scancode, a);
      }
    };
    input.wrap = true;
    inputPlace.add(input);
    input.setFn(mod -> {
      if (mod==0) { send(); return true; }
      return false;
    });
    // input.append("hello world _asd_ *asd* ---sdsad--- [link _it_](hello) `code \\`_it_ asd` abc `` hello \\`world `` ||spoiler|| text\n```java\nhello\nworld\n```\nmore");
    ((BtnNode) base.ctx.id("send")).setFn(c -> send());
    ((BtnNode) base.ctx.id("upload")).setFn(c -> {
      if (view!=null) openFile(null, null, p -> {
        if (p!=null && view!=null) view.room().upload(p);
      });
    });
    
    this.profilePath = Paths.get(profilePath);
    cfgUpdated();
  }
  
  @Override
  public void setup() {
    super.setup();
    loadProfile();
  }
  
  
  public int imageSafety() { // 0-none; 1-safe; 2-all
    String p = gc.getProp("chat.loadImgs").val();
    if (p.equals("none")) return 0;
    if (p.equals("safe")) return 1;
    if (p.equals("all")) return 2;
    MxServer.warn("invalid chat.loadImgs value");
    return 2;
  }
  
  public void loadProfile() {
    // TODO clear out old loaded profile
    try {
      Obj obj = JSON.parseObj(new String(Files.readAllBytes(profilePath), StandardCharsets.UTF_8));
      for (Obj c : obj.arr("accounts").objs()) {
        if (c.str("type").equals("matrix")) {
          addUser(new MxChatUser(this, c));
        } else throw new RuntimeException("Unknown account type '"+c.str("type")+"'");
      }
    } catch (IOException e) {
      System.err.println("Failed to load profile");
      throw new RuntimeException(e);
    }
  }
  
  public static void warn(String s) {
    System.err.println("["+LocalDateTime.now()+" !!] "+s);
  }
  
  public void send() {
    Chatroom r = room();
    if (r!=null) {
      String s = input.getAll();
      if (s.length()>0) {
        if (editing!=null) r.edit(editing, s);
        else r.post(s, replying==null? null : replying.id);
        markEdit(null);
        markReply(null);
        input.removeAll();
        input.um.clear();
      }
    }
  }
  public void setEdit(ChatEvent m) {
    if (!markEdit(m)) return;
    input.removeAll();
    if (editing!=null) {
      input.append(editing.getSrc());
      input.um.clear();
    }
  }
  private boolean markEdit(ChatEvent m) {
    if (replying!=null) return false;
    if (editing!=null) editing.mark(0);
    editing = m;
    if (editing!=null) editing.mark(1);
    return true;
  }
  public void markReply(ChatEvent m) {
    if (editing!=null) return;
    if (replying!=null) replying.mark(0);
    replying = m;
    if (replying!=null) replying.mark(2);
  }
  
  public void addUser(ChatUser u) {
    users.add(u);
    if (accountNode.ch.sz!=0) accountNode.add(accountNode.ctx.makeHere(gc.getProp("chat.rooms.accountSepP").gr()));
    accountNode.add(u.node);
  }
  
  public int toLast; // 0-no; 1-smooth; 2-instant; 3-to highlighted
  public ChatEvent toHighlight;
  public void hideCurrent() {
    setEdit(null);
    markReply(null);
    if (view!=null) view.hide();
    view = null;
    cHover = null;
    removeAllMessages();
    lastTimeStr = null;
  }
  public void toRoom(Chatroom c) {
    toRoom(c, null);
  }
  public void toRoom(Chatroom c, ChatEvent toHighlight) {
    hideCurrent();
    view = c;
    c.show();
    updActions();
    toLast = toHighlight!=null? 3 : 2;
    this.toHighlight = toHighlight;
  }
  public void toTranscript(TranscriptView v) {
    hideCurrent();
    view = v;
    v.show();
    updActions();
    toLast = 0;
  }
  public void updActions() {
    String info = "";
    if (cHover!=null) info+= Time.localNearTimeStr(cHover.msg.time);
    info+= "\n";
    Chatroom room = room();
    if (room!=null && room.typing.length()>0) {
      info+= room.typing;
    }
    actionbar.replace(0, new StringNode(actionbar.ctx, info));
  }
  
  public String hoverURL = null;
  public String currentAction = null;
  public void updInfo() {
    ArrayList<String> infos = new ArrayList<>();
    if (currentAction!=null) infos.add(currentAction);
    if (hoverURL!=null) infos.add(hoverURL);
    if (globalHidden) infos.add("unread counts disabled");
    StringBuilder info = new StringBuilder();
    for (String c : infos) {
      if (info.length()>0) info.append("; ");
      info.append(c);
    }
    infobar.replace(0, new StringNode(infobar.ctx, info.toString()));
  }
  public void setCurrentName(String s) {
    base.ctx.id("roomName").replace(0, new StringNode(base.ctx, s));
    updateTitle();
    
  }
  
  private boolean prevAtEnd;
  private int updInfoDelay;
  private long nextTimeUpdate;
  public int endDist;
  public void tick() {
    endDist = gc.em*20;
    
    boolean nAtEnd = atEnd();
    if (prevAtEnd!=nAtEnd) {
      updateUnread();
      prevAtEnd = nAtEnd;
    }
    if (updInfoDelay--==0) updActions();
    if (newHover) {
      if (pHover!=null) pHover.msg.markRel(false);
      if (cHover!=null) cHover.msg.markRel(true);
      pHover = cHover;
      newHover = false;
      if (cHover!=null) updActions();
      else updInfoDelay = 10;
    }
    
    super.tick();
    
    for (ChatUser c : users) c.tick();
    
    if (view!=null) view.viewTick();
    
    
    if (gc.lastNs>nextTimeUpdate) {
      insertLastTime();
      nextTimeUpdate = gc.lastNs + (long)60e9;
    }
    
    if (saveRequested) {
      saveRequested = false;
      
      Val[] accounts = new Val[users.sz];
      for (int i = 0; i < accounts.length; i++) {
        ChatUser u = users.get(i);
        accounts[i] = u.data();
      }
      Obj res = Obj.fromKV("accounts", new Arr(accounts));
      try {
        Files.write(profilePath, res.toString(2).getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        System.err.println("Failed to write profile file");
        throw new RuntimeException("Failed to write profile file");
      }
    }
  }
  
  public boolean draw(Graphics g, boolean full) {
    return super.draw(g, full);
  }
  
  public int tickDelta() {
    return focused? 1000/60 : 1000/5;
  }
  
  public void stopped() {
    for (ChatUser u : users) u.close();
  }
  
  
  
  private MsgNode cHover, pHover;
  private boolean newHover;
  public void hovered(MsgNode n) {
    cHover = n;
    newHover = true;
  }
  
  
  public void removeAllMessages() {
    newHover = true;
    msgs.clearCh();
  }
  public MsgNode createMessage(ChatEvent cm) {
    MsgNode r = new MsgNode(msgs.ctx.shadow(), cm.type(), cm);
    r.ctx.id("user").replace(0, new UserTagNode(this, cm));
    return r;
  }
  
  private String lastTimeStr;
  public String currDelta() {
    if (!gc.getProp("chat.timeSinceLast").b()) return null;
    if (msgs.ch.sz==0 || !(view instanceof Chatroom)) return null;
    int n = msgs.ch.sz-1;
    Node a = msgs.ch.get(n);
    if (!(a instanceof MsgNode)) { n--; if(n>=0) a = msgs.ch.get(n); }
    if (!(a instanceof MsgNode)) return null;
    ChatEvent at = ((MsgNode) a).msg;
    float h = Duration.between(at.time, Instant.now()).getSeconds()/3600f;
    if (h<1) return null;
    return timeDelta(h);
  }
  
  EnumProp lastTimeProp = new EnumProp("lastTime");
  public void removeLastTime() {
    nextTimeUpdate = 0;
    if (msgs.ch.sz>0) {
      Node n = msgs.ch.peek();
      int pos = n.id("infoType");
      if (pos>=0 && n.vs[pos]==lastTimeProp) msgs.ch.removeAt(msgs.ch.sz - 1);
    }
  }
  public void insertLastTime() { // only called once, in tick
    String c = currDelta();
    if (!Objects.equals(c, lastTimeStr)) {
      lastTimeStr = c;
      removeLastTime();
      if (c==null) return;
      msgs.add(makeInfo(lastTimeProp, "chat.info.textP", new StringNode(ctx, "The last message was posted "+c+" ago.")));
    }
  }
  
  public Node makeInfo(EnumProp type, String cfg, Node body) {
    Node n = ctx.make(gc.getProp(cfg).gr());
    n.ctx.id("body").add(body);
    
    Node msg = ctx.make(gc.getProp("chat.info.mainP").gr());
    msg.set(msg.id("infoType"), type);
    msg.ctx.id("body").add(n);
    return msg;
  }
  
  public void insertMessages(boolean atEnd, Vec<? extends ChatEvent> nds) { // expects all to be non-null
    if (nds.sz==0) return;
    if (atEnd) removeLastTime();
    newHover = true;
    Vec<Node> prep = new Vec<>();
    Node p = null;
    for (ChatEvent c : nds) {
      Node a = c.show(false);
      if (p!=null) {
        Node sep = handlePair(p, a);
        if (sep!=null) prep.add(sep);
      }
      p = prep.add(a);
    }
    if (msgs.ch.sz>0) {
      if (atEnd) {
        Node sep = handlePair(msgs.ch.peek(), prep.get(0));
        if (sep!=null) prep.insert(0, sep);
      } else {
        Node sep = handlePair(prep.peek(), msgs.ch.get(0));
        if (sep!=null) prep.add(sep);
      }
    }
    msgs.insert(atEnd? msgs.ch.sz : 0, prep);
    if (!atEnd) msgsScroll.ignoreStart();
    if ( atEnd) msgsScroll.ignoreEnd();
  }
  
  
  public boolean atEnd() {
    return msgsScroll.atEnd(5);
  }
  public void addMessage(ChatEvent cm, boolean live) {
    removeLastTime();
    newHover = true;
    Node msg = cm.show(live);
    boolean atEnd = atEnd();
    if (msgs.ch.sz>0) {
      Node sep = handlePair(msgs.ch.peek(), msg);
      if (sep!=null) msgs.add(sep);
    }
    msgs.add(msg);
    if (atEnd && toLast==0) toLast = 1;
    if (atEnd) msgsScroll.ignoreEnd();
  }
  public void updMessage(Node msg, ChatEvent cm, Node body, boolean live) {
    boolean end = atEnd();
    newHover = true;
    if (cm.edited) {
      Node n = msg.ctx.id("edit");
      if (n.ch.sz==0) n.add(n.ctx.make(n.gc.getProp("chat.icon.editedP").gr()));
    }
    Node n = msg.ctx.id("body");
    Node nb;
    if (cm.target!=null) {
      nb = new STextNode(ctx, Node.KS_NONE, Node.VS_NONE);
      nb.add(new LinkBtn(ctx, nb.ctx.makeHere(n.gc.getProp("chat.icon.replyP").gr()), cm));
      nb.add(body);
    } else if (body instanceof InlineNode) {
      nb = new STextNode(body);
    } else nb = body;
    nb.add(new InlineNode.LineEnd(ctx, false));
    n.replace(1, nb);
    if (end) toLast = Math.max(toLast, live? 1 : 2);
  }
  
  public void unreadChanged() {
    updateTitle();
  }
  public void updateTitle() {
    Chatroom room = room();
    
    int otherNew = 0;
    int currentNew = room==null?0:room.unread();
    boolean ping = false;
    for (ChatUser u : users) {
      for (Chatroom r : u.rooms()) {
        if (r!=room) otherNew+= r.unread();
        ping|= r.ping;
      }
    }
    
    String ct;
    if (otherNew!=0 || currentNew!=0 || ping) {
      StringBuilder b = new StringBuilder("(");
      if (currentNew!=0) b.append(currentNew);
      if (ping) b.append("*");
      if (otherNew!=0) b.append("+").append(otherNew);
      b.append(") ");
      ct = b.toString();
    }
    
    else ct = "";
    if (view==null) setTitle(ct+"chat");
    else setTitle(ct+view.title());
  }
  
  public void updateUnread() {
    if (view instanceof Chatroom) ((Chatroom) view).unreadChanged();
    else unreadChanged();
  }
  
  
  private boolean saveRequested;
  public void requestSave() {
    saveRequested = true;
  }
  
  EnumProp laterProp = new EnumProp("later");
  // private static final SimpleDateFormat df = new SimpleDateFormat("E, MM d yyyy");
  private static final DateTimeFormatter df = DateTimeFormatter.ofPattern("EE, d MMM yyyy");
  public Node handlePair(Node a, Node b) {
    if (!(a instanceof MsgNode) || !(b instanceof MsgNode)) return null;
    ChatEvent at = ((MsgNode) a).msg;
    ChatEvent bt = ((MsgNode) b).msg;
    Duration d = Duration.between(at.time, bt.time);
    float h = d.getSeconds()/3600f;
    
    boolean between = h>=1 && gc.getProp("chat.timeBetween").b();
    
    LocalDate newDate = null;
    if (gc.getProp("chat.logDate").b()) {
      LocalDate adt = Time.localDateTime(at.time).toLocalDate();
      LocalDate bdt = Time.localDateTime(bt.time).toLocalDate();
      if (!adt.equals(bdt)) newDate = bdt;
    }
  
    boolean merge = h<5f/60 && at.userEq(bt) && gc.getProp("chat.mergeMessages").b() && newDate==null;
    ((UserTagNode) b.ctx.id("user").ch.get(0)).setVis(!merge);
    Node padU = b.ctx.id("padU");
    padU.set(padU.id("u"), merge? new LenProp(gc, 0, "px") : gc.getProp("chat.msg.sep"));
    if (between || newDate!=null) {
      if (newDate==null) return makeInfo(laterProp, "chat.info.textP", new StringNode(ctx, timeDelta(h)+" later..."));
      else return makeInfo(laterProp, "chat.info.titleP", new StringNode(ctx, df.format(newDate)));
    }
    return null;
  }
  
  private String timeDelta(float h) {
    int i = (int) h;
    String k = "hour";
    if (h>=24) {
      i/= 24;
      k = "day";
    }
    return i+" "+k+(i==1?"":"s");
  }
  
  
  static class LinkBtn extends PadCNode {
    private final ChatEvent m;
    public LinkBtn(Ctx ctx, Node ch, ChatEvent m) {
      super(ctx, ch, 0, .05f, .1f, .1f);
      this.m = m;
    }
    public void hoverS() { ctx.win().setCursor(CursorType.HAND); }
    public void hoverE() { ctx.win().setCursor(CursorType.REGULAR); }
  
    public void mouseStart(int x, int y, Click c) {
      super.mouseStart(x, y, c);
      c.register(this, x, y);
    }
    public void mouseTick(int x, int y, Click c) { c.onClickEnd(); }
    public void mouseUp(int x, int y, Click c) {
      if (gc.isClick(c)) m.toTarget();
    }
  }
  
  
  
  public boolean chatKey(Key key, int scancode, KeyAction a) {
    if (view==null) return false;
    return view.key(key, scancode, a);
  }
  
  public boolean chatTyped(int codepoint) {
    if (view==null) return false;
    return view.typed(codepoint);
  }
  
  public void focused() { super.focused();
    updateUnread();
  }
  
  public boolean key(Key key, int scancode, KeyAction a) {
    String name = gc.keymap(key, a, "chat");
    switch (name) {
      case "fontPlus":  gc.setEM(gc.em+1); return true;
      case "fontMinus": gc.setEM(gc.em-1); return true;
      case "hideCurrent":
        if (view instanceof Chatroom) ((Chatroom) view).toggleHide();
        return true;
      case "hideGlobal":
        globalHidden^= true;
        for (ChatUser u : users) for (Chatroom r : u.rooms()) r.unreadChanged();
        updInfo();
        return true;
      case "roomUp": case "roomDn": {
        boolean up = name.equals("roomUp");
        Chatroom room = room();
        if (room==null) {
          for (ChatUser u : users) {
            Vec<Chatroom> rs = u.rooms();
            if (rs.sz!=0) {
              toRoom(rs.get(0));
              return true;
            }
          }
          return true;
        }
        ChatUser u = room.user();
        Vec<Chatroom> rl = u.rooms();
        int y = rl.indexOf(room);
        if (up && y==0) {
          int yA = users.indexOf(u)-1;
          while (yA>=0 && users.get(yA).rooms().sz==0) yA--;
          if (yA>=0 && users.get(yA).rooms().sz!=0) toRoom(users.get(yA).rooms().peek());
          return true;
        }
        if (!up && y==rl.sz-1) {
          int yA = users.indexOf(u)+1;
          while (yA<users.sz && users.get(yA).rooms().sz==0) yA++;
          if (yA<users.sz && users.get(yA).rooms().sz!=0) toRoom(users.get(yA).rooms().get(0));
          return true;
        }
        toRoom(rl.get(up? y-1 : y+1));
        return true;
      }
    }
    
    if (a.typed) {
      if (key.k_esc()) {
      }
      if (key.k_f5()) {
        gc.reloadCfg();
        return true;
      }
    }
    if (a.press) {
      if (key.k_f12()) { createTools(); return true; }
      if (key.k_f2()) { StringNode.PARAGRAPH_TEXT^= true; gc.cfgUpdated(); return true; }
    }
    if (super.key(key, scancode, a)) return true;
    if (a.press && !key.isModifier()) focus(input);
    return super.key(key, scancode, a);
  }
  
  public static void main(String[] args) {
    Windows.setManager(Windows.Manager.JWM);
    
    Windows.start(mgr -> {
      BaseCtx ctx = Ctx.newCtx();
      ctx.put("msgBorder", MsgBorderNode::new);
      ctx.put("hideOverflow", HideOverflowNode::new);
      
      GConfig gc = GConfig.newConfig(gc0 -> {
        gc0.addCfg(() -> Tools.readRes("chat.dzcfg"));
        gc0.addCfg(() -> {
          if (Files.exists(LOCAL_CFG)) return Tools.readFile(LOCAL_CFG);
          return "";
        });
      });
      
      mgr.start(new ChatMain(gc, ctx, args.length==0? DEFAULT_PROFILE : args[0], gc.getProp("chat.ui").gr()));
    });
  }
  
  public int colMyNick, colMyPill;
  public int[] colOtherNicks;
  public int[] colOtherPills;
  public Paint msgBorder;
  private static int[] colorList(Prop p) {
    if (p instanceof ColProp) {
      return new int[]{p.col()};
    } else {
      Vec<PNode> l = p.gr().ch;
      int[] res = new int[l.sz];
      for (int i = 0; i < res.length; i++) {
        int c = Token.ColorTok.parsePrefixed(((PNodeStr) l.get(i)).s);
        res[i] = c;
      }
      return res;
    }
  }
  @Override public void cfgUpdated() { super.cfgUpdated();
    colMyNick = gc.getProp("chat.userCols.myNick").col();
    colMyPill = gc.getProp("chat.userCols.myPill").col();
    colOtherNicks = colorList(gc.getProp("chat.userCols.otherNicks"));
    colOtherPills = colorList(gc.getProp("chat.userCols.otherPills"));
    
    msgBorder = new Paint().setColor(gc.getProp("chat.msg.border").col()).setPathEffect(PathEffect.makeDash(new float[]{1, 1}, 0));
    if (gc.getProp("chat.preview.enabled").b()) {
      input.setLang(MDLang.makeLanguage(this, input));
    } else input.setLang(gc.langs().defLang);
  }
}