package libSE;

import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.net.URIBuilder;
import org.jsoup.Jsoup;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

public final class SEAccount implements AutoCloseable {
  public enum ServerSites {
    STACK_EXCHANGE("chat.stackexchange.com"),
    STACK_OVERFLOW("chat.stackoverflow.com"),
    META_STACK_EXCHANGE("chat.meta.stackexchange.com");

    final String site;

    ServerSites(String site) {
      this.site = site;
    }
  }

  public static final String CACHE_DIR = "./cache";
  static {
    try {
      Files.createDirectory(Paths.get(CACHE_DIR));
    } catch (FileAlreadyExistsException ex) {
      // intentionally ignored
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static boolean DEBUG = false;

  static void debug(String s) {
    if (DEBUG) System.out.println(s);
  }

  private static Path cachedCookiesPath(String server, String email) {
    return Paths.get(CACHE_DIR, "libse_cookies_" + server + "_" +Integer.toHexString(email.hashCode()) + ".dat");
  }

  private static boolean loadCookies(String server, String email, CookieStore cookies) throws ClassNotFoundException, IOException {
    try {
      final var fis = new FileInputStream(cachedCookiesPath(server, email).toFile());
      final var ois = new ObjectInputStream(fis);
      final var read = ois.readObject();
      cookies.clear();
      for (final var cookie : ((CookieStore) read).getCookies()) {
        cookies.addCookie(cookie);
      }
      return true;
    } catch (FileNotFoundException ex) {
      debug("Cookies not found");
      return false;
    }
  }

  private static void dumpCookies(String server, String email, CookieStore cookies) throws IOException {
    final var fos = new FileOutputStream(cachedCookiesPath(server, email).toFile());
    final var oos = new ObjectOutputStream(fos);
    oos.writeObject(cookies);
    debug("Dumped cookies");
  }

  private static Optional<String> getChatFKey(CloseableHttpClient client, String server) {
    try {
      final var soup = Utils.getHtml(client, "https://" + server + "/chats/join/favorite");
      final var fkey = soup.select("#content form input[name=fkey]").attr("value");
      return Optional.of(fkey);
    } catch (Exception ex) {
      debug(Utils.exToString(ex));
      return Optional.empty();
    }
  }

  private static OptionalLong getChatUserId(CloseableHttpClient client, String server) {
    try {
      final var soup = Utils.getHtml(client, "https://" + server + "/chats/join/favorite");
      final var id = soup.select(".topbar-menu-links a").attr("href").split("/")[2];
      return OptionalLong.of(Long.parseLong(id));
    } catch (Exception ex) {
      debug(Utils.exToString(ex));
      return OptionalLong.empty();
    }
  }

  private static Optional<String> scrapeFKey(CloseableHttpClient client) {
    try {
      final var soup = Utils.getHtml(client, "https://meta.stackexchange.com/users/login");
      return Optional.of(soup.select("[name=fkey]").attr("value"));
    } catch (Exception ex) {
      debug(Utils.exToString(ex));
      return Optional.empty();
    }
  }

  private static String doSELogin(CloseableHttpClient client, String host, String email, String password, String fkey) throws IOException, ParseException {
    final var body = MultipartEntityBuilder.create()
        .addTextBody("email", email)
        .addTextBody("password", password)
        .addTextBody("fkey", fkey)
        .addTextBody("isSignup", "false")
        .addTextBody("isLogin", "true")
        .addTextBody("isPassword", "false")
        .addTextBody("isAddLogin", "false")
        .addTextBody("hasCaptcha", "false")
        .addTextBody("ssrc", "head")
        .addTextBody("submitButton", "Log In")
        .build();
    return Utils.post(client, host + "/users/login-or-signup/validation/track", body);
  }

  private static void loadProfile(CloseableHttpClient client, String host, String email, String password, String fkey) throws URISyntaxException, IOException, ParseException {
    final var body = MultipartEntityBuilder.create()
        .addTextBody("email", email)
        .addTextBody("password", password)
        .addTextBody("fkey", fkey)
        .addTextBody("ssrc", "head")
        .build();
    final var response = Utils.post(client, new URIBuilder(host + "/users/login")
        .addParameter("ssrc", "head")
        .addParameter("returnurl", host)
        .build()
        .toString(), body);
    final var soup = Jsoup.parse(response);
    if (soup.select("title").text().contains("Human verification")) {
      throw new SEException.LoginError("Failed to load SE profile: Caught by captcha. (It's almost like I'm not human!) Wait around 5min and try again.");
    }
  }

  private static void universalLogin(CloseableHttpClient client, String host) throws IOException, ParseException {
    Utils.post(client, host + "/users/login/universal/request");
  }

  public final String server;
  public final boolean useCookies;
  public final CookieStore cookieStore = new BasicCookieStore();
  public String fkey;
  public long userId;
  public HashMap<Long, SERoom> rooms = new HashMap<>();
  public HashMap<SERoom, Thread> roomThreads = new HashMap<>();

  public SEAccount(ServerSites server, boolean useCookies) {
    this(server.site, useCookies);
  }

  public SEAccount(String server, boolean useCookies) {
    this.server = server;
    this.useCookies = useCookies;
  }

  private boolean needsToLogin(String email) throws IOException, ClassNotFoundException {
    if (useCookies) {
      if (loadCookies(server, email, cookieStore)) {
        cookieStore.clearExpired(Instant.now());
        return cookieStore.getCookies().stream().noneMatch(cookie -> cookie.getDomain().equals("stackexchange.com") && cookie.getPath().equals("/") && cookie.getName().equals("acct"));
      }
    }
    return true;
  }

  public void authenticate(String email, String password, String host) throws IOException, ClassNotFoundException {
    if (useCookies) {
      if (loadCookies(server, email, cookieStore)) {
        debug("Loaded cookies");
      }
    }
    cookieStore.clearExpired(Instant.now());
    try (final var client = HttpClientBuilder.create()
        .setDefaultHeaders(List.of(new BasicHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; dzaima/chat; +http://github.com/dzaima/chat)")))
        .setDefaultCookieStore(cookieStore)
        .build()) {
      if (needsToLogin(email)) {
        assert Objects.nonNull(password);
        debug("Logging into SE...");
        debug("Acquiring fkey...");
        final var fkey_ = scrapeFKey(client);
        if (fkey_.isEmpty()) throw new SEException.LoginError("Failed to scrape site fkey.");
        final var fkey = fkey_.get();
        debug("Acquired fkey: " + fkey);
        debug("Logging into " + host + "...");
        final var result = doSELogin(client, host, email, password, fkey);
        if (!result.equals("Login-OK")) throw new SEException.LoginError("Site login failed: " + result);
        debug("Logged into " + host + "!");
        debug("Loading profile...");
        loadProfile(client, host, email, password, fkey);
        debug("Loaded SE profile!");
        debug("Logging into the rest of the network...");
        universalLogin(client, host);
        if (useCookies) {
          debug("Dumping cookies...");
          dumpCookies(server, email, cookieStore);
        }
      }
      final var fkey_ = getChatFKey(client, server);
      final var userId_ = getChatUserId(client, server);
      if (fkey_.isEmpty() || userId_.isEmpty()) throw new SEException.LoginError("Login failed.");
      fkey = fkey_.get();
      userId = userId_.getAsLong();
      debug("Chat fkey is " + fkey + ", user ID is " + userId);
    } catch (ParseException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  public SERoom joinRoom(long roomId) throws InterruptedException {
    debug("Joining room " + roomId);
    if (fkey == null) throw new SEException.LoginError("Not logged in!");
    if (rooms.containsKey(roomId)) return rooms.get(roomId);
    final var room = new SERoom(server, cookieStore, fkey, userId, roomId);
    final var thread = new Thread(() -> {
      try {
        room.loop();
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
    thread.start();
    room.connected.await();
    rooms.put(roomId, room);
    roomThreads.put(room, thread);
    return room;
  }

  public void leaveRoom(long roomId) throws InterruptedException {
    final var room = rooms.get(roomId);
    if (Objects.isNull(room)) return;
    room.shouldExit.set(true);
    room.hasExited.await();
    roomThreads.remove(room);
    rooms.remove(roomId);
  }

  public void leaveAllRooms() throws InterruptedException {
    for (final var id : List.copyOf(rooms.keySet())) this.leaveRoom(id);
  }

  @Override
  public void close() throws Exception {
    leaveAllRooms();
  }
}
