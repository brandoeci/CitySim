export type EventTypeName =
  | 'TRAFFIC_JAM'
  | 'ACCIDENT'
  | 'ROAD_CLOSURE'
  | 'VIP_CONVOY'
  | 'EMERGENCY';

export type EventStatusName = 'ACTIVE' | 'RESOLVED' | 'EXPIRED';

export const EVENT_LABELS: Record<EventTypeName, string> = {
  TRAFFIC_JAM: 'Hora pico',
  ACCIDENT: 'Accidente masivo',
  ROAD_CLOSURE: 'Cierre de vía',
  VIP_CONVOY: 'Convoy VIP',
  EMERGENCY: 'Emergencia'
};

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

  /** zoneId -> via que ese distrito debe cerrar. */
  targetEdges: Record<string, string>;

  /** Distritos que ya cumplieron su objetivo. */
  respondedBy: string[];

  /** La via que ESTE administrador debe cerrar (null si no le toca). */
  myTargetEdge: string | null;

  /** Si este administrador ya cumplio. */
  iResponded: boolean;
}
