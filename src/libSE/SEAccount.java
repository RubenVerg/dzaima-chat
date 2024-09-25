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

public class SEAccount {
  public static final Set<String> SE_DOMAINS = Set.of(
    "askubuntu.com",
    "mathoverflow.net",
    "serverfault.com",
    "stackoverflow.com",
    "stackexchange.com",
    "stackapps.com",
    "superuser.com"
  );

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

  public final boolean useCookies;
  public final CookieStore cookieStore = new BasicCookieStore();
  public String fkey;
  public long userId;

  private static void debug(String s) {
    if (DEBUG) System.out.println(s);
  }

  private static Path cachedCookiesPath(String email) {
    return Paths.get(CACHE_DIR, "libse_cookies_" + Integer.toHexString(email.hashCode()) + ".dat");
  }

  public static boolean loadCookies(String email, CookieStore cookies) throws ClassNotFoundException, IOException {
    try {
      final var fis = new FileInputStream(cachedCookiesPath(email).toFile());
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

  public static void dumpCookies(String email, CookieStore cookies) throws IOException {
    final var fos = new FileOutputStream(cachedCookiesPath(email).toFile());
    final var oos = new ObjectOutputStream(fos);
    oos.writeObject(cookies);
    debug("Dumped cookies");
  }

  public static Optional<String> getChatFKey(CloseableHttpClient client) {
    try {
      final var soup = Utils.getHtml(client, "https://chat.stackexchange.com/chats/join/favorite");
      final var fkey = soup.select("#content form input[name=fkey]").attr("value");
      return Optional.of(fkey);
    } catch (Exception ex) {
      debug(Utils.exToString(ex));
      return Optional.empty();
    }
  }

  public static OptionalLong getChatUserId(CloseableHttpClient client) {
    try {
      final var soup = Utils.getHtml(client, "https://chat.stackexchange.com/chats/join/favorite");
      final var id = soup.select(".topbar-menu-links a").attr("href").split("/")[2];
      return OptionalLong.of(Long.parseLong(id));
    } catch (Exception ex) {
      debug(Utils.exToString(ex));
      return OptionalLong.empty();
    }
  }

  public static Optional<String> scrapeFKey(CloseableHttpClient client) {
    try {
      final var soup = Utils.getHtml(client, "https://meta.stackexchange.com/users/login");
      return Optional.of(soup.select("[name=fkey]").attr("value"));
    } catch (Exception ex) {
      debug(Utils.exToString(ex));
      return Optional.empty();
    }
  }

  public static String doSELogin(CloseableHttpClient client, String host, String email, String password, String fkey) throws IOException, ParseException {
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

  public static void loadProfile(CloseableHttpClient client, String host, String email, String password, String fkey) throws URISyntaxException, IOException, ParseException {
    // final HashMap<String, JSON.Val> body = new HashMap<>();
    // body.put("email", new JSON.Str(email));
    // body.put("password", new JSON.Str(password));
    // body.put("fkey", new JSON.Str(fkey));
    // body.put("ssrc", new JSON.Str("head"));
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

  public static String universalLogin(CloseableHttpClient client, String host) throws IOException, ParseException {
    return Utils.post(client, host + "/users/login/universal/request");
  }

  public boolean needsToLogin(String email) throws IOException, ClassNotFoundException {
    if (useCookies) {
      if (loadCookies(email, cookieStore)) {
        cookieStore.clearExpired(Instant.now());
        return cookieStore.getCookies().stream().noneMatch(cookie -> cookie.getDomain().equals("stackexchange.com") && cookie.getPath().equals("/") && cookie.getName().equals("acct"));
      }
    }
    return true;
  }

  public SEAccount(boolean useCookies) {
    this.useCookies = useCookies;
  }

  public void authenticate(String email, String password, String host) throws IOException, ClassNotFoundException {
    if (useCookies) {
      if (loadCookies(email, cookieStore)) {
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
          dumpCookies(email, cookieStore);
        }
      }
      final var fkey_ = getChatFKey(client);
      final var userId_ = getChatUserId(client);
      if (fkey_.isEmpty() || userId_.isEmpty()) throw new SEException.LoginError("Login failed.");
      fkey = fkey_.get();
      userId = userId_.getAsLong();
      debug("Chat fkey is " + fkey + ", user ID is " + Long.toString(userId));
    } catch (ParseException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
