package edu.escuelaing.citysim.engine;

import edu.escuelaing.citysim.engine.car.CarSpawner;
import edu.escuelaing.citysim.engine.simulation.SimulationClock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class CitySimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CitySimulatorApplication.class, args);
    }

    @Bean
    ApplicationRunner autoStart(SimulationClock clock, CarSpawner spawner) {
        return args -> {
            spawner.spawn(200);
            clock.start();
        };
    }
}
