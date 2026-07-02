package edu.escuelaing.citysim.core.sba;

public interface ProcessingUnit {
    String getZoneId();
    void tick(long tickNumber);
    int getLocalCarCount();
    void adoptCar(String carId);
    void releaseCar(String carId);
}
