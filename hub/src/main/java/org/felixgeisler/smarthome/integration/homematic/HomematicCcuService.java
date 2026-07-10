package org.felixgeisler.smarthome.integration.homematic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.felixgeisler.smarthome.HttpClients;
import org.felixgeisler.smarthome.capability.Capability;
import org.felixgeisler.smarthome.device.SensorSpec;
import org.felixgeisler.smarthome.settings.SettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Talks to a Homematic CCU over its JSON-RPC API ({@code /api/homematic.cgi}): session login,
 * device discovery, and reading and writing channel datapoints.
 *
 * <p>Holds the connection state (host and credentials) seeded from {@link HomematicProperties} and
 * updated by {@link #connect(String, String, String)}, persisted through {@link SettingsStore} so a
 * connected CCU survives a restart. A session is obtained lazily and cached; because the CCU caps
 * concurrent sessions and expires idle ones, an expired session (JSON-RPC error 400) is
 * transparently re-established once and the call retried.
 */
@Service
@EnableConfigurationProperties(HomematicProperties.class)
public class HomematicCcuService {

  private static final Logger log = LoggerFactory.getLogger(HomematicCcuService.class);

  /** JSON-RPC error code returned when the session id is missing or expired. */
  private static final int SESSION_EXPIRED = 400;

  /**
   * JSON-RPC error code returned when the credentials are wrong (or too many sessions are open).
   */
  private static final int INVALID_CREDENTIALS = 501;

  /** Datapoint operation bits reported by {@code getParamsetDescription}. */
  private static final int OP_READ = 1;

  private static final int OP_WRITE = 2;

  /** Settings keys under which the connected CCU's host and credentials are persisted. */
  private static final String HOST_SETTING = "homematic.host";

  private static final String USERNAME_SETTING = "homematic.username";

  private static final String PASSWORD_SETTING = "homematic.password";

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  /** The CCU's own central devices, which expose no user-controllable channels. */
  private static final Set<String> CENTRAL_TYPES =
      Set.of("HM-RCV-50", "HmIP-RCV-50", "HmIP-CCU3");

  /** JSON-RPC parameter names reused across calls. */
  private static final String INTERFACE = "interface";

  private static final String ADDRESS = "address";

  private final RestClient restClient;
  private final ObjectMapper json;
  private final SettingsStore settings;
  private final AtomicReference<String> host = new AtomicReference<>();
  private final AtomicReference<String> username = new AtomicReference<>();
  private final AtomicReference<String> password = new AtomicReference<>();
  private final AtomicReference<String> session = new AtomicReference<>();

  /**
   * Creates the service, seeding the connection from configuration.
   *
   * @param properties the configured Homematic settings
   * @param settings the store that persists a connected CCU across restarts
   * @param json the mapper used to build requests and read JSON-RPC results
   */
  public HomematicCcuService(
      HomematicProperties properties, SettingsStore settings, ObjectMapper json) {
    this.host.set(properties.ccuHost());
    this.username.set(properties.username());
    this.password.set(properties.password());
    this.settings = settings;
    this.json = json;
    this.restClient = HttpClients.withTimeouts(TIMEOUT, TIMEOUT);
  }

  /**
   * Restores a CCU connected on a previous run, so its credentials survive a restart. Persisted
   * values override the configured seed.
   */
  @PostConstruct
  void restore() {
    settings.get(HOST_SETTING).ifPresent(host::set);
    settings.get(USERNAME_SETTING).ifPresent(username::set);
    settings.get(PASSWORD_SETTING).ifPresent(password::set);
  }

  /**
   * Reports whether a CCU connection is currently held.
   *
   * @return true if a host and credentials are all set, so the CCU can be reached
   */
  public boolean isConnected() {
    return present(host.get()) && present(username.get()) && present(password.get());
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  /**
   * Connects to the CCU at the given host with the given WebUI credentials, persisting them on
   * success.
   *
   * @param hostInput the CCU host (IP or host[:port], with or without a scheme)
   * @param user the WebUI username
   * @param pass the WebUI password
   * @return true if the login succeeded; false if the credentials were rejected
   * @throws HomematicCcuException if the CCU was unreachable or returned an unexpected response
   */
  public boolean connect(String hostInput, String user, String pass) {
    String authority = authority(hostInput);
    JsonRpcEnvelope env =
        rpc(endpointFor(authority), "Session.login", Map.of("username", user, "password", pass));
    if (env.error() != null) {
      if (env.error().code() == INVALID_CREDENTIALS) {
        return false;
      }
      throw new HomematicCcuException("Homematic CCU login failed: " + env.error().message());
    }
    String sid = sessionId(env);
    // Commit the new connection only after the login succeeds, so a rejected attempt cannot break
    // an existing working connection.
    session.set(sid);
    host.set(authority);
    username.set(user);
    password.set(pass);
    settings.save(HOST_SETTING, authority);
    settings.save(USERNAME_SETTING, user);
    settings.save(PASSWORD_SETTING, pass);
    log.info("Connected to Homematic CCU at {}", authority);
    return true;
  }

  /**
   * Discovers the CCU's controllable and sensing channels, ready to register as hub devices. Each
   * channel is classified from its datapoints: a writable {@code STATE} bool is a switch; a
   * datapoint that maps to a neutral sensor type makes a sensing channel.
   *
   * @return the discovered devices
   * @throws HomematicCcuException if no CCU is connected, or it could not be reached
   */
  public List<HomematicDevice> discoverDevices() {
    CcuDevice[] devices =
        convert(authed("Device.listAllDetail", new LinkedHashMap<>()), CcuDevice[].class);
    List<HomematicDevice> result = new ArrayList<>();
    if (devices != null) {
      for (CcuDevice device : devices) {
        if (!CENTRAL_TYPES.contains(device.type())) {
          collectFrom(device, result);
        }
      }
    }
    return result;
  }

  /**
   * Reads one datapoint of a channel.
   *
   * @param externalId the channel address as {@code "<interface>/<channelAddress>"}
   * @param valueKey the datapoint key (e.g. {@code "STATE"})
   * @return the value as the CCU reports it (a string, even for booleans and numbers)
   * @throws HomematicCcuException if no CCU is connected, or it could not be reached
   */
  public String readValue(String externalId, String valueKey) {
    String[] target = split(externalId);
    Map<String, Object> params = new LinkedHashMap<>();
    params.put(INTERFACE, target[0]);
    params.put(ADDRESS, target[1]);
    params.put("valueKey", valueKey);
    return convert(authed("Interface.getValue", params), String.class);
  }

  /**
   * Writes one datapoint of a channel.
   *
   * @param externalId the channel address as {@code "<interface>/<channelAddress>"}
   * @param valueKey the datapoint key (e.g. {@code "STATE"})
   * @param type the CCU value type (e.g. {@code "boolean"} for a bool, {@code "double"} for a
   *     float)
   * @param value the value to set
   * @throws HomematicCcuException if no CCU is connected, or it could not be reached
   */
  public void writeValue(String externalId, String valueKey, String type, Object value) {
    String[] target = split(externalId);
    Map<String, Object> params = new LinkedHashMap<>();
    params.put(INTERFACE, target[0]);
    params.put(ADDRESS, target[1]);
    params.put("valueKey", valueKey);
    params.put("type", type);
    params.put("value", value);
    authed("Interface.setValue", params);
  }

  /**
   * Reads every current datapoint value of a channel in one call, for the sensor poll.
   *
   * @param externalId the channel address as {@code "<interface>/<channelAddress>"}
   * @return the channel's datapoints keyed by name, each value as the CCU reports it
   * @throws HomematicCcuException if no CCU is connected, or it could not be reached
   */
  public Map<String, String> readChannel(String externalId) {
    String[] target = split(externalId);
    Map<String, Object> params = new LinkedHashMap<>();
    params.put(INTERFACE, target[0]);
    params.put(ADDRESS, target[1]);
    params.put("paramsetKey", "VALUES");
    Map<?, ?> raw = convert(authed("Interface.getParamset", params), Map.class);
    Map<String, String> values = new LinkedHashMap<>();
    if (raw != null) {
      raw.forEach((key, value) -> values.put(String.valueOf(key), String.valueOf(value)));
    }
    return values;
  }

  private void collectFrom(CcuDevice device, List<HomematicDevice> out) {
    String iface = device.interfaceName();
    String switchId = null;
    List<ChannelSensors> sensorChannels = new ArrayList<>();
    for (CcuChannel channel : device.channels()) {
      if (channel.index() == 0 || !(bool(channel.isReadable()) || bool(channel.isWritable()))) {
        continue;
      }
      CcuDatapoint[] datapoints = describeQuietly(iface, channel.address());
      if (datapoints == null) {
        continue;
      }
      if (switchId == null && bool(channel.isWritable()) && hasWritableState(datapoints)) {
        switchId = iface + "/" + channel.address();
      }
      List<SensorSpec> sensors = sensorsFrom(datapoints);
      if (!sensors.isEmpty()) {
        sensorChannels.add(new ChannelSensors(iface + "/" + channel.address(), sensors));
      }
    }
    if (switchId != null) {
      out.add(
          new HomematicDevice(switchId, device.name(), Set.of(Capability.SWITCHABLE), List.of()));
    }
    boolean disambiguate = switchId != null || sensorChannels.size() > 1;
    for (ChannelSensors sensorChannel : sensorChannels) {
      String name =
          disambiguate ? device.name() + " " + roleFor(sensorChannel.sensors()) : device.name();
      out.add(
          new HomematicDevice(
              sensorChannel.externalId(),
              name,
              Set.of(Capability.SENSING),
              sensorChannel.sensors()));
    }
  }

  // A CCU channel may expose no VALUES paramset; treat a description failure as "nothing here".
  private CcuDatapoint[] describeQuietly(String iface, String address) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put(INTERFACE, iface);
    params.put(ADDRESS, address);
    params.put("paramsetKey", "VALUES");
    try {
      return convert(authed("Interface.getParamsetDescription", params), CcuDatapoint[].class);
    } catch (HomematicCcuException ex) {
      log.debug("No VALUES paramset for channel {}", address, ex);
      return new CcuDatapoint[0];
    }
  }

  private static boolean hasWritableState(CcuDatapoint... datapoints) {
    for (CcuDatapoint datapoint : datapoints) {
      if ("STATE".equals(datapoint.id())
          && "BOOL".equals(datapoint.type())
          && (operations(datapoint) & OP_WRITE) != 0) {
        return true;
      }
    }
    return false;
  }

  private static List<SensorSpec> sensorsFrom(CcuDatapoint... datapoints) {
    List<SensorSpec> sensors = new ArrayList<>();
    for (CcuDatapoint datapoint : datapoints) {
      if ((operations(datapoint) & OP_READ) == 0) {
        continue;
      }
      HomematicDatapoints.sensorFor(datapoint.id())
          .ifPresent(
              mapping ->
                  sensors.add(
                      new SensorSpec(
                          mapping.type().getKey(),
                          mapping.type(),
                          mapping.type().getDefaultUnit())));
    }
    return sensors;
  }

  private static String roleFor(List<SensorSpec> sensors) {
    boolean power = sensors.stream().anyMatch(s -> "power".equals(s.key()));
    boolean temperature = sensors.stream().anyMatch(s -> "temperature".equals(s.key()));
    if (power) {
      return "Power";
    }
    return temperature ? "Climate" : "Sensors";
  }

  private static int operations(CcuDatapoint datapoint) {
    try {
      return Integer.parseInt(datapoint.operations().trim());
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  // Issues one JSON-RPC call with the session injected, re-establishing an expired session once.
  private JsonNode authed(String method, Map<String, Object> params) {
    Map<String, Object> withSession = new LinkedHashMap<>(params);
    withSession.put("_session_id_", currentSession());
    URI target = endpoint();
    JsonRpcEnvelope env = rpc(target, method, withSession);
    if (env.error() != null && env.error().code() == SESSION_EXPIRED) {
      session.set(null);
      withSession.put("_session_id_", currentSession());
      env = rpc(target, method, withSession);
    }
    if (env.error() != null) {
      throw new HomematicCcuException("Homematic CCU error: " + env.error().message());
    }
    return env.result();
  }

  private String currentSession() {
    String current = session.get();
    return current != null ? current : login();
  }

  private String login() {
    String user = username.get();
    String pass = password.get();
    if (host.get() == null || user == null || pass == null) {
      throw new HomematicCcuException("No Homematic CCU is connected; connect one first");
    }
    JsonRpcEnvelope env =
        rpc(endpoint(), "Session.login", Map.of("username", user, "password", pass));
    if (env.error() != null) {
      throw new HomematicCcuException("Homematic CCU login failed: " + env.error().message());
    }
    String sid = sessionId(env);
    session.set(sid);
    return sid;
  }

  private String sessionId(JsonRpcEnvelope env) {
    String sid = convert(env.result(), String.class);
    if (sid == null || sid.isBlank()) {
      throw new HomematicCcuException("Homematic CCU returned no session");
    }
    return sid;
  }

  private JsonRpcEnvelope rpc(URI endpoint, String method, Object params) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("method", method);
    request.put("params", params);
    request.put("id", 1);
    JsonRpcEnvelope env;
    try {
      env =
          restClient
              .post()
              .uri(endpoint)
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(JsonRpcEnvelope.class);
    } catch (RestClientException ex) {
      throw new HomematicCcuException(
          "Could not reach the Homematic CCU at " + endpoint.getHost(), ex);
    }
    if (env == null) {
      throw new HomematicCcuException("Empty response from the Homematic CCU");
    }
    return env;
  }

  private <T> T convert(JsonNode node, Class<T> type) {
    return json.convertValue(node, type);
  }

  private URI endpoint() {
    String authority = host.get();
    if (authority == null) {
      throw new HomematicCcuException("No Homematic CCU is connected; connect one first");
    }
    return endpointFor(authority);
  }

  private static URI endpointFor(String authority) {
    return base(authority).replacePath("/api/homematic.cgi").build().toUri();
  }

  private static String[] split(String externalId) {
    int slash = externalId.indexOf('/');
    if (slash < 0) {
      throw new HomematicCcuException("Malformed Homematic external id: " + externalId);
    }
    return new String[] {externalId.substring(0, slash), externalId.substring(slash + 1)};
  }

  // Reduce a user-entered host to its bare authority, so a scheme, path, or query cannot be
  // injected into the request.
  private static String authority(String hostInput) {
    String value = hostInput.trim().replaceFirst("^[a-zA-Z]+://", "");
    int slash = value.indexOf('/');
    return slash < 0 ? value : value.substring(0, slash);
  }

  private static UriComponentsBuilder base(String authority) {
    return UriComponentsBuilder.fromUriString("http://" + authority).replaceQuery(null);
  }

  private static boolean bool(Boolean value) {
    return Boolean.TRUE.equals(value);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record JsonRpcEnvelope(JsonNode result, CcuError error) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CcuError(int code, String message) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CcuDevice(
      String name,
      String type,
      @JsonProperty("interface") String interfaceName,
      List<CcuChannel> channels) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CcuChannel(String address, int index, Boolean isReadable, Boolean isWritable) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record CcuDatapoint(
      @JsonProperty("ID") String id,
      @JsonProperty("TYPE") String type,
      @JsonProperty("OPERATIONS") String operations) {}

  private record ChannelSensors(String externalId, List<SensorSpec> sensors) {}
}
