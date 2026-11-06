import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class PlaylistBench {
    private static final int WARMUPS = 2;
    private static final int RUNS = 10;
    private static volatile long blackhole;

    @FunctionalInterface
    private interface Operation { long run(); }

    public static void main(String[] args) throws IOException {
        List<String> csv = new ArrayList<>();
        csv.add("size,structure,operation,run,operations,elapsed_ns,ns_per_operation,estimated_container_objects,memory_before_bytes,memory_after_bytes");
        for (int size : new int[]{1_000, 10_000, 50_000}) {
            runCase(csv, size, "ArrayList", "append", () -> {
                ArrayList<Integer> list = new ArrayList<>();
                long start = System.nanoTime();
                for (int i = 0; i < size; i++) list.add(i);
                return finish(start, list.size());
            }, size);
            runCase(csv, size, "LinkedList", "append", () -> {
                LinkedList<Integer> list = new LinkedList<>();
                long start = System.nanoTime();
                for (int i = 0; i < size; i++) list.add(i);
                return finish(start, list.size());
            }, size);
            runCase(csv, size, "ArrayDeque", "append", () -> {
                ArrayDeque<Integer> deque = new ArrayDeque<>();
                long start = System.nanoTime();
                for (int i = 0; i < size; i++) deque.addLast(i);
                return finish(start, deque.size());
            }, size);
            runCase(csv, size, "plain_array", "append", () -> {
                int[] values = new int[size];
                long start = System.nanoTime();
                for (int i = 0; i < size; i++) values[i] = i;
                return finish(start, values[size - 1]);
            }, size);
            runCase(csv, size, "ArrayList", "front_insert_remove", () -> {
                ArrayList<Integer> list = filledList(size);
                long start = System.nanoTime();
                for (int i = 0; i < 100; i++) { list.add(0, -i); list.remove(0); }
                return finish(start, list.size());
            }, 200);
            runCase(csv, size, "LinkedList", "front_insert_remove", () -> {
                LinkedList<Integer> list = filledLinked(size);
                long start = System.nanoTime();
                for (int i = 0; i < 100; i++) { list.addFirst(-i); list.removeFirst(); }
                return finish(start, list.size());
            }, 200);
            runCase(csv, size, "ArrayDeque", "front_insert_remove", () -> {
                ArrayDeque<Integer> deque = filledDeque(size);
                long start = System.nanoTime();
                for (int i = 0; i < 100; i++) { deque.addFirst(-i); deque.removeFirst(); }
                return finish(start, deque.size());
            }, 200);
            runCase(csv, size, "plain_array", "front_insert_remove", () -> {
                int[] values = new int[size + 100];
                for (int i = 0; i < size; i++) values[i] = i;
                long start = System.nanoTime();
                for (int i = 0; i < 100; i++) {
                    System.arraycopy(values, 0, values, 1, size);
                    values[0] = -i;
                    System.arraycopy(values, 1, values, 0, size);
                }
                return finish(start, values[0] + values[size - 1]);
            }, 200);
            runCase(csv, size, "ArrayList", "middle_insert_remove", () -> {
                ArrayList<Integer> list = filledList(size);
                int middle = list.size() / 2;
                long start = System.nanoTime();
                for (int i = 0; i < 100; i++) { list.add(middle, -i); list.remove(middle); }
                return finish(start, list.size());
            }, 200);
            runCase(csv, size, "LinkedList", "middle_insert_remove", () -> {
                LinkedList<Integer> list = filledLinked(size);
                long start = System.nanoTime();
                for (int i = 0; i < 100; i++) {
                    var cursor = list.listIterator(list.size() / 2);
                    cursor.add(-i);
                    cursor.previous();
                    cursor.remove();
                }
                return finish(start, list.size());
            }, 200);
            runCase(csv, size, "plain_array", "middle_insert_remove", () -> {
                int[] values = new int[size + 100];
                for (int i = 0; i < size; i++) values[i] = i;
                int middle = size / 2;
                long start = System.nanoTime();
                for (int i = 0; i < 100; i++) {
                    System.arraycopy(values, middle, values, middle + 1, size - middle);
                    values[middle] = -i;
                    System.arraycopy(values, middle + 1, values, middle, size - middle);
                }
                return finish(start, values[middle]);
            }, 200);
            runCase(csv, size, "ArrayList", "get_kth", () -> {
                ArrayList<Integer> list = filledList(size);
                long sum = 0;
                long start = System.nanoTime();
                for (int i = 0; i < 10_000; i++) sum += list.get((i * 31) % size);
                if (sum == Long.MIN_VALUE) throw new AssertionError();
                return finish(start, sum);
            }, 10_000);
            runCase(csv, size, "LinkedList", "get_kth", () -> {
                LinkedList<Integer> list = filledLinked(size);
                long sum = 0;
                long start = System.nanoTime();
                for (int i = 0; i < 100; i++) sum += list.get((i * 31) % size);
                if (sum == Long.MIN_VALUE) throw new AssertionError();
                return finish(start, sum);
            }, 100);
            runCase(csv, size, "plain_array", "get_kth", () -> {
                int[] values = new int[size];
                for (int i = 0; i < size; i++) values[i] = i;
                long sum = 0;
                long start = System.nanoTime();
                for (int i = 0; i < 10_000; i++) sum += values[(i * 31) % size];
                if (sum == Long.MIN_VALUE) throw new AssertionError();
                return finish(start, sum);
            }, 10_000);
            runCase(csv, size, "ArrayList", "full_iteration", () -> iterate(filledList(size)), size);
            runCase(csv, size, "LinkedList", "full_iteration", () -> iterate(filledLinked(size)), size);
            runCase(csv, size, "ArrayDeque", "full_iteration", () -> iterate(filledDeque(size)), size);
            runCase(csv, size, "plain_array", "full_iteration", () -> {
                int[] values = new int[size];
                for (int i = 0; i < size; i++) values[i] = i;
                long sum = 0;
                long start = System.nanoTime();
                for (int value : values) sum += value;
                if (sum == Long.MIN_VALUE) throw new AssertionError();
                return finish(start, sum);
            }, size);
        }
        Files.write(Path.of("Q2-results.csv"), csv);
        System.out.println("Results written to Q2-results.csv (10 timed runs per case; costs are environment-dependent).");
    }

    private static void runCase(List<String> csv, int size, String structure, String operation,
                                Operation action, int operations) {
        for (int i = 0; i < WARMUPS; i++) action.run();
        long sum = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (int run = 1; run <= RUNS; run++) {
            Runtime runtime = Runtime.getRuntime();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
            long elapsed = action.run();
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            sum += elapsed;
            min = Math.min(min, elapsed);
            max = Math.max(max, elapsed);
            long estimatedObjects = switch (structure) {
                case "LinkedList" -> size + 1L;
                case "ArrayList", "ArrayDeque" -> 2;
                default -> 1;
            };
            csv.add(String.format(Locale.ROOT, "%d,%s,%s,%d,%d,%d,%.2f,%d,%d,%d",
                    size, structure, operation, run, operations, elapsed, (double) elapsed / operations,
                    estimatedObjects, memoryBefore, memoryAfter));
        }
        System.out.printf(Locale.ROOT, "%5d %-10s %-22s avg=%d ns/op min=%d max=%d%n",
                size, structure, operation, sum / RUNS / operations, min / operations, max / operations);
    }

    private static long iterate(Iterable<Integer> values) {
        long sum = 0, start = System.nanoTime();
        for (int value : values) sum += value;
        if (sum == Long.MIN_VALUE) throw new AssertionError();
        return finish(start, sum);
    }

    private static long finish(long start, long value) {
        long elapsed = System.nanoTime() - start;
        blackhole = value;
        return elapsed;
    }

    private static ArrayList<Integer> filledList(int size) {
        ArrayList<Integer> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) list.add(i);
        return list;
    }

    private static LinkedList<Integer> filledLinked(int size) {
        LinkedList<Integer> list = new LinkedList<>();
        for (int i = 0; i < size; i++) list.add(i);
        return list;
    }

    private static ArrayDeque<Integer> filledDeque(int size) {
        ArrayDeque<Integer> deque = new ArrayDeque<>(size);
        for (int i = 0; i < size; i++) deque.addLast(i);
        return deque;
    }
}
