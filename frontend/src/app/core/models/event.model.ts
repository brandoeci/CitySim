export type EventTypeName =
  | 'TRAFFIC_JAM'
  | 'ACCIDENT'
  | 'ROAD_CLOSURE'
  | 'EMERGENCY'
  | 'VIP_CONVOY'
  | 'AREA_SHIELD'
  | 'EVACUATION'
  | 'GRIDLOCK';

export type EventStatusName = 'ACTIVE' | 'RESOLVED' | 'EXPIRED' | 'FAILED';

export const EVENT_LABELS: Record<EventTypeName, string> = {
  TRAFFIC_JAM: 'Hora pico',
  ACCIDENT: 'Accidente masivo',
  ROAD_CLOSURE: 'Cierre de vía',
  EMERGENCY: 'Emergencia',
  VIP_CONVOY: 'Convoy VIP',
  AREA_SHIELD: 'Zona protegida',
  EVACUATION: 'Evacuación',
  GRIDLOCK: 'Descongestión'
};

export type ObjectiveKind =
  | 'CLOSE_EDGE'
  | 'SHIELD_AREA'
  | 'CLEAR_CORRIDOR'
  | 'EVACUATE_AREA'
  | 'RELIEVE_JUNCTION';

/** Objetivo de un distrito dentro de un evento. Segun `kind` solo aplican algunos campos. */
export interface EventObjective {
  zoneId: string;
  kind: ObjectiveKind;
  /** CLOSE_EDGE: [via a cerrar]. CLEAR_CORRIDOR: cadena de tramos del corredor. */
  edgeIds: string[];
  /** SHIELD_AREA / EVACUATE_AREA: rectangulo del area. */
  minX: number | null;
  minY: number | null;
  maxX: number | null;
  maxY: number | null;
  /** RELIEVE_JUNCTION: nodo del cruce. */
  intersectionId: string | null;
  /** Tolerancia / segundos requeridos / umbral, fijo al generar el evento. */
  threshold: number;
  /** Contador vivo. */
  current: number;
  completed: boolean;
  failed: boolean;
}

/** Lo que llega por /api/events/active y por /topic/events. */
export interface ActiveEvent {
  eventId: number;
  type: EventTypeName;
  status: EventStatusName;
  affectedZoneId: string;
  description: string;
  durationSeconds: number;
  requiredActions: number;
  totalActions: number;
  progressPercent: number;
  startedAt: string | null;

  /** zoneId -> objetivo que ese distrito debe cumplir. */
  objectives: Record<string, EventObjective>;

  /** El objetivo de ESTE administrador (null si no le toca ninguno). */
  myObjective: EventObjective | null;

  /** Si este administrador ya cumplio. */
  iCompleted: boolean;
}
