package org.example.device;

import org.example.web.WebSocketBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Periodically marks devices as offline when they haven't been heard from
 * within their expected heartbeat window.
 *
 * Thresholds per device type:
 *   - SOLAKON_METER / SOLAKON_INVERTER: 5 minutes (poll every ~30 s)
 *   - HUE_LIGHT / SHELLY_PLUG:         10 minutes (SSE / active poll)
 *   - HOMEMATIC_RADIATOR:              15 minutes (passive poll every few min)
 *   - everything else:                 15 minutes (conservative)
 *
 * When a device transitions online→offline the updated device is broadcast
 * over WebSocket so the frontend can show a toast / update health badges.
 */
@Component
public class DeviceOfflineChecker {

    private static final Logger log = LoggerFactory.getLogger(DeviceOfflineChecker.class);

    private final DeviceRepository deviceRepository;
    private final WebSocketBroadcaster broadcaster;

    public DeviceOfflineChecker(DeviceRepository deviceRepository,
                                 WebSocketBroadcaster broadcaster) {
        this.deviceRepository = deviceRepository;
        this.broadcaster      = broadcaster;
    }

    /** Runs every 3 minutes. */
    @Scheduled(fixedDelay = 3 * 60 * 1000)
    @Transactional
    public void checkOffline() {
        Instant now = Instant.now();
        List<Device> all = deviceRepository.findAll();
        int marked = 0;

        for (Device d : all) {
            if (d.getLastSeen() == null) continue;   // never seen — don't mark offline

            // HUE_LIGHT uses SSE — it only sends events on state changes, not heartbeats.
            // A lamp that has been on for hours without being touched won't have a recent
            // lastSeen even though it is perfectly reachable. Skip time-based offline
            // detection for HUE_LIGHT; the adapter marks it online when SSE events arrive.
            if (d.getType() == DeviceType.HUE_LIGHT) continue;

            long thresholdMinutes = switch (d.getType()) {
                case SOLAKON_METER, SOLAKON_INVERTER -> 5;
                case SHELLY_PLUG                     -> 10;
                default                              -> 15;
            };

            boolean shouldBeOnline =
                    d.getLastSeen().isAfter(now.minus(thresholdMinutes, ChronoUnit.MINUTES));

            if (d.isOnline() && !shouldBeOnline) {
                d.setOnline(false);
                deviceRepository.save(d);
                broadcaster.broadcastDeviceState(d);
                log.info("[OfflineChecker] Device '{}' (id={}) marked offline (last seen {}m ago)",
                        d.getName(), d.getId(),
                        ChronoUnit.MINUTES.between(d.getLastSeen(), now));
                marked++;
            }
        }

        if (marked > 0) log.info("[OfflineChecker] Marked {} device(s) offline", marked);
    }
}
