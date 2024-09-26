package libSE;

public class SEException extends RuntimeException {
  public static final class FutureError extends SEException {
    public FutureError(String message) {
      super(message);
    }
  }

  public static final class LoginError extends SEException {
    public LoginError(String message) {
      super(message);
    }
  }

  public static final class RoomError extends SEException {
    public RoomError(String message) {
      super(message);
    }
  }

  public static final class ConnectionError extends SEException {
    public ConnectionError(String message) {
      super(message);
    }
  }

  public static final class RatelimitError extends SEException {
    public int retryAfter;
    public RatelimitError(String message, int retryAfter) {
      super(message);
      this.retryAfter = retryAfter;
    }
  }

  public static final class NotAllowedError extends SEException {
    public NotAllowedError(String message) {
      super(message);
    }
  }

  public static final class OperationFailedError extends SEException {
    public OperationFailedError(String message) {
      super(message);
    }

    public OperationFailedError(Exception ex) {
      super(ex);
    }
  }

  public SEException(String message) {
    super(message);
  }

  public SEException(Exception ex) {
    super(ex);
  }
}
