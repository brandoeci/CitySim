package edu.escuelaing.citysim.core.model;

import java.io.Serializable;
import java.util.List;

public class CarState implements Serializable {

    private String carId;
    private double x;
    private double y;
    private double heading;
    private double speed;
    private String currentZoneId;
    private String currentEdgeId;
    private double segmentOffset;
    private int laneIndex;
    private List<String> pathNodes;
    private int pathIndex;
    private CarStatus status;
    private long lastUpdatedTick;
    private String color;
    private String originNodeId;
    private String destinationNodeId;

    public CarState() {}

    private CarState(Builder b) {
        this.carId = b.carId; this.x = b.x; this.y = b.y;
        this.heading = b.heading; this.speed = b.speed;
        this.currentZoneId = b.currentZoneId; this.currentEdgeId = b.currentEdgeId;
        this.segmentOffset = b.segmentOffset; this.laneIndex = b.laneIndex;
        this.pathNodes = b.pathNodes; this.pathIndex = b.pathIndex;
        this.status = b.status; this.lastUpdatedTick = b.lastUpdatedTick;
        this.color = b.color; this.originNodeId = b.originNodeId;
        this.destinationNodeId = b.destinationNodeId;
    }

    public String getCarId()              { return carId; }
    public double getX()                  { return x; }
    public double getY()                  { return y; }
    public double getHeading()            { return heading; }
    public double getSpeed()              { return speed; }
    public String getCurrentZoneId()      { return currentZoneId; }
    public String getCurrentEdgeId()      { return currentEdgeId; }
    public double getSegmentOffset()      { return segmentOffset; }
    public int getLaneIndex()             { return laneIndex; }
    public List<String> getPathNodes()    { return pathNodes; }
    public int getPathIndex()             { return pathIndex; }
    public CarStatus getStatus()          { return status; }
    public long getLastUpdatedTick()      { return lastUpdatedTick; }
    public String getColor()              { return color; }
    public String getOriginNodeId()       { return originNodeId; }
    public String getDestinationNodeId()  { return destinationNodeId; }

    public void setCarId(String v)             { carId = v; }
    public void setX(double v)                 { x = v; }
    public void setY(double v)                 { y = v; }
    public void setHeading(double v)           { heading = v; }
    public void setSpeed(double v)             { speed = v; }
    public void setCurrentZoneId(String v)     { currentZoneId = v; }
    public void setCurrentEdgeId(String v)     { currentEdgeId = v; }
    public void setSegmentOffset(double v)     { segmentOffset = v; }
    public void setLaneIndex(int v)            { laneIndex = v; }
    public void setPathNodes(List<String> v)   { pathNodes = v; }
    public void setPathIndex(int v)            { pathIndex = v; }
    public void setStatus(CarStatus v)         { status = v; }
    public void setLastUpdatedTick(long v)     { lastUpdatedTick = v; }
    public void setColor(String v)             { color = v; }
    public void setOriginNodeId(String v)      { originNodeId = v; }
    public void setDestinationNodeId(String v) { destinationNodeId = v; }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String carId; private double x, y, heading, speed;
        private String currentZoneId, currentEdgeId;
        private double segmentOffset; private int laneIndex;
        private List<String> pathNodes; private int pathIndex;
        private CarStatus status; private long lastUpdatedTick;
        private String color, originNodeId, destinationNodeId;

        public Builder() {}

        public Builder(CarState s) {
            this.carId = s.carId; this.x = s.x; this.y = s.y;
            this.heading = s.heading; this.speed = s.speed;
            this.currentZoneId = s.currentZoneId; this.currentEdgeId = s.currentEdgeId;
            this.segmentOffset = s.segmentOffset; this.laneIndex = s.laneIndex;
            this.pathNodes = s.pathNodes; this.pathIndex = s.pathIndex;
            this.status = s.status; this.lastUpdatedTick = s.lastUpdatedTick;
            this.color = s.color; this.originNodeId = s.originNodeId;
            this.destinationNodeId = s.destinationNodeId;
        }

        public Builder carId(String v)              { carId = v; return this; }
        public Builder x(double v)                  { x = v; return this; }
        public Builder y(double v)                  { y = v; return this; }
        public Builder heading(double v)            { heading = v; return this; }
        public Builder speed(double v)              { speed = v; return this; }
        public Builder currentZoneId(String v)      { currentZoneId = v; return this; }
        public Builder currentEdgeId(String v)      { currentEdgeId = v; return this; }
        public Builder segmentOffset(double v)      { segmentOffset = v; return this; }
        public Builder laneIndex(int v)             { laneIndex = v; return this; }
        public Builder pathNodes(List<String> v)    { pathNodes = v; return this; }
        public Builder pathIndex(int v)             { pathIndex = v; return this; }
        public Builder status(CarStatus v)          { status = v; return this; }
        public Builder lastUpdatedTick(long v)      { lastUpdatedTick = v; return this; }
        public Builder color(String v)              { color = v; return this; }
        public Builder originNodeId(String v)       { originNodeId = v; return this; }
        public Builder destinationNodeId(String v)  { destinationNodeId = v; return this; }

        public CarState build() { return new CarState(this); }
    }
}
