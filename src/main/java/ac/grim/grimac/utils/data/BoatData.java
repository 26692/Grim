package ac.grim.grimac.utils.data;

import ac.grim.grimac.utils.enums.BoatEntityStatus;

public class BoatData {
    public boolean boatUnderwater = false;
    public double lastYd;
    public double midTickY;
    public float landFriction;
    public BoatEntityStatus status;
    public BoatEntityStatus oldStatus;
    public double waterLevel;
    public float deltaRotation;

    public float nextVehicleForward = 0f;
    public float nextVehicleHorizontal = 0f;

    public BoatData() {

    }
}
