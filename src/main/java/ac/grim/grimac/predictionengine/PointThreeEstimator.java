package ac.grim.grimac.predictionengine;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.predictionengine.predictions.PredictionEngine;
import ac.grim.grimac.utils.blockstate.BaseBlockState;
import ac.grim.grimac.utils.collisions.CollisionData;
import ac.grim.grimac.utils.collisions.datatypes.CollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.VectorData;
import ac.grim.grimac.utils.math.GrimMath;
import ac.grim.grimac.utils.nmsutil.Collisions;
import ac.grim.grimac.utils.nmsutil.FluidTypeFlowing;
import ac.grim.grimac.utils.nmsutil.GetBoundingBox;
import ac.grim.grimac.utils.nmsutil.Materials;
import io.github.retrooper.packetevents.utils.player.ClientVersion;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Set;

/**
 * A lot of care was put into handling all the stupid stuff occurring between events
 * <p>
 * Such as: Placing water and lava in a worldguard region to climb up walls within 0.03
 * A single tick of bubble columns
 * Placing ladders in worldguard regions
 * Some plugin thinking it's funny to spam levitation effects rapidly
 * Some plugin thinking it's funny to spam gravity effects rapidly
 * Someone trying to false grim by using negative levitation effects
 * Open trapdoor, 0.03 upward into closed trapdoor, open trapdoor the tick before the next movement.
 * <p>
 * We must separate horizontal and vertical movement
 * The player can never actually control vertical movement directly
 * Vertically - we must compensate for gravity and for stepping movement
 * <p>
 * Stepping can be compensated for by expanding by 0.03, seting the vector down by the minimum movement allowed
 * and then moving the box up by the collision epsilon, and then pushing the box by 0.03 again
 * avoiding using the isEmpty() and rather using the collision move method, to avoid bypass/abuse
 * <p>
 * Jumping movement IS one of these starting vectors, although the length between the jump and
 * not jumping is outside the allowed vectors - as jumping cannot desync
 * <p>
 * Fluid pushing is quite strange - we simply expand by 0.03 and check for horizontal and vertical flowing.
 * As poses often desync, we cannot actually know the exact value.
 * <p>
 * Additionally, we must recheck for fluid between world updates to see if the player was swimming
 * or in lava at any point within the skipped tick
 * <p>
 * We must also check for a player starting gliding, stopping gliding, all within 0.03, which might
 * be possible due to mojang's shitty implementation of gliding and netcode
 * <p>
 * We must also check for the user placing ladders, which gives them control of vertical movement
 * once again also between world changes
 * <p>
 * We must also be aware of sneaking, which is implemented terribly by mojang
 * There should be a post check for sending sneaking updates, but it's not implemented yet...
 * If the user has been sneaking for 2 movements without stopping, then we know that they are sneaking
 * This is due to poses being done AFTER the player moves, adding a 50 ms delay
 * And due to slowness processing BEFORE poses are updated, adding another 50 ms delay
 * However, on 1.13, the delay is instant because mojang wasn't given a chance to be incompetent -_-
 * <p>
 * We also must be aware of levitation from the last tick
 * We also must be aware of bubble columns
 * <p>
 * Additionally, because poses are done AFTER the previous tick, we must know the minimum height the player's
 * bounding box can be, to avoid noclip falses.  Funnily enough, vanilla falses due to this... fucking mojang.
 * This is done because when the player can't have changed their pose for one tick, the second we know their god
 * damn pose.  The third fucking tick fixes the slow movement desync.  Thanks a lot, mojang - for falsing
 * your own anticheat and not caring enough to fix it.  Causing this giant mess that we all know you won't
 * fix for another decade... and if you do fix it... you will only make it worse (remember the bucket desync?)
 * <p>
 * Call me out for shitty code (in this class) - but please put the blame on Mojang instead.  None of this would be needed
 * if Minecraft's netcode wasn't so terrible.
 */
public class PointThreeEstimator {
    private final GrimPlayer player;

    // The one thing we don't need to store is if the player 0.03'd to the ground, as this sends a packet
    // seriously, what the fuck mojang.  You send the player touched the ground but not their pos.
    // Is the position not important to you?  Why do you throw this data out??? God-damn it Mojang!
    //
    // If a player is moving upwards and a block is within 0.03 of their head, then they can hit this block
    // This results in what appears to be too great of gravity
    private boolean headHitter = false;
    // If the player was within 0.03 of water between now and the last movement
    private boolean isNearFluid = false;
    // If a player places a ladder in a worldguard region etc.
    @Getter
    private boolean isNearClimbable = false;
    // If a player stops and star gliding all within 0.03
    private boolean isGliding = false;
    // If the player's gravity has changed
    private boolean gravityChanged = false;

