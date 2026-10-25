#!/usr/bin/env python3
"""Semantic checker. Run from the repo root: python tests/check.py"""
import json, os, random, subprocess, sys
from collections import deque

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMP = os.path.join(ROOT, "tests", "_batch.jsonl")


def run(requests):
    with open(TMP, "w") as f:
        for r in requests:
            f.write(json.dumps(r) + "\n")
    p = subprocess.run([sys.executable, os.path.join(ROOT, "tools", "run.py"), "--input", TMP],
                       capture_output=True, text=True, cwd=ROOT)
    os.remove(TMP)
    lines = [l for l in p.stdout.splitlines() if l.startswith("{")]
    if len(lines) != len(requests):
        sys.exit(f"expected {len(requests)} results, got {len(lines)}\n{p.stdout[-800:]}\n{p.stderr[-800:]}")
    return [json.loads(l) for l in lines]


# ---------- reference helpers (independent of the Java code) ----------
def dsu_parts(n):
    p = list(range(n))
    def find(x):
        while p[x] != x:
            p[x] = p[p[x]]
            x = p[x]
        return x
    return find, p


def count_components(n, edges):
    find, p = dsu_parts(n)
    c = n
    for e in edges:
        a, b = find(e[0]), find(e[1])
        if a != b:
            p[a] = b
            c -= 1
    return c


def ref_mst_cost(n, edges):
    find, p = dsu_parts(n)
    cost = 0
    for u, v, w in sorted(edges, key=lambda e: e[2]):
        a, b = find(u), find(v)
        if a != b:
            p[a] = b
            cost += w
    return cost


def closure(n, edges):
    R = [[False] * n for _ in range(n)]
    for e in edges:
        R[e[0]][e[1]] = True
    for k in range(n):
        for i in range(n):
            if R[i][k]:
                for j in range(n):
                    if R[k][j]:
                        R[i][j] = True
    return R


def kahn(n, edges):
    """Topological order via Kahn's algorithm, or None if a cycle exists."""
    indeg = [0] * n
    adj = [[] for _ in range(n)]
    for i, e in enumerate(edges):
        adj[e[0]].append(i)
        indeg[e[1]] += 1
    q = deque(v for v in range(n) if indeg[v] == 0)
    order = []
    while q:
        u = q.popleft()
        order.append(u)
        for i in adj[u]:
            v = edges[i][1]
            indeg[v] -= 1
            if indeg[v] == 0:
                q.append(v)
    return order if len(order) == n else None


# ---------- validators (raise AssertionError on a wrong answer) ----------
def check_dsu(req, res):
    n = req["n"]
    find, p = dsu_parts(n)
    size = [1] * n
    comps, largest, exp = n, (1 if n else 0), []
    for u, v in req["operations"]:
        a, b = find(u), find(v)
        if a != b:
            p[a] = b
            size[b] += size[a]
            comps -= 1
            largest = max(largest, size[b])
        exp.append([comps, largest])
    assert res["states"] == exp, "dsu states differ"


def check_forest(req, res):
    n, edges = req["n"], req["edges"]
    ids = res["edge_ids"]
    c = count_components(n, edges)
    assert res["components"] == c, "components"
    assert len(ids) == len(set(ids)) == n - c, "edge count / repeated id"
    find, p = dsu_parts(n)
    total = 0
    for i in ids:
        assert 0 <= i < len(edges), "bad edge id"
        u, v, w = edges[i]
        a, b = find(u), find(v)
        assert a != b, "cycle or self-loop selected"
        p[a] = b
        total += w
    assert total == res["cost"] == ref_mst_cost(n, edges), "cost is not optimal / inconsistent"


def check_scc_small(req, res):
    n, edges, comp = req["n"], req["edges"], res["component"]
    assert len(comp) == n
    R = closure(n, edges)
    for u in range(n):
        for v in range(n):
            same = u == v or (R[u][v] and R[v][u])
            assert (comp[u] == comp[v]) == same, "scc membership"
    exp = {(comp[u], comp[v]) for u, v in edges if comp[u] != comp[v]}
    got = [tuple(x) for x in res["condensation"]]
    assert len(got) == len(set(got)) and set(got) == exp, "condensation"


