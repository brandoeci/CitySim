package edu.escuelaing.citysim.engine.auth;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column
    private String zoneId;

    public Long getId()              { return id; }
    public String getUsername()      { return username; }
    public String getPassword()      { return password; }
    public String getZoneId()        { return zoneId; }
    public void setUsername(String v){ username = v; }
    public void setPassword(String v){ password = v; }
    public void setZoneId(String v)  { zoneId = v; }
}