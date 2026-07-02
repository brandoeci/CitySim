package edu.escuelaing.citysim.engine.persistence;

import edu.escuelaing.citysim.core.model.CarState;
import edu.escuelaing.citysim.core.sba.DataPump;
import edu.escuelaing.citysim.engine.config.SimulationProperties;
import edu.escuelaing.citysim.engine.simulation.SimulationClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PostgresDataPump implements DataPump {

    private static final Logger log = LoggerFactory.getLogger(PostgresDataPump.class);

    private final CarSnapshotRepository repository;
    private final SimulationClock clock;
    private final SimulationProperties props;

    public PostgresDataPump(CarSnapshotRepository repository, SimulationClock clock,
                             SimulationProperties props) {
        this.repository = repository;
        this.clock = clock;
        this.props = props;
    }

    @Override
    @Async
    public void persist(long tick, Collection<CarState> cars) {
        if (cars.isEmpty()) return;
        try {
            List<CarSnapshot> snapshots = cars.stream()
                    .map(c -> CarSnapshot.builder()
                            .tick(tick)
                            .carId(c.getCarId())
                            .x(c.getX())
                            .y(c.getY())
                            .zoneId(c.getCurrentZoneId())
                            .status(c.getStatus() != null ? c.getStatus().name() : null)
                            .snapshotAt(Instant.now())
                            .build())
                    .collect(Collectors.toList());
            repository.saveAll(snapshots);
            log.debug("Persisted {} car snapshots at tick {}", snapshots.size(), tick);
        } catch (Exception e) {
            log.error("Failed to persist snapshot at tick {}: {}", tick, e.getMessage());
        }
    }
}
