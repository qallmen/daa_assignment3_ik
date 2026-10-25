import java.util.*;
public class Bench { static final long SEED = 12345L; static final int RUNS = 5; static long sink = 0;
static int rw(Random r) { return r.nextInt(2_000_000_001) - 1_000_000_000; }

/** Connected graph: random spanning tree + random extra edges (loops/parallels possible), shuffled. */
static int[][] gen(int n, int m, long seed) {
    Random r = new Random(seed);
    int[][] e = new int[m][];
    int k = 0;
    for (int v = 1; v < n; v++) e[k++] = new int[]{r.nextInt(v), v, rw(r)};
    while (k < m) e[k++] = new int[]{r.nextInt(n), r.nextInt(n), rw(r)};
    for (int i = m - 1; i > 0; i--) {
        int j = r.nextInt(i + 1);
        int[] t = e[i]; e[i] = e[j]; e[j] = t;
    }
    return e;
}

static long timeOnce(boolean kruskal, int n, int[][] e) {
    long t0 = System.nanoTime();
    Map<String, Object> res = kruskal ? Algorithms.kruskalCore(n, e) : Algorithms.primCore(n, e);
    long dt = System.nanoTime() - t0;
    sink += res.size();
    return dt;
}

static double ms(long ns) { return ns / 1e6; }

static long median(long[] a) {
    long[] b = a.clone();
    Arrays.sort(b);
    return b[b.length / 2];
}

public static void main(String[] args) {
    System.out.println("java.version=" + System.getProperty("java.version")
            + " vm=" + System.getProperty("java.vm.name")
            + " os=" + System.getProperty("os.name") + " " + System.getProperty("os.arch")
            + " cpus=" + Runtime.getRuntime().availableProcessors()
            + " maxHeapMB=" + Runtime.getRuntime().maxMemory() / (1024 * 1024));
    System.out.println("timer=System.nanoTime, seed=" + SEED + ", runs per method per size=" + RUNS);

    int[][] sizes = {{1000, 5000}, {10000, 50000}, {100000, 250000}};

    // global JIT warm-up on a mid-size graph (results discarded)
    int[][] w = gen(10000, 50000, SEED + 99);
    for (int i = 0; i < 20; i++) { timeOnce(true, 10000, w); timeOnce(false, 10000, w); }

    System.out.println();
    System.out.println("| n | m | MST cost | Kruskal runs (ms) | Kruskal median (ms) | Prim runs (ms) | Prim median (ms) |");
    System.out.println("|---|---|---|---|---|---|---|");
    for (int[] s : sizes) {
        int n = s[0], m = s[1];
        int[][] e = gen(n, m, SEED);

        // correctness first: both methods must agree and give a spanning tree
        Map<String, Object> k = Algorithms.kruskalCore(n, e), p = Algorithms.primCore(n, e);
        long ck = (Long) k.get("cost"), cp = (Long) p.get("cost");
        if (ck != cp || !k.get("components").equals(p.get("components"))
                || ((int[]) k.get("edge_ids")).length != n - 1
                || ((int[]) p.get("edge_ids")).length != n - 1) {
            System.out.println("MISMATCH at n=" + n + ": kruskal=" + ck + " prim=" + cp);
            return;
        }

        for (int i = 0; i < 3; i++) { timeOnce(true, n, e); timeOnce(false, n, e); }  // per-size warm-up

        long[] tk = new long[RUNS], tp = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {      // alternate methods so both see the same conditions
            tk[i] = timeOnce(true, n, e);
            tp[i] = timeOnce(false, n, e);
        }
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (int i = 0; i < RUNS; i++) {
            a.append(String.format(Locale.ROOT, "%.2f", ms(tk[i]))).append(i < RUNS - 1 ? ", " : "");
            b.append(String.format(Locale.ROOT, "%.2f", ms(tp[i]))).append(i < RUNS - 1 ? ", " : "");
        }
        System.out.println("| " + n + " | " + m + " | " + ck + " | " + a + " | "
                + String.format(Locale.ROOT, "%.2f", ms(median(tk))) + " | " + b + " | "
                + String.format(Locale.ROOT, "%.2f", ms(median(tp))) + " |");
    }
    System.out.println("\n(sink=" + sink + ")");
}
}