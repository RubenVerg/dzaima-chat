package libSE;

import dzaima.utils.JSON;

import java.time.Instant;
import java.util.OptionalLong;

public class SEEvent {
  enum EventType {
    MESSAGE(1),
    EDIT(2),
    JOIN(3),
    LEAVE(4),
    NAME_CHANGE(5),
    MESSAGE_STARRED(6),
    DEBUG(7),
    MENTION(8),
    FLAG(9),
    DELETE(10),
    FILE_UPLOAD(11),
    MODERATOR_FLAG(12),
    SETTINGS_CHANGED(13),
    GLOBAL_NOTIFICATION(14),
    ACCESS_CHANGED(15),
    USER_NOTIFICATION(16),
    INVITATION(17),
    REPLY(18),
    MESSAGE_MOVED_OUT(19),
    MESSAGE_MOVED_IN(20),
    TIME_BREAK(21),
    FEED_TICKER(22),
    USER_SUSPENSION(29),
    USER_MERGE(30),
    USER_NAME_OR_AVATAR_CHANGE(34);

    public final int id;

    EventType(int id) {
      this.id = id;
    }
  }

  public final int eventType;
  public final Instant timeStamp;
  public final long id;

  public SEEvent(int eventType, Instant timeStamp, long id) {
    this.eventType = eventType;
    this.timeStamp = timeStamp;
    this.id = id;
  }

  public SEEvent(JSON.Obj data) {
    this.eventType = data.get("event_type").asInt();
    this.timeStamp = Instant.ofEpochSecond(data.get("time_stamp").asLong());
    this.id = data.get("id", new JSON.Num(0)).asLong();
  }

  public static class RoomEvent extends SEEvent {
    public final long roomId;
    public final String roomName;

    public RoomEvent(int eventType, Instant timeStamp, long id, long roomId, String roomName) {
      super(eventType, timeStamp, id);
      this.roomId = roomId;
      this.roomName = roomName;
    }

    public RoomEvent(JSON.Obj data) {
      super(data);
      this.roomId = data.get("room_id").asLong();
      this.roomName = data.get("room_name", new JSON.Str("")).str();
    }
  }

  public static class MessageEvent extends RoomEvent {
    public final String content;
    public final long messageId;
    public final long userId;
    public final String userName;
    public final OptionalLong parentId;
    public final Object showParent;
    public final long targetUserId;
    public final int messageStars;
    public final int messageOwnerStars;
    public final int messageEdits;

    public MessageEvent(Instant timeStamp, long id, long roomId, String roomName, String content, long mesageId, long userId, String userName, OptionalLong parentId, Object showParent, long targetUserId, int messageStars, int messageOwnerStars, int messageEdits) {
      super(1, timeStamp, id, roomId, roomName);
      this.content = content;
      this.messageId = mesageId;
      this.userId = userId;
      this.userName = userName;
      this.parentId = parentId;
      this.showParent = null;
      this.targetUserId = targetUserId;
      this.messageStars = messageStars;
      this.messageOwnerStars = messageOwnerStars;
      this.messageEdits = messageEdits;
    }

    public MessageEvent(JSON.Obj data) {
      super(data);
      this.content = data.str("content", null);
      this.messageId = data.get("message_id").asLong();
      this.userId = data.get("user_id").asLong();
      this.userName = data.get("user_name").str();
      this.parentId = data.has("parent_id") ? OptionalLong.of(data.get("parent_id").asLong()) : OptionalLong.empty();
      this.showParent = data.get("show_parent", null);
      this.targetUserId = data.getInt("target_user_id", 0);
      this.messageStars = data.getInt("message_stars", 0);
      this.messageOwnerStars = data.getInt("message_owner_stars", 0);
      this.messageEdits = data.getInt("message_edits", 0);
    }
  }

  public static class DeleteEvent extends RoomEvent {
    public final long userId;
    public final String userName;
    public final long messageId;
    public final int messageEdits;
    public final OptionalLong targetUserId;
    public final OptionalLong parentId;
    public final boolean showParent;

    public DeleteEvent(Instant timeStamp, long id, long roomId, String roomName, long userId, String userName, long messageId, int messageEdits, OptionalLong targetUserId, OptionalLong parentId, boolean showParent) {
      super(10, timeStamp, id, roomId, roomName);
      this.userId = userId;
      this.userName = userName;
      this.messageId = messageId;
      this.messageEdits = messageEdits;
      this.targetUserId = targetUserId;
      this.parentId = parentId;
      this.showParent = showParent;
    }

    public DeleteEvent(JSON.Obj data) {
      super(data);
      this.userId = data.get("user_id").asLong();
      this.userName = data.get("user_name").str();
      this.messageId = data.get("message_id").asLong();
      this.messageEdits = data.getInt("message_edits", 0);
      this.targetUserId = data.has("target_user_id") ? OptionalLong.of(data.get("target_user_id").asLong()) : OptionalLong.empty();
      this.parentId = data.has("parent_id") ? OptionalLong.of(data.get("parent_id").asLong()) : OptionalLong.empty();
      this.showParent = data.get("show_parent").bool(false);
    }
  }
}
