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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SERoom {
  public final String server;
  public final CookieStore cookieStore;
  public final String fkey;
  public final long userId;
  public final long roomId;
  public List<SEEventHandler> handlers = new ArrayList<>();
  private CloseableHttpClient client;
  CountDownLatch connected = new CountDownLatch(1);
  AtomicBoolean shouldExit = new AtomicBoolean(false);
  CountDownLatch hasExited = new CountDownLatch(1);

  private class MentionHandler extends SEEventHandler {
    public void onMention(SEEvent.MessageEvent ev) {
      try {
        final var data = MultipartEntityBuilder.create()
            .addTextBody("id", Long.toString(ev.id))
            .addTextBody("fkey", fkey)
            .build();
        Utils.post(client, "https://" + server + "/messages/ack", data);
      } catch (Exception ignored) { }
    }
  }

  public SERoom(String server, CookieStore store, String fkey, long userId, long roomId) {
    this.server = server;
    this.cookieStore = store;
    this.fkey = fkey;
    this.userId = userId;
    this.roomId = roomId;
    handlers.add(new MentionHandler());
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

  void loop() throws InterruptedException, URISyntaxException {
    client = HttpClientBuilder.create()
        .setDefaultHeaders(List.of(new BasicHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; user " + userId + "; dzaima/chat; +http://github.com/dzaima/chat)")))
        .setDefaultCookieStore(cookieStore)
        .build();
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
            final var data = webSocket.waitForMessage(Duration.ofSeconds(3));
            if (Objects.nonNull(data) && !data.isEmpty()) {
              process(JSON.parseObj(data));
            }
          } catch (WSClient.ClosedException ex) {
            SEAccount.debug(Utils.exToString(ex));
            break connectSocket;
          } catch (WSClient.TimeoutException ignored) {
            SEAccount.debug("Timed out, reconnecting");
            break connectSocket;
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
          if (Duration.between(Instant.now(), connectedAt).compareTo(Duration.ofHours(2)) > 0) break connectSocket;
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
                case SEEvent.EventType.MESSAGE: handlers.forEach(h -> h.onMessage(new SEEvent.MessageEvent(ev))); break;
                case SEEvent.EventType.EDIT: handlers.forEach(h -> h.onEdit(new SEEvent.MessageEvent(ev))); break;
                case SEEvent.EventType.JOIN: handlers.forEach(h -> h.onJoin(new SEEvent(ev))); break;
                case SEEvent.EventType.LEAVE: handlers.forEach(h -> h.onLeave(new SEEvent(ev))); break;
                case SEEvent.EventType.NAME_CHANGE: handlers.forEach(h -> h.onNameChange(new SEEvent(ev))); break;
                case SEEvent.EventType.MESSAGE_STARRED: handlers.forEach(h -> h.onMessageStarred(new SEEvent(ev))); break;
                case SEEvent.EventType.DEBUG: handlers.forEach(h -> h.onDebug(new SEEvent(ev))); break;
                case SEEvent.EventType.MENTION: handlers.forEach(h -> h.onMention(new SEEvent.MessageEvent(ev))); break;
                case SEEvent.EventType.FLAG: handlers.forEach(h -> h.onFlag(new SEEvent(ev))); break;
                case SEEvent.EventType.DELETE: handlers.forEach(h -> h.onDelete(new SEEvent.DeleteEvent(ev))); break;
                case SEEvent.EventType.FILE_UPLOAD: handlers.forEach(h -> h.onFileUpload(new SEEvent(ev))); break;
                case SEEvent.EventType.MODERATOR_FLAG: handlers.forEach(h -> h.onModeratorFlag(new SEEvent(ev))); break;
                case SEEvent.EventType.SETTINGS_CHANGED: handlers.forEach(h -> h.onSettingsChanged(new SEEvent(ev))); break;
                case SEEvent.EventType.GLOBAL_NOTIFICATION: handlers.forEach(h -> h.onGlobalNotification(new SEEvent(ev))); break;
                case SEEvent.EventType.ACCESS_CHANGED: handlers.forEach(h -> h.onAccessChanged(new SEEvent(ev))); break;
                case SEEvent.EventType.USER_NOTIFICATION: handlers.forEach(h -> h.onUserNotification(new SEEvent(ev))); break;
                case SEEvent.EventType.INVITATION: handlers.forEach(h -> h.onInvitation(new SEEvent(ev))); break;
                case SEEvent.EventType.REPLY: handlers.forEach(h -> h.onReply(new SEEvent.MessageEvent(ev))); break;
                case SEEvent.EventType.MESSAGE_MOVED_OUT: handlers.forEach(h -> h.onMessageMovedOut(new SEEvent(ev))); break;
                case SEEvent.EventType.MESSAGE_MOVED_IN: handlers.forEach(h -> h.onMessageMovedIn(new SEEvent(ev))); break;
                case SEEvent.EventType.TIME_BREAK: handlers.forEach(h -> h.onTimeBreak(new SEEvent(ev))); break;
                case SEEvent.EventType.FEED_TICKER: handlers.forEach(h -> h.onFeedTicker(new SEEvent(ev))); break;
                case SEEvent.EventType.USER_SUSPENSION: handlers.forEach(h -> h.onUserSuspension(new SEEvent(ev))); break;
                case SEEvent.EventType.USER_MERGE: handlers.forEach(h -> h.onUserMerge(new SEEvent(ev))); break;
                case SEEvent.EventType.USER_NAME_OR_AVATAR_CHANGE: handlers.forEach(h -> h.onUserNameOrAvatarChange(new SEEvent(ev))); break;
              }
            } else {
              handlers.forEach(h -> h.onUnknown(ev));
            }
          }
        }
      }
    }
  }

  public void register(SEEventHandler handler) {
    handlers.add(handler);
  }

  public void unregister(SEEventHandler handler) {
    handlers.remove(handler);
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
}
