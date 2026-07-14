export interface District {
  index: number;
  username: string;
  zoneIds: string[];
  minCol: number;
  maxCol: number;
}

export interface PresenceState {
  districts: District[];
  myDistrictIndex: number;
  activeUsers: number;
  maxUsers: number;
}
