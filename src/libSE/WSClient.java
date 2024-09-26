package libSE;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;

public class WSClient extends WebSocketClient {
  private CountDownLatch connected = new CountDownLatch(1);
  public final Queue<String> messageQueue = new ArrayDeque<>();

  public WSClient(URI url) {
    super(url);
  }

  public WSClient(URI url, Map<String, String> httpHeaders) {
    super(url, httpHeaders);
  }

  @Override
  public void onOpen(ServerHandshake ignored) {
    connected.countDown();
  }

  @Override
  public void onMessage(String message) {
    messageQueue.add(message);
  }

  @Override
  public void onClose(int code, String reason, boolean remote) {
    if (remote) throw new ClosedException(code, reason);
  }

  @Override
  public void onError(Exception ex) {
    throw new RuntimeException(ex);
  }

  public static class ClosedException extends RuntimeException {
    public ClosedException(int code, String reason) {
      super("Socket closed\n" + code + ": " + reason);
    }
  }

  public static class TimeoutException extends RuntimeException {
    public TimeoutException(Duration timeout) {
      super("Timeout expired: " + timeout.toString());
    }
  }

  public String waitForMessage() throws InterruptedException {
    while (messageQueue.isEmpty()) Thread.sleep(100);
    return messageQueue.remove();
  }

  public String waitForMessage(Duration timeout) throws InterruptedException {
    final var start = Instant.now();
    while (messageQueue.isEmpty()) {
      if (start.plus(timeout).isBefore(Instant.now())) throw new TimeoutException(timeout);
      Thread.sleep(100);
    }
    return messageQueue.remove();
  }

  public void connect() {
    super.connect();
    try {
      connected.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
