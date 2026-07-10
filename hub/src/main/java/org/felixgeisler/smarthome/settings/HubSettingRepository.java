package org.felixgeisler.smarthome.settings;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link HubSetting} values. */
public interface HubSettingRepository extends JpaRepository<HubSetting, String> {}
