package edu.escuelaing.citysim.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "simulation")
public class SimulationProperties {

    private int maxCars = 1000;
    private long tickRateMs = 50;
    private double minSafeDistance = 0.15;
    private int persistEveryNTicks = 100;
    private MapConfig map = new MapConfig();

    public int getMaxCars()                 { return maxCars; }
    public void setMaxCars(int v)           { maxCars = v; }
    public long getTickRateMs()             { return tickRateMs; }
    public void setTickRateMs(long v)       { tickRateMs = v; }
    public double getMinSafeDistance()      { return minSafeDistance; }
    public void setMinSafeDistance(double v){ minSafeDistance = v; }
    public int getPersistEveryNTicks()      { return persistEveryNTicks; }
    public void setPersistEveryNTicks(int v){ persistEveryNTicks = v; }
    public MapConfig getMap()               { return map; }
    public void setMap(MapConfig v)         { map = v; }

    public static class MapConfig {
        private int gridWidth  = 200;
        private int gridHeight = 200;
        private int zoneCols   = 10;
        private int zoneRows   = 10;

        public int getGridWidth()            { return gridWidth; }
        public void setGridWidth(int v)      { gridWidth = v; }
        public int getGridHeight()           { return gridHeight; }
        public void setGridHeight(int v)     { gridHeight = v; }
        public int getZoneCols()             { return zoneCols; }
        public void setZoneCols(int v)       { zoneCols = v; }
        public int getZoneRows()             { return zoneRows; }
        public void setZoneRows(int v)       { zoneRows = v; }
    }
}
