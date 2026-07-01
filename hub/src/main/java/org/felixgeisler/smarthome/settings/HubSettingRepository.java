package org.felixgeisler.smarthome.settings;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for persisted {@link HubSetting} values, keyed by setting name. */
public interface HubSettingRepository extends JpaRepository<HubSetting, String> {}
