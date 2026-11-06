import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.TreeSet;

public class TriageDemo {
    private static final Comparator<Patient> TREATMENT_ORDER =
            Comparator.comparingInt(Patient::severity)
                    .thenComparingLong(Patient::arrivalCounter);
    private static final long AGING_INTERVAL_MS = 1_000;

    public record Patient(String name, int severity, long arrivalCounter, long arrivalMillis) {
        public Patient {
            if (severity < 1 || severity > 5) throw new IllegalArgumentException("Severity must be 1..5");
        }
        @Override
        public String toString() { return name + "(S" + severity + ")"; }
    }

    private record Promotion(Entry entry, int targetLevel, long dueMillis) {}

    private static final class Entry {
        private final Patient patient;
        private int currentLevel;
        private boolean waiting = true;

        private Entry(Patient patient) {
            this.patient = patient;
            this.currentLevel = patient.severity();
        }
    }

    // Discrete aging promotes a waiting patient one severity band per interval.
    // Each promotion has a known timestamp, so ordering never depends on a changing PQ comparator.
    private static final class AgingTriage {
        private final TreeSet<Entry>[] lanes;
        private final PriorityQueue<Promotion> promotions = new PriorityQueue<>(
                Comparator.comparingLong(Promotion::dueMillis)
                        .thenComparingLong(p -> p.entry().patient.arrivalCounter()));

        @SuppressWarnings("unchecked")
        private AgingTriage() {
            lanes = (TreeSet<Entry>[]) new TreeSet<?>[6];
            Comparator<Entry> arrivalOrder = Comparator.comparingLong(e -> e.patient.arrivalCounter());
            for (int level = 1; level <= 5; level++) lanes[level] = new TreeSet<>(arrivalOrder);
        }

        private void add(Patient patient) {
            Entry entry = new Entry(patient);
            lanes[entry.currentLevel].add(entry);
            if (entry.currentLevel > 1) {
                promotions.add(new Promotion(entry, entry.currentLevel - 1,
                        patient.arrivalMillis() + AGING_INTERVAL_MS));
            }
        }

        private Patient poll(long nowMillis) {
            while (!promotions.isEmpty() && promotions.peek().dueMillis() <= nowMillis) {
                Promotion promotion = promotions.poll();
                Entry entry = promotion.entry();
                if (!entry.waiting || entry.currentLevel != promotion.targetLevel() + 1) continue;
                lanes[entry.currentLevel].remove(entry);
                entry.currentLevel = promotion.targetLevel();
                lanes[entry.currentLevel].add(entry);
                if (entry.currentLevel > 1) {
                    promotions.add(new Promotion(entry, entry.currentLevel - 1,
                            promotion.dueMillis() + AGING_INTERVAL_MS));
                }
            }
            for (int level = 1; level <= 5; level++) {
                Entry entry = lanes[level].pollFirst();
                if (entry != null) {
                    entry.waiting = false;
                    return entry.patient;
                }
            }
            return null;
        }
    }

    public static void main(String[] args) throws IOException {
        PriorityQueue<Patient> queue = new PriorityQueue<>(TREATMENT_ORDER);
        queue.addAll(List.of(
                new Patient("Asha", 5, 5, 0), new Patient("Ravi", 3, 3, 0),
                new Patient("Meena", 1, 0, 0), new Patient("John", 3, 4, 0),
                new Patient("Kumar", 1, 1, 0), new Patient("Lata", 2, 2, 0)));
        List<Patient> orderedView = new ArrayList<>();
        PriorityQueue<Patient> copy = new PriorityQueue<>(queue);
        while (!copy.isEmpty()) orderedView.add(copy.poll());
        System.out.println("Waiting-list treatment order (polled copy): " + orderedView);
        List<String> treatment = new ArrayList<>();
        while (!queue.isEmpty()) treatment.add(queue.poll().toString());
        System.out.println("Next-patient removals: " + String.join(" ", treatment));
        if (!String.join(" ", treatment).equals("Meena(S1) Kumar(S1) Lata(S2) Ravi(S3) John(S3) Asha(S5)")) {
            throw new IllegalStateException("PriorityQueue order did not match expected output");
        }

        PriorityQueue<Patient> eight = new PriorityQueue<>(TREATMENT_ORDER);
        eight.addAll(List.of(
                new Patient("P1", 4, 0, 0), new Patient("P2", 2, 1, 0),
                new Patient("P3", 2, 2, 0), new Patient("P4", 5, 3, 0),
                new Patient("P5", 1, 4, 0), new Patient("P6", 3, 5, 0),
                new Patient("P7", 1, 6, 0), new Patient("P8", 4, 7, 0)));
        List<String> eightOrder = new ArrayList<>();
        while (!eight.isEmpty()) eightOrder.add(eight.poll().name());
        System.out.println("Hand-checkable eight-patient order: " + eightOrder);

        runAgingDemonstration();
        benchmark(csvHeaderAndRun());
    }

