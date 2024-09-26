package libSE;

import dzaima.utils.JSON;

public abstract class SEEventHandler {
  void onMessage(SEEvent.MessageEvent e) { }
  void onEdit(SEEvent.MessageEvent e) { }
  void onJoin(SEEvent e) { }
  void onLeave(SEEvent e) { }
  void onNameChange(SEEvent e) { }
  void onMessageStarred(SEEvent e) { }
  void onDebug(SEEvent e) { }
  void onMention(SEEvent.MessageEvent e) { }
  void onFlag(SEEvent e) { }
  void onDelete(SEEvent.DeleteEvent e) { }
  void onFileUpload(SEEvent e) { }
  void onModeratorFlag(SEEvent e) { }
  void onSettingsChanged(SEEvent e) { }
  void onGlobalNotification(SEEvent e) { }
  void onAccessChanged(SEEvent e) { }
  void onUserNotification(SEEvent e) { }
  void onInvitation(SEEvent e) { }
  void onReply(SEEvent.MessageEvent e) { }
  void onMessageMovedOut(SEEvent e) { }
  void onMessageMovedIn(SEEvent e) { }
  void onTimeBreak(SEEvent e) { }
  void onFeedTicker(SEEvent e) { }
  void onUserSuspension(SEEvent e) { }
  void onUserMerge(SEEvent e) { }
  void onUserNameOrAvatarChange(SEEvent e) { }

  void onUnknown(JSON.Obj data) { }
}
