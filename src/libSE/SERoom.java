package libSE;

import dzaima.utils.JSON;
import dzaima.utils.Pair;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class SERoom {
  public final String server;
  public final CookieStore cookieStore;
  public final String fkey;
  public final long userId;
  public final long roomId;
  public String roomName;
  final List<SEEventHandler> eventHandlers = new ArrayList<>();
  private final CloseableHttpClient client;
  final CountDownLatch connected = new CountDownLatch(1);
  final AtomicBoolean shouldExit = new AtomicBoolean(false);
  final CountDownLatch hasExited = new CountDownLatch(1);
  public final List<SEMessage> messages = new ArrayList<>();
  final Lock messagesLock = new ReentrantLock();
  final List<MessageHandler> messageHandlers = new ArrayList<>();

  private class DefaultEventHandler extends SEEventHandler {
    private void receiveMessage(SEEvent.MessageEvent ev, boolean isMention, Consumer<SEMessage> what) {
      messagesLock.lock();
      try {
        if (messages.stream().noneMatch(m -> m.id == ev.messageId)) {
          final var message = new SEMessage(ev, isMention, SERoom.this);
          messages.add(message);
          what.accept(message);
        }
      } finally {
        messagesLock.unlock();
      }
    }

    @Override
    public void onMention(SEEvent.MessageEvent ev) {
      try {
        final var data = MultipartEntityBuilder.create()
            .addTextBody("id", Long.toString(ev.id))
            .addTextBody("fkey", fkey)
            .build();
        Utils.post(client, "https://" + server + "/messages/ack", data);
        receiveMessage(ev, true, message -> {
          messageHandlers.forEach(h -> h.onMessage(message));
          messageHandlers.forEach(h -> h.onMention(message));
        });
      } catch (Exception ignored) { }
    }

    @Override
    public void onMessage(SEEvent.MessageEvent ev) {
      receiveMessage(ev, false, message -> messageHandlers.forEach(h -> h.onMessage(message)));
    }

    @Override
    public void onReply(SEEvent.MessageEvent ev) {
      receiveMessage(ev, true, message -> messageHandlers.forEach(h -> h.onMessage(message)));
    }

    @Override
    void onEdit(SEEvent.MessageEvent ev) {
      messagesLock.lock();
      try {
        messages.stream()
            .filter(m -> m.id == ev.messageId)
            .findFirst()
            .ifPresent(oldMessage -> {
              final var newMessage = new SEMessage(ev, oldMessage.isMention, SERoom.this);
              messages.set(messages.indexOf(oldMessage), newMessage);
              messageHandlers.forEach(h -> h.onEdit(oldMessage, newMessage));
            });
      } finally {
        messagesLock.unlock();
      }
    }

    @Override
    public void onDelete(SEEvent.DeleteEvent ev) {
      messagesLock.lock();
      try {
        messages.stream()
            .filter(m -> m.id == ev.messageId)
            .findFirst()
            .ifPresent(message -> {
              messages.set(messages.indexOf(message), new SEMessage(
                message.id,
                message.timeStamp,
                message.replyId,
                null,
                message.room,
                message.userId,
                message.userName,
                message.stars,
                message.ownerStars,
                message.messageEdits,
                false));
              messageHandlers.forEach(h -> h.onDelete(message));
            });
      } finally {
        messagesLock.unlock();
      }
    }
  }

  public static abstract class MessageHandler {
    public void onMessage(SEMessage message) { }
    public void onEdit(SEMessage oldMessage, SEMessage newMessage) { }
    public void onDelete(SEMessage message) { }
    public void onMention(SEMessage message) { }
    public void onLoadOldMessage(SEMessage message) { }
  }

  public SERoom(String server, CookieStore store, String fkey, long userId, long roomId) {
    this.server = server;
    this.cookieStore = store;
    this.fkey = fkey;
    this.userId = userId;
    this.roomId = roomId;
    this.client = HttpClientBuilder.create()
        .setDefaultHeaders(List.of(new BasicHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; user " + userId + "; dzaima/chat; +http://github.com/dzaima/chat)")))
        .setDefaultCookieStore(cookieStore)
        .build();
    eventHandlers.add(new DefaultEventHandler());
  }

  @Override
  public int hashCode() {
    return Long.hashCode(roomId);
  }

  public void shutdown() {
    try {
      Utils.get(client, "https://" + server + "/chats/leave/" + roomId);
      client.close();
    } catch (Exception ex) {
      SEAccount.debug(Utils.exToString(ex));
    }
  }

  private String getSocketUrl() throws IOException, ParseException {
    final var data = MultipartEntityBuilder.create()
        .addTextBody("fkey", fkey)
        .addTextBody("roomid", Long.toString(roomId))
        .build();
    final var res = Utils.post(client, "https://" + server + "/ws-auth", data);
    return JSON.parse(res).str("url") + "?l=" + Long.toString(Instant.now().getEpochSecond());
  }

  public void getInfo() {
    try {
      final var res = Utils.getHtml(client, "https://" + server + "/rooms/" + roomId);
      roomName = res.select("#roomname").text();
    } catch (Exception ex) {
      throw new SEException.OperationFailedError(ex);
    }
  }

  void loop() throws InterruptedException, URISyntaxException {
    WSClient webSocket = null;
    try {
      connectSocket: while (!shouldExit.get()) {
        String url = null;
        while (true) {
          try {
            url = getSocketUrl();
            break;
          } catch (IOException | ParseException ex) {
            SEAccount.debug(Utils.exToString(ex));
            Thread.sleep(3000);
          }
        }
        webSocket = new WSClient(new URI(url), Map.of("Origin", "http://" + server));
        webSocket.connect();
        connected.countDown();
        Instant connectedAt = Instant.now();
        while (!shouldExit.get()) {
          try {
            final var data = webSocket.waitForMessage(shouldExit);
            if (Objects.nonNull(data) && !data.isEmpty()) {
              process(JSON.parseObj(data));
            }
          } catch (WSClient.ClosedException ex) {
            SEAccount.debug(Utils.exToString(ex));
            continue connectSocket;
          } catch (WSClient.TimeoutException ignored) {
            continue connectSocket;
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
          if (Duration.between(Instant.now(), connectedAt).compareTo(Duration.ofHours(2)) > 0) continue connectSocket;
        }
      }
    } finally {
      if (Objects.nonNull(webSocket) && webSocket.isOpen()) webSocket.close();
      while (Objects.isNull(webSocket) || !webSocket.isClosed()) Thread.sleep(100);
      shutdown();
      hasExited.countDown();
    }
  }

  private void process(JSON.Obj data) {
    if (data.has("r" + roomId)) {
      JSON.Obj r = data.obj("r" + roomId);
      if (r.orderedKeys().length != 0 && r.has("e")) {
        for (final var ev_ : r.arr("e")) {
          if (ev_ instanceof JSON.Obj ev) {
            SEAccount.debug(ev.toString(2));
            final var eventType = Arrays.stream(SEEvent.EventType.values()).filter(evt -> evt.id == ev.getInt("event_type")).findFirst();
            if (eventType.isPresent()) {
              switch (eventType.get()) {
                case SEEvent.EventType.MESSAGE: eventHandlers.forEach(h -> h.onMessage(new SEEvent.MessageEvent(ev))); break;
                case SEEvent.EventType.EDIT: eventHandlers.forEach(h -> h.onEdit(new SEEvent.MessageEvent(ev))); break;
                case SEEvent.EventType.JOIN: eventHandlers.forEach(h -> h.onJoin(new SEEvent(ev))); break;
                case SEEvent.EventType.LEAVE: eventHandlers.forEach(h -> h.onLeave(new SEEvent(ev))); break;
                case SEEvent.EventType.NAME_CHANGE: eventHandlers.forEach(h -> h.onNameChange(new SEEvent(ev))); break;
                case SEEvent.EventType.MESSAGE_STARRED: eventHandlers.forEach(h -> h.onMessageStarred(new SEEvent(ev))); break;
                case SEEvent.EventType.DEBUG: eventHandlers.forEach(h -> h.onDebug(new SEEvent(ev))); break;
                case SEEvent.EventType.MENTION: eventHandlers.forEach(h -> h.onMention(new SEEvent.MessageEvent(ev))); break;
                case SEEvent.EventType.FLAG: eventHandlers.forEach(h -> h.onFlag(new SEEvent(ev))); break;
                case SEEvent.EventType.DELETE: eventHandlers.forEach(h -> h.onDelete(new SEEvent.DeleteEvent(ev))); break;
                case SEEvent.EventType.FILE_UPLOAD: eventHandlers.forEach(h -> h.onFileUpload(new SEEvent(ev))); break;
                case SEEvent.EventType.MODERATOR_FLAG: eventHandlers.forEach(h -> h.onModeratorFlag(new SEEvent(ev))); break;
                case SEEvent.EventType.SETTINGS_CHANGED: eventHandlers.forEach(h -> h.onSettingsChanged(new SEEvent(ev))); break;
                case SEEvent.EventType.GLOBAL_NOTIFICATION: eventHandlers.forEach(h -> h.onGlobalNotification(new SEEvent(ev))); break;
                case SEEvent.EventType.ACCESS_CHANGED: eventHandlers.forEach(h -> h.onAccessChanged(new SEEvent(ev))); break;
                case SEEvent.EventType.USER_NOTIFICATION: eventHandlers.forEach(h -> h.onUserNotification(new SEEvent(ev))); break;
                case SEEvent.EventType.INVITATION: eventHandlers.forEach(h -> h.onInvitation(new SEEvent(ev))); break;
                case SEEvent.EventType.REPLY: eventHandlers.forEach(h -> h.onReply(new SEEvent.MessageEvent(ev))); break;
                case SEEvent.EventType.MESSAGE_MOVED_OUT: eventHandlers.forEach(h -> h.onMessageMovedOut(new SEEvent(ev))); break;
                case SEEvent.EventType.MESSAGE_MOVED_IN: eventHandlers.forEach(h -> h.onMessageMovedIn(new SEEvent(ev))); break;
                case SEEvent.EventType.TIME_BREAK: eventHandlers.forEach(h -> h.onTimeBreak(new SEEvent(ev))); break;
                case SEEvent.EventType.FEED_TICKER: eventHandlers.forEach(h -> h.onFeedTicker(new SEEvent(ev))); break;
                case SEEvent.EventType.USER_SUSPENSION: eventHandlers.forEach(h -> h.onUserSuspension(new SEEvent(ev))); break;
                case SEEvent.EventType.USER_MERGE: eventHandlers.forEach(h -> h.onUserMerge(new SEEvent(ev))); break;
                case SEEvent.EventType.USER_NAME_OR_AVATAR_CHANGE: eventHandlers.forEach(h -> h.onUserNameOrAvatarChange(new SEEvent(ev))); break;
              }
            } else {
              eventHandlers.forEach(h -> h.onUnknown(ev));
            }
          }
        }
      }
    }
  }

  public void register(SEEventHandler handler) {
    eventHandlers.add(handler);
  }

  public void unregister(SEEventHandler handler) {
    eventHandlers.remove(handler);
  }

  public void register(MessageHandler handler) {
    messageHandlers.add(handler);
  }

  public void unregister(MessageHandler handler) {
    messageHandlers.remove(handler);
  }

  private static final Pattern RETRY_PATTERN = Pattern.compile("You can perform this action again in (\\d+)");

  private String request(String url, MultipartEntityBuilder dataBuilder) {
    try {
      final var req = new HttpPost(url);
      req.setEntity(dataBuilder.addTextBody("fkey", fkey).build());
      req.setHeader(new BasicHeader(HttpHeaders.REFERER, "https://" + server + "/rooms/" + roomId));
      while (true) {
        final var res = client.execute(req, response -> {
          final var entity = response.getEntity();
          if (Objects.isNull(entity)) return new Pair<>("", response.getCode());
          return new Pair<>(EntityUtils.toString(response.getEntity()), response.getCode());
        });
        if (res.b == 409) {
          final var matcher = RETRY_PATTERN.matcher(res.a);
          if (matcher.find()) {
            final var retryAfter = Integer.parseInt(matcher.group(1));
            Thread.sleep(retryAfter * 1000L + 100L);
          } else {
            SEAccount.debug("Could not extract rate limit retry amount");
            Thread.sleep(1000);
          }
        } else if (res.b != 200) {
          throw new SEException.OperationFailedError("Request failed: " + res.b + "\n" + res.a);
        } else {
          return res.a;
        }
      }
    } catch (Exception ex) {
      throw new SEException.OperationFailedError(ex);
    }
  }

  public void bookmark(long start, long end, String title) {
    try {
      final var res = JSON.parseObj(request("https://" + server + "/conversation/new", MultipartEntityBuilder.create()
          .addTextBody("roomId", Long.toString(roomId))
          .addTextBody("firstMessageId", Long.toString(start))
          .addTextBody("lastMessageId", Long.toString(end))
          .addTextBody("title", title)
      ));
      if (!res.get("ok").bool(false)) {
        throw new SEException.OperationFailedError(res.toString(2));
      }
    } catch (JSON.JSONException ex) {
      throw new SEException.OperationFailedError(ex);
    }
  }

  public void removeBookmark(String title) {
    final var response = request("https://" + server + "/conversation/delete/" + roomId + "/" + title, MultipartEntityBuilder.create());
    if (!response.equals("ok")) {
      throw new SEException.OperationFailedError(response);
    }
  }

  public long send(String message) {
    assert !message.isEmpty() : "Message cannot be empty!";
    try {
      final var res = JSON.parseObj(request("https://" + server + "/chats/" + roomId + "/messages/new", MultipartEntityBuilder.create()
          .addTextBody("text", message)));
      return res.get("id").asLong();
    } catch (JSON.JSONException ex) {
      throw new SEException.OperationFailedError(ex);
    }
  }

  public long reply(long target, String message) {
    return send(":" + target + " " + message);
  }

  public void edit(long messageId, String newMessage) {
    assert !newMessage.isEmpty() : "Message cannot be empty!";
    final var response = request("https://" + server + "/messages/" + messageId, MultipartEntityBuilder.create()
        .addTextBody("text", newMessage));
    if (!response.equals("ok")) {
      throw new SEException.OperationFailedError(response);
    }
  }

  public void delete(long messageId) {
    final var response = request("https://" + server + "/messages/" + messageId + "/delete", MultipartEntityBuilder.create());
    if (!response.equals("ok")) {
      throw new SEException.OperationFailedError(response);
    }
  }

  public void star(long messageId) {
    final var response = request("https://" + server + "/messages/" + messageId + "/star", MultipartEntityBuilder.create());
    if (!response.equals("ok")) {
      throw new SEException.OperationFailedError(response);
    }
  }

  public void pin(long messageId) {
    final var response = request("https://" + server + "/messages/" + messageId + "/owner-star", MultipartEntityBuilder.create());
    if (!response.equals("ok")) {
      throw new SEException.OperationFailedError(response);
    }
  }

  public void unpin(long messageId) {
    final var response = request("https://" + server + "/messages/" + messageId + "/unowner-star", MultipartEntityBuilder.create());
    if (!response.equals("ok")) {
      throw new SEException.OperationFailedError(response);
    }
  }

  public void clearStars(long messageId) {
    final var response = request("https://" + server + "/messages/" + messageId + "/unstar", MultipartEntityBuilder.create());
    if (!response.equals("ok")) {
      throw new SEException.OperationFailedError(response);
    }
  }

  public void moveMessages(Collection<Long> messages, long targetRoom) {
    final var response = request("https://" + server + "/admin/movePosts/" + roomId, MultipartEntityBuilder.create()
        .addTextBody("to", Long.toString(targetRoom))
        .addTextBody("ids", messages.stream().map(id -> Long.toString(id)).collect(Collectors.joining(","))));
    if (!response.equals(Integer.toString(messages.size()))) {
      throw new SEException.OperationFailedError(response);
    }
  }

  public List<Pair<Long, String>> pingable() {
    try {
      final var response = JSON.parseArr(Utils.get(client, "https://" + server + "/rooms/pingable/" + roomId + "?_=" + Instant.now().getEpochSecond() * 1000));
      return StreamSupport.stream(response.arrs().spliterator(), false).map(arr -> new Pair<>(arr.get(0).asLong(), arr.get(1).str())).toList();
    } catch (Exception ex) {
      throw new SEException.OperationFailedError(ex);
    }
  }

  public List<SEMessage> previousMessages() {
    return previousMessages(100);
  }

  public List<SEMessage> previousMessages(long count) {
    assert count > 0 && count <= 100;
    messagesLock.lock();
    try {
      String response;
      if (messages.isEmpty()) {
        response = request("https://" + server + "/chats/" + roomId + "/events", MultipartEntityBuilder.create()
            .addTextBody("since", "0")
            .addTextBody("mode", "Messages")
            .addTextBody("msgCount", Long.toString(count)));
      } else {
        final var firstMessage = messages.getFirst().id;
        response = request("https://" + server + "/chats/" + roomId + "/events?before=" + firstMessage + "&mode=Messages&msgCount=" + count, MultipartEntityBuilder.create());
      }
      return StreamSupport.stream(JSON.parseObj(response).arr("events").objs().spliterator(), false).map(obj -> {
        final var message = new SEMessage(new SEEvent.MessageEvent(obj), false, SERoom.this); // TODO isMention
        final var messageRightAfter = messages.stream().filter(m -> m.id > message.id).findFirst();
        messages.add(messageRightAfter.map(messages::indexOf).orElse(messages.size()), message);
        messageHandlers.forEach(h -> h.onLoadOldMessage(message));
        return message;
      }).toList();
    } catch (Exception ex) {
      throw new SEException.OperationFailedError(ex);
    } finally {
      messagesLock.unlock();
    }
  }
}
