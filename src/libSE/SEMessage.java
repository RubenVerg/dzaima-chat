package libSE;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;

public class SEMessage {
  public final long id;
  public final Instant timeStamp;
  public final OptionalLong replyId;
  public final String content;
  public final String plainContent;
  public final SERoom room;
  public final long roomId;
  public final long userId;
  public final String userName;
  public final int stars;
  public final int ownerStars;
  public final int messageEdits;
  public final boolean isMention;

  public SEMessage(long id, Instant timeStamp, OptionalLong replyId, String content, SERoom room, long roomId, long userId, String userName, int stars, int ownerStars, int messageEdits, boolean isMention) {
    this.id = id;
    this.timeStamp = timeStamp;
    this.replyId = replyId;
    this.content = Objects.nonNull(content) ? Jsoup.parse(content).select("body").html() : null;
    this.plainContent = Objects.nonNull(content) ? Entities.unescape(Jsoup.parse(content).select("body").text()) : null;
    this.room = room;
    this.roomId = roomId;
    this.userId = userId;
    this.userName = userName;
    this.stars = stars;
    this.ownerStars = ownerStars;
    this.messageEdits = messageEdits;
    this.isMention = isMention;
  }

  public SEMessage(SEEvent.MessageEvent ev, boolean isMention, SERoom room) {
    this(
        ev.messageId,
        ev.timeStamp,
        ev.parentId,
        ev.content,
        room,
        ev.roomId,
        ev.userId,
        ev.userName,
        ev.messageStars,
        ev.messageOwnerStars,
        ev.messageEdits,
        isMention
    );
  }
}
