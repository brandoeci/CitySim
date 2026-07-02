package edu.escuelaing.citysim.engine.config;

import edu.escuelaing.citysim.core.map.CityMap;
import edu.escuelaing.citysim.core.map.MapFactory;
import edu.escuelaing.citysim.core.pathfinding.AStarPathFinder;
import edu.escuelaing.citysim.core.pathfinding.PathFinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CityMapConfig {

    @Bean
    public CityMap cityMap(SimulationProperties props) {
        SimulationProperties.MapConfig mc = props.getMap();
        return MapFactory.generate(
                mc.getGridWidth(),
                mc.getGridHeight(),
                mc.getZoneCols(),
                mc.getZoneRows()
        );
    }

    @Bean
    public PathFinder pathFinder() {
        return new AStarPathFinder();
    }
}
