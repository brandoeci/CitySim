export interface ZoneInfo {
  id: string;
  x: number;
  y: number;
  w: number;
  h: number;
  owner: string;
}

/** Un tramo de via. Ahora trae su id, para poder cerrarlo con la herramienta. */
export interface RoadSegment {
  id: string;
  zoneId: string;
  lanes: number;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

export interface CityMapData {
  width: number;
  height: number;
  gridWidth: number;
  gridHeight: number;
  highways: RoadSegment[];
  streets: RoadSegment[];
  zones: ZoneInfo[];
  blockedEdges: Record<string, string>;  // edgeId -> username que la cerro
}