    private boolean isNearHorizontalFlowingLiquid = false; // We can't calculate the direction, only a toggle
    private boolean isNearVerticalFlowingLiquid = false; // We can't calculate exact values, once again a toggle
    private boolean isNearBubbleColumn = false; // We can't calculate exact values once again

    private boolean hasPositiveLevitation = false; // Positive potion effects [0, 128]
    private boolean hasNegativeLevitation = false; // Negative potion effects [-127, -1]
    private boolean didLevitationChange = false; // We can't predict with an unknown amount of ticks between a levitation change

    @Getter
    private boolean wasAlwaysCertain = true;

    public PointThreeEstimator(GrimPlayer player) {
        this.player = player;
    }

    // Handle game events that occur between skipped ticks - thanks a lot mojang for removing the idle packet!
    public void handleChangeBlock(int x, int y, int z, BaseBlockState state) {
        CollisionBox data = CollisionData.getData(state.getMaterial()).getMovementCollisionBox(player, player.getClientVersion(), state, x, y, z);
        SimpleCollisionBox normalBox = GetBoundingBox.getBoundingBoxFromPosAndSize(player.x, player.y, player.z, 0.6, 1.8);

        // Calculate head hitters.  Take a shortcut by checking if the player doesn't intersect with this block, but does
        // when the player vertically moves upwards by 0.03!  This is equivalent to the move method, but MUCH faster.
        if (!normalBox.copy().expand(0.03, 0, 0.03).isIntersected(data) && normalBox.copy().expand(0.03, 0.03, 0.03).isIntersected(data)) {
            headHitter = true;
        }

        SimpleCollisionBox pointThreeBox = GetBoundingBox.getBoundingBoxFromPosAndSize(player.x, player.y - 0.03, player.z, 0.66, 1.86);
        if ((Materials.isWater(player.getClientVersion(), state) || Materials.checkFlag(state.getMaterial(), Materials.LAVA)) &&
                pointThreeBox.isIntersected(new SimpleCollisionBox(x, y, z))) {

            if (state.getMaterial() == Material.BUBBLE_COLUMN) {
                isNearBubbleColumn = true;
            }

            Vector fluidVector = FluidTypeFlowing.getFlow(player, x, y, z);
            if (fluidVector.getX() != 0 || fluidVector.getZ() != 0) {
                isNearHorizontalFlowingLiquid = true;
            }
            if (fluidVector.getY() != 0) {
                isNearVerticalFlowingLiquid = true;
            }

            isNearFluid = true;
        }

        if ((state.getMaterial() == Material.POWDER_SNOW || Materials.checkFlag(state.getMaterial(), Materials.CLIMBABLE)) && pointThreeBox.isIntersected(new SimpleCollisionBox(x, y, z))) {
            isNearClimbable = true;
        }
    }

    /**
     * If a player's gravity changed, or they have levitation effects, it's safer to not predict their next gravity
     * and to just give them lenience
     */
    public boolean canPredictNextVerticalMovement() {
        return !gravityChanged && !didLevitationChange;
    }

    public boolean controlsVerticalMovement() {
        return isNearFluid || isNearClimbable || isNearHorizontalFlowingLiquid || isNearVerticalFlowingLiquid || isNearBubbleColumn || isGliding;
    }

    public void updatePlayerPotions(String potion, Integer level) {
        if (potion.equals("LEVITATION")) {
            boolean oldPositiveLevitation = hasPositiveLevitation;
            boolean oldNegativeLevitation = hasNegativeLevitation;

            hasPositiveLevitation = hasPositiveLevitation || (level != null && level >= 0);
            hasNegativeLevitation = hasNegativeLevitation || (level != null && level < 0);

            if (oldPositiveLevitation != hasPositiveLevitation || oldNegativeLevitation != hasNegativeLevitation) {
                didLevitationChange = true;
            }
        }
    }

    public void updatePlayerGliding() {
        isGliding = true;
    }

    public void updatePlayerGravity() {
        gravityChanged = true;
    }

