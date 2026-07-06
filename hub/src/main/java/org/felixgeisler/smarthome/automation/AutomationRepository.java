package org.felixgeisler.smarthome.automation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Automation} aggregates. */
public interface AutomationRepository extends JpaRepository<Automation, Long> {

  /**
   * Returns every enabled automation, for the engine to evaluate against incoming telemetry.
   *
   * @return the enabled automations
   */
  List<Automation> findByEnabledTrue();
}
