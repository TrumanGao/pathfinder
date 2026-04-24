# pathfinder

A comparative platform for shortest-path algorithms on a road network.

## Structure

### server

- Spring Boot service that loads a road-network GeoJSON into an in-memory graph (adjacency list).
- Implements Dijkstra and A* on top of a pluggable edge-cost model with three objectives: distance, time, and balanced.
- Exposes REST endpoints for metadata, POI search, nearest-node snapping, and routing.

### web

- React application that visualizes the pathfinding process and results.
- Uses Vite for fast development and build.
- Renders the exploration process and final path with different colors based on the data received from the backend.

## Getting Started

### server

```powershell
cd server
.\mvnw.cmd spring-boot:run
```

`http://localhost:8080`。

### web

```powershell
cd web
npm install
npm run dev
```
