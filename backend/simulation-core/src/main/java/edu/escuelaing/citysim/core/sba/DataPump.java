package edu.escuelaing.citysim.core.sba;

import edu.escuelaing.citysim.core.model.CarState;

import java.util.Collection;

public interface DataPump {
    void persist(long tick, Collection<CarState> cars);
}
