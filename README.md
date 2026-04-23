# pathfinder

A comparative platform for shortest-path algorithms on a road network.

## Backend

- Spring Boot service that loads a road-network GeoJSON into an in-memory graph (adjacency list).
- Implements Dijkstra and A* on top of a pluggable edge-cost model with three objectives: distance, time, and balanced.
- Exposes REST endpoints for metadata, POI search, nearest-node snapping, and routing.

## Frontend

- React + TypeScript + Vite single-page app.
- Uses Leaflet to render the OSM basemap, search results, start/end pins, and the computed path.

## Running

See `server/` and `web/` for build and run instructions.
