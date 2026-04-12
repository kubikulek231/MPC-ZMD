package enums;

public enum QualityType {
    RED("Red"),
    GREEN("Green"),
    BLUE("Blue"),
    Y("Y"),
    CB("Cb"),
    CR("Cr"),
    RGB("RGB"),
    YCBCR("YCbCr");

    private final String name;

    QualityType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