def check_topo(req, res, cyclic):
    n, edges = req["n"], req["edges"]
    assert res["acyclic"] == (not cyclic), "acyclic flag"
    if cyclic:
        assert res["order"] == [], "order must be empty"
        return
    order = res["order"]
    assert sorted(order) == list(range(n)), "order is not a permutation"
    pos = {v: i for i, v in enumerate(order)}
    assert all(pos[u] < pos[v] for u, v in edges), "edge goes backwards"


def check_dag(req, res):
    n, edges, s = req["n"], req["edges"], req["source"]
    order = kahn(n, edges)
    if order is None:
        assert res == {"acyclic": False, "distance": [], "parent_edge": []}, "cyclic graph answer"
        return
    out = [[] for _ in range(n)]
    for u, v, w in edges:
        out[u].append((v, w))
    dist = [None] * n
    dist[s] = 0
    for u in order:
        if dist[u] is None:
            continue
        for v, w in out[u]:
            if dist[v] is None or dist[u] + w < dist[v]:
                dist[v] = dist[u] + w
    assert res["acyclic"] is True and res["distance"] == dist, "distance"
    pe = res["parent_edge"]
    assert len(pe) == n
    for v in range(n):
        if v == s or dist[v] is None:
            assert pe[v] == -1, "parent of source/unreachable must be -1"
        else:
            e = pe[v]
            assert 0 <= e < len(edges), "bad parent id"
            u, x, w = edges[e]
            assert x == v and dist[u] is not None and dist[u] + w == dist[v], "invalid parent edge"


# ---------- self-test: validators accept an alternative answer and reject bad witnesses ----------
def expect_fail(fn, *args):
    try:
        fn(*args)
    except AssertionError:
        return
    sys.exit("validator accepted an invalid witness!")


def selftest():
    req = {"task": "kruskal", "n": 4, "edges": [[0, 1, 4], [1, 2, 2], [0, 2, 3]]}
    check_forest(req, {"cost": 5, "edge_ids": [2, 1], "components": 2})        # alternative valid order
    expect_fail(check_forest, req, {"cost": 5, "edge_ids": [0, 1], "components": 2})  # not optimal
    expect_fail(check_forest, req, {"cost": 4, "edge_ids": [1, 1], "components": 2})  # repeated id
    d = {"task": "dag", "n": 5, "source": 0, "edges": [[0, 1, 3], [0, 2, 8], [1, 2, -7], [2, 3, 4]]}
    check_dag(d, {"acyclic": True, "distance": [0, 3, -4, 0, None], "parent_edge": [-1, 0, 2, 3, -1]})
    expect_fail(check_dag, d, {"acyclic": True, "distance": [0, 3, -4, 0, None], "parent_edge": [-1, 0, 1, 3, -1]})
    print("selftest: validators accept the alternative answer and reject invalid witnesses")


# ---------- test cases ----------
def small_cases(rng, count):
    cases = []
    for _ in range(count):
        n = rng.randint(0, 7)
        m = rng.randint(0, 12) if n else 0
        pairs = [[rng.randrange(n), rng.randrange(n)] for _ in range(m)]   # loops/parallels allowed
        wedges = [p + [rng.randint(-5, 5)] for p in pairs]
        cases.append(({"task": "dsu", "n": n, "operations": pairs}, check_dsu))
        cases.append(({"task": "kruskal", "n": n, "edges": wedges}, check_forest))
        cases.append(({"task": "prim", "n": n, "edges": wedges}, check_forest))
        cases.append(({"task": "scc", "n": n, "edges": pairs}, check_scc_small))
        cyc = kahn(n, pairs) is None
        cases.append(({"task": "topo", "n": n, "edges": pairs}, lambda r, s, c=cyc: check_topo(r, s, c)))
        if n >= 1:
            cases.append(({"task": "dag", "n": n, "source": rng.randrange(n), "edges": wedges}, check_dag))
        if n >= 2:                                    # guaranteed-acyclic graphs with parallel edges
            perm = list(range(n))
            rng.shuffle(perm)
            dp = []
            for _ in range(m):
                a, b = sorted(rng.sample(range(n), 2))
                dp.append([perm[a], perm[b]])
            dw = [p + [rng.randint(-5, 5)] for p in dp]
            cases.append(({"task": "dag", "n": n, "source": rng.randrange(n), "edges": dw}, check_dag))
            cases.append(({"task": "topo", "n": n, "edges": dp}, lambda r, s: check_topo(r, s, False)))
    return cases


