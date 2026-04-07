package com.cepsandik.electionservice.config;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.TemporalAmount;

/**
 * Tüm seçim zamanlaması ve anlık karşılaştırmalar için tek kaynak: UTC {@link Instant}.
 */
@Component
public class UtcClock {

    private final Clock clock = Clock.systemUTC();

    public Instant instant() {
        return clock.instant();
    }

    public Instant plus(TemporalAmount amount) {
        return instant().plus(amount);
    }
}
