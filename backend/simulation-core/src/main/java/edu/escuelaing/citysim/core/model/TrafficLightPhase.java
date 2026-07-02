package edu.escuelaing.citysim.core.model;

import java.io.Serializable;

public class TrafficLightPhase implements Serializable {

    private String intersectionId;
    private TrafficLightState state;
    private int ticksInCurrentState;
    private int greenDuration;
    private int yellowDuration;
    private int redDuration;

    public TrafficLightPhase() {}

    private TrafficLightPhase(Builder b) {
        this.intersectionId = b.intersectionId;
        this.state = b.state;
        this.ticksInCurrentState = b.ticksInCurrentState;
        this.greenDuration = b.greenDuration;
        this.yellowDuration = b.yellowDuration;
        this.redDuration = b.redDuration;
    }

    public String getIntersectionId()    { return intersectionId; }
    public TrafficLightState getState()  { return state; }
    public int getTicksInCurrentState()  { return ticksInCurrentState; }
    public int getGreenDuration()        { return greenDuration; }
    public int getYellowDuration()       { return yellowDuration; }
    public int getRedDuration()          { return redDuration; }

    public void setIntersectionId(String v)    { intersectionId = v; }
    public void setState(TrafficLightState v)  { state = v; }
    public void setTicksInCurrentState(int v)  { ticksInCurrentState = v; }
    public void setGreenDuration(int v)        { greenDuration = v; }
    public void setYellowDuration(int v)       { yellowDuration = v; }
    public void setRedDuration(int v)          { redDuration = v; }

    public boolean isGreen() { return state == TrafficLightState.GREEN; }
    public boolean isRed()   { return state == TrafficLightState.RED || state == TrafficLightState.YELLOW; }

    public TrafficLightPhase advance() {
        int nextTicks = ticksInCurrentState + 1;
        TrafficLightState nextState = state;
        switch (state) {
            case GREEN  -> { if (nextTicks >= greenDuration)  { nextState = TrafficLightState.YELLOW; nextTicks = 0; } }
            case YELLOW -> { if (nextTicks >= yellowDuration) { nextState = TrafficLightState.RED;    nextTicks = 0; } }
            case RED    -> { if (nextTicks >= redDuration)    { nextState = TrafficLightState.GREEN;  nextTicks = 0; } }
        }
        return new Builder(this).state(nextState).ticksInCurrentState(nextTicks).build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String intersectionId;
        private TrafficLightState state = TrafficLightState.RED;
        private int ticksInCurrentState = 0;
        private int greenDuration  = 40;
        private int yellowDuration = 5;
        private int redDuration    = 40;

        public Builder() {}

        public Builder(TrafficLightPhase p) {
            this.intersectionId = p.intersectionId; this.state = p.state;
            this.ticksInCurrentState = p.ticksInCurrentState;
            this.greenDuration = p.greenDuration; this.yellowDuration = p.yellowDuration;
            this.redDuration = p.redDuration;
        }

        public Builder intersectionId(String v)       { intersectionId = v; return this; }
        public Builder state(TrafficLightState v)     { state = v; return this; }
        public Builder ticksInCurrentState(int v)     { ticksInCurrentState = v; return this; }
        public Builder greenDuration(int v)           { greenDuration = v; return this; }
        public Builder yellowDuration(int v)          { yellowDuration = v; return this; }
        public Builder redDuration(int v)             { redDuration = v; return this; }

        public TrafficLightPhase build() { return new TrafficLightPhase(this); }
    }
}