    public void endOfTickTick() {
        SimpleCollisionBox pointThreeBox = GetBoundingBox.getBoundingBoxFromPosAndSize(player.x, player.y - 0.03, player.z, 0.66, 1.86);

        // Determine the head hitter using the current Y position
        SimpleCollisionBox oldBB = player.boundingBox;
        player.boundingBox = GetBoundingBox.getBoundingBoxFromPosAndSize(player.x, player.y, player.z, 0.6, 1.8);
        headHitter = Collisions.collide(player, 0, 0.03, 0).getY() != 0.03;
        player.boundingBox = oldBB;

        // The last tick determines whether the player is swimming for the next tick
        isNearFluid = player.compensatedWorld.containsLiquid(pointThreeBox);

        checkNearbyBlocks(pointThreeBox);

        Integer levitationAmplifier = player.compensatedPotions.getLevitationAmplifier();

        boolean oldPositiveLevitation = hasPositiveLevitation;
        boolean oldNegativeLevitation = hasNegativeLevitation;

        hasPositiveLevitation = levitationAmplifier != null && levitationAmplifier >= 0;
        hasNegativeLevitation = levitationAmplifier != null && levitationAmplifier < 0;

        didLevitationChange = oldPositiveLevitation != hasPositiveLevitation || oldNegativeLevitation != hasNegativeLevitation;

        isGliding = player.isGliding;
        gravityChanged = false;
        wasAlwaysCertain = true;
    }

    private void checkNearbyBlocks(SimpleCollisionBox pointThreeBox) {
        // Reset variables
        isNearHorizontalFlowingLiquid = false;
        isNearVerticalFlowingLiquid = false;
        isNearClimbable = false;
        isNearBubbleColumn = false;

        // Check for flowing water
        for (int bbX = GrimMath.floor(pointThreeBox.minX); bbX <= GrimMath.ceil(pointThreeBox.maxX); bbX++) {
            for (int bbY = GrimMath.floor(pointThreeBox.minY); bbY <= GrimMath.ceil(pointThreeBox.maxY); bbY++) {
                for (int bbZ = GrimMath.floor(pointThreeBox.minZ); bbZ <= GrimMath.ceil(pointThreeBox.maxZ); bbZ++) {
                    Vector flow = FluidTypeFlowing.getFlow(player, bbX, bbY, bbZ);
                    if (flow.getX() != 0 || flow.getZ() != 0) {
                        isNearHorizontalFlowingLiquid = true;
                    }
                    if (flow.getY() != 0) {
                        isNearVerticalFlowingLiquid = true;
                    }

                    Material mat = player.compensatedWorld.getBukkitMaterialAt(bbX, bbY, bbZ);
                    if (Materials.checkFlag(player.compensatedWorld.getBukkitMaterialAt(bbX, bbY, bbZ), Materials.CLIMBABLE) || mat == Material.POWDER_SNOW) {
                        isNearClimbable = true;
                    }

                    if (mat == Material.BUBBLE_COLUMN) {
                        isNearBubbleColumn = true;
                    }
                }
            }
        }
    }

    public boolean closeEnoughToGroundToStepWithPointThree(VectorData data) {
        // This is intensive, only run it if we need it... compensate for stepping with 0.03
        //
        // This is technically wrong
        // A player can 0.03 while stepping while slightly going off of the block, in order to not
        // be vertically colliding (for 1.14+ clients only)
        //
        // To that I say... how the fuck do you even do that?
        // Yes, it's possible, but slightly going off mainly occurs when going at high speeds
        // and 0.03 when the player is barely moving
        //
        // This can cause falses in other parts of the anticheat, so it's better just to hope the
        // player doesn't step AND 0.03 AND step off at the same time... (even if they do, other
        // 0.03 mitigation systems MAY be able to fix this)
        //
        // I give up.
        if (player.clientControlledVerticalCollision && data != null && data.isZeroPointZeroThree()) {
            SimpleCollisionBox playerBox = player.boundingBox;
            player.boundingBox = player.boundingBox.copy().expand(0.03, 0, 0.03).offset(0, 0.03, 0);
            // 0.16 magic value -> 0.03 plus gravity, plus some additional lenience
            Vector collisionResult = Collisions.collide(player, 0, -0.2, 0);
            player.boundingBox = playerBox;
            return collisionResult.getY() != -0.2;
        }

        return false;
    }

