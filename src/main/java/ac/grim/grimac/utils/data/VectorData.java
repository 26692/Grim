package ac.grim.grimac.utils.data;

import com.google.common.base.Objects;
import org.bukkit.util.Vector;

public class VectorData {
    public VectorType vectorType;
    public VectorData lastVector;
    public Vector vector;

    // For handling replacing the type of vector it is while keeping data
    // Not currently used as this system isn't complete
    public VectorData(Vector vector, VectorData lastVector, VectorType vectorType) {
        this.vector = vector;
        this.lastVector = lastVector;
        this.vectorType = vectorType;
    }

    public VectorData(Vector vector, VectorType vectorType) {
        this.vector = vector;
        this.vectorType = vectorType;
    }

    public VectorData setVector(Vector newVec, VectorType type) {
        return new VectorData(newVec, this, type);
    }

    public boolean hasVectorType(VectorType type) {
        VectorData last = this;

        while (last != null) {
            if (last.vectorType == type)
                return true;

            last = last.lastVector;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(vectorType, lastVector, vector);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VectorData)) return false;
        VectorData that = (VectorData) o;
        return vectorType == that.vectorType && Objects.equal(lastVector, that.lastVector) && Objects.equal(vector, that.vector);
    }

    // TODO: For debugging everything should have it's own type!
    // Would make false positives really easy to fix
    // But seriously, we could trace the code to find the mistake
    public enum VectorType {
        Normal,
        Swimhop,
        Climbable,
        Knockback,
        HackyClimbable,
        Teleport,
        SkippedTicks,
        Explosion,
        InputResult,
        StuckMultiplier,
        Spectator,
        Dead,
        Jump,
        SurfaceSwimming,
        SwimmingSpace,
        BestVelPicked,
        LegacySwimming,
        Elytra,
        Firework,
        Lenience,
        TridentJump,
        Trident,
        SlimePistonBounce,
        Entity_Pushing,
        ZeroPointZeroThree,
        AttackSlow
    }

    @Override
    public String toString() {
        return "VectorData{" +
                "vectorType=" + vectorType +
                ", vector=" + vector +
                '}';
    }
}
