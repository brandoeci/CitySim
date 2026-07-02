package edu.escuelaing.citysim.core.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Generates a synthetic Manhattan-style city grid programmatically.
 * Every 10th row/col is a highway (4 lanes, speed 2.0); rest are streets (2 lanes, speed 1.0).
 * Zones: zoneCols x zoneRows sub-grids, each covering (gridWidth/zoneCols) x (gridHeight/zoneRows) nodes.
 */
public class MapFactory {

    private static final Logger log = LoggerFactory.getLogger(MapFactory.class);

    private static final double BLOCK_SIZE   = 10.0;
    private static final double HIGHWAY_SPEED = 2.0;
    private static final double STREET_SPEED  = 1.0;
    private static final int    HIGHWAY_LANES  = 4;
    private static final int    STREET_LANES   = 2;
    private static final int    HIGHWAY_INTERVAL = 10;

    public static CityMap generate(int gridWidth, int gridHeight, int zoneCols, int zoneRows) {
        log.info("Generating city map {}x{} with {}x{} zones", gridWidth, gridHeight, zoneCols, zoneRows);
        long t0 = System.currentTimeMillis();

        int nodesPerZoneCol = gridWidth / zoneCols;
        int nodesPerZoneRow = gridHeight / zoneRows;

        Map<String, Node> nodes = new HashMap<>(gridWidth * gridHeight * 2);
        Map<String, Edge> edges = new HashMap<>(gridWidth * gridHeight * 4 * 2);
        Map<String, Zone> zones = new HashMap<>(zoneCols * zoneRows);

        Map<String, Set<String>> zoneNodeIds = new HashMap<>();
        Map<String, Set<String>> zoneEdgeIds = new HashMap<>();
        for (int zr = 0; zr < zoneRows; zr++) {
            for (int zc = 0; zc < zoneCols; zc++) {
                String zid = "Z_" + zr + "_" + zc;
                zoneNodeIds.put(zid, new HashSet<>());
                zoneEdgeIds.put(zid, new HashSet<>());
            }
        }

        Map<String, List<String>> outgoing = new HashMap<>(gridWidth * gridHeight * 2);
        Map<String, List<String>> incoming = new HashMap<>(gridWidth * gridHeight * 2);

        for (int row = 0; row < gridHeight; row++) {
            for (int col = 0; col < gridWidth; col++) {
                String nid = "N_" + row + "_" + col;
                outgoing.put(nid, new ArrayList<>(4));
                incoming.put(nid, new ArrayList<>(4));
                int zr = Math.min(row / nodesPerZoneRow, zoneRows - 1);
                int zc = Math.min(col / nodesPerZoneCol, zoneCols - 1);
                zoneNodeIds.get("Z_" + zr + "_" + zc).add(nid);
            }
        }

        for (int row = 0; row < gridHeight; row++) {
            for (int col = 0; col < gridWidth; col++) {
                String src = "N_" + row + "_" + col;
                boolean rowHighway = (row % HIGHWAY_INTERVAL == 0);
                boolean colHighway = (col % HIGHWAY_INTERVAL == 0);

                if (col + 1 < gridWidth) {
                    String tgt = "N_" + row + "_" + (col + 1);
                    String eid  = "E_H_"  + row + "_" + col;
                    String eidR = "E_H_"  + row + "_" + col + "_R";
                    Edge e  = buildEdge(eid,  src, tgt, rowHighway, row, col,     nodesPerZoneRow, nodesPerZoneCol, zoneRows, zoneCols);
                    Edge eR = buildEdge(eidR, tgt, src, rowHighway, row, col + 1, nodesPerZoneRow, nodesPerZoneCol, zoneRows, zoneCols);
                    edges.put(eid,  e);  edges.put(eidR, eR);
                    outgoing.get(src).add(eid);  incoming.get(tgt).add(eid);
                    outgoing.get(tgt).add(eidR); incoming.get(src).add(eidR);
                    zoneEdgeIds.get(e.zoneId()).add(eid);
                    zoneEdgeIds.get(eR.zoneId()).add(eidR);
                }

                if (row + 1 < gridHeight) {
                    String tgt = "N_" + (row + 1) + "_" + col;
                    String eid  = "E_V_"  + row + "_" + col;
                    String eidR = "E_V_"  + row + "_" + col + "_R";
                    Edge e  = buildEdge(eid,  src, tgt, colHighway, row,     col, nodesPerZoneRow, nodesPerZoneCol, zoneRows, zoneCols);
                    Edge eR = buildEdge(eidR, tgt, src, colHighway, row + 1, col, nodesPerZoneRow, nodesPerZoneCol, zoneRows, zoneCols);
                    edges.put(eid,  e);  edges.put(eidR, eR);
                    outgoing.get(src).add(eid);  incoming.get(tgt).add(eid);
                    outgoing.get(tgt).add(eidR); incoming.get(src).add(eidR);
                    zoneEdgeIds.get(e.zoneId()).add(eid);
                    zoneEdgeIds.get(eR.zoneId()).add(eidR);
                }
            }
        }

        for (int row = 0; row < gridHeight; row++) {
            for (int col = 0; col < gridWidth; col++) {
                String nid = "N_" + row + "_" + col;
                double x = col * BLOCK_SIZE;
                double y = row * BLOCK_SIZE;
                int zr = Math.min(row / nodesPerZoneRow, zoneRows - 1);
                int zc = Math.min(col / nodesPerZoneCol, zoneCols - 1);
                String zid = "Z_" + zr + "_" + zc;
                nodes.put(nid, new Node(nid, x, y, zid, true,
                        List.copyOf(outgoing.get(nid)),
                        List.copyOf(incoming.get(nid))));
            }
        }

        for (int zr = 0; zr < zoneRows; zr++) {
            for (int zc = 0; zc < zoneCols; zc++) {
                String zid = "Z_" + zr + "_" + zc;
                double minX = zc * nodesPerZoneCol * BLOCK_SIZE;
                double minY = zr * nodesPerZoneRow * BLOCK_SIZE;
                double maxX = Math.min(minX + nodesPerZoneCol * BLOCK_SIZE, (gridWidth  - 1) * BLOCK_SIZE);
                double maxY = Math.min(minY + nodesPerZoneRow * BLOCK_SIZE, (gridHeight - 1) * BLOCK_SIZE);
                zones.put(zid, new Zone(zid, zr, zc, minX, minY, maxX, maxY,
                        Set.copyOf(zoneNodeIds.get(zid)),
                        Set.copyOf(zoneEdgeIds.get(zid))));
            }
        }

        log.info("Map generated in {}ms: {} nodes, {} edges, {} zones",
                System.currentTimeMillis() - t0, nodes.size(), edges.size(), zones.size());
        return new CityMap(nodes, edges, zones, gridWidth, gridHeight);
    }

    private static Edge buildEdge(String eid, String src, String tgt, boolean highway,
                                   int srcRow, int srcCol,
                                   int nodesPerZoneRow, int nodesPerZoneCol,
                                   int zoneRows, int zoneCols) {
        int zr = Math.min(srcRow / nodesPerZoneRow, zoneRows - 1);
        int zc = Math.min(srcCol / nodesPerZoneCol, zoneCols - 1);
        String zid = "Z_" + zr + "_" + zc;
        return new Edge(eid, src, tgt, BLOCK_SIZE,
                highway ? HIGHWAY_SPEED : STREET_SPEED,
                highway ? HIGHWAY_LANES : STREET_LANES,
                highway, zid);
    }
}
