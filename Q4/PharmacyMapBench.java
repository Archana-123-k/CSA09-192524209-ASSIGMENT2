import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class PharmacyMapBench {
    private static final int RUNS = 12;
    private static final int LOOKUPS = 100_000;

    public static void main(String[] args) throws IOException {
        List<String> csv = new ArrayList<>();
        csv.add("size,map,operation,run,operations,elapsed_ns,ns_per_operation,estimated_map_objects,memory_before_bytes,memory_after_bytes");
        for (int size : new int[]{100, 5_000, 20_000}) {
            String[] lookupKeys = new String[LOOKUPS];
            for (int lookup = 0; lookup < LOOKUPS; lookup++) {
                lookupKeys[lookup] = codeFor((lookup * 31) % size);
            }
            for (String mapType : List.of("HashMap", "TreeMap", "LinkedHashMap")) {
                Map<String, String> medicines = null;
                List<String> insertionOrder = new ArrayList<>(size);
                for (int warmup = 0; warmup < 3; warmup++) {
                    Map<String, String> candidate = create(mapType);
                    for (int i = size - 1; i >= 0; i--) {
                        candidate.put(codeFor(i), "Medicine-" + i);
                    }
                }
                for (int run = 1; run <= RUNS; run++) {
                    Map<String, String> candidate = create(mapType);
                    long memoryBefore = usedMemory();
                    long putStart = System.nanoTime();
                    for (int i = 0; i < size; i++) {
                        int item = size - 1 - i;
                        candidate.put(codeFor(item), "Medicine-" + item);
                    }
                    long elapsed = System.nanoTime() - putStart;
                    record(csv, size, mapType, "put", run, size, elapsed,
                            estimatedMapObjects(mapType, size), memoryBefore, usedMemory());
                    if (run == RUNS) medicines = candidate;
                }
                for (int i = size - 1; i >= 0; i--) insertionOrder.add(codeFor(i));

                for (int warmup = 0; warmup < 3; warmup++) {
                    for (int lookup = 0; lookup < LOOKUPS; lookup++) {
                        medicines.get(lookupKeys[lookup]);
                    }
                }
                for (int run = 1; run <= RUNS; run++) {
                    long memoryBefore = usedMemory();
                    long start = System.nanoTime();
                    long checksum = 0;
                    for (int lookup = 0; lookup < LOOKUPS; lookup++) {
                        String value = medicines.get(lookupKeys[lookup]);
                        checksum += value.length();
                    }
                    long elapsed = System.nanoTime() - start;
                    if (checksum == 0) throw new IllegalStateException("Lookup checksum unexpectedly zero");
                    record(csv, size, mapType, "get_100000", run, LOOKUPS, elapsed,
                            estimatedMapObjects(mapType, size), memoryBefore, usedMemory());
                }
                for (String order : List.of("code_order_report", "insertion_order_report")) {
                    for (int warmup = 0; warmup < 3; warmup++) {
                        reportCodes(medicines, insertionOrder, order);
                    }
                    for (int run = 1; run <= RUNS; run++) {
                        long memoryBefore = usedMemory();
                        long start = System.nanoTime();
                        List<String> report = reportCodes(medicines, insertionOrder, order);
                        long elapsed = System.nanoTime() - start;
                        if (report.size() != size || !isCorrectOrder(report, insertionOrder, order)) {
                            throw new IllegalStateException(mapType + " produced an incorrect " + order);
                        }
                        record(csv, size, mapType, order, run, size, elapsed,
                                estimatedMapObjects(mapType, size), memoryBefore, usedMemory());
                    }
                }
                System.out.printf("%s size=%d: both code-order and insertion-order reports verified.%n", mapType, size);
            }
        }
        Files.write(Path.of("Q4-results.csv"), csv);
        System.out.printf("100,000 lookups per run; 12 measured runs; CSV written to Q4-results.csv.%n");
    }

    private static Map<String, String> create(String mapType) {
        return switch (mapType) {
            case "HashMap" -> new HashMap<>();
            case "TreeMap" -> new TreeMap<>();
            case "LinkedHashMap" -> new LinkedHashMap<>();
            default -> throw new IllegalArgumentException("Unknown map: " + mapType);
        };
    }

    private static String codeFor(int number) {
        return String.format(Locale.ROOT, "MED%06d", number);
    }

    private static List<String> reportCodes(Map<String, String> medicines, List<String> insertionOrder, String order) {
        List<String> codes = new ArrayList<>(medicines.size());
        if (order.equals("code_order_report")) {
            if (medicines instanceof TreeMap) {
                codes.addAll(medicines.keySet());
            } else {
                codes.addAll(medicines.keySet());
                codes.sort(String::compareTo);
            }
        } else if (medicines instanceof LinkedHashMap) {
            codes.addAll(medicines.keySet());
        } else {
            codes.addAll(insertionOrder);
        }
        return codes;
    }

    private static boolean isCorrectOrder(List<String> codes, List<String> insertionOrder, String order) {
        if (order.equals("insertion_order_report")) return codes.equals(insertionOrder);
        for (int i = 1; i < codes.size(); i++) {
            if (codes.get(i - 1).compareTo(codes.get(i)) > 0) return false;
        }
        return true;
    }

    private static long estimatedMapObjects(String mapType, int size) {
        return size + (mapType.equals("TreeMap") ? 1L : 2L);
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void record(List<String> csv, int size, String mapType, String operation, int run,
                               int operations, long elapsed, long estimatedObjects, long memoryBefore,
                               long memoryAfter) {
        csv.add(String.format(Locale.ROOT, "%d,%s,%s,%d,%d,%d,%.2f,%d,%d,%d", size, mapType,
                operation, run, operations, elapsed, (double) elapsed / operations, estimatedObjects,
                memoryBefore, memoryAfter));
    }
}
