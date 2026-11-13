package it.crystalnest.cobweb.api.pack.delivery;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Reads the dedicated server's asset publication and hosting settings.
 */
record AssetDeliveryConfig(List<Identifier> packs, Hosting hosting, int port, @Nullable String bindAddress, @Nullable String publicHost, @Nullable URI publicBaseUrl) {
  static final Path FILE = Path.of("config", "cobweb-asset-delivery.json");

  static AssetDeliveryConfig read() throws IOException {
    if (Files.notExists(FILE)) {
      return new AssetDeliveryConfig(List.of(), Hosting.GAME_PORT, 0, null, null, null);
    }
    try (var reader = Files.newBufferedReader(FILE)) {
      var json = JsonParser.parseReader(reader).getAsJsonObject();
      var ids = new LinkedHashSet<Identifier>();
      if (json.has("packs")) {
        for (var value : json.getAsJsonArray("packs")) {
          if (!value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Pack IDs must be strings");
          }
          String id = value.getAsString();
          if (id.indexOf(':') < 1 || !ids.add(Identifier.parse(id))) {
            throw new IllegalArgumentException("Expected a unique namespaced asset pack ID: " + id);
          }
        }
      }
      String mode = string(json, "hosting");
      Hosting hosting = switch (mode == null ? "game-port" : mode) {
        case "game-port" -> Hosting.GAME_PORT;
        case "separate-port" -> Hosting.SEPARATE_PORT;
        case "external" -> Hosting.EXTERNAL;
        default -> throw new IllegalArgumentException("Unknown asset hosting mode " + mode);
      };
      int port = json.has("port") ? json.getAsJsonPrimitive("port").getAsBigDecimal().intValueExact() : 0;
      String bind = string(json, "bind_address");
      String host = string(json, "public_host");
      String base = string(json, "public_base_url");
      if (hosting == Hosting.SEPARATE_PORT ? port < 1 || port > 65_535 : json.has("port") || bind != null) {
        throw new IllegalArgumentException("Use port 1 through 65535 and optional bind_address with separate-port hosting");
      }
      if (host != null) {
        httpBase(host, 80);
      }
      URI url = base == null ? null : publicUrl(base);
      if (hosting == Hosting.EXTERNAL && url == null) {
        throw new IllegalArgumentException("External asset hosting requires public_base_url");
      }
      return new AssetDeliveryConfig(List.copyOf(ids), hosting, port, bind, host, url);
    } catch (RuntimeException failure) {
      throw new IOException("Invalid asset delivery settings in " + FILE, failure);
    }
  }

  URI baseUrl(String host, int gamePort, int advertisedGamePort) {
    if (publicBaseUrl != null) {
      return publicBaseUrl;
    }
    int advertisedPort = hosting == Hosting.SEPARATE_PORT ? port : publicHost == null ? advertisedGamePort : gamePort;
    return httpBase(publicHost == null ? host : publicHost, advertisedPort);
  }

  private static URI httpBase(String host, int port) {
    try {
      URI result = new URI("http", null, host, port == 80 ? -1 : port, AssetPackHttp.ROUTE, null, null);
      if (result.getHost() == null || port < 1 || port > 65_535) {
        throw new IllegalArgumentException("Invalid public asset host or port");
      }
      return result;
    } catch (URISyntaxException failure) {
      throw new IllegalArgumentException("Invalid public asset host or port", failure);
    }
  }

  private static URI publicUrl(String text) {
    URI uri = URI.create(text);
    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getRawUserInfo() != null
        || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65_535) {
      throw new IllegalArgumentException("public_base_url must be an HTTP(S) base URL without credentials, a query or a fragment");
    }
    return uri.getRawPath().endsWith("/") ? uri : URI.create(uri + "/");
  }

  @Nullable
  private static String string(JsonObject json, String key) {
    if (!json.has(key)) {
      return null;
    }
    var value = json.getAsJsonPrimitive(key);
    if (!value.isString() || value.getAsString().isBlank()) {
      throw new IllegalArgumentException("Expected a nonempty string for " + key);
    }
    return value.getAsString();
  }

  enum Hosting {
    GAME_PORT,
    SEPARATE_PORT,
    EXTERNAL
  }
}
