package edu.escuelaing.citysim.engine.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "car_snapshots",
       indexes = {
           @Index(name = "idx_snapshot_tick",   columnList = "tick"),
           @Index(name = "idx_snapshot_car_id", columnList = "car_id")
       })
public class CarSnapshot {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tick",    nullable = false) private long tick;
    @Column(name = "car_id",  length = 36, nullable = false) private String carId;
    @Column(name = "x")       private double x;
    @Column(name = "y")       private double y;
    @Column(name = "zone_id", length = 20) private String zoneId;
    @Column(name = "status",  length = 20) private String status;
    @Column(name = "snapshot_at") private Instant snapshotAt;

    public CarSnapshot() {}

    private CarSnapshot(Builder b) {
        this.tick = b.tick; this.carId = b.carId; this.x = b.x; this.y = b.y;
        this.zoneId = b.zoneId; this.status = b.status; this.snapshotAt = b.snapshotAt;
    }

    public Long getId()          { return id; }
    public long getTick()        { return tick; }
    public String getCarId()     { return carId; }
    public double getX()         { return x; }
    public double getY()         { return y; }
    public String getZoneId()    { return zoneId; }
    public String getStatus()    { return status; }
    public Instant getSnapshotAt() { return snapshotAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private long tick; private String carId; private double x, y;
        private String zoneId, status; private Instant snapshotAt;

        public Builder tick(long v)          { tick = v; return this; }
        public Builder carId(String v)       { carId = v; return this; }
        public Builder x(double v)           { x = v; return this; }
        public Builder y(double v)           { y = v; return this; }
        public Builder zoneId(String v)      { zoneId = v; return this; }
        public Builder status(String v)      { status = v; return this; }
        public Builder snapshotAt(Instant v) { snapshotAt = v; return this; }
        public CarSnapshot build()           { return new CarSnapshot(this); }
    }
}