    // This method can be improved by using the actual movement to see if 0.03 was feasible...
    public void determineCanSkipTick(float speed, Set<VectorData> init) {
        // Determine if the player can make an input below 0.03
        double minimum = Double.MAX_VALUE;

        if (isNearClimbable()) { // Due to skipping ticks, and 0.03, sneaking can get hidden on ladders...
            player.couldSkipTick = true;
            return;
        }

        // Fixes an issue where 0.03 causes an issue with 0.03 mitigation because slightly moving the player
        // -_- this game sucks
        SimpleCollisionBox oldPlayerBox = player.boundingBox;
        player.boundingBox = player.boundingBox.copy().expand(0.03, 0, 0.03);

        // Takes 0.01 millis, on average, to compute... this should be improved eventually
        for (VectorData data : init) {
            // Try to get the vector as close to zero as possible to give the best chance at 0.03...
            Vector toZeroVec = new PredictionEngine().handleStartingVelocityUncertainty(player, data, new Vector());
            // Collide to handle mostly gravity, but other scenarios similar to this.
            Vector collisionResult = Collisions.collide(player, toZeroVec.getX(), toZeroVec.getY(), toZeroVec.getZ(), Integer.MIN_VALUE, null);

            double minHorizLength = Math.hypot(collisionResult.getX(), collisionResult.getZ()) - speed;
            double length = Math.abs(collisionResult.getY()) + Math.max(0, minHorizLength);

            minimum = Math.min(minimum, length);

            if (minimum < 0.03) break;
        }

        player.boundingBox = oldPlayerBox;

        // As long as we are mathematically correct here, this should be perfectly accurate
        player.couldSkipTick = minimum < 0.03;
    }

    public double getHorizontalFluidPushingUncertainty(VectorData vector) {
        // We don't know if the player was in the water because of zero point fucking three
        // End of tick and start of tick can double this fluid motion, so we need to double it
        return isNearHorizontalFlowingLiquid && vector.isZeroPointZeroThree() ? 0.014 * 2 : 0;
    }

    public double getVerticalFluidPushingUncertainty(VectorData vector) {
        // We don't know if the player was in the water because of zero point fucking three
        // End of tick and start of tick can double this fluid motion, so we need to double it
        return isNearVerticalFlowingLiquid && vector.isZeroPointZeroThree() ? 0.014 * 2 : 0;
    }

    public double getVerticalBubbleUncertainty(VectorData vectorData) {
        return isNearBubbleColumn && vectorData.isZeroPointZeroThree() ? 0.35 : 0;
    }

    public double getAdditionalVerticalUncertainty(VectorData vector) {
        double fluidAddition = vector.isZeroPointZeroThree() ? 0.014 : 0;

        if (headHitter) {
            wasAlwaysCertain = false;
            // Head hitters return the vector to 0, and then apply gravity to it.
            // Not much room for abuse for this, so keep it lenient
            return -Math.max(0, vector.vector.getY()) - 0.1 - fluidAddition;
        } else if (player.uncertaintyHandler.wasAffectedByStuckSpeed()) {
            wasAlwaysCertain = false;
            // This shouldn't be needed but stuck speed can desync very easily with 0.03...
            // Especially now that both sweet berries and cobwebs are affected by stuck speed and overwrite each other
            return -0.1 - fluidAddition;
        }

        // The player couldn't have skipped their Y tick here... no point to simulate (and stop a bypass)
        if (!vector.isZeroPointZeroThree()) return 0;

        double minMovement = player.getClientVersion().isNewerThanOrEquals(ClientVersion.v_1_9) ? 0.003 : 0.005;

        // This should likely be refactored, but it works well.
        double yVel = vector.vector.getY();
        double maxYTraveled = 0;
        boolean first = true;
        do {
            // If less than minimum movement, then set to 0
            if (Math.abs(yVel) < minMovement) yVel = 0;

            // Don't add the first vector to the movement.  We already counted it.
            if (!first) {
                maxYTraveled += yVel;
            }
            first = false;

            // Simulate end of tick vector
            yVel = iterateGravity(player, yVel);

            // We aren't making progress, avoid infinite loop (This can be due to the player not having gravity)
            if (yVel == 0) break;
        } while (Math.abs(maxYTraveled + vector.vector.getY()) < 0.03);

        if (maxYTraveled != 0) {
            wasAlwaysCertain = false;
        }

        // Negate the current vector and replace it with the one we just simulated
        return maxYTraveled;
    }

    private double iterateGravity(GrimPlayer player, double y) {
        if (player.compensatedPotions.getLevitationAmplifier() != null) {
            // This supports both positive and negative levitation
            y += (0.05 * (player.compensatedPotions.getLevitationAmplifier() + 1) - y * 0.2);
        } else if (player.hasGravity) {
            // Simulate gravity
            y -= player.gravity;
        }

        // Simulate end of tick friction
        return y * 0.98;
    }
}
