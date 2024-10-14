package libSE;

import dzaima.utils.Pair;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
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
              * receive <count>: wait for <count> messages to be sent, printing them
              * favorites: list all favorite rooms
              * chat: start a (very) basic chat terminal interface""");
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
      switch (action) {
        case "nothing" -> System.out.print("");
        case "send" -> {
          room.send(extraArgs.getFirst());
          System.out.println("Sent message");
        }
        case "receive" -> {
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
        }
        case "favorites" -> {
          for (final var fav : account.favorites()) {
            System.out.println(fav);
          }
        }
        case "chat" -> {
          final var scanner = new Scanner(System.in);
          room.register(new SERoom.MessageHandler() {
            @Override
            public void onMessage(SEMessage message) {
              System.out.println("[" + message.id + "] " + message.userName + ": " + message.plainContent);
            }

            @Override
            public void onEdit(SEMessage oldMessage, SEMessage newMessage) {
              System.out.println("[" + newMessage.id + "] ✏️ " + newMessage.userName + ": " + newMessage.plainContent);
            }

            @Override
            public void onDelete(SEMessage message) {
              System.out.println("[" + message.id + "] ❌ " + message.userName + ": " + message.plainContent);
            }

            @Override
            public void onMention(SEMessage message) {
              System.out.println("[" + message.id + "] ⚠️ " + message.userName + ": " + message.plainContent);
            }
          });
          handler:
          while (true) {
            System.out.print("> ");
            final var message = scanner.nextLine();
            if (!message.isEmpty() && message.charAt(0) == '/') {
              switch (message) {
                case "/exit":
                  break handler;
                case "/info":
                  System.out.println("Room name: " + room.roomName);
                  break;
                case "/users":
                  final var users = room.pingable().stream().sorted(Comparator.comparing(p -> p.a));
                  for (final var user : users.toList()) {
                    System.out.println("* [" + user.a + "] " + user.b);
                  }
                  break;
                case "/messages":
                  for (final var msg : room.messages) {
                    System.out.println("[" + msg.id + "] " + msg.userName + ": " + msg.plainContent);
                  }
                  break;
                case "/load":
                  room.previousMessages();
                  break;
                case "/help":
                  System.out.println("Commands:");
                  System.out.println("/exit: exit the chat");
                  System.out.println("/info: show room info");
                  System.out.println("/users: pingable users");
                  System.out.println("/messages: all seen messages");
                  System.out.println("/load: load previous messages");
                  System.out.println("/help: show this help");
                  break;
                default:
                  System.out.println("Unknown command: " + message);
                  break;
              }
            } else if (!message.isEmpty()) {
              room.send(message);
            }
          }
        }
        default -> throw new RuntimeException("Unknown action: " + action);
      }
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
