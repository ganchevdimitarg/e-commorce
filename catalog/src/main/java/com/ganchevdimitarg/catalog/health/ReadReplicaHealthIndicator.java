package com.ganchevdimitarg.catalog.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Exposes the read replica's reachability as the {@code replica} actuator
 * health component.
 *
 * <p>The status is deliberately always {@link Health#up() UP}: a replica
 * outage is a graceful degradation, not a service failure. The router falls
 * back to the writer, so requests still succeed (at the cost of extra primary
 * load and possible read-after-write consistency). The {@code reader} and
 * {@code route} details let operators see whether traffic is currently served
 * by the replica or has degraded to the writer.
 *
 * <p>This indicator is deliberately <em>not</em> part of the readiness group:
 * pod readiness must not fail just because the replica is unreachable. The
 * bean reuses the router's 2-second cached {@link ReplicaHealthProbe}, so a
 * health call does not open a replica connection on every scrape.
 */
@Component("replica")
@Profile("!test")
public class ReadReplicaHealthIndicator implements HealthIndicator {

    private final ReplicaHealthProbe replicaHealthProbe;

    public ReadReplicaHealthIndicator(ReplicaHealthProbe replicaHealthProbe) {
        this.replicaHealthProbe = replicaHealthProbe;
    }

    @Override
    public Health health() {
        boolean reachable = replicaHealthProbe.getAsBoolean();
        return Health.up()
                .withDetail("reader", reachable ? "reachable" : "unreachable")
                .withDetail("route", reachable ? "reader" : "writer")
                .build();
    }
}
