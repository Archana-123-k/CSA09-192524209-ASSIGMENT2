import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.TreeSet;

public class StarvationRedesign {
    private static final long AGE_INTERVAL_MS = 1_000;

    private record Patient(String name, int severity, long arrivalOrder, long arrivalMillis) {}
    private record Promotion(Entry entry, int newLevel, long dueMillis) {}

    private static final class Entry {
        private final Patient patient;
        private int level;
        private boolean waiting = true;

        private Entry(Patient patient) {
            this.patient = patient;
            level = patient.severity();
        }
    }

    private static final class AgingScheduler {
        private final TreeSet<Entry>[] levels;
        private final PriorityQueue<Promotion> duePromotions = new PriorityQueue<>(
                Comparator.comparingLong(Promotion::dueMillis)
                        .thenComparingLong(p -> p.entry().patient.arrivalOrder()));
        private long promotionCount;

        @SuppressWarnings("unchecked")
        private AgingScheduler() {
            levels = (TreeSet<Entry>[]) new TreeSet<?>[6];
            Comparator<Entry> order = Comparator.comparingLong(e -> e.patient.arrivalOrder());
            for (int level = 1; level <= 5; level++) levels[level] = new TreeSet<>(order);
        }

        private void add(Patient patient) {
            Entry entry = new Entry(patient);
            levels[entry.level].add(entry);
            scheduleNextPromotion(entry, patient.arrivalMillis() + AGE_INTERVAL_MS);
        }

        private void scheduleNextPromotion(Entry entry, long due) {
            if (entry.level > 1) duePromotions.add(new Promotion(entry, entry.level - 1, due));
        }

        private Patient poll(long nowMillis) {
            while (!duePromotions.isEmpty() && duePromotions.peek().dueMillis() <= nowMillis) {
                Promotion promotion = duePromotions.poll();
                promotionCount++;
                Entry entry = promotion.entry();
                if (!entry.waiting || entry.level != promotion.newLevel() + 1) continue;
                levels[entry.level].remove(entry);
                entry.level = promotion.newLevel();
                levels[entry.level].add(entry);
                scheduleNextPromotion(entry, promotion.dueMillis() + AGE_INTERVAL_MS);
            }
            for (int level = 1; level <= 5; level++) {
                Entry entry = levels[level].pollFirst();
                if (entry != null) {
                    entry.waiting = false;
                    return entry.patient;
                }
            }
            return null;
        }

        private long promotionCount() { return promotionCount; }
    }

    private record Measurement(long elapsedNanos, long beforeBytes, long afterBytes, long objectEstimate,
                               long operationCount, String firstPatient) {}

    public static void main(String[] args) throws IOException {
        demonstrateCorrectness();
        List<String> csv = new ArrayList<>();
        csv.add("design,run,patients,elapsed_ns,operations,estimated_objects,memory_before_bytes,memory_after_bytes,first_patient");
        int size = 5_000;
        List<Patient> patients = createPatients(size);
        for (String design : List.of("static_priority", "aging_redesign")) {
            for (int warmup = 0; warmup < 3; warmup++) measure(patients, design);
            long total = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
            for (int run = 1; run <= 12; run++) {
                Measurement measurement = measure(patients, design);
                total += measurement.elapsedNanos();
                min = Math.min(min, measurement.elapsedNanos());
                max = Math.max(max, measurement.elapsedNanos());
                csv.add(String.format(Locale.ROOT,
                        "%s,%d,%d,%d,%d,%d,%d,%d,%s",
                        design, run, size, measurement.elapsedNanos(), measurement.operationCount(),
                        measurement.objectEstimate(), measurement.beforeBytes(), measurement.afterBytes(),
                        measurement.firstPatient()));
            }
            System.out.printf(Locale.ROOT, "%s n=%d: avg=%d ns min=%d max=%d%n",
                    design, size, total / 12, min, max);
        }
        Files.write(Path.of("Q7-results.csv"), csv);
    }

    private static void demonstrateCorrectness() {
        List<Patient> arrivals = createPatients(51);
        PriorityQueue<Patient> original = new PriorityQueue<>(
                Comparator.comparingInt(Patient::severity).thenComparingLong(Patient::arrivalOrder));
        AgingScheduler redesigned = new AgingScheduler();
        for (Patient patient : arrivals) {
            original.add(patient);
            redesigned.add(patient);
        }
        Patient oldS5 = original.poll();
        Patient agedS5 = redesigned.poll(4_000);
        System.out.println("Q7 same-arrival simulation: original serves " + oldS5.name()
                + "; aging redesign serves " + agedS5.name() + " after 4,000 ms.");
        if (oldS5.name().equals("Waiting-S5") || !agedS5.name().equals("Waiting-S5")) {
            throw new IllegalStateException("Q7 starvation objective was not met");
        }
    }

    private static List<Patient> createPatients(int size) {
        List<Patient> patients = new ArrayList<>(size);
        patients.add(new Patient("Waiting-S5", 5, 0, 0));
        for (int i = 1; i < size; i++) {
            patients.add(new Patient("Urgent-" + i, 1, i, 0));
        }
        return patients;
    }

    private static Measurement measure(List<Patient> patients, String design) {
        Runtime runtime = Runtime.getRuntime();
        long before = runtime.totalMemory() - runtime.freeMemory();
        long start = System.nanoTime();
        long operations;
        long objectEstimate;
        String first;
        if (design.equals("static_priority")) {
            PriorityQueue<Patient> queue = new PriorityQueue<>(
                    Comparator.comparingInt(Patient::severity).thenComparingLong(Patient::arrivalOrder));
            queue.addAll(patients);
            first = queue.poll().name();
            operations = patients.size() + 1L;
            objectEstimate = patients.size() + 1L;
        } else {
            AgingScheduler scheduler = new AgingScheduler();
            for (Patient patient : patients) scheduler.add(patient);
            Patient selected = scheduler.poll(4_000);
            first = selected.name();
            operations = patients.size() + 1L + scheduler.promotionCount();
            objectEstimate = patients.size() * 2L + scheduler.promotionCount();
        }
        long elapsed = System.nanoTime() - start;
        long after = runtime.totalMemory() - runtime.freeMemory();
        return new Measurement(elapsed, before, after, objectEstimate, operations, first);
    }
}
