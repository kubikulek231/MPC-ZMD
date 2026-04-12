package enums;

public enum SamplingType {
    S_4_4_4("4:4:4"),
    S_4_2_2("4:2:2"),
    S_4_2_0("4:2:0"),
    S_4_1_1("4:1:1");

    String name;

    SamplingType(String s) {
        name = s;
    }

    @Override
    public String toString() {
        return "Sampling: " + name;
    }
}
