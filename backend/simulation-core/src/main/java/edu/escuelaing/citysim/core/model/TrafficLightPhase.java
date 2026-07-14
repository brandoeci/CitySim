package edu.escuelaing.citysim.core.model;

import java.io.Serializable;

/**
 * Semaforo de una interseccion.
 *
 * El campo 'state' representa la fase del EJE HORIZONTAL. El eje vertical es
 * siempre su opuesto: cuando el horizontal esta en verde, el vertical esta en
 * rojo, igual que en un cruce real. Asi basta una maquina de estados por
 * interseccion en lugar de cuatro semaforos independientes que habria que
 * mantener sincronizados.
 *
 * Ciclo completo:
 *   H verde  / V rojo    (greenDuration)
 *   H amarillo / V rojo  (yellowDuration)
 *   H rojo  / V verde    (greenDuration)
 *   H rojo  / V amarillo (yellowDuration)
 */
public class TrafficLightPhase implements Serializable {

    private String intersectionId;

    /** Fase del eje horizontal. */
    private TrafficLightState state;

    /** True cuando el eje vertical tiene el paso (el horizontal esta en rojo). */
    private boolean verticalTurn;

    private int ticksInCurrentState;
    private int greenDuration;
    private int yellowDuration;
    private int redDuration;

    public TrafficLightPhase() {}

    private TrafficLightPhase(Builder b) {
        this.intersectionId = b.intersectionId;
        this.state = b.state;
        this.verticalTurn = b.verticalTurn;
        this.ticksInCurrentState = b.ticksInCurrentState;
        this.greenDuration = b.greenDuration;
        this.yellowDuration = b.yellowDuration;
        this.redDuration = b.redDuration;
    }

    public String getIntersectionId()    { return intersectionId; }
    public TrafficLightState getState()  { return state; }
    public boolean isVerticalTurn()      { return verticalTurn; }
    public int getTicksInCurrentState()  { return ticksInCurrentState; }
    public int getGreenDuration()        { return greenDuration; }
    public int getYellowDuration()       { return yellowDuration; }
    public int getRedDuration()          { return redDuration; }

    public void setIntersectionId(String v)    { intersectionId = v; }
    public void setState(TrafficLightState v)  { state = v; }
    public void setVerticalTurn(boolean v)     { verticalTurn = v; }
    public void setTicksInCurrentState(int v)  { ticksInCurrentState = v; }
    public void setGreenDuration(int v)        { greenDuration = v; }
    public void setYellowDuration(int v)       { yellowDuration = v; }
    public void setRedDuration(int v)          { redDuration = v; }

    /** Color que ve un carro que llega por el eje indicado. */
    public TrafficLightState stateFor(boolean horizontal) {
        boolean myTurn = (horizontal != verticalTurn);
        if (!myTurn) return TrafficLightState.RED;
        return state;
    }

    /** True si el carro que llega por ese eje debe detenerse. */
    public boolean isRedFor(boolean horizontal) {
        TrafficLightState s = stateFor(horizontal);
        return s == TrafficLightState.RED || s == TrafficLightState.YELLOW;
    }

    // Compatibilidad con codigo que aun no distingue el eje.
    public boolean isGreen() { return state == TrafficLightState.GREEN; }
    public boolean isRed()   { return state == TrafficLightState.RED || state == TrafficLightState.YELLOW; }

    /**
     * Avanza el ciclo. Al terminar el amarillo, el paso cambia de eje.
     */
    public TrafficLightPhase advance() {
        int nextTicks = ticksInCurrentState + 1;
        TrafficLightState nextState = state;
        boolean nextVertical = verticalTurn;

        switch (state) {
            case GREEN -> {
                if (nextTicks >= greenDuration) {
                    nextState = TrafficLightState.YELLOW;
                    nextTicks = 0;
                }
            }
            case YELLOW -> {
                if (nextTicks >= yellowDuration) {
                    // Fin del amarillo: el paso pasa al otro eje.
                    nextState = TrafficLightState.GREEN;
                    nextVertical = !verticalTurn;
                    nextTicks = 0;
                }
            }
            case RED -> {
                if (nextTicks >= redDuration) {
                    nextState = TrafficLightState.GREEN;
                    nextTicks = 0;
                }
            }
        }

        return new Builder(this)
                .state(nextState)
                .verticalTurn(nextVertical)
                .ticksInCurrentState(nextTicks)
                .build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String intersectionId;
        private TrafficLightState state = TrafficLightState.GREEN;
        private boolean verticalTurn = false;
        private int ticksInCurrentState = 0;
        private int greenDuration  = 60;
        private int yellowDuration = 8;
        private int redDuration    = 60;

        public Builder() {}

        public Builder(TrafficLightPhase p) {
            this.intersectionId = p.intersectionId;
            this.state = p.state;
            this.verticalTurn = p.verticalTurn;
            this.ticksInCurrentState = p.ticksInCurrentState;
            this.greenDuration = p.greenDuration;
            this.yellowDuration = p.yellowDuration;
            this.redDuration = p.redDuration;
        }

        public Builder intersectionId(String v)       { intersectionId = v; return this; }
        public Builder state(TrafficLightState v)     { state = v; return this; }
        public Builder verticalTurn(boolean v)        { verticalTurn = v; return this; }
        public Builder ticksInCurrentState(int v)     { ticksInCurrentState = v; return this; }
        public Builder greenDuration(int v)           { greenDuration = v; return this; }
        public Builder yellowDuration(int v)          { yellowDuration = v; return this; }
        public Builder redDuration(int v)             { redDuration = v; return this; }

        public TrafficLightPhase build() { return new TrafficLightPhase(this); }
    }
}