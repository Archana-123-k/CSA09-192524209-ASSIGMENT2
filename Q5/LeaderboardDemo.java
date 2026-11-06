import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class LeaderboardDemo {
    public record Team(String name, int points, int goalDiff) {
        @Override
        public String toString() { return name + "(" + points + "," + goalDiff + ")"; }
    }

    public static final class Leaderboard<T extends Team> {
        private final Comparator<? super T> rankingOrder;

        public Leaderboard(Comparator<? super T> rankingOrder) {
            this.rankingOrder = rankingOrder;
        }

        // The extends wildcard accepts lists of T or any subtype of T; the copy prevents source mutation.
        public List<T> ranking(List<? extends T> items) {
            List<T> copy = new ArrayList<>(items);
            copy.sort(rankingOrder);
            return copy;
        }
    }

    private static final Comparator<Team> TEAM_ORDER =
            Comparator.comparingInt(Team::points).reversed()
                    .thenComparing(Comparator.comparingInt(Team::goalDiff).reversed())
                    .thenComparing(Team::name);

    public static void main(String[] args) throws IOException {
        Leaderboard<Team> leaderboard = new Leaderboard<>(TEAM_ORDER);
        List<Team> sample = new ArrayList<>(List.of(
                new Team("Eagles", 9, 6), new Team("Bisons", 9, 4),
                new Team("Panthers", 9, 4), new Team("Cobras", 6, 1)));
        List<Team> originalOrder = new ArrayList<>(sample);
        System.out.println("Expected ranking: " + leaderboard.ranking(sample));
        if (!sample.equals(originalOrder)) throw new IllegalStateException("ranking() changed its input");

        List<Team> ties = List.of(
                new Team("Zeta", 10, 5), new Team("Alpha", 10, 5),
                new Team("Beta", 10, 5), new Team("Aardvark", 10, 4),
                new Team("Omega", 9, 99), new Team("Alpha", 10, 5));
        System.out.println("Many-ties test: " + leaderboard.ranking(ties));
        verifyComparatorLaws(ties);

        List<Team> overflowCase = List.of(
                new Team("Minimum", Integer.MIN_VALUE, 0),
                new Team("Maximum", Integer.MAX_VALUE, 0),
                new Team("Middle", 0, 0));
        List<Team> correctOverflowOrder = leaderboard.ranking(overflowCase);
        List<Team> faultyOrder = new ArrayList<>(overflowCase);
        faultyOrder.sort((left, right) -> right.points() - left.points());
        System.out.println("Overflow test, correct comparator: " + correctOverflowOrder);
        System.out.println("Overflow test, subtraction comparator (faulty): " + faultyOrder);
        if (!correctOverflowOrder.get(0).name().equals("Maximum")
                || faultyOrder.get(0).name().equals("Maximum")) {
            throw new IllegalStateException("The overflow test did not expose the faulty comparator");
        }

        List<String> csv = new ArrayList<>();
        csv.add("teams,run,operations,elapsed_ns,ns_per_team,estimated_auxiliary_reference_slots,memory_before_bytes,memory_after_bytes");
        for (int size : new int[]{1_000, 10_000, 50_000}) {
            List<Team> data = randomTeams(size);
            for (int i = 0; i < 3; i++) leaderboard.ranking(data);
            long total = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
            for (int run = 1; run <= 12; run++) {
                long memoryBefore = usedMemory();
                long start = System.nanoTime();
                List<Team> result = leaderboard.ranking(data);
                long elapsed = System.nanoTime() - start;
                long memoryAfter = usedMemory();
                if (result.size() != size) throw new IllegalStateException("Ranking lost teams");
                total += elapsed;
                min = Math.min(min, elapsed);
                max = Math.max(max, elapsed);
                csv.add(String.format(Locale.ROOT, "%d,%d,%d,%d,%.2f,%d,%d,%d", size, run, size,
                        elapsed, (double) elapsed / size, size + size / 2, memoryBefore, memoryAfter));
            }
            System.out.printf(Locale.ROOT, "Sort %d teams: avg=%d ns min=%d max=%d%n",
                    size, total / 12, min, max);
        }
        Files.write(Path.of("Q5-results.csv"), csv);
    }

    private static List<Team> randomTeams(int size) {
        Random random = new Random(90210L + size);
        List<Team> teams = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            teams.add(new Team("Team-" + i, random.nextInt(101), random.nextInt(61) - 30));
        }
        return teams;
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void verifyComparatorLaws(List<Team> items) {
        if (TEAM_ORDER.compare(new Team("Twin", 8, 3), new Team("Twin", 8, 3)) != 0) {
            throw new IllegalStateException("Equal ranking keys must compare as zero");
        }
        for (Team a : items) {
            if (TEAM_ORDER.compare(a, a) != 0) throw new IllegalStateException("Reflexivity failed");
            for (Team b : items) {
                int ab = Integer.signum(TEAM_ORDER.compare(a, b));
                int ba = Integer.signum(TEAM_ORDER.compare(b, a));
                if (ab != -ba) throw new IllegalStateException("Sign symmetry failed");
                for (Team c : items) {
                    if (TEAM_ORDER.compare(a, b) <= 0 && TEAM_ORDER.compare(b, c) <= 0
                            && TEAM_ORDER.compare(a, c) > 0) {
                        throw new IllegalStateException("Transitivity failed");
                    }
                }
            }
        }
        System.out.println("Comparator law checks passed for reflexivity, sign symmetry, and transitivity.");
    }
}
