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
 * Dedicated server asset publication and hosting settings.
 *
 * @param packs selected logical pack IDs in increasing priority.
 * @param hosting transport used to expose the archives.
 * @param port dedicated HTTP port, or zero for the other hosting modes.
 * @param bindAddress local bind address, or {@code null} to use the game server's bind address.
 * @param publicHost public hostname, or {@code null} to derive it from the player's connection.
 * @param publicBaseUrl explicit download base, or {@code null} to derive it from the hosting settings.
 */
record AssetDeliveryConfig(List<Identifier> packs, Hosting hosting, int port, @Nullable String bindAddress, @Nullable String publicHost, @Nullable URI publicBaseUrl) {
  /**
   * Configuration path relative to the dedicated server's working directory.
   */
  static final Path FILE = Path.of("config", "cobweb-asset-delivery.json");

  /**
   * Reads and validates operator settings, preserving pack order.
   * A missing file returns a configuration with delivery disabled.
   *
   * @return parsed settings or disabled defaults.
   * @throws IOException if the file cannot be read or contains invalid settings.
   */
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

  /**
   * Resolves the base URL advertised to a client.
   * An explicit public base URL takes precedence. Otherwise, a separate listener uses its configured port, a public host on the game listener uses the bound game port, and the default uses the address the player joined.
   *
   * @param host hostname the client used to join.
   * @param gamePort local Minecraft listener port.
   * @param advertisedGamePort Minecraft port the client used to join.
   * @return public base URL ending with a slash.
   * @throws IllegalArgumentException if the derived host or port cannot be used.
   */
  URI baseUrl(String host, int gamePort, int advertisedGamePort) {
    if (publicBaseUrl != null) {
      return publicBaseUrl;
    }
    int advertisedPort = hosting == Hosting.SEPARATE_PORT ? port : publicHost == null ? advertisedGamePort : gamePort;
    return httpBase(publicHost == null ? host : publicHost, advertisedPort);
  }

  /**
   * Builds a URL for the built-in HTTP endpoint, omitting the default HTTP port.
   *
   * @param host hostname or address to advertise.
   * @param port HTTP port to advertise.
   * @return base URL for the archive endpoint.
   * @throws IllegalArgumentException if the host or port cannot be used.
   */
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

  /**
   * Validates an operator-provided HTTP or HTTPS base URL and ensures a trailing slash.
   *
   * @param text configured base URL.
   * @return validated base URL.
   * @throws IllegalArgumentException if the URL is invalid, contains credentials, a query or a fragment, or has an invalid port.
   */
  private static URI publicUrl(String text) {
    URI uri = URI.create(text);
    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getRawUserInfo() != null
        || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65_535) {
      throw new IllegalArgumentException("public_base_url must be an HTTP(S) base URL without credentials, a query or a fragment");
    }
    return uri.getRawPath().endsWith("/") ? uri : URI.create(uri + "/");
  }

  /**
   * Reads an optional nonblank string property.
   *
   * @param json configuration object.
   * @param key property name.
   * @return value, or {@code null} if the property is absent.
   * @throws RuntimeException if the property is present with an invalid value.
   */
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

  /**
   * Transport used to expose published pack archives.
   */
  enum Hosting {
    /**
     * Serves HTTP on the Minecraft listener.
     */
    GAME_PORT,
    /**
     * Serves HTTP on a dedicated listener.
     */
    SEPARATE_PORT,
    /**
     * Publishes archives for an externally hosted URL and opens no Cobweb listener.
     */
    EXTERNAL
  }
}
