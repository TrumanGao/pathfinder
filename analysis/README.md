# Pathfinder — Data Structures & Algorithms Analysis

Supplementary material for the course project. This folder contains the
theoretical analysis and empirical benchmarks that back the implementation
choices in `server/`. Nothing here is shipped as part of the running
application — these are the project's *why* documents.

## Contents

| File | What's inside |
|---|---|
| [complexity.md](complexity.md) | Asymptotic analysis of every data structure and algorithm in the project, plus the reasoning for each choice over alternatives. |
| [admissibility.md](admissibility.md) | Per-objective proof sketches that the A* heuristic is admissible (or identifies the case where it is not). |
| [benchmark.md](benchmark.md) | Benchmark methodology, raw results, interpretation, and what the numbers confirm about the theoretical predictions. |
| `results/` | Raw CSV output from benchmark runs — one file per run, timestamped. |

## How to reproduce the benchmarks

The benchmark harness lives under the server module's test sources so it
can reach the production classes; nothing leaks into the shipped jar.

```bash
cd server
./mvnw.cmd test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=edu.northeastern.pathfinder.benchmark.BenchmarkTool \
  -Dexec.args="--pairs 200 --seed 42 --out ../analysis/results/benchmark-latest.csv"
```

On Windows, you can also run it directly if your IDE launches the
`main()` method of `BenchmarkTool` with the test classpath.

Arguments:
- `--pairs N` — number of (start, end) pairs to route (default 100)
- `--seed S` — RNG seed for reproducible pair selection (default 42)
- `--out PATH` — CSV output path (default stdout)
- `--geojson PATH` — override the graph source (default `../data/full.geojson`)

## Quick scorecard

| Aspect | Claim | Evidence |
|---|---|---|
| Adjacency list over matrix | Edge density ≈ 2e-6, matrix would be 28 TB | [complexity.md § graph](complexity.md#graph-representation) |
| kd-tree over linear scan | 1.88M nodes → O(log N) vs O(N) | [benchmark.md § nearest](benchmark.md#nearest-neighbor) |
| A* is optimal on distance/time | Haversine is admissible | [admissibility.md](admissibility.md) |
| Bidirectional halves work | Explored edges ≈ 1/2 of Dijkstra on average | [benchmark.md § routing](benchmark.md#routing-algorithms) |
| Binary cache is worth it | 7.5× faster graph load | [benchmark.md § cache](benchmark.md#graph-cache) |
