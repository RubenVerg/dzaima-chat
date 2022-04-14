package chat.ui;

import chat.ChatMain;
import dzaima.ui.eval.PNodeGroup;
import dzaima.ui.gui.*;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Prop;
import dzaima.utils.Tools;
import io.github.humbleui.skija.*;

public class MsgBorderNode extends Node {
  MsgNode n;
  private int bgCol;
  public MsgBorderNode(Ctx ctx, String[] ks, Prop[] vs) {
    super(ctx, ks, vs);
  }
  
  public void propsUpd() { mRedraw(); // no super call; only redraw is needed
    bgCol = vs[id("bg")].col();
  }
  
  public int minW() { return ch.get(0).minW()+2; }
  public int maxW() { return ch.get(0).maxW()+2; }
  public int minH(int w) { return ch.get(0).minH(w-2)+2; }
  public int maxH(int w) { return ch.get(0).maxH(w-2)+2; }
  protected void resized() { ch.get(0).resize(w-2, h-2, 1, 1); }
  
  public void hoverS() { hover( true); n.msg.room().m.hovered(n); }
  public void hoverE() { hover(false); n.msg.room().m.hovered(null); }
  
  public boolean on;
  public void hover(boolean on) {
    this.on = on;
    mRedraw();
  }
  
  public void mouseStart(int x, int y, Click c) {
    super.mouseStart(x, y, c);
    if (c.bR()) c.register(this, x, y);
  }
  
  public void mouseDown(int x, int y, Click c) {
    if (c.bR()) n.msg.rightClick(c);
  }
  
  public void bg(Graphics g, boolean full) {
    if (Tools.st(bgCol)) pbg(g, full);
    g.rect(0, 0, w, h, bgCol);
  }
  
  public void drawC(Graphics g) {
    if (on) {
      Paint p = n.msg.room().m.msgBorder;
      g.dashH(0, 0  , w, p);
      g.dashH(0, h-1, w, p);
      g.dashV(0  , 0, h, p);
      g.dashV(w-1, 0, h, p);
    }
  }
}