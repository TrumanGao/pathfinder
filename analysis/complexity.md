# Complexity Analysis

This document walks through each data structure and algorithm used in
the pathfinder server, states its asymptotic complexity, and justifies
the choice over alternatives that might seem more familiar from lecture.

Throughout: **V** = number of nodes, **E** = number of edges. Current
dataset: V ≈ 1.88 × 10⁶, E ≈ 3.96 × 10⁶ (directed segments after the
`oneway` direction policy).

---

## Graph representation

**Choice.** Adjacency list as `Map<String, List<Edge>>`
([Graph.java](../server/src/main/java/edu/northeastern/pathfinder/graph/Graph.java)).

| Representation | Space | `getOutgoing(v)` | `hasEdge(u,v)` |
|---|---|---|---|
| Adjacency matrix | Θ(V²) | Θ(V) | Θ(1) |
| **Adjacency list** | **Θ(V + E)** | **O(deg(v))** | O(deg(v)) |

For this graph, V² ≈ 3.55 × 10¹². A dense matrix would need roughly
**28 TB** at one bit per edge. Adjacency list fits in ~1 GB with the
string-interned representation and is what every serious routing
engine (OSRM, GraphHopper, OSMnx) uses. The `hasEdge` degradation is
irrelevant — shortest-path algorithms only ever iterate outgoing edges,
never ask "does edge (u,v) exist?".

**Reverse adjacency** is built lazily on first call to `getIncoming()`
in O(V + E) and cached. Bidirectional search is the only consumer;
unidirectional queries never pay for it.

### Why string node ids?

Node ids come from the GeoJSON's coordinate string and carry geographic
meaning. Using `String` costs roughly **80 bytes per id** (16B header +
char[] + pointer + HashMap entry), versus ~4 bytes for an `int` index.
At 1.88 M nodes this is ~150 MB of overhead.

The alternative — a bidirectional `String ↔ int` mapping — would cut
heap usage roughly 5× but introduces a translation layer at every
public API call. For a course project with the graph under 2 GB in
heap, the readability win outweighs the memory cost. In a production
setting at 10× the scale, the trade flips.

---

## Priority queue for Dijkstra / A* / Bi-Dijkstra

**Choice.** JDK `PriorityQueue` (binary min-heap).

| Operation | Binary heap | Fibonacci heap | d-ary heap (d=4) |
|---|---|---|---|
| Insert | O(log V) | **O(1) amortised** | O(log_d V) |
| Extract-min | O(log V) | O(log V) amortised | O(d · log_d V) |
| Decrease-key | O(log V) | **O(1) amortised** | O(log_d V) |

**Dijkstra total:** O((V + E) log V) with binary heap,
**O(V log V + E)** theoretical best with Fibonacci heap.

Despite the asymptotic advantage, Fibonacci heaps lose to binary heaps
in practice on every real benchmark — the hidden constants (per-node
pointer overhead, cascading cuts) dominate at realistic sizes. We use
the "lazy deletion" trick (push a fresh entry instead of doing a real
decrease-key, skip stale entries on pop), which is what JDK's heap
supports efficiently.

---

## Dijkstra's algorithm

Single-source, non-negative edge costs.

**Complexity.** O((V + E) log V) with binary heap.

**Correctness invariant.** When a node is extracted from the priority
queue, its `dist[v]` is final. The proof is the standard one:
non-negativity guarantees that any later relaxation of `v` would have
to go through a node `u` with `dist[u] ≥ dist[v]`, and edges can only
add cost.

**Why it fits.** The pathfinder cost model always yields non-negative
edge costs for every objective (distance is a metric, time is
distance/speed with positive speed, balanced is a convex combination
of two non-negative terms, safe-walk multiplies a non-negative base by
a positive safety factor). The prerequisite holds.

---

## A*

**Complexity.** Worst case same as Dijkstra: O((V + E) log V).
Average case: fewer nodes expanded by a factor that depends on how
well the heuristic approximates the true cost-to-go.

