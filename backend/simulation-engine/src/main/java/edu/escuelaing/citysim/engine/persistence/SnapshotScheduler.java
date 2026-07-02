package edu.escuelaing.citysim.engine.persistence;

import edu.escuelaing.citysim.core.sba.DataPump;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.config.SimulationProperties;
import edu.escuelaing.citysim.engine.simulation.SimulationClock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SnapshotScheduler {

    private final SpaceDataGrid space;
    private final DataPump dataPump;
    private final SimulationClock clock;
    private final SimulationProperties props;

    public SnapshotScheduler(SpaceDataGrid space, DataPump dataPump,
                              SimulationClock clock, SimulationProperties props) {
        this.space = space;
        this.dataPump = dataPump;
        this.clock = clock;
        this.props = props;
    }

    // Persist every 5 seconds (not every tick to avoid DB pressure)
    @Scheduled(fixedRateString = "${simulation.persist-every-n-ticks:100}000")
    public void snapshot() {
        if (!clock.isRunning()) return;
        long tick = clock.getTickNumber();
        if (tick % props.getPersistEveryNTicks() != 0) return;
        dataPump.persist(tick, space.getAllCars().values());
    }
}
