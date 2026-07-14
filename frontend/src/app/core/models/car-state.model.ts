export type CarStatus = 'MOVING' | 'WAITING_LIGHT' | 'WAITING_TRAFFIC' | 'ARRIVED' | 'SPAWNING';

export interface CarDelta {
  carId: string;
  x: number;
  y: number;
  heading: number;
  status: CarStatus;
  color: string;
}

export interface SimulationFrame {
  frameId: number;
  timestamp: number;
  deltas: CarDelta[];
  removedCarIds: string[];
  totalCars: number;
  fps: number;
}

export interface CarState {
  carId: string;
  x: number;
  y: number;
  heading: number;
  status: CarStatus;
  color: string;
}
