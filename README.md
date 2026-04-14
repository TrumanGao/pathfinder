# pathfinder

A comparative platform for pathfinding algorithms.

## Structure

### server

- Spring Boot application that implements Dijkstra, A\*, and Bidirectional BFS algorithms.
- RESTful APIs for the frontend to send requests and fetch algorithm results and performance metrics.
- The backend also integrates with a large language model API to generate analysis of the algorithm results.

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
