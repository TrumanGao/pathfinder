# pathfinder

A comparative platform for pathfinding algorithms.

## 后端

- Spring Boot application that implements Dijkstra, A\*, and Bidirectional BFS algorithms.
- RESTful APIs for the frontend to send requests and fetch algorithm results and performance metrics.
- The backend also integrates with a large language model API to generate analysis of the algorithm results.

## 前端

- React application that visualizes the pathfinding process and results.
- Uses Vite for fast development and build.
- Renders the exploration process and final path with different colors based on the data received from the backend.
