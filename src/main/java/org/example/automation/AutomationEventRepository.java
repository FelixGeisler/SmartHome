package org.example.automation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutomationEventRepository extends JpaRepository<AutomationEvent, Long> {

    /** Most-recent events first, capped by the caller-supplied Pageable. */
    List<AutomationEvent> findAllByOrderByFiredAtDesc(Pageable pageable);
}
