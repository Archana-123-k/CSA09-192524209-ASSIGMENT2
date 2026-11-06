import java.util.Objects;

public final class Student {
    private final String name;
    private String regNo;

    public Student(String name, String regNo) {
        this.name = name;
        this.regNo = regNo;
    }

    public String name() { return name; }
    public String regNo() { return regNo; }
    public void setRegNo(String regNo) { this.regNo = regNo; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Student student)) return false;
        return Objects.equals(regNo, student.regNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(regNo);
    }

    @Override
    public String toString() { return name + "(" + regNo + ")"; }
}
