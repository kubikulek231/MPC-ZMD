package enums;

public enum TransformType {
    DCT("DCT"),
    WHT("WHT");

    String name;

    TransformType(String dct) {
        name = dct;
    }

    @Override
    public String toString() {
        return "Function: " + name;
    }
}
