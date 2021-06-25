package ac.grim.grimac.utils.data;

import ac.grim.grimac.GrimAC;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.nmsImplementations.Materials;
import ac.grim.grimac.utils.nmsImplementations.XMaterial;
import io.github.retrooper.packetevents.utils.nms.NMSUtils;
import io.github.retrooper.packetevents.utils.player.Hand;
import io.github.retrooper.packetevents.utils.reflection.Reflection;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.Collection;

public class PredictionData {
    private static final Method onePointEightAttribute;
    private static Object movementSpeedAttribute;

    static {
        onePointEightAttribute = Reflection.getMethod(NMSUtils.entityHumanClass, "getAttributeInstance", 0);
        try {
            if (XMaterial.getVersion() == 8) {
                // 1.8 mappings
                movementSpeedAttribute = NMSUtils.getNMSClass("GenericAttributes").getDeclaredField("MOVEMENT_SPEED").get(null);
            } else if (XMaterial.getVersion() < 8) {
                // 1.7 mappings
                movementSpeedAttribute = NMSUtils.getNMSClass("GenericAttributes").getDeclaredField("d").get(null);
            }
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public GrimPlayer player;
    public double playerX;
    public double playerY;
    public double playerZ;
    public float xRot;
    public float yRot;
    public boolean onGround;
    public boolean isSprinting;
    public boolean isSneaking;
    public boolean isUsingItem = false;
    public World playerWorld;
    public double movementSpeed;
    public float jumpAmplifier;
    public float levitationAmplifier = 0;
    public float slowFallingAmplifier = 0;
    public float dolphinsGraceAmplifier = 0;
    public float flySpeed;
    public double fallDistance;
    public Integer playerVehicle;
    public float vehicleHorizontal;
    public float vehicleForward;
    public boolean isJustTeleported = false;
    public VelocityData firstBreadKB;
    public VelocityData requiredKB;
    public VelocityData firstBreadExplosion = null;
    public VelocityData possibleExplosion = null;
    public int minimumTickRequiredToContinue;
    public int lastTransaction;
    public int itemHeld;
    public float horseJump = 0;

    public boolean isDummy = false;

    // For regular movement
    public PredictionData(GrimPlayer player, double playerX, double playerY, double playerZ, float xRot, float yRot, boolean onGround) {
        this.player = player;
        this.playerX = playerX;
        this.playerY = playerY;
        this.playerZ = playerZ;
        this.xRot = xRot;
        this.yRot = yRot;
        this.onGround = onGround;

        this.isSprinting = player.packetStateData.isPacketSprinting;
        this.isSneaking = player.packetStateData.isPacketSneaking;

        // Handle the player dropping food to stop eating
        if (player.packetStateData.eatingHand == Hand.MAIN_HAND) {
            ItemStack mainHand = player.bukkitPlayer.getInventory().getItem(player.bukkitPlayer.getInventory().getHeldItemSlot());
            if (mainHand == null || !Materials.isUsable(mainHand.getType())) {
                player.packetStateData.isEating = false;
            }
        } else {
            ItemStack offHand = player.bukkitPlayer.getInventory().getItemInOffHand();
            // I don't believe you bukkit that this cannot be null from 1.9 to 1.17
            if (offHand == null || !Materials.isUsable(offHand.getType())) {
                player.packetStateData.isEating = false;
            }
        }

        this.isUsingItem = player.packetStateData.isEating;

        this.playerWorld = player.bukkitPlayer.getWorld();
        this.fallDistance = player.bukkitPlayer.getFallDistance();
        this.movementSpeed = getMovementSpeedAttribute(player.bukkitPlayer);

        // When a player punches a mob, bukkit thinks the player isn't sprinting
        if (isSprinting && !player.bukkitPlayer.isSprinting()) this.movementSpeed *= 1.3D;

        Collection<PotionEffect> playerPotionEffects = player.bukkitPlayer.getActivePotionEffects();

        this.jumpAmplifier = getHighestPotionEffect(playerPotionEffects, "JUMP", 0);
        this.levitationAmplifier = getHighestPotionEffect(playerPotionEffects, "LEVITATION", 9);
        this.slowFallingAmplifier = getHighestPotionEffect(playerPotionEffects, "SLOW_FALLING", 13);
        this.dolphinsGraceAmplifier = getHighestPotionEffect(playerPotionEffects, "DOLPHINS_GRACE", 13);

        this.flySpeed = player.bukkitPlayer.getFlySpeed() / 2;
        this.playerVehicle = player.packetStateData.vehicle;

        firstBreadKB = player.knockbackHandler.getFirstBreadOnlyKnockback();
        requiredKB = player.knockbackHandler.getRequiredKB();

        firstBreadExplosion = player.explosionHandler.getFirstBreadAddedExplosion();
        possibleExplosion = player.explosionHandler.getPossibleExplosions();

        minimumTickRequiredToContinue = GrimAC.getCurrentTick() + 2;
        lastTransaction = player.packetStateData.packetLastTransactionReceived;

        itemHeld = player.packetStateData.lastSlotSelected;
        player.packetStateData.horseJump = 0;
    }

    public static double getMovementSpeedAttribute(LivingEntity entity) {
        if (XMaterial.getVersion() > 8) {
            return entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getValue();
        }

        try {
            Method handle = Reflection.getMethod(entity.getClass(), "getHandle", 0);
            Object attribute = onePointEightAttribute.invoke(handle.invoke(entity), movementSpeedAttribute);
            Method valueField = attribute.getClass().getMethod("getValue");
            return (double) valueField.invoke(attribute);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0.1f;
    }

    public static int getHighestPotionEffect(Collection<PotionEffect> effects, String typeName, int minimumVersion) {
        if (XMaterial.getVersion() < minimumVersion) return 0;

        PotionEffectType type = PotionEffectType.getByName(typeName);

        int highestEffect = 0;
        for (PotionEffect effect : effects) {
            if (effect.getType() == type && effect.getAmplifier() > highestEffect)
                highestEffect = effect.getAmplifier();
        }

        return highestEffect;
    }

    // For riding entity movement while in control
    public PredictionData(GrimPlayer player, double boatX, double boatY, double boatZ, float xRot, float yRot) {
        this.player = player;
        this.playerX = boatX;
        this.playerY = boatY;
        this.playerZ = boatZ;
        this.xRot = xRot;
        this.yRot = yRot;
        this.onGround = true;
        this.isSprinting = false;
        this.isSneaking = false;
        this.playerVehicle = player.packetStateData.vehicle;
        this.vehicleForward = player.packetStateData.packetVehicleForward;
        this.vehicleHorizontal = player.packetStateData.packetVehicleHorizontal;

        Collection<PotionEffect> playerPotionEffects = player.bukkitPlayer.getActivePotionEffects();

        this.jumpAmplifier = getHighestPotionEffect(playerPotionEffects, "JUMP", 0);
        this.levitationAmplifier = getHighestPotionEffect(playerPotionEffects, "LEVITATION", 9);
        this.slowFallingAmplifier = getHighestPotionEffect(playerPotionEffects, "SLOW_FALLING", 13);
        this.dolphinsGraceAmplifier = getHighestPotionEffect(playerPotionEffects, "DOLPHINS_GRACE", 13);

        this.playerWorld = player.bukkitPlayer.getWorld();
        this.fallDistance = player.bukkitPlayer.getFallDistance();
        this.movementSpeed = getMovementSpeedAttribute(player.bukkitPlayer);

        firstBreadKB = player.knockbackHandler.getFirstBreadOnlyKnockback();
        requiredKB = player.knockbackHandler.getRequiredKB();

        minimumTickRequiredToContinue = GrimAC.getCurrentTick() + 2;
        lastTransaction = player.packetStateData.packetLastTransactionReceived;

        itemHeld = player.packetStateData.lastSlotSelected;

        if (player.packetStateData.horseJump > 0) {
            if (player.packetStateData.horseJump >= 90) {
                horseJump = 1.0F;
            } else {
                horseJump = 0.4F + 0.4F * player.packetStateData.horseJump / 90.0F;
            }
        }

        player.packetStateData.horseJump = 0;
    }

    public PredictionData(GrimPlayer player) {
        PacketEntity vehicle = player.compensatedEntities.getEntity(player.packetStateData.vehicle);
        this.player = player;
        this.playerVehicle = player.packetStateData.vehicle;

        this.playerX = vehicle.position.getX();
        this.playerY = vehicle.position.getY();
        this.playerZ = vehicle.position.getZ();

        firstBreadKB = player.knockbackHandler.getFirstBreadOnlyKnockback();
        requiredKB = player.knockbackHandler.getRequiredKB();

        firstBreadExplosion = player.explosionHandler.getFirstBreadAddedExplosion();
        possibleExplosion = player.explosionHandler.getPossibleExplosions();

        minimumTickRequiredToContinue = GrimAC.getCurrentTick() + 2;
        lastTransaction = player.packetStateData.packetLastTransactionReceived;

        itemHeld = player.packetStateData.lastSlotSelected;

        isDummy = true;
        player.packetStateData.horseJump = 0;
    }
}
