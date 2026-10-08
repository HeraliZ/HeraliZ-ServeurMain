package fr.heraliz.airstrike.strike;

public enum PlaneType {
    ATTACK_JET,
    CARGO;

    public static PlaneType byId(int id) {
        PlaneType[] v = values();
        return id >= 0 && id < v.length ? v[id] : ATTACK_JET;
    }
}