**Optimality.** A* returns the shortest path iff the heuristic `h` is
**admissible** — i.e. `h(v) ≤ d*(v, t)` for every `v`, where `d*` is
the true cost from `v` to the target `t`.

The per-objective admissibility analysis is in
[admissibility.md](admissibility.md).

**Pathfinder's heuristic.** Haversine great-circle distance between
the current node and the target, scaled per objective (see
[PathCostModel.java](../server/src/main/java/edu/northeastern/pathfinder/pathfinding/PathCostModel.java)).

---

## Bidirectional Dijkstra

**Complexity.**
- Worst case: O((V + E) log V), same as Dijkstra.
- Expected on road networks: roughly √(Dijkstra's explored set).
  If Dijkstra expands a ball of radius *r* around the source containing
  ~*cr²* nodes, bidirectional expands two balls of radius ~*r/2* each,
  containing ~*cr²/2* nodes total — a factor-of-two-to-four speedup
  empirically.

**Termination condition.** Let μ = the best known path cost through
any node seen by both frontiers. Stop as soon as
`top(OpenForward) + top(OpenBackward) ≥ μ`.

**Correctness.** Suppose the true shortest path has cost *d*. At the
moment the termination condition fires, no path with cost < μ can
remain undiscovered: any such path's meeting node *w* has
`distF[w] + distB[w] < μ`, but `distF[w] ≥ top(OpenForward)` and
`distB[w] ≥ top(OpenBackward)` for any *w* not yet settled — so the
inequality forces μ ≤ *d*, and since μ comes from a real discovered
path, μ = *d*.

**Prerequisite.** Edge costs must be non-negative *and* the backward
graph's edge cost for edge *(u, v)* must equal the forward cost. Our
cost functions depend only on edge attributes, so this holds trivially.

**Implementation.** See [BidirectionalDijkstra.java](../server/src/main/java/edu/northeastern/pathfinder/pathfinding/BidirectionalDijkstra.java).
The reverse adjacency index is lazily materialised in `Graph` on first
bidirectional query.

### Why not bidirectional A*?

A naïve bi-A* with the same Haversine heuristic on both sides is **not
admissible as a whole**: the forward frontier prioritises `g + h(v, t)`,
the backward prioritises `gB + h(s, v)`, and the usual termination
condition `topF + topB ≥ μ` no longer guarantees optimality. The fix
is a symmetric potential function — e.g. NBA*'s
`pf(v) = (h(v, t) − h(s, v)) / 2` — which is non-trivial to implement
correctly. Bi-Dijkstra gives most of the practical speedup with none
of the correctness hazard.

---

## kd-tree for nearest-neighbour lookup

**Problem.** Given a query `(lat, lon)`, find the graph node closest
by great-circle distance. Called every time a user clicks the map.

**Old implementation.** Linear scan over all 1.88 M nodes — O(V)
haversine calls per query.

**New implementation.** Balanced 2D kd-tree built once at service
initialisation
([NodeKdTree.java](../server/src/main/java/edu/northeastern/pathfinder/graph/NodeKdTree.java)).

| Operation | Complexity | Notes |
|---|---|---|
| Build | O(V log V) expected | Quickselect-based in-place partitioning on a shared `int[]` index array. |
| Query (nearest 1) | O(log V) expected, O(V) worst case | Worst case requires adversarial input; unlikely on real-world coordinates. |
| Space | Θ(V) | Three `int[V]` + three parallel arrays of coordinates/ids. |

### Handling the sphere-vs-plane mismatch

kd-trees assume a Euclidean space where axis-aligned bounding boxes
prune correctly, but haversine distance is defined on a sphere. Naively
applying a planar kd-tree to (lat, lon) breaks: one degree of longitude
at 38° N is only cos(38°) ≈ 0.79 degrees of "great-circle-equivalent"
distance, so the splitting plane over-prunes on one axis.

**Fix.** Multiply each longitude by `cos(meanLat)` before storing it
in the tree. Within the DC bounding box this is a uniform scale factor
that makes Euclidean distance in the scaled (lat, lon_scaled) plane
monotonic with great-circle distance. The kd-tree's pruning test then
operates on a consistent metric. The final reported distance is still
computed with the exact haversine formula.

This trick is exact enough for any dataset whose longitudinal span is
small compared to a quarter circumference — for continental-scale
coverage one would switch to 3D unit-vector embedding, which is also
O(log V) but requires a 3D kd-tree.

### Pruning

Standard kd-tree nearest: descend the near subtree first, then visit
the far subtree only if the axis-aligned separation squared is less
than the current best squared distance. With `lonScale` applied, both
sides of this inequality live in the same (scaled-plane) coordinate
system.

---

## Inverted index for search

**Choice.** `Map<String, List<Integer>>` mapping each token to the
list of item indices that contain it
([SearchService.java](../server/src/main/java/edu/northeastern/pathfinder/service/SearchService.java)).

**Build.** O(Σ tokens(i)) across all SearchItems, one-time at startup.

**Query.** Token-by-token lookup, union of posting lists as candidates,
then scoring:
- Exact / prefix / contains: O(1) per token per candidate
- Fuzzy (Levenshtein ≤ 2): O(|q| · |t|) per candidate per token

On the current dataset of 138 K searchable items, queries return in
single-digit milliseconds even for typos.

---

## Binary graph cache

**Motivation.** Fresh startup parses 900 MB of GeoJSON via Jackson
streaming — ~10 seconds of JSON tokenisation, UTF-8 decoding, and
haversine distance per segment.

**Format**
([GraphCacheStore.java](../server/src/main/java/edu/northeastern/pathfinder/graph/GraphCacheStore.java)):

```
MAGIC (8B) | VERSION (4B) | source size (8B) | source mtime (8B) |
STRING TABLE (count + UTF strings) |
NODES (count + {id_idx, lon, lat}*) |
OUTGOING (source count + {src_idx, edge_count, edges*}) |
SEARCH ITEMS (count + items*) |
REPORT COUNTERS (4 × int)
```

**String table.** OSM tags are highly repetitive — `"residential"` and
`"service"` appear hundreds of thousands of times. A single dedup
table cuts the serialized size from an estimated ~1.5 GB (naive UTF
repetition) to 279 MB with 1.96 M unique strings. Every string elsewhere
in the file is an `int` index into this table.

**Validation.** Cache is keyed on the source GeoJSON's (size, mtime).
Hashing would be more robust but parsing 900 MB of content to verify a
hash would cost as much as the parse we are trying to avoid.

**Atomicity.** Write to a sibling `.tmp` file, then `Files.move` with
`ATOMIC_MOVE` → `REPLACE_EXISTING` fallback. A mid-write crash leaves
the old cache (or nothing) intact.

---

## Summary table

| Component | Structure | Time | Space |
|---|---|---|---|
| Graph | Adjacency list (HashMap + ArrayList) | O(1) add, O(deg) iterate | Θ(V + E) |
| Reverse adjacency | Lazy mirror | O(V + E) build (once) | Θ(V + E) |
| Dijkstra | Binary heap | O((V+E) log V) | O(V) |
| A* | Binary heap + heuristic | O((V+E) log V) worst, less in practice | O(V) |
| Bi-Dijkstra | Two binary heaps | O((V+E) log V) worst, √(Dijkstra) expected | O(V) |
| Nearest node | 2D kd-tree | O(log V) query / O(V log V) build | Θ(V) |
| Keyword search | Inverted index + Levenshtein | ~O(|q|·candidates) | Θ(Σ tokens) |
| Startup cache | Binary format + string table | O(V + E) load | Θ(V + E) on disk |
