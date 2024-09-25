package libSE;

import dzaima.utils.JSON;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

public final class Utils {
  public static String get(CloseableHttpClient client, String url) throws IOException, ParseException {
    final var req = new HttpGet(url);
    return client.execute(req, response -> EntityUtils.toString(response.getEntity()));
  }

  public static Document getHtml(CloseableHttpClient client, String url) throws IOException, ParseException {
    return Jsoup.parse(get(client, url));
  }

  public static String post(CloseableHttpClient client, String url) throws IOException, ParseException {
    final var req = new HttpPost(url);
    return client.execute(req, response -> {
      final var entity = response.getEntity();
      if (Objects.isNull(entity)) return "";
      return EntityUtils.toString(response.getEntity());
    });
  }

  public static String post(CloseableHttpClient client, String url, HttpEntity data) throws IOException, ParseException {
    final var req = new HttpPost(url);
    req.setEntity(data);
    return client.execute(req, response -> {
      final var entity = response.getEntity();
      if (Objects.isNull(entity)) return "";
      return EntityUtils.toString(response.getEntity());
    });
  }

  public static String post(CloseableHttpClient client, String url, String data, ContentType contentType) throws IOException, ParseException {
    return post(client, url, new StringEntity(data, contentType));
  }

  public static String exToString(Exception e) {
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }
}
