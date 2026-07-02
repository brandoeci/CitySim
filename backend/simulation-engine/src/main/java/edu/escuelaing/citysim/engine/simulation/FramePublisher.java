package edu.escuelaing.citysim.engine.simulation;

import edu.escuelaing.citysim.core.model.SimulationFrame;
import edu.escuelaing.citysim.core.sba.SpaceDataGrid;
import org.springframework.stereotype.Component;

@Component
public class FramePublisher {

    private final SpaceDataGrid space;

    public FramePublisher(SpaceDataGrid space) {
        this.space = space;
    }

    public void publish(SimulationFrame frame) {
        space.publishFrame(frame);
    }
}
