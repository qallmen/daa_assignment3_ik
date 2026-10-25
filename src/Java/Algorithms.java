import java.util.*;

/**
 * Implement the six methods and add your route-reconstruction helper.
 * Input extraction is provided; all algorithm state belongs to the current request.
 */
public final class Algorithms {

    // ================= public entry points (stubs from the template) =================

    /** DSU with path compression and union by size/rank; return states. */
    static Map<String, Object> dsu(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] operations = Input.operations(input);
        return dsuCore(n, operations);
    }

    /** Separate Kruskal implementation; return cost, edge_ids, components. */
    static Map<String, Object> kruskal(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        return kruskalCore(n, edges);
    }

    /** Separate Prim implementation; restart for every unvisited component. */
    static Map<String, Object> prim(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        return primCore(n, edges);
    }

    /** Kosaraju's two DFS passes; return component and exact condensation. */
    static Map<String, Object> scc(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        return sccCore(n, edges);
    }

    /** Three-state DFS and reverse finish order; return acyclic and order. */
    static Map<String, Object> topo(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        int[] order = topoOrder(n, edges);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("acyclic", order != null);
        result.put("order", order != null ? order : new int[0]);
        return result;
    }

    /** Whole-graph cycle check and DAG relaxation; return acyclic, distance, parent_edge. */
    static Map<String, Object> dag(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int source = Input.source(input);
        int[][] edges = Input.edges(input);
        return dagCore(n, edges, source);
    }

    // ================= helpers =================

    private static int find(int[] p, int x) {
        int r = x;
        while (p[r] != r) r = p[r];
        while (p[x] != r) { int nx = p[x]; p[x] = r; x = nx; }   // path compression
        return r;
    }

    // mode 0: directed forward, 1: directed reversed, 2: undirected (self-loops skipped)
    // returns {start, adj}; adj holds original edge IDs
    private static int[][] csr(int n, int[][] edges, int mode) {
        int[] start = new int[n + 1];
        for (int[] e : edges) {
            if (mode == 0) start[e[0] + 1]++;
            else if (mode == 1) start[e[1] + 1]++;
            else if (e[0] != e[1]) { start[e[0] + 1]++; start[e[1] + 1]++; }
        }
        for (int i = 0; i < n; i++) start[i + 1] += start[i];
        int[] pos = Arrays.copyOf(start, n);
        int[] adj = new int[start[n]];
        for (int i = 0; i < edges.length; i++) {
            int u = edges[i][0], v = edges[i][1];
            if (mode == 0) adj[pos[u]++] = i;
            else if (mode == 1) adj[pos[v]++] = i;
            else if (u != v) { adj[pos[u]++] = i; adj[pos[v]++] = i; }
        }
        return new int[][]{start, adj};
    }

    private static long key(int[][] edges, int id) {
        return ((long) (edges[id][2] + 1_000_000_000) << 18) | id;   // id < 2^18 since m <= 250000
    }

    // ================= DSU =================

    private static Map<String, Object> dsuCore(int n, int[][] operations) {
        int[] parent = new int[n], size = new int[n];
        for (int i = 0; i < n; i++) { parent[i] = i; size[i] = 1; }
        int comps = n, largest = n > 0 ? 1 : 0;
        int[][] states = new int[operations.length][];
        for (int i = 0; i < operations.length; i++) {
            int a = find(parent, operations[i][0]), b = find(parent, operations[i][1]);
            if (a != b) {
                if (size[a] < size[b]) { int t = a; a = b; b = t; }
                parent[b] = a;                       // union by size
                size[a] += size[b];
                comps--;
                if (size[a] > largest) largest = size[a];
            }
            states[i] = new int[]{comps, largest};
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("states", states);
        return result;
    }

    // ================= Kruskal =================

    private static Map<String, Object> kruskalCore(int n, int[][] edges) {
        int m = edges.length;
        long[] keys = new long[m];
        for (int i = 0; i < m; i++) keys[i] = key(edges, i);
        Arrays.sort(keys);
        int[] parent = new int[n], size = new int[n];
        for (int i = 0; i < n; i++) { parent[i] = i; size[i] = 1; }
        long cost = 0;
        int[] chosen = new int[Math.max(n - 1, 0)];
        int cnt = 0;
        for (long k : keys) {
            int id = (int) (k & ((1 << 18) - 1));
            int a = find(parent, edges[id][0]), b = find(parent, edges[id][1]);
            if (a == b) continue;                    // also rejects self-loops
            if (size[a] < size[b]) { int t = a; a = b; b = t; }
            parent[b] = a;
            size[a] += size[b];
            cost += edges[id][2];
            chosen[cnt++] = id;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cost", cost);
        result.put("edge_ids", Arrays.copyOf(chosen, cnt));
        result.put("components", n - cnt);
        return result;
    }

    // ================= Prim (lazy heap, restart in every component) =================

    private static Map<String, Object> primCore(int n, int[][] edges) {
        int[][] g = csr(n, edges, 2);
        int[] start = g[0], adj = g[1];
        boolean[] vis = new boolean[n];
        long cost = 0;
        int[] chosen = new int[Math.max(n - 1, 0)];
        int cnt = 0, comps = 0;
        PriorityQueue<Long> heap = new PriorityQueue<>();
        for (int s = 0; s < n; s++) {
            if (vis[s]) continue;
            comps++;
            vis[s] = true;
            for (int j = start[s]; j < start[s + 1]; j++) heap.add(key(edges, adj[j]));
            while (!heap.isEmpty()) {
                long k = heap.poll();
                int id = (int) (k & ((1 << 18) - 1));
                int a = edges[id][0], b = edges[id][1];
                if (vis[a] && vis[b]) continue;
                int v = vis[a] ? b : a;              // the unvisited endpoint
                vis[v] = true;
                cost += edges[id][2];
                chosen[cnt++] = id;
                for (int j = start[v]; j < start[v + 1]; j++) {
                    int e = adj[j];
                    if (!vis[edges[e][0]] || !vis[edges[e][1]]) heap.add(key(edges, e));
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cost", cost);
        result.put("edge_ids", Arrays.copyOf(chosen, cnt));
        result.put("components", comps);
        return result;
    }

    // ================= SCC (Kosaraju) =================

    private static int[] finishOrder(int n, int[] start, int[] adj, int[][] edges) {
        boolean[] vis = new boolean[n];
        int[] ptr = Arrays.copyOf(start, n);
        int[] order = new int[n];
        int oc = 0;
        int[] stack = new int[n];
        for (int s = 0; s < n; s++) {
            if (vis[s]) continue;
            int sp = 0;
            stack[sp++] = s;
            vis[s] = true;
            while (sp > 0) {
                int u = stack[sp - 1];
                if (ptr[u] < start[u + 1]) {
                    int v = edges[adj[ptr[u]++]][1];
                    if (!vis[v]) { vis[v] = true; stack[sp++] = v; }
                } else {
                    order[oc++] = u;
                    sp--;
                }
            }
        }
        return order;
    }

    private static Map<String, Object> sccCore(int n, int[][] edges) {
        int[][] f = csr(n, edges, 0), r = csr(n, edges, 1);
        int[] order = finishOrder(n, f[0], f[1], edges);          // pass 1
        int[] comp = new int[n];
        Arrays.fill(comp, -1);
        int label = 0;
        int[] stack = new int[n];
        for (int i = n - 1; i >= 0; i--) {                         // pass 2 on the transpose
            int s = order[i];
            if (comp[s] != -1) continue;
            int sp = 0;
            stack[sp++] = s;
            comp[s] = label;
            while (sp > 0) {
                int u = stack[--sp];
                for (int j = r[0][u]; j < r[0][u + 1]; j++) {
                    int v = edges[r[1][j]][0];                      // source of the original edge
                    if (comp[v] == -1) { comp[v] = label; stack[sp++] = v; }
                }
            }
            label++;
        }
        HashSet<Long> seen = new HashSet<>();
        List<int[]> cond = new ArrayList<>();
        for (int[] e : edges) {
            int a = comp[e[0]], b = comp[e[1]];
            if (a != b && seen.add((long) a * n + b)) cond.add(new int[]{a, b});
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("component", comp);
        result.put("condensation", cond.toArray(new int[0][]));
        return result;
    }

    // ================= topological order (three-state DFS) =================

    /** Returns a topological order, or null if a directed cycle exists. */
    private static int[] topoOrder(int n, int[][] edges) {
        int[][] g = csr(n, edges, 0);
        int[] start = g[0], adj = g[1];
        byte[] color = new byte[n];                  // 0 unvisited, 1 active, 2 finished
        int[] ptr = Arrays.copyOf(start, n);
        int[] finish = new int[n];
        int fc = 0;
        int[] stack = new int[n];
        for (int s = 0; s < n; s++) {
            if (color[s] != 0) continue;
            int sp = 0;
            stack[sp++] = s;
            color[s] = 1;
            while (sp > 0) {
                int u = stack[sp - 1];
                if (ptr[u] < start[u + 1]) {
                    int v = edges[adj[ptr[u]++]][1];
                    if (color[v] == 1) return null;  // edge to an active vertex -> cycle (self-loop too)
                    if (color[v] == 0) { color[v] = 1; stack[sp++] = v; }
                } else {
                    color[u] = 2;
                    finish[fc++] = u;
                    sp--;
                }
            }
        }
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = finish[n - 1 - i];  // reverse finish order
        return order;
    }

    // ================= DAG shortest paths =================

    private static Map<String, Object> dagCore(int n, int[][] edges, int source) {
        int[] order = topoOrder(n, edges);           // checks the WHOLE graph
        Map<String, Object> result = new LinkedHashMap<>();
        if (order == null) {
            result.put("acyclic", false);
            result.put("distance", new Long[0]);
            result.put("parent_edge", new int[0]);
            return result;
        }
        int[][] g = csr(n, edges, 0);
        int[] start = g[0], adj = g[1];
        long[] d = new long[n];
        boolean[] reach = new boolean[n];
        int[] parent = new int[n];
        Arrays.fill(parent, -1);
        reach[source] = true;
        for (int u : order) {
            if (!reach[u]) continue;                 // never relax from an unreachable vertex
            for (int j = start[u]; j < start[u + 1]; j++) {
                int id = adj[j], v = edges[id][1];
                long nd = d[u] + edges[id][2];
                if (!reach[v] || nd < d[v]) { reach[v] = true; d[v] = nd; parent[v] = id; }
            }
        }
        Long[] dist = new Long[n];
        for (int i = 0; i < n; i++) dist[i] = reach[i] ? Long.valueOf(d[i]) : null;
        result.put("acyclic", true);
        result.put("distance", dist);
        result.put("parent_edge", parent);
        return result;
    }

    /**
     * Route helper: vertex sequence source..target.
     * Convention: an unreachable target returns an empty list.
     */
    static List<Integer> reconstructRoute(int source, int target, int[][] edges, int[] parentEdge) {
        List<Integer> path = new ArrayList<>();
        if (target == source) { path.add(source); return path; }
        if (parentEdge[target] == -1) return path;
        int v = target;
        path.add(v);
        while (v != source) {
            v = edges[parentEdge[v]][0];             // tail of the parent edge
            path.add(v);
        }
        Collections.reverse(path);
        return path;
    }
}
