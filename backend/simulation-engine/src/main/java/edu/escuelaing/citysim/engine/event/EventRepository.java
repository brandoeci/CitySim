package edu.escuelaing.citysim.engine.event;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EventRepository extends JpaRepository<SimulationEvent, Long> {
    List<SimulationEvent> findAllByOrderByStartedAtDesc();
}