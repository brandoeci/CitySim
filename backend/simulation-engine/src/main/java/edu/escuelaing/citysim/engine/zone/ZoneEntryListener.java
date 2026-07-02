package edu.escuelaing.citysim.engine.zone;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;
import edu.escuelaing.citysim.core.model.CarState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Hazelcast EntryListener on the "cars" IMap.
 * Routes car adoptions to the correct ZoneProcessingUnit on this backend instance.
 */
public class ZoneEntryListener
        implements EntryAddedListener<String, CarState>,
                   EntryUpdatedListener<String, CarState>,
                   EntryRemovedListener<String, CarState> {

    private static final Logger log = LoggerFactory.getLogger(ZoneEntryListener.class);

    private final Map<String, ZoneProcessingUnit> ownedZones;
    private final Predicate<String> isOwnedZone;

    public ZoneEntryListener(Map<String, ZoneProcessingUnit> ownedZones) {
        this.ownedZones = ownedZones;
        this.isOwnedZone = ownedZones::containsKey;
    }

    @Override
    public void entryAdded(EntryEvent<String, CarState> event) {
        CarState car = event.getValue();
        if (car == null) return;
        String zoneId = car.getCurrentZoneId();
        if (isOwnedZone.test(zoneId)) {
            ZoneProcessingUnit zpu = ownedZones.get(zoneId);
            if (zpu != null) zpu.adoptCar(car.getCarId());
        }
    }

    @Override
    public void entryUpdated(EntryEvent<String, CarState> event) {
        CarState newCar = event.getValue();
        CarState oldCar = event.getOldValue();
        if (newCar == null) return;

        String newZone = newCar.getCurrentZoneId();
        String oldZone = oldCar != null ? oldCar.getCurrentZoneId() : null;

        // Car crossed into an owned zone
        if (isOwnedZone.test(newZone) && !newZone.equals(oldZone)) {
            ZoneProcessingUnit zpu = ownedZones.get(newZone);
            if (zpu != null) {
                zpu.adoptCar(newCar.getCarId());
                log.debug("Adopted car {} into zone {}", newCar.getCarId(), newZone);
            }
        }

        // Car left an owned zone
        if (oldZone != null && isOwnedZone.test(oldZone) && !oldZone.equals(newZone)) {
            ZoneProcessingUnit zpu = ownedZones.get(oldZone);
            if (zpu != null) zpu.releaseCar(newCar.getCarId());
        }
    }

    @Override
    public void entryRemoved(EntryEvent<String, CarState> event) {
        CarState old = event.getOldValue();
        if (old == null) return;
        ZoneProcessingUnit zpu = ownedZones.get(old.getCurrentZoneId());
        if (zpu != null) zpu.releaseCar(old.getCarId());
    }
}
