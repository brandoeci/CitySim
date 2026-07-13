package edu.escuelaing.citysim.engine.car;

import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import edu.escuelaing.citysim.engine.config.SimulationProperties;
import edu.escuelaing.citysim.engine.event.EventGeneratorLeader;
import edu.escuelaing.citysim.engine.simulation.SimulationClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mantiene viva la poblacion de la ciudad.
 *
 * Cuando un carro llega a su destino se elimina del Space. Sin reposicion, la
 * ciudad se vacia sola. Este componente observa el numero de carros en el Space
 * y, si esta por debajo del objetivo, spawnea los que falten en puntos nuevos.
 *
 * Es un lazo de control clasico sobre el estado compartido: no necesita saber
 * QUE carro murio ni CUANDO, solo compara el estado actual con el deseado y
 * converge. Encaja con SBA: el estado vive en el Space y la unidad de proceso
 * lo observa y corrige.
 *
 * Reutiliza la eleccion de lider de EventGeneratorLeader: si hubiera varias
 * instancias del backend, todas verian el mismo deficit y spawnearian por su
 * cuenta, multiplicando los carros. Solo el lider repone.
 */
@Component
public class PopulationMaintainer {

    private static final Logger log = LoggerFactory.getLogger(PopulationMaintainer.class);

    /** Tope de carros a reponer por ciclo, para no crear un pico de golpe. */
    private static final int MAX_REFILL_PER_CYCLE = 40;

    private final SpaceDataGrid space;
    private final CarSpawner spawner;
    private final SimulationClock clock;
    private final EventGeneratorLeader leader;
    private final AtomicInteger targetCars;

    public PopulationMaintainer(SpaceDataGrid space, CarSpawner spawner,
                                SimulationClock clock, EventGeneratorLeader leader,
                                SimulationProperties props) {
        this.space = space;
        this.spawner = spawner;
        this.clock = clock;
        this.leader = leader;
        this.targetCars = new AtomicInteger(props.getTargetCars());
    }

    @Scheduled(fixedDelay = 2000L)
    public void maintain() {
        if (!clock.isRunning()) return;
        if (!leader.isLeader()) return;

        int target = targetCars.get();
        long current = space.getCarCount();
        if (current >= target) return;

        int deficit = (int) Math.min(target - current, MAX_REFILL_PER_CYCLE);
        int spawned = spawner.spawn(deficit);
        if (spawned > 0) {
            log.debug("Poblacion repuesta: +{} (objetivo {}, habia {})", spawned, target, current);
        }
    }

    public int getTargetCars() {
        return targetCars.get();
    }

    public void setTargetCars(int value) {
        int safe = Math.max(0, value);
        targetCars.set(safe);
        log.info("Poblacion objetivo ajustada a {}", safe);
    }
}