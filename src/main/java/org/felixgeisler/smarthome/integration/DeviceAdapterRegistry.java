package org.felixgeisler.smarthome.integration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Routes to the {@link DeviceAdapter} responsible for a given adapter type. */
@Component
public class DeviceAdapterRegistry {

  private final Map<String, DeviceAdapter> adaptersByType;

  /**
   * Collects every {@link DeviceAdapter} bean on the context, keyed by its adapter type.
   *
   * @param adapters all adapter beans
   */
  public DeviceAdapterRegistry(List<DeviceAdapter> adapters) {
    this.adaptersByType =
        adapters.stream()
            .collect(Collectors.toMap(DeviceAdapter::adapterType, Function.identity()));
  }

  /**
   * Returns the adapter registered for the given type.
   *
   * @param adapterType the adapter identifier
   * @return the matching adapter
   * @throws UnknownAdapterException if no adapter handles the type
   */
  public DeviceAdapter get(String adapterType) {
    DeviceAdapter adapter = adaptersByType.get(adapterType);
    if (adapter == null) {
      throw new UnknownAdapterException(adapterType);
    }
    return adapter;
  }
}
