package org.example.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IntegrationInstanceRepository extends JpaRepository<IntegrationInstance, Long> {
    List<IntegrationInstance> findByEnabled(boolean enabled);
    List<IntegrationInstance> findByAdapterType(String adapterType);
}
