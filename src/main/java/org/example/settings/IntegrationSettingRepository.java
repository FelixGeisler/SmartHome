package org.example.settings;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IntegrationSettingRepository extends JpaRepository<IntegrationSetting, Long> {
    Optional<IntegrationSetting> findBySettingKey(String settingKey);
}
