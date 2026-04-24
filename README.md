# Pathfinder

A comparative platform for shortest-path algorithms on a road network.

**Stack**: Java Spring Boot | React 19 + Vite + TypeScript | Leaflet map visualization

---

## Setup

**Prerequisites**:

- JDK 17+ ([Eclipse Temurin](https://adoptium.net/) recommended)
- Node.js 18+
- PowerShell (built into Windows)

**Install dependencies**:

```powershell
cd server
.\mvnw.cmd --version          # verify JDK on PATH

cd ..\web
npm install
```

---

## Development

Two dev servers wired together by the Vite proxy.

**Start Backend** (API on `http://localhost:8080`):

```powershell
cd server
.\mvnw.cmd spring-boot:run
```

**Start Frontend** (Vite on `http://localhost:5173`, proxies `/api/*` to 8080):

```powershell
cd web
npm run dev
```

**Stop**: Press `Ctrl+C` in each terminal.

---

## Packaging

**Purpose**: Build a single Windows zip (no Java/Node required for end users).

**One command** (from repo root):

```powershell
powershell -ExecutionPolicy Bypass -File .\package.ps1
```

**What [`package.ps1`](package.ps1) does** (~60 seconds):

| Step | Action                                         | Output                                                 |
| ---- | ---------------------------------------------- | ------------------------------------------------------ |
| 1    | `npm run build`                                | `web/dist/` (static files)                             |
| 2    | Copy dist + GeoJSON into Spring Boot classpath | Ready for fat jar                                      |
| 3    | `mvn clean package`                            | `server/target/pathfinder-0.0.1-SNAPSHOT.jar` (~57 MB) |
| 4    | `jpackage` (embed JRE + launcher)              | `release/Pathfinder/` (exe + runtime + jar)            |
| 5    | `Compress-Archive`                             | `release/Pathfinder-0.0.1.zip` (~97 MB)                |

**Result**:

- `release/Pathfinder/` — runnable folder with `Pathfinder.exe`
- `release/Pathfinder-0.0.1.zip` — the deliverable

---

## Delivery

**For end users**:

1. Extract `Pathfinder-0.0.1.zip` (right-click → Extract All)
2. Open `Pathfinder/` → double-click `Pathfinder.exe`
3. Console window appears with startup logs
4. After ~10 seconds, default browser opens `http://localhost:8080/`
5. Close console to quit

**For testing locally**:

```powershell
.\release\Pathfinder\Pathfinder.exe
```

---

## Project Structure

| Path                | Role                                                                                |
| ------------------- | ----------------------------------------------------------------------------------- |
| `server/`           | Spring Boot: loads GeoJSON into graph, runs Dijkstra/A*, exposes `/api/*` endpoints |
| `web/`              | React + Leaflet: visualizes algorithm exploration and route                         |
| `data/full.geojson` | OSM road network                                                                    |
| `package.ps1`       | End-to-end build script (see [Packaging](#packaging))                               |
