import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StringConstructionBench {
    private static final int WARMUPS = 3;
    private static final int RUNS = 12;
    private static volatile int blackhole;

    private record Workload(String name, int tickets, int fieldsPerTicket) {}
    private record Sample(long elapsedNanos, long estimatedStrings, long memoryBefore, long memoryAfter) {}

    public static void main(String[] args) throws IOException, InterruptedException {
        List<Workload> workloads = List.of(
                new Workload("one_short_ticket", 1, 8),
                new Workload("10000_tickets_50_fields", 10_000, 50),
                new Workload("own_250_tickets_20_fields", 250, 20));
        List<String> csv = new ArrayList<>();
        csv.add("workload,approach,run,tickets,fields_per_ticket,field_appends,elapsed_ns,estimated_intermediate_strings,memory_before_bytes,memory_after_bytes");

        for (Workload workload : workloads) {
            for (String approach : List.of("String_plus", "StringBuilder", "StringBuffer")) {
                for (int i = 0; i < WARMUPS; i++) {
                    buildAll(workload, approach);
                }
                long[] times = new long[RUNS];
                for (int run = 1; run <= RUNS; run++) {
                    Sample sample = measure(workload, approach);
                    times[run - 1] = sample.elapsedNanos();
                    csv.add(String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%d,%d,%d,%d,%d",
                            workload.name(), approach, run, workload.tickets(), workload.fieldsPerTicket(),
                            (long) workload.tickets() * workload.fieldsPerTicket(), sample.elapsedNanos(),
                            sample.estimatedStrings(), sample.memoryBefore(), sample.memoryAfter()));
                }
                printSummary(workload, approach, times);
            }
        }
        Files.write(Path.of("Q1-results.csv"), csv);
        runSharedLogDemo();
    }

    private static Sample measure(Workload workload, String approach) {
        Runtime runtime = Runtime.getRuntime();
        long before = usedMemory(runtime);
        long start = System.nanoTime();
        String result = buildAll(workload, approach);
        long elapsed = System.nanoTime() - start;
        long after = usedMemory(runtime);
        if (result.isEmpty()) throw new IllegalStateException("Ticket output must not be empty");
        blackhole = result.hashCode();
        long estimatedStrings = (long) workload.tickets()
                * (approach.equals("String_plus") ? workload.fieldsPerTicket() : 1);
        return new Sample(elapsed, estimatedStrings, before, after);
    }

    private static String buildAll(Workload workload, String approach) {
        StringBuilder allTickets = new StringBuilder();
        for (int ticket = 0; ticket < workload.tickets(); ticket++) {
            String text = buildTicket(ticket, workload.fieldsPerTicket(), approach);
            allTickets.append(text).append('\n');
        }
        return allTickets.toString();
    }

    private static String buildTicket(int ticket, int fields, String approach) {
        if (approach.equals("String_plus")) {
            String result = "";
            for (int field = 0; field < fields; field++) {
                result = result + fieldValue(ticket, field) + (field + 1 == fields ? "" : "|");
            }
            return result;
        }
        if (approach.equals("StringBuilder")) {
            StringBuilder result = new StringBuilder();
            for (int field = 0; field < fields; field++) {
                if (field > 0) result.append('|');
                result.append(fieldValue(ticket, field));
            }
            return result.toString();
        }
        StringBuffer result = new StringBuffer();
        for (int field = 0; field < fields; field++) {
            if (field > 0) result.append('|');
            result.append(fieldValue(ticket, field));
        }
        return result.toString();
    }

    private static String fieldValue(int ticket, int field) {
        String value = switch (field % 8) {
            case 0 -> "PNR" + ticket;
            case 1 -> "Passenger" + ticket;
            case 2 -> "Coach-S2";
            case 3 -> "Berth-" + (field + 1);
            case 4 -> "Fare-1250";
            case 5 -> "From-Chennai";
            case 6 -> "To-Bengaluru";
            default -> "Journey-2026-12-01";
        };
        return field >= 8 ? value + "-" + field : value;
    }

    private static long usedMemory(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void printSummary(Workload workload, String approach, long[] times) {
        long sum = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (long time : times) {
            sum += time;
            min = Math.min(min, time);
            max = Math.max(max, time);
        }
        System.out.printf(Locale.ROOT, "%s %-12s average=%d ns min=%d ns max=%d ns%n",
                workload.name(), approach, sum / times.length, min, max);
    }

    private static void runSharedLogDemo() throws InterruptedException {
        StringBuffer log = new StringBuffer();
        List<Thread> workers = new ArrayList<>();
        for (int worker = 0; worker < 4; worker++) {
            final int workerId = worker;
            Thread thread = new Thread(() -> {
                for (int booking = 0; booking < 250; booking++) {
                    // A single synchronized append call keeps the complete record together.
                    log.append("worker=" + workerId + ",booking=" + booking + '\n');
                }
            });
            workers.add(thread);
            thread.start();
        }
        for (Thread worker : workers) worker.join();
        long lines = log.toString().lines().count();
        if (lines != 1000) throw new IllegalStateException("Expected 1000 log records, got " + lines);
        System.out.println("Shared booking log: " + lines + " records; StringBuffer synchronized append demo passed.");
    }
}
