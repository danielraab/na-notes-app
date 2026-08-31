package app.nanotes.backend.notes;

public enum Permission {
    OWNER("owner"),
    EDIT("edit"),
    READ("read");

    private final String wireValue;

    Permission(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static Permission fromWireValue(String v) {
        for (Permission p : values()) {
            if (p.wireValue.equals(v)) {
                return p;
            }
        }
        throw new IllegalArgumentException("unknown permission: " + v);
    }
}
