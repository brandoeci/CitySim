export type LightState = 'GREEN' | 'YELLOW' | 'RED';

/**
 * Semaforo de un cruce mayor. Trae el color de CADA eje: cuando el horizontal
 * esta en verde, el vertical esta en rojo, como en un cruce real.
 */
export interface TrafficLight {
  id: string;
  x: number;
  y: number;
  horizontalState: LightState;
  verticalState: LightState;
}
