package org.example.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DashboardConfigRepository extends JpaRepository<DashboardConfig, Long> {
    List<DashboardConfig> findAllByOrderBySortOrderAsc();
}
