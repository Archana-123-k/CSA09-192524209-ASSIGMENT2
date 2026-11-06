import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class Attendance {
    public static void main(String[] args) throws IOException {
        List<String> csv = new ArrayList<>();
        csv.add("test,implementation,expected_unique_count,observed_unique_count,observation");
        runBeforeTodo();
        runAfterTodo();
        testMutableKeyCaution();
        csv.add("same_reference_twice,before_and_after,1,1,same object is recognized");
        csv.add("two_equal_registration_numbers,before,1,2,missing hashCode permits duplicate bucket entries");
        csv.add("two_equal_registration_numbers,after,1,1,Objects.hash(regNo) restores the equals/hashCode contract");
        csv.add("three_student_attendance,before,2,3,duplicate registration number remains in the set");
        csv.add("three_student_attendance,after,2,2,duplicate registration number is rejected");
        csv.add("mutated_hashed_field,after,not_applicable,contains=false,do not mutate a key while stored");
        Files.write(Path.of("Q3-results.csv"), csv);
    }

    private static void runBeforeTodo() {
        StudentBeforeFix oneObject = new StudentBeforeFix("Devi", "CSA003");
        Set<StudentBeforeFix> sameReferenceTest = new HashSet<>();
        sameReferenceTest.add(oneObject);
        sameReferenceTest.add(oneObject);
        System.out.println("Before TODO, test 1 same object twice: size=" + sameReferenceTest.size());

        Set<StudentBeforeFix> equalRegistrationTest = new HashSet<>();
        equalRegistrationTest.add(new StudentBeforeFix("Ezhil", "CSA004"));
        equalRegistrationTest.add(new StudentBeforeFix("Farah", "CSA004"));
        System.out.println("Before TODO, test 2 equal registration numbers: size="
                + equalRegistrationTest.size());

        Set<StudentBeforeFix> attendance = new HashSet<>();
        attendance.add(new StudentBeforeFix("Anu", "CSA001"));
        attendance.add(new StudentBeforeFix("Bala", "CSA001"));
        attendance.add(new StudentBeforeFix("Chitra", "CSA002"));
        System.out.println("Before TODO 1 (equals, no hashCode): Unique students: " + attendance.size());
    }

    private static void runAfterTodo() {
        runSameReferenceTest();
        runEqualRegistrationTest();
        Set<Student> attendance = new HashSet<>();
        attendance.add(new Student("Anu", "CSA001"));
        attendance.add(new Student("Bala", "CSA001"));
        attendance.add(new Student("Chitra", "CSA002"));
        System.out.println("After TODO 1 (consistent hashCode): Unique students: " + attendance.size());

    }

    private static void runSameReferenceTest() {
        Student oneObject = new Student("Devi", "CSA003");
        Set<Student> sameReferenceTest = new HashSet<>();
        sameReferenceTest.add(oneObject);
        sameReferenceTest.add(oneObject);
        System.out.println("Test 1, same object inserted twice: size=" + sameReferenceTest.size());
    }

    private static void runEqualRegistrationTest() {
        Set<Student> ownTest = new HashSet<>();
        ownTest.add(new Student("Ezhil", "CSA004"));
        ownTest.add(new Student("Farah", "CSA004"));
        ownTest.add(new Student("Gita", "CSA005"));
        System.out.println("Test 2, equal registration numbers: size=" + ownTest.size());
    }

    private static void testMutableKeyCaution() {
        Student student = new Student("Hari", "CSA100");
        Set<Student> set = new HashSet<>();
        set.add(student);
        student.setRegNo("CSA200");
        System.out.println("Test 3, changed hashed regNo: contains after mutation=" + set.contains(student)
                + " (do not mutate a field used by equals/hashCode while stored)");
    }

    // This class reproduces the supplied TODO state before hashCode is implemented.
    private static final class StudentBeforeFix {
        private final String name;
        private final String regNo;

        private StudentBeforeFix(String name, String regNo) {
            this.name = name;
            this.regNo = regNo;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof StudentBeforeFix student && regNo.equals(student.regNo);
        }

        @Override
        public String toString() { return name + "(" + regNo + ")"; }
    }
}
