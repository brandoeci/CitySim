# CitySim

Simulador de tráfico urbano distribuido en tiempo real, construido sobre una
**Space-Based Architecture (SBA)**. Ciudad tipo Manhattan, miles de vehículos
enrutados con A*, semáforos por fase, y varios usuarios administrando zonas
distintas de la misma ciudad al mismo tiempo, viendo los cambios de los demás
en vivo vía WebSocket.

Proyecto académico — **Arquitecturas de Software (ARSW)**, Escuela Colombiana
de Ingeniería Julio Garavito.

## Arquitectura

El estado de la simulación (autos, semáforos, vías bloqueadas, eventos) vive
en un grid de datos en memoria (Hazelcast), no en una base de datos central.
Cada zona de la ciudad la procesa de forma autónoma una `ZoneProcessingUnit`;
`ZoneRegistry` reparte las zonas entre las instancias del backend disponibles,
así que agregar instancias escala la capacidad sin rediseñar nada. Postgres
solo guarda lo asíncrono y durable (usuarios, salas) — la ruta caliente de la
simulación nunca toca disco.

## Estructura del proyecto

```
City-Simulator/
├── backend/                           Backend Spring Boot (multi-módulo Maven, Java 21)
│   ├── pom.xml                        POM padre — módulos, versiones compartidas
│   ├── simulation-core/                Modelo de dominio puro, sin Spring
│   │   └── src/main/java/edu/escuelaing/citysim/core/
│   │       ├── map/                   CityMap, construcción de la grilla de la ciudad
│   │       ├── model/                 CarState, TrafficLightPhase, SimulationFrame, eventos...
│   │       ├── pathfinding/           A* sobre el grafo de la ciudad
│   │       └── sba/                   Interfaz SpaceDataGrid (el "espacio" de SBA)
│   │
│   └── simulation-engine/              Motor de simulación + API REST/WebSocket (Spring Boot)
│       └── src/main/java/edu/escuelaing/citysim/engine/
│           ├── auth/                  JWT, registro/login
│           ├── car/                   CarAgent (movimiento), CollisionAvoider
│           ├── config/                Propiedades de simulación, seguridad
│           ├── event/                 Eventos colaborativos (cierre de vía, escudo, evacuación...)
│           ├── persistence/           Repositorios JPA (Postgres)
│           ├── room/                  Salas multiusuario: RoomManager, RoomSimulation
│           ├── simulation/            SimulationOrchestrator, FramePublisher
│           ├── space/                 HazelcastSpaceDataGrid (implementación del "espacio")
│           ├── traffic/               TrafficLightController
│           ├── web/                   Controllers REST (auth, tools, events, rooms, status)
│           └── zone/                  ZoneProcessingUnit, ZoneRegistry, DistrictService
│
├── frontend/                          Angular (standalone components + signals)
│   └── src/app/
│       ├── core/
│       │   ├── interceptors/          JWT en cada request
│       │   ├── models/                Tipos compartidos (CarState, TrafficLight, Room...)
│       │   └── services/              Auth, WebSocket/STOMP, herramientas, simulación
│       └── features/
│           ├── auth/                  Login / registro
│           ├── city-view/
│           │   └── city-canvas/       Canvas + Web Worker de renderizado (simulation.worker.ts)
│           ├── control-panel/         Panel del admin: FPS, autos, tick, sala
│           ├── events/                Panel de eventos colaborativos
│           ├── room/                  Crear / unirse a sala
│           └── tools/                 Toolbar de herramientas administrativas
│
├── deploy/                            Config para el despliegue manual en Azure Container Instances
│   ├── Dockerfile.hazelcast
│   ├── Dockerfile.web
│   └── aci-nginx.conf
│
├── docs/
│   ├── Sustentacion_ARSW_CitySimulator.pptx
│   ├── SONARCLOUD_SETUP.md            Cómo está conectado SonarCloud y por qué no vía CI
│   └── diagrama_caso_de_uso_tiempo_real.drawio
│
├── hazelcast/                         Config del clúster Hazelcast (local / escalado)
├── nginx/                             Proxy reverso para docker-compose (API + WebSocket + estáticos)
├── docker-compose.yml                 Stack completo: hz-node, backend, postgres, frontend, nginx
├── docker-compose.scale.yml           Overlay de escalado (6 backends + 4 nodos Hazelcast)
└── .github/workflows/ci.yml           CI: build + tests backend, type-check + build frontend
```

## Cómo correrlo

```bash
# Backend: siempre compilar el JAR antes (el Dockerfile lo copia, no compila Maven)
cd backend && mvn clean package -DskipTests && cd ..

# Levantar todo el stack
docker compose up --build
```

Frontend en `http://localhost:8080` (servido detrás del proxy nginx, que enruta
`/api` y `/ws` al backend).

Para probar escalado (más instancias de backend + más nodos Hazelcast):

```bash
docker compose -f docker-compose.yml -f docker-compose.scale.yml up --build
```

## Pruebas y calidad de código

```bash
cd backend && mvn clean verify      # tests unitarios + integración, cobertura JaCoCo
cd frontend && npx tsc --noEmit     # type-check
```

CI corre ambos en cada push a `main`/`master` (`.github/workflows/ci.yml`).
El análisis de SonarCloud corre en modo automático directo desde GitHub — ver
[`docs/SONARCLOUD_SETUP.md`](docs/SONARCLOUD_SETUP.md).

## Despliegue

La demo en vivo corre en Azure Container Instances (Dockerfiles y config en
`deploy/`, no versionados como imágenes — se construyen a partir de este
repo). El despliegue es manual; no hay CD automatizado.