    private static void runAgingDemonstration() {
        PriorityQueue<Patient> fixedPriority = new PriorityQueue<>(TREATMENT_ORDER);
        AgingTriage aging = new AgingTriage();
        long counter = 0;
        Patient lowUrgency = new Patient("Waiting-S5", 5, counter++, 0);
        fixedPriority.add(lowUrgency);
        aging.add(lowUrgency);
        for (int i = 1; i <= 50; i++) {
            long arrival = i * 100L;
            Patient urgent = new Patient("Urgent-" + i, 1, counter++, arrival);
            fixedPriority.add(urgent);
            aging.add(urgent);
        }
        Patient staticFirst = fixedPriority.poll();
        Patient agedFirst = aging.poll(5_000);
        System.out.println("Continuous arrivals: static-priority next=" + staticFirst
                + "; aged next=" + agedFirst + " after 5000 ms");
        if (!agedFirst.name().equals("Waiting-S5") || staticFirst.name().equals("Waiting-S5")) {
            throw new IllegalStateException("Aging policy did not prevent the tested starvation case");
        }
    }

    private static List<String> csvHeaderAndRun() {
        return new ArrayList<>(List.of("size,operation,run,operations,elapsed_ns,ns_per_operation,estimated_queue_objects,memory_before_bytes,memory_after_bytes"));
    }

    private static void benchmark(List<String> csv) throws IOException {
        for (int size : new int[]{100, 1_000, 5_000}) {
            for (int warmup = 0; warmup < 3; warmup++) {
                PriorityQueue<Patient> queue = new PriorityQueue<>(TREATMENT_ORDER);
                for (int i = 0; i < size; i++) queue.add(new Patient("W" + i, (i % 5) + 1, i, 0));
                while (!queue.isEmpty()) queue.poll();
            }
            long addTotal = 0, pollTotal = 0;
            for (int run = 1; run <= 12; run++) {
                Runtime runtime = Runtime.getRuntime();
                long before = runtime.totalMemory() - runtime.freeMemory();
                PriorityQueue<Patient> queue = new PriorityQueue<>(TREATMENT_ORDER);
                long start = System.nanoTime();
                for (int i = 0; i < size; i++) queue.add(new Patient("P" + i, (i % 5) + 1, i, 0));
                long addNanos = System.nanoTime() - start;
                long afterAdd = runtime.totalMemory() - runtime.freeMemory();
                start = System.nanoTime();
                while (!queue.isEmpty()) queue.poll();
                long pollNanos = System.nanoTime() - start;
                long afterPoll = runtime.totalMemory() - runtime.freeMemory();
                addTotal += addNanos;
                pollTotal += pollNanos;
                addCsvRow(csv, size, "add", run, size, addNanos, before, afterAdd);
                addCsvRow(csv, size, "poll_all", run, size, pollNanos, afterAdd, afterPoll);
            }
            System.out.printf(Locale.ROOT, "PriorityQueue n=%d: add avg %.2f ns/item; poll avg %.2f ns/item%n",
                    size, (double) addTotal / (12 * size), (double) pollTotal / (12 * size));
        }
        Files.write(Path.of("Q6-results.csv"), csv);
    }

    private static void addCsvRow(List<String> csv, int size, String operation, int run,
                                  int operations, long elapsed, long before, long after) {
        csv.add(String.format(Locale.ROOT, "%d,%s,%d,%d,%d,%.2f,%d,%d,%d",
                size, operation, run, operations, elapsed, (double) elapsed / operations,
                operations + 1L, before, after));
    }
}
