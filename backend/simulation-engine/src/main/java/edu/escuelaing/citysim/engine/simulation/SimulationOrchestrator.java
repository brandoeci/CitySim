package edu.escuelaing.citysim.engine.simulation;

import edu.escuelaing.citysim.core.model.CarDelta;
import edu.escuelaing.citysim.core.model.SimulationFrame;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.zone.ZoneProcessingUnit;
import edu.escuelaing.citysim.engine.zone.ZoneRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SimulationOrchestrator {

    private final ZoneRegistry zoneRegistry;
    private final SpaceDataGrid space;
    private final FramePublisher framePublisher;

    private final ExecutorService zoneExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicLong frameId = new AtomicLong(0);

    private volatile long lastTickTime = System.currentTimeMillis();
    private volatile double currentFps = 0;

    public SimulationOrchestrator(ZoneRegistry zoneRegistry, SpaceDataGrid space,
                                   FramePublisher framePublisher) {
        this.zoneRegistry = zoneRegistry;
        this.space = space;
        this.framePublisher = framePublisher;
    }

    public void tick(long tickNumber) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastTickTime;
        if (elapsed > 0) currentFps = 1000.0 / elapsed;
        lastTickTime = now;

        Collection<ZoneProcessingUnit> zones = zoneRegistry.getOwnedZones().values();
        if (zones.isEmpty()) return;

        // Process all zones in parallel using virtual threads
        List<Future<?>> futures = new ArrayList<>(zones.size());
        for (ZoneProcessingUnit zpu : zones) {
            futures.add(zoneExecutor.submit(() -> zpu.tick(tickNumber)));
        }
        for (Future<?> f : futures) {
            try { f.get(2, TimeUnit.SECONDS); }
            catch (Exception e) { Thread.currentThread().interrupt(); }
        }

        // Collect deltas from all zones (already computed inside tick())
        List<CarDelta> allDeltas = new ArrayList<>();
        List<String> allRemoved = new ArrayList<>();
        for (ZoneProcessingUnit zpu : zones) {
            allDeltas.addAll(zpu.drainDeltas());
            allRemoved.addAll(zpu.drainRemoved());
        }

        if (!allDeltas.isEmpty() || !allRemoved.isEmpty()) {
            SimulationFrame frame = new SimulationFrame(
                    frameId.incrementAndGet(), now,
                    allDeltas, allRemoved,
                    (int) space.getCarCount(), currentFps
            );
            framePublisher.publish(frame);
        }
    }

    public double getCurrentFps() { return currentFps; }
}
