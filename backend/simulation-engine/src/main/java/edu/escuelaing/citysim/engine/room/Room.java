package edu.escuelaing.citysim.engine.room;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    private String name;
    private String createdBy;
    private int maxPlayers;
    private String status;
    private Instant createdAt;

    public Long getId()                { return id; }
    public String getCode()            { return code; }
    public String getName()            { return name; }
    public String getCreatedBy()       { return createdBy; }
    public int getMaxPlayers()         { return maxPlayers; }
    public String getStatus()          { return status; }
    public Instant getCreatedAt()      { return createdAt; }

    public void setCode(String v)          { code = v; }
    public void setName(String v)          { name = v; }
    public void setCreatedBy(String v)     { createdBy = v; }
    public void setMaxPlayers(int v)       { maxPlayers = v; }
    public void setStatus(String v)        { status = v; }
    public void setCreatedAt(Instant v)    { createdAt = v; }
}
