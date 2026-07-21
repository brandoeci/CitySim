package edu.escuelaing.citysim.engine.event;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "simulation_events")
public class SimulationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // columnDefinition explicito: sin el, Hibernate 6 genera un CHECK
    // constraint atado al set de valores del enum en el momento de crear la
    // tabla, y ddl-auto=update nunca lo actualiza cuando el enum gana
    // valores nuevos (paso exactamente eso al agregar AREA_SHIELD/EVACUATION/
    // GRIDLOCK y FAILED). Con varchar plano no hay constraint que journalear.
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(30)")
    private EventType type;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private EventStatus status;

    private String affectedZoneId;
    private String description;
    private int durationSeconds;
    private int requiredActions;
    private Instant startedAt;
    private Instant resolvedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_actions", joinColumns = @JoinColumn(name = "event_id"))
    @MapKeyColumn(name = "zone_id")
    @Column(name = "action_count")
    private Map<String, Integer> actionsByZone = new HashMap<>();

    public Long getId()                         { return id; }
    public EventType getType()                  { return type; }
    public EventStatus getStatus()              { return status; }
    public String getAffectedZoneId()           { return affectedZoneId; }
    public String getDescription()              { return description; }
    public int getDurationSeconds()             { return durationSeconds; }
    public int getRequiredActions()             { return requiredActions; }
    public Instant getStartedAt()               { return startedAt; }
    public Instant getResolvedAt()              { return resolvedAt; }
    public Map<String, Integer> getActionsByZone() { return actionsByZone; }

    public void setType(EventType v)            { type = v; }
    public void setStatus(EventStatus v)        { status = v; }
    public void setAffectedZoneId(String v)     { affectedZoneId = v; }
    public void setDescription(String v)        { description = v; }
    public void setDurationSeconds(int v)       { durationSeconds = v; }
    public void setRequiredActions(int v)       { requiredActions = v; }
    public void setStartedAt(Instant v)         { startedAt = v; }
    public void setResolvedAt(Instant v)        { resolvedAt = v; }
}