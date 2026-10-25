Assignment 3 – Graph Algorithms (Java 17-compatible code)
1. Build, run and test commands
Environment for all runs: GitHub Codespaces, Linux amd64, 2 vCPUs, OpenJDK 25.0.4.1 (Microsoft build), Python 3.12+.
python tools/run.py --setup
python tools/run.py --check                      # 36 public cases
python tools/run.py --input tests/my_cases.jsonl # own cases, prints actual results
python tests/check.py                            # semantic checker (random + large tests)
javac -d /tmp/bench src/Java/*.java tests/Bench.java tests/RouteTest.java
java -cp /tmp/bench RouteTest                    # route-helper tests
java -cp /tmp/bench Bench                        # Kruskal vs Prim benchmark
kruskalCore, primCore and dagCore are package-private (not private) only so that Bench and RouteTest can call them directly. The JSON entry points (kruskal, prim, dag, ...) only extract the input and call them.
2. Methods
Common data structure: csr(n, edges, mode) builds a compressed adjacency array (start[], adj[] holding original edge IDs) in O(n+m); edge IDs are never lost. Totals and distances use long; weights fit in int.
DSU
Steps: find with path compression (iterative, two passes); union by size; after every operation append [components, largest].
Invariant: parent is a forest; size[r] is correct at every root r; components = number of roots; largest = max root size.
Correctness: union of two different roots merges exactly two sets, so components drops by one and the new size is the sum; equal roots (repeated or self-unions) change nothing.
Boundaries: n=0 gives states=[]; initial largest=1 only if n>0; self-unions and repeated unions.
Complexity: O(n + q·α(n)) time, O(n+q) memory (including the states).
Kruskal
Steps: pack each edge as ((w+10^9) << 18) | id in a long[], sort it, scan the edges in order, and accept an edge when its endpoints are in different DSU sets.
Invariant: the accepted edges always form a forest, and after processing a prefix of the sorted edges the accepted set is contained in some minimum spanning forest (cut/cycle property with an exchange argument; breaking ties by ID is still a valid order).
Correctness: an edge joining two components is the lightest unprocessed edge crossing the cut between them, so it is safe; a rejected edge would close a cycle (self-loops always do). The number of accepted edges is n − c, so components = n − accepted.
Boundaries: self-loops, parallel edges, negative weights, isolates, totals above 32 bits, n=0. The packing is valid because w+10^9 ∈ [0, 2·10^9] and id < 2^18 = 262144, which is larger than the maximum m = 250000.
Complexity: O(n + m log m) time, O(n+m) memory.
Prim
Steps: build the undirected CSR (self-loops skipped). For every unvisited vertex start a new tree (components++), push its edges (same packed key) into a lazy PriorityQueue<Long>; pop the smallest edge; if both endpoints are visited, skip it; otherwise visit the unvisited endpoint, add the edge and push the new vertex's edges.
Invariant: the chosen edges of the current component form a tree on the visited set S; every chosen edge was the lightest edge leaving S at the moment it was chosen (cut property).
Correctness: stale heap entries are exactly those with both endpoints visited, so the first valid pop is the lightest crossing edge. Restarting from every unvisited vertex yields a spanning forest, isolates included.
Boundaries: same as Kruskal; the edge ID is recovered from the packed key.
Complexity: each edge is pushed at most twice, so O(n + m log m) time and O(n+m) memory.
SCC (Kosaraju)
Steps: pass 1 is an iterative DFS on G that records the finish order; pass 2 is a DFS on the transpose (reversed CSR) in decreasing finish order, and every new tree gets a new label. The condensation is the set of distinct pairs (comp[u], comp[v]) with different labels, deduplicated with a HashSet<Long> keyed a*n+b.
Invariant: the unlabeled vertex with the highest finish time lies in a source SCC of the remaining condensation; in the transpose it is a sink, so the DFS from it labels exactly its SCC.
Boundaries: isolates are their own SCCs; self-loops produce no condensation edge and duplicate edges produce only one; n=0 gives empty arrays.
Complexity: O(n+m) expected time (hash set), O(n+m) memory.
Topological order (three-state DFS)
Steps: iterative DFS with colors 0/1/2 (unvisited / active / finished); an edge to an active vertex means a cycle; otherwise the reverse of the finish order is returned.
Invariant: the active vertices are exactly the vertices on the current DFS stack.
Correctness: an edge to an active vertex closes a cycle (a self-loop included). If no such edge exists, then for every edge u→v the vertex v finishes before u, so reversing the finish order puts u before v. All components are started from the outer loop, so a cycle anywhere is found. An edge to a finished vertex alone is not a cycle.
Boundaries: self-loop, duplicate edges, n=0, cycle in a component that is not the first one.
Complexity: O(n+m) time and memory.
DAG shortest paths
Steps: run the topological DFS on the whole graph first (a cycle anywhere, even unreachable from the source, gives acyclic=false with empty arrays). Then process vertices in topological order and relax the outgoing edges of reachable vertices only. Reachability is a separate boolean array, so -1 is never used as "infinity".
Invariant: when u is processed, dist[u] is final, because all its predecessors come earlier in topological order.
Correctness: parent_edge[v] is the original ID of the last edge that strictly improved v; it ends at v and satisfies dist[tail] + w = dist[v]; its tail comes earlier in the order, so following the parents reaches the source. Unreachable vertices keep distance=null and parent_edge=-1; the source has 0 and -1.
Boundaries: negative weights, parallel and tied edges (the strict < keeps the first best edge), a cycle outside the reachable part, a self-loop, n=1.
Complexity: O(n+m) time and memory.
3. State reset and deep DFS
Reset: every method allocates its arrays inside the call and there are no mutable static fields, so consecutive requests in one process cannot influence each other. This is tested by running mixed requests repeatedly in one process (tools/run.py --check repeats the batch twice; tests/check.py sends about 1,500 mixed requests in one process).
Deep DFS: no recursion is used. Each DFS keeps an explicit int[] stack (size n is enough, because every vertex is pushed once) and a per-vertex pointer ptr[u] into the CSR adjacency, so a suspended vertex resumes where it stopped and neighbors are never rescanned. A 100,000-vertex chain was tested without stack failure.
4. Route helper
reconstructRoute(source, target, edges, parentEdge) starts at target, repeatedly takes the tail of parentEdge[v] until it reaches source, and then reverses the list. Convention: the route to the source is [source]; an unreachable target returns an empty list. Time and output space are O(L) for a route with L vertices.
Actual output of java -cp /tmp/bench RouteTest (A reachable, B source, C unreachable, D parallel edges, E tied routes). The program checks the direction of every edge and that the weight sum equals the distance:
A: reachable target (example from the assignment)
  distance=[0, 3, -4, 0, null] parent_edge=[-1, 0, 2, 3, -1]
  reconstructRoute(source=0, target=3) = [0, 1, 2, 3]
  edge ids=[0, 2, 3], weight sum=0, distance=0
B: target is the source
  distance=[0, 3, -4, 0, null] parent_edge=[-1, 0, 2, 3, -1]
  reconstructRoute(source=0, target=0) = [0]
  edge ids=[], weight sum=0, distance=0
C: unreachable target
  distance=[0, 3, -4, 0, null] parent_edge=[-1, 0, 2, 3, -1]
  reconstructRoute(source=0, target=4) = []
  unreachable target -> empty list
D: parallel edges with different weights
  distance=[0, 2] parent_edge=[-1, 1]
  reconstructRoute(source=0, target=1) = [0, 1]
  edge ids=[1], weight sum=2, distance=2
E: tied shortest routes (0->2 and 0->1->2 both cost 6)
  distance=[0, 5, 6] parent_edge=[-1, 0, 3]
  reconstructRoute(source=0, target=2) = [0, 2]
  edge ids=[3], weight sum=6, distance=6
ALL ROUTE CHECKS PASSED
In D the lighter of the parallel edges (ID 1, weight 2) is chosen. In E both routes cost 6; the direct edge 3 is kept because the relaxation uses a strict <.
5. Tests
tests/my_cases.jsonl contains 10 hand-made requests and tests/my_cases.actual.jsonl the actual output. All 10 matched the hand-derived expectations: DSU with repeated and self unions and n=0; Kruskal and Prim on the same graph (cost 5, two components, one isolate); a total of 3,000,000,000 (beyond 32 bits) with a negative self-loop; SCC with a duplicated cross edge; a cycle not reachable from vertex 0 in topo; a self-loop in topo; the assignment's DAG example (distance=[0,3,-4,0,null], parent_edge=[-1,0,2,3,-1]); and a cycle unreachable from the DAG source.
tests/check.py uses independent validators: forest feasibility and optimum cost (against a separate DSU-based reference), SCC membership via transitive closure and exact condensation, topological precedence, and DAG distances against a Kahn-order DP with validity of every parent edge. It first runs a self-test showing that the validators accept an alternative valid answer (reordered edge IDs) and reject invalid witnesses (a non-optimal forest, a repeated ID, a wrong parent edge). Result of the last run: small random 1480/1480, large/deep 14/14. The large cases include n = 100,000 chains for DSU, SCC, topo and DAG, 250,000-edge random graphs for Kruskal and Prim, a ring SCC, a cycle unreachable from the DAG source, and a 250,000-edge random DAG.
tests/route_test_output.txt holds the output of the route-helper tests (section 4).
Public checks: the GitHub Actions workflow "Public checks" runs on every push and passed for the solution commit (all 36 public cases).
6. Benchmark: Kruskal vs Prim
Inputs: Bench.gen(n, m, seed) with seed 12345: a connected graph (random spanning tree plus random extra edges, loops and parallel edges possible), weights uniform in [-10^9, 10^9], edges shuffled. Both methods receive the identical int[][].
Timing boundary: only the call to kruskalCore / primCore is timed (key packing and sort for Kruskal; CSR construction and heap for Prim). Graph generation, JSON parsing and output are excluded for both methods. Compilation is excluded (javac runs separately).
Timer and warm-up: System.nanoTime; 20 warm-up iterations of both methods on an n=10,000 graph, then 3 warm-up runs per size, then 5 timed runs per method per size, alternating Kruskal and Prim. Before timing, the costs, component counts and edge counts (n−1) of both methods were checked to agree; full forest validity is covered by tests/check.py.
Environment: GitHub Codespaces, Linux amd64, 2 vCPUs, OpenJDK 25.0.4.1 64-Bit Server VM, max heap 1986 MB. Command: java -cp /tmp/bench Bench (raw output in tests/benchmark_output.txt).
n m MST cost Kruskal runs (ms) Kruskal median Prim runs (ms) Prim median
1,000 5,000 -753294345880 0.47, 0.32, 0.40, 0.34, 0.32 0.34 1.29, 1.03, 1.06, 1.01, 1.02 1.03
10,000 50,000 -7595640104986 6.48, 4.90, 4.52, 4.14, 6.69 4.90 20.14, 20.55, 17.61, 19.14, 26.20 20.14
100,000 250,000 -53280544546492 27.00, 34.83, 50.02, 31.06, 32.12 32.12 115.28, 112.79, 150.35, 114.94, 150.70 115.28
Observations. Both methods return the same cost at every size. Prim's median is about 3.0×, 4.1× and 3.6× Kruskal's at the three sizes. Both grow roughly like m log m, as the asymptotic analysis predicts. A plausible reason for the constant-factor gap (not measured separately) is that Kruskal sorts a primitive long[], while this Prim uses a PriorityQueue<Long> with boxed objects, up to 2m heap insertions and a CSR build.
Limitations. Only one graph family (sparse, connected, m between 2.5n and 5n) and one seed were used; dense graphs might favor Prim differently. A shared 2-vCPU VM with JIT compilation and garbage collection adds noise (for example, the Kruskal runs at n=100,000 range from 27 to 50 ms), so medians of 5 runs are indicative, not precise. The comparison applies to these two implementations, not to the algorithms in general.
7. Sources and AI assistance
AI assistance: I used Claude (Anthropic) during this assignment. It produced first versions of the Java implementations, tests/check.py, tests/Bench.java, tests/RouteTest.java and a draft of this document. I ran everything myself in GitHub Codespaces: the public checks, my 10 own cases, tests/check.py, RouteTest and the benchmark, and I compared the outputs with the answers I derived by hand.