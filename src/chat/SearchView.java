package chat;

import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.*;
import dzaima.ui.node.types.editable.EditNode;

public abstract class SearchView extends View {
  public final ChatMain m;
  public final View originalView;
  public final Node n;
  
  public SearchView(ChatMain m, View originalView) {
    this.m = m;
    this.originalView = originalView;
    n = m.ctx.make(m.gc.getProp("chat.search.ui").gr());
    for (String c : new String[]{"showContext", "allRooms", /*"serverSide",*/ "caseSensitive", "exactMatch"}) {
      ((CheckboxNode) n.ctx.id(c)).setFn(n -> updatedBtns());
    }
    ((EditNode) n.ctx.id("text")).setFn(n -> {
      runSearch();
      return true;
    });
    ((BtnNode) n.ctx.id("closeBtn")).setFn(n -> close());
  }
  
  public boolean allRooms() { return ((CheckboxNode) n.ctx.id("allRooms")).enabled; }
  public boolean showContext() { return ((CheckboxNode) n.ctx.id("showContext")).enabled; }
  public boolean serverSide() { return false; }
  // public boolean serverSide() { return ((CheckboxNode) n.ctx.id("serverSide")).enabled; }
  public boolean caseSensitive() { return ((CheckboxNode) n.ctx.id("caseSensitive")).enabled; }
  public boolean exactMatch() { return ((CheckboxNode) n.ctx.id("exactMatch")).enabled; }
  public int contextSize() { return 2;  }
  
  public Chatroom room() {
    return originalView.room();
  }
  
  private String getText() {
    return ((EditNode) n.ctx.id("text")).getAll();
  }
  public abstract void processSearch(String text);
  public void runSearch() {
    String text = getText();
    processSearch(text);
  }
  
  public void updatedBtns() {
    if (!serverSide()) runSearch();
  }
  String pSearch = "";
  
  public void viewTick() {
    if (!serverSide()) {
      String nSearch = getText();
      if (nSearch.equals(pSearch)) return;
      pSearch = nSearch;
      runSearch();
    }
  }
  
  public void show() {
    Node p = m.ctx.id("searchPlace");
    p.clearCh();
    p.add(n);
    textInput().focusMe();
    m.setCurrentName(title());
  }
  
  public void hide() {
    m.ctx.id("searchPlace").clearCh();
  }
  
  public String title() {
    return "search";
  }
  
  private void close() {
    m.toView(originalView);
  }
  
  private void toggleCheckbox(String s) {
    CheckboxNode c = (CheckboxNode) n.ctx.id(s);
    c.toggle();
    c.focusMe();
  }
  public boolean key(Key key, int scancode, KeyAction a) {
    switch (m.gc.keymap(key, a, "chat.search")) {
      case "showContext": toggleCheckbox("showContext"); return true;
      case "allRooms": toggleCheckbox("allRooms"); return true;
      // case "serverSide": toggleCheckbox("serverSide"); return true;
      case "caseSensitive": toggleCheckbox("caseSensitive"); return true;
      case "exactMatch": toggleCheckbox("exactMatch"); return true;
      case "cancel": close(); return true;
      case "focusField": n.ctx.id("text").focusMe(); return true;
    }
    return false;
  }
  
  public boolean typed(int codepoint) {
    return false;
  }
  
  public String asCodeblock(String s) {
    return originalView.asCodeblock(s);
  }
  
  public Node textInput() {
    return n.ctx.id("text");
  }
}
