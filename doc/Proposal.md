# Group Project Proposal

**Project Leader:** Junyao Yang
**Group Members:** Feixiang Gao, Maier Shi, Junyao Yang

---

## 1. Project Overview

We plan to build a lightweight navigation web application that can compute routes on a road network and show the result on an interactive map.

Instead of using a relational database, the backend will load a static dataset (nodes and edges) into an **in-memory graph** when the server starts. The backend will run classic graph routing algorithms (e.g., **Dijkstra** and **A\***). The computed route and basic metrics will be returned to the frontend.

**Stretch goal (optional):** If time allows, we will call the **OpenAI API** to generate short, context-aware travel notes based on the route result and user inputs.

---

## 2. Data Structure and Algorithms

### 2.1 Core Data Structure

- **Graph (in memory)**
  - `Node`: id, latitude, longitude (and optional attributes)
  - `Edge`: from, to, distance/weight (and optional attributes)
- Planned representation:
  - **Adjacency list**, for example `HashMap<NodeId, List<Edge>>`

### 2.2 Algorithms (Planned)

- **Dijkstra**: shortest path baseline (non-negative edge weights)
- **A\***: heuristic shortest path (using straight-line distance as the heuristic)

**Stretch goal (optional):**

- Multi-stop routing (TSP-like). If implemented, it will likely be a simplified version due to time constraints.

---

## 3. System Architecture (High Level)

### 3.1 Backend (Java Spring Boot)

- Load dataset files (`.csv` / `.json`) into memory at startup
- Provide stateless REST APIs, such as:
  - `GET /route?start=...&end=...&algo=dijkstra|astar`
- Response (planned):
  - route coordinates/polyline
  - basic metrics (path length, nodes visited, runtime)
- **Optional AI step (stretch):**
  - call OpenAI API to generate a short explanation or travel notes based on the route output

### 3.2 Frontend (React)

- Render the map and draw the computed route using Leaflet / React-Leaflet
- Allow the user to select start/end locations (simple version: two inputs or clicking on map)
- Display metrics and (if enabled) AI-generated text

### 3.3 Data Source

- Static local files stored in the backend project (no database)
- Data parsing:
  - Java file I/O
  - libraries like Jackson / OpenCSV (or similar)

---

## 4. Technology Stack & Environment

- **Frontend:** React (Vite), Leaflet / React-Leaflet
- **Backend:** Java Spring Boot (REST APIs, algorithm logic)
- **Data:** Local `.csv` / `.json` files (loaded into memory)
- **AI (stretch):** OpenAI API (backend-to-backend request via WebClient or RestTemplate)
- **Dataset preparation (offline):** Python + OSMnx to extract nodes/edges from OpenStreetMap

---

## 5. Implementation Plan (Timeline)

### Phase 1 — Setup & Data Model

- Define shared data model (`Node`, `Edge`) and graph representation
- Prepare small test datasets (including a tiny synthetic graph for early testing)
- Create an initial API contract and frontend mock rendering

### Phase 2 — Routing Algorithms & Map Visualization

- Implement and test:
  - Dijkstra
  - A\*
- Connect backend output to the frontend map:
  - draw route line
  - show basic metrics

### Phase 3 — Integration & Polishing

- Clean up API responses and handle edge cases (invalid inputs, no path, etc.)
- **Optional:** integrate OpenAI for short route explanation / travel notes
- Final demo preparation (end-to-end route query → map display)

---

## 6. Expected Deliverables / Demo

At the end of the project, we expect to demonstrate:

1. Load a road graph from static files into memory
2. Compute a route (Dijkstra or A\*) between two points
3. Show the route on an interactive map and report basic metrics  
4. **(Optional)** Display AI-generated short notes related to the route

---
