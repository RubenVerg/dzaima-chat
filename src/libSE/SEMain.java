package libSE;

import java.io.IOException;

public class SEMain {
  public static void main(String[] args) {
    SEAccount.DEBUG = true;

    String email = args[0];
    String password = args[1].equals("cookie") ? null : args[2];
    long roomId = Long.parseLong(args[2]);
    String message = args[3];

    try (final var account = new SEAccount(SEAccount.ServerSites.STACK_EXCHANGE, true)) {
      account.authenticate(email, password, "https://codegolf.stackexchange.com");
      System.out.println("Connected");
      final var room = account.joinRoom(roomId);
      System.out.println("Joined room " + roomId);
      room.send(message);
      System.out.println("Sent message");
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