def check_scc_chain(req, res):
    n, comp = req["n"], res["component"]
    assert len(set(comp)) == n, "chain: every vertex must be its own SCC"
    got = [tuple(x) for x in res["condensation"]]
    assert len(got) == n - 1 and set(got) == {(comp[i], comp[i + 1]) for i in range(n - 1)}, "chain condensation"


def check_scc_ring(req, res):
    assert len(set(res["component"])) == 1 and res["condensation"] == [], "ring must be one SCC"


def large_cases(rng):
    N = 100000
    chain = [[i, i + 1] for i in range(N - 1)]
    cases = []
    cases.append(({"task": "dsu", "n": N, "operations": chain + [[i, i] for i in range(N)]}, check_dsu))
    heavy = [[i, i + 1, 10 ** 9] for i in range(N - 1)]
    for t in ("kruskal", "prim"):
        cases.append(({"task": t, "n": N, "edges": heavy},
                      lambda r, s: (check_forest(r, s), (_ := None) or
                                    (s["cost"] == (N - 1) * 10 ** 9 or (_ for _ in ()).throw(AssertionError("cost"))))))
    big = [[rng.randrange(N), rng.randrange(N), rng.randint(-10 ** 9, 10 ** 9)] for _ in range(250000)]
    for t in ("kruskal", "prim"):
        cases.append(({"task": t, "n": N, "edges": big}, check_forest))
    cases.append(({"task": "scc", "n": N, "edges": chain}, check_scc_chain))
    cases.append(({"task": "scc", "n": N, "edges": chain + [[N - 1, 0]]}, check_scc_ring))
    cases.append(({"task": "topo", "n": N, "edges": chain}, lambda r, s: check_topo(r, s, False)))
    cases.append(({"task": "topo", "n": N, "edges": chain + [[N - 1, 0]]}, lambda r, s: check_topo(r, s, True)))
    neg = [[i, i + 1, -10 ** 9] for i in range(N - 1)]
    cases.append(({"task": "dag", "n": N, "source": 0, "edges": neg}, check_dag))
    cases.append(({"task": "dag", "n": N, "source": N // 2, "edges": neg}, check_dag))
    half = 50000                                      # cycle 50000<->50001 unreachable from source 0
    cyc_edges = [[i, i + 1, 1] for i in range(half - 1)] + [[half, half + 1, 1], [half + 1, half, 1]]
    cases.append(({"task": "dag", "n": half + 2, "source": 0, "edges": cyc_edges}, check_dag))
    dag_edges = []
    for _ in range(250000):                           # full-limit random DAG
        a, b = sorted(rng.sample(range(N), 2))
        dag_edges.append([a, b, rng.randint(-10 ** 9, 10 ** 9)])
    cases.append(({"task": "dag", "n": N, "source": 0, "edges": dag_edges}, check_dag))
    cases.append(({"task": "topo", "n": N, "edges": [e[:2] for e in dag_edges]}, lambda r, s: check_topo(r, s, False)))
    return cases


def main():
    selftest()
    rng = random.Random(12345)
    failed = 0
    for name, cases in (("small random", small_cases(rng, 200)), ("large / deep", large_cases(rng))):
        results = run([c[0] for c in cases])
        bad = 0
        for i, ((req, chk), res) in enumerate(zip(cases, results)):
            try:
                chk(req, res)
            except (AssertionError, KeyError) as e:
                bad += 1
                print(f"FAIL {name} #{i} task={req['task']} n={req['n']}: {e!r}")
        failed += bad
        print(f"{name}: {len(cases) - bad}/{len(cases)} passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()