package edu.escuelaing.citysim.engine.event;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Implementa leader election distribuida usando Hazelcast IMap con TTL.
 * Solo la instancia lider genera eventos colaborativos.
 * Si el lider muere, el TTL expira en 15s y otra instancia toma el rol.
 */
@Component
public class EventGeneratorLeader {

    private static final Logger log = LoggerFactory.getLogger(EventGeneratorLeader.class);
    private static final String LEADER_MAP  = "event-leader";
    private static final String LEADER_KEY  = "leader";
    private static final long   LEASE_TTL   = 15L;
    private static final long   RENEW_EVERY = 5000L;

    private final IMap<String, String> leaderMap;
    private final String instanceId = UUID.randomUUID().toString();

    private boolean wasLeader = false;

    @Autowired
    public EventGeneratorLeader(HazelcastInstance hazelcast) {
        this(hazelcast, "");
    }

    /** @param prefix antepuesto al nombre del mapa (p.ej. "room:ABC123:"), vacio para el global. */
    public EventGeneratorLeader(HazelcastInstance hazelcast, String prefix) {
        this.leaderMap = hazelcast.getMap(prefix + LEADER_MAP);
    }

    @Scheduled(fixedDelay = RENEW_EVERY)
    public void renewOrAcquire() {
        String current = leaderMap.get(LEADER_KEY);

        if (current == null) {
            // Nadie es lider � intentar adquirir
            String previous = leaderMap.putIfAbsent(LEADER_KEY, instanceId, LEASE_TTL, TimeUnit.SECONDS);
            if (previous == null) {
                log.info("Liderazgo adquirido por instancia {}", instanceId);
                wasLeader = true;
            }
        } else if (current.equals(instanceId)) {
            // Soy el lider � renovar TTL
            leaderMap.set(LEADER_KEY, instanceId, LEASE_TTL, TimeUnit.SECONDS);
            if (!wasLeader) {
                log.info("Liderazgo renovado por instancia {}", instanceId);
                wasLeader = true;
            }
        } else {
            // Otro es el lider
            if (wasLeader) {
                log.info("Liderazgo perdido por instancia {} � lider actual: {}", instanceId, current);
                wasLeader = false;
            }
        }
    }

    public boolean isLeader() {
        return instanceId.equals(leaderMap.get(LEADER_KEY));
    }

    public String getInstanceId() {
        return instanceId;
    }
}
