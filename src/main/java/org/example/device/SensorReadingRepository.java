package org.example.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    List<SensorReading> findByRoomAndMetricOrderByRecordedAtDesc(String room, String metric);

    Optional<SensorReading> findTop1ByTopicOrderByRecordedAtDesc(String topic);

    List<SensorReading> findByTopicAndRecordedAtAfterOrderByRecordedAtAsc(String topic, Instant since);

    List<SensorReading> findByRoomAndMetricAndRecordedAtAfterOrderByRecordedAtAsc(String room, String metric, Instant since);

    /** Latest reading for every distinct topic (used for dashboard overview) */
    @Query("""
            SELECT s FROM SensorReading s
            WHERE s.recordedAt = (
                SELECT MAX(s2.recordedAt) FROM SensorReading s2 WHERE s2.topic = s.topic
            )
            ORDER BY s.room, s.metric
            """)
    List<SensorReading> findLatestPerTopic();
}
