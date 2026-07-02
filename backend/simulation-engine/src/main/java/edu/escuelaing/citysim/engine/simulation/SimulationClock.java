package edu.escuelaing.citysim.engine.simulation;

import edu.escuelaing.citysim.engine.config.SimulationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SimulationClock {

    private static final Logger log = LoggerFactory.getLogger(SimulationClock.class);

    private final SimulationOrchestrator orchestrator;
    private final SimulationProperties props;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sim-clock");
                t.setDaemon(true);
                return t;
            });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong tickNumber = new AtomicLong(0);
    private ScheduledFuture<?> scheduledTask;

    public SimulationClock(SimulationOrchestrator orchestrator, SimulationProperties props) {
        this.orchestrator = orchestrator;
        this.props = props;
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            long rateMs = props.getTickRateMs();
            scheduledTask = scheduler.scheduleAtFixedRate(
                    this::tick, 0, rateMs, TimeUnit.MILLISECONDS
            );
            log.info("Simulation started at {} ms/tick", rateMs);
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
                scheduledTask = null;
            }
            log.info("Simulation stopped at tick {}", tickNumber.get());
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public long getTickNumber() {
        return tickNumber.get();
    }

    private void tick() {
        try {
            orchestrator.tick(tickNumber.incrementAndGet());
        } catch (Exception e) {
            log.error("Error during simulation tick {}", tickNumber.get(), e);
        }
    }
}
