package libSE;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class SEMain {
  public static void main(String[] args) {
    if (args.length < 5) {
      System.err.println(
          """
              Usage: j <debug> <email> <password> <room id> <action>
              where <debug> chooses whether to print debug messages,
              <email> and <password> are valid login credentials for Code Golf Stack Exchange, and <password> can be `cookie` to use cached credentials,
              <room id> is a valid room ID for a room on chat.stackexchange.com
                (*not* chat.stackoverflow.com or chat.meta.stackexchange.com),
              <action> is one of the following:
              * nothing: print room info and exit
              * send <message>: send a message
              * receive <count>: wait for <count> messages to be sent, printing them""");
      System.exit(1);
    }

    SEAccount.DEBUG = Boolean.parseBoolean(args[0]);
    String email = args[1];
    String password = args[2].equals("cookie") ? null : args[2];
    long roomId = Long.parseLong(args[3]);
    String action = args[4];
    List<String> extraArgs = Arrays.stream(args).skip(5).toList();

    try (final var account = new SEAccount(SEAccount.ServerSites.STACK_EXCHANGE, true)) {
      account.authenticate(email, password, "https://codegolf.stackexchange.com");
      System.out.println("Connected");
      final var room = account.joinRoom(roomId);
      room.getInfo();
      System.out.println("Joined room " + room.roomName + " (" + roomId + ")");
      if (action.equals("nothing")) {
        System.out.print("");
      } else if (action.equals("send")) {
        room.send(extraArgs.getFirst());
        System.out.println("Sent message");
      } else if (action.equals("receive")) {
        final var count = Integer.parseInt(extraArgs.getFirst());
        final var received = new CountDownLatch(count);
        System.out.println("Waiting for message");
        room.register(new SEEventHandler() {
          @Override
          public void onMessage(SEEvent.MessageEvent ev) {
            System.out.println("Got message: " + ev.content);
            received.countDown();
          }
        });
        received.await();
      } else {
        throw new RuntimeException("Unknown action: " + action);
      }
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
