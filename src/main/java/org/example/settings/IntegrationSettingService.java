package org.example.settings;

import org.springframework.stereotype.Service;

@Service
public class IntegrationSettingService {

    private final IntegrationSettingRepository repo;

    public IntegrationSettingService(IntegrationSettingRepository repo) {
        this.repo = repo;
    }

    /** Returns the DB value for key, or fallback if not set. */
    public String get(String key, String fallback) {
        return repo.findBySettingKey(key)
                .map(IntegrationSetting::getSettingValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(fallback);
    }
}
