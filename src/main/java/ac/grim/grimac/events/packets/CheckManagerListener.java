package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.BlockPlace;
import ac.grim.grimac.utils.anticheat.update.PositionUpdate;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.utils.anticheat.update.VehiclePositionUpdate;
import ac.grim.grimac.utils.blockplace.BlockPlaceResult;
import ac.grim.grimac.utils.blockplace.ConsumesBlockPlace;
import ac.grim.grimac.utils.blockstate.BaseBlockState;
import ac.grim.grimac.utils.blockstate.helper.BlockStateHelper;
import ac.grim.grimac.utils.collisions.HitboxData;
import ac.grim.grimac.utils.collisions.datatypes.CollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.HitData;
import ac.grim.grimac.utils.data.TeleportAcceptData;
import ac.grim.grimac.utils.enums.FluidTag;
import ac.grim.grimac.utils.math.GrimMath;
import ac.grim.grimac.utils.math.VectorUtils;
import ac.grim.grimac.utils.nmsutil.*;
import io.github.retrooper.packetevents.event.PacketListenerAbstract;
import io.github.retrooper.packetevents.event.PacketListenerPriority;
import io.github.retrooper.packetevents.event.impl.PacketPlayReceiveEvent;
import io.github.retrooper.packetevents.event.impl.PacketPlaySendEvent;
import io.github.retrooper.packetevents.packettype.PacketType;
import io.github.retrooper.packetevents.packetwrappers.play.in.blockdig.WrappedPacketInBlockDig;
import io.github.retrooper.packetevents.packetwrappers.play.in.blockplace.WrappedPacketInBlockPlace;
import io.github.retrooper.packetevents.packetwrappers.play.in.flying.WrappedPacketInFlying;
import io.github.retrooper.packetevents.packetwrappers.play.in.vehiclemove.WrappedPacketInVehicleMove;
import io.github.retrooper.packetevents.utils.pair.Pair;
import io.github.retrooper.packetevents.utils.player.ClientVersion;
import io.github.retrooper.packetevents.utils.player.Direction;
import io.github.retrooper.packetevents.utils.player.Hand;
import io.github.retrooper.packetevents.utils.server.ServerVersion;
import io.github.retrooper.packetevents.utils.vector.Vector3d;
import io.github.retrooper.packetevents.utils.vector.Vector3i;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class CheckManagerListener extends PacketListenerAbstract {

    long lastPosLook = 0;

    public CheckManagerListener() {
        super(PacketListenerPriority.LOW);
    }

    // Copied from MCP...
    // Returns null if there isn't anything.
    //
    // I do have to admit that I'm starting to like bifunctions/new java 8 things more than I originally did.
    // although I still don't understand Mojang's obsession with streams in some of the hottest methods... that kills performance
    static HitData traverseBlocks(GrimPlayer player, Vector3d start, Vector3d end, BiFunction<BaseBlockState, Vector3i, HitData> predicate) {
        // I guess go back by the collision epsilon?
        double endX = GrimMath.lerp(-1.0E-7D, end.x, start.x);
        double endY = GrimMath.lerp(-1.0E-7D, end.y, start.y);
        double endZ = GrimMath.lerp(-1.0E-7D, end.z, start.z);
        double startX = GrimMath.lerp(-1.0E-7D, start.x, end.x);
        double startY = GrimMath.lerp(-1.0E-7D, start.y, end.y);
        double startZ = GrimMath.lerp(-1.0E-7D, start.z, end.z);
        int floorStartX = GrimMath.floor(startX);
        int floorStartY = GrimMath.floor(startY);
        int floorStartZ = GrimMath.floor(startZ);


        if (start.equals(end)) return null;

        BaseBlockState state = player.compensatedWorld.getWrappedBlockStateAt(floorStartX, floorStartY, floorStartZ);
        HitData apply = predicate.apply(state, new Vector3i(floorStartX, floorStartY, floorStartZ));

        if (apply != null) {
            return apply;
        }

        double xDiff = endX - startX;
        double yDiff = endY - startY;
        double zDiff = endZ - startZ;
        int xSign = GrimMath.sign(xDiff);
        int ySign = GrimMath.sign(yDiff);
        int zSign = GrimMath.sign(zDiff);

        double posXInverse = xSign == 0 ? Double.MAX_VALUE : xSign / xDiff;
        double posYInverse = ySign == 0 ? Double.MAX_VALUE : ySign / yDiff;
        double posZInverse = zSign == 0 ? Double.MAX_VALUE : zSign / zDiff;

        double d12 = posXInverse * (xSign > 0 ? 1.0D - GrimMath.frac(startX) : GrimMath.frac(startX));
        double d13 = posYInverse * (ySign > 0 ? 1.0D - GrimMath.frac(startY) : GrimMath.frac(startY));
        double d14 = posZInverse * (zSign > 0 ? 1.0D - GrimMath.frac(startZ) : GrimMath.frac(startZ));

        // Can't figure out what this code does currently
        while (d12 <= 1.0D || d13 <= 1.0D || d14 <= 1.0D) {
            if (d12 < d13) {
                if (d12 < d14) {
                    floorStartX += xSign;
                    d12 += posXInverse;
                } else {
                    floorStartZ += zSign;
                    d14 += posZInverse;
                }
            } else if (d13 < d14) {
                floorStartY += ySign;
                d13 += posYInverse;
            } else {
                floorStartZ += zSign;
                d14 += posZInverse;
            }

            state = player.compensatedWorld.getWrappedBlockStateAt(floorStartX, floorStartY, floorStartZ);
            apply = predicate.apply(state, new Vector3i(floorStartX, floorStartY, floorStartZ));

            if (apply != null) {
                return apply;
            }
        }

        return null;
    }

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        byte packetID = event.getPacketId();

        GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getPlayer());
        if (player == null) return;

        if (PacketType.Play.Client.Util.isInstanceOfFlying(packetID)) {
            WrappedPacketInFlying flying = new WrappedPacketInFlying(event.getNMSPacket());

            boolean hasPosition = packetID == PacketType.Play.Client.POSITION || packetID == PacketType.Play.Client.POSITION_LOOK;
            boolean hasLook = packetID == PacketType.Play.Client.LOOK || packetID == PacketType.Play.Client.POSITION_LOOK;
            boolean onGround = flying.isOnGround();

            player.packetStateData.lastPacketWasTeleport = false;
            TeleportAcceptData teleportData = null;
            if (hasPosition) {
                Vector3d position = VectorUtils.clampVector(flying.getPosition());
                teleportData = player.getSetbackTeleportUtil().checkTeleportQueue(position.getX(), position.getY(), position.getZ());
                player.packetStateData.lastPacketWasTeleport = teleportData.isTeleport();
            }

            // Don't check duplicate 1.17 packets (Why would you do this mojang?)
            // Don't check rotation since it changes between these packets, with the second being irrelevant.
            //
            // If a player sends a POS LOOK in a vehicle... then it was this stupid fucking mechanic
            if (hasPosition && hasLook && !player.packetStateData.lastPacketWasTeleport &&
                    (player.getClientVersion().isNewerThanOrEquals(ClientVersion.v_1_17) &&
                            new Vector3d(player.x, player.y, player.z).equals(flying.getPosition())) || player.inVehicle) {
                lastPosLook = System.currentTimeMillis();
                player.packetStateData.lastPacketWasOnePointSeventeenDuplicate = true;

                // Don't let players on 1.17+ clients on 1.8- servers FastHeal by right-clicking
                // the ground with a bucket... ViaVersion marked this as a WONTFIX, so I'll include the fix.
                if (ServerVersion.getVersion().isOlderThanOrEquals(ServerVersion.v_1_8_8)) {
                    event.setCancelled(true);
                }
                return;
            }

            lastPosLook = System.currentTimeMillis();

            // Check for blocks within 0.03 of the player's position before allowing ground to be true - if 0.03
            boolean nearGround = Collisions.collide(player, 0, -0.03, 0).getY() != -0.03;
            if (!hasPosition && onGround != player.packetStateData.packetPlayerOnGround && nearGround && player.clientVelocity.getY() < 0.03) {
                player.lastOnGround = true;
                player.uncertaintyHandler.onGroundUncertain = true;
                player.uncertaintyHandler.lastTickWasNearGroundZeroPointZeroThree = true;
                player.clientClaimsLastOnGround = true;
            }

            player.lastX = player.x;
            player.lastY = player.y;
            player.lastZ = player.z;
            player.lastXRot = player.xRot;
            player.lastYRot = player.yRot;

            player.packetStateData.lastPacketWasOnePointSeventeenDuplicate = false;

            player.packetStateData.packetPlayerOnGround = onGround;

            if (hasLook) {
                player.xRot = flying.getYaw();
                player.yRot = flying.getPitch();
            }

            if (hasPosition) {
                Vector3d position = flying.getPosition();
                Vector3d clampVector = VectorUtils.clampVector(position);

                player.x = clampVector.getX();
                player.y = clampVector.getY();
                player.z = clampVector.getZ();

                final PositionUpdate update = new PositionUpdate(new Vector3d(player.x, player.y, player.z), position, onGround, teleportData.isTeleport(), teleportData.getSetback());
                player.checkManager.onPositionUpdate(update);
            }

            if (hasLook) {
                float deltaXRot = player.xRot - player.lastXRot;
                float deltaYRot = player.yRot - player.lastYRot;

                final RotationUpdate update = new RotationUpdate(player.lastXRot, player.lastYRot, player.xRot, player.yRot, deltaXRot, deltaYRot);
                player.checkManager.onRotationUpdate(update);
            }

            player.packetStateData.didLastLastMovementIncludePosition = player.packetStateData.didLastMovementIncludePosition;
            player.packetStateData.didLastMovementIncludePosition = hasPosition;
        }

        if (packetID == PacketType.Play.Client.VEHICLE_MOVE) {
            WrappedPacketInVehicleMove move = new WrappedPacketInVehicleMove(event.getNMSPacket());
            Vector3d position = move.getPosition();

            player.lastX = player.x;
            player.lastY = player.y;
            player.lastZ = player.z;

            Vector3d clamp = VectorUtils.clampVector(position);
            player.x = clamp.getX();
            player.y = clamp.getY();
            player.z = clamp.getZ();

            final boolean isTeleport = player.getSetbackTeleportUtil().checkVehicleTeleportQueue(position.getX(), position.getY(), position.getZ());
            player.packetStateData.lastPacketWasTeleport = isTeleport;
            final VehiclePositionUpdate update = new VehiclePositionUpdate(clamp, position, move.getYaw(), move.getPitch(), isTeleport);
            player.checkManager.onVehiclePositionUpdate(update);

            player.packetStateData.receivedSteerVehicle = false;
        }

        if (packetID == PacketType.Play.Client.BLOCK_DIG) {
            WrappedPacketInBlockDig dig = new WrappedPacketInBlockDig(event.getNMSPacket());


            if (dig.getDigType() == WrappedPacketInBlockDig.PlayerDigType.STOP_DESTROY_BLOCK) {
                BaseBlockState block = player.compensatedWorld.getWrappedBlockStateAt(dig.getBlockPosition());
                // Not unbreakable
                if (XMaterial.getHardness(XMaterial.fromMaterial(block.getMaterial())) != -1.0f) {
                    player.compensatedWorld.updateBlock(dig.getBlockPosition().getX(), dig.getBlockPosition().getY(), dig.getBlockPosition().getZ(), 0);
                }
            }

            if (dig.getDigType() == WrappedPacketInBlockDig.PlayerDigType.START_DESTROY_BLOCK) {
                // GET destroy speed
                // Starts with itemstack get destroy speed
                ItemStack tool = player.bukkitPlayer.getItemInHand();

                // A creative mode player cannot break things with a sword!
                if (player.gamemode == GameMode.CREATIVE && tool.getType().name().contains("SWORD")) {
                    return;
                }

                BaseBlockState block = player.compensatedWorld.getWrappedBlockStateAt(dig.getBlockPosition());

                boolean isBestTool = false;
                float speedMultiplier = 1.0f;

                // 1.13 and below need their own huge methods to support this...
                if (tool.getType().name().endsWith("_AXE")) {
                    isBestTool = Tag.MINEABLE_AXE.isTagged(block.getMaterial());
                } else if (tool.getType().name().endsWith("_PICKAXE")) {
                    isBestTool = Tag.MINEABLE_PICKAXE.isTagged(block.getMaterial());
                } else if (tool.getType().name().endsWith("_SHOVEL")) {
                    isBestTool = Tag.MINEABLE_SHOVEL.isTagged(block.getMaterial());
                }

                if (isBestTool) {
                    int tier = 0;
                    if (tool.getType().name().contains("WOOD")) { // Tier 0
                        speedMultiplier = 2.0f;
                    } else if (tool.getType().name().contains("STONE")) { // Tier 1
                        speedMultiplier = 4.0f;
                        tier = 1;
                    } else if (tool.getType().name().contains("IRON")) { // Tier 2
                        speedMultiplier = 6.0f;
                        tier = 2;
                    } else if (tool.getType().name().contains("DIAMOND")) { // Tier 3
                        speedMultiplier = 8.0f;
                        tier = 3;
                    } else if (tool.getType().name().contains("GOLD")) { // Tier 0
                        speedMultiplier = 12.0f;
                    } else if (tool.getType().name().contains("NETHERITE")) { // Tier 4
                        speedMultiplier = 9.0f;
                        tier = 4;
                    }

                    if (tier < 3 && Tag.NEEDS_DIAMOND_TOOL.isTagged(block.getMaterial())) {
                        isBestTool = false;
                    } else if (tier < 2 && Tag.NEEDS_IRON_TOOL.isTagged(block.getMaterial())) {
                        isBestTool = false;
                    } else if (tier < 1 && Tag.NEEDS_STONE_TOOL.isTagged(block.getMaterial())) {
                        isBestTool = false;
                    }
                }

                // Shears can mine some blocks faster
                if (tool.getType() == XMaterial.SHEARS.parseMaterial()) {
                    if (block.getMaterial() == XMaterial.COBWEB.parseMaterial() || Materials.checkFlag(block.getMaterial(), Materials.LEAVES)) {
                        speedMultiplier = 15.0f;
                    } else if (block.getMaterial().name().contains("WOOL")) {
                        speedMultiplier = 5.0f;
                    } else if (block.getMaterial() == XMaterial.VINE.parseMaterial() ||
                            block.getMaterial() == XMaterial.GLOW_LICHEN.parseMaterial()) {
                        speedMultiplier = 2.0f;
                    }

                    isBestTool = block.getMaterial() == XMaterial.COBWEB.parseMaterial() ||
                            block.getMaterial() == XMaterial.REDSTONE_WIRE.parseMaterial() ||
                            block.getMaterial() == XMaterial.TRIPWIRE.parseMaterial();
                }

                // Swords can also mine some blocks faster
                if (tool.getType().name().contains("SWORD")) {
                    if (block.getMaterial() == XMaterial.COBWEB.parseMaterial()) {
                        speedMultiplier = 15.0f;
                    } else if (Materials.checkFlag(block.getMaterial(), Materials.PLANT) ||
                            Materials.checkFlag(block.getMaterial(), Materials.LEAVES) ||
                            block.getMaterial() == XMaterial.PUMPKIN.parseMaterial() ||
                            block.getMaterial() == XMaterial.MELON.parseMaterial()) {
                        speedMultiplier = 1.5f;
                    }

                    isBestTool = block.getMaterial() == XMaterial.COBWEB.parseMaterial();
                }

                float blockHardness = XMaterial.getHardness(XMaterial.fromMaterial(block.getMaterial()));

                if (isBestTool) {
                    if (blockHardness == -1.0f) {
                        speedMultiplier = 0;
                    } else {
                        int digSpeed = tool.getEnchantmentLevel(Enchantment.DIG_SPEED);
                        if (digSpeed > 0) {
                            speedMultiplier += digSpeed * digSpeed + 1;
                        }
                    }
                }

                Integer digSpeed = player.compensatedPotions.getPotionLevel("DIG_SPEED");
                Integer conduit = player.compensatedPotions.getPotionLevel("CONDUIT_POWER");

                if (digSpeed != null || conduit != null) {
                    int i = 0;
                    int j = 0;
                    if (digSpeed != null) {
                        i = digSpeed;
                    }

                    if (conduit != null) {
                        j = conduit;
                    }

                    int hasteLevel = Math.max(i, j);

                    speedMultiplier *= 1 + (0.2 * hasteLevel);
                }

                Integer miningFatigue = player.compensatedPotions.getPotionLevel("SLOW_DIGGING");

                if (miningFatigue != null) {
                    switch (miningFatigue) {
                        case 0:
                            speedMultiplier *= 0.3;
                            break;
                        case 1:
                            speedMultiplier *= 0.09;
                            break;
                        case 2:
                            speedMultiplier *= 0.0027;
                            break;
                        default:
                            speedMultiplier *= 0.00081;
                    }
                }

                boolean hasAquaAffinity = false;

                ItemStack helmet = player.bukkitPlayer.getInventory().getHelmet();
                ItemStack chestplate = player.bukkitPlayer.getInventory().getChestplate();
                ItemStack leggings = player.bukkitPlayer.getInventory().getLeggings();
                ItemStack boots = player.bukkitPlayer.getInventory().getBoots();

                if ((helmet != null && helmet.containsEnchantment(Enchantment.WATER_WORKER)) ||
                        (chestplate != null && chestplate.containsEnchantment(Enchantment.WATER_WORKER)) ||
                        (leggings != null && leggings.containsEnchantment(Enchantment.WATER_WORKER)) ||
                        (boots != null && boots.containsEnchantment(Enchantment.WATER_WORKER))) {
                    hasAquaAffinity = true;
                }

                if (player.fluidOnEyes == FluidTag.WATER && !hasAquaAffinity) {
                    speedMultiplier /= 5;
                }

                if (!player.onGround) {
                    speedMultiplier /= 5;
                }

                float damage = speedMultiplier / blockHardness;

                boolean canHarvest = !XMaterial.requiresCorrectTool(XMaterial.fromMaterial(block.getMaterial())) || isBestTool;
                if (canHarvest) {
                    damage /= 30;
                } else {
                    damage /= 100;
                }

                //Instant breaking
                if (damage > 1 || player.gamemode == GameMode.CREATIVE) {
                    player.compensatedWorld.updateBlock(dig.getBlockPosition().getX(), dig.getBlockPosition().getY(), dig.getBlockPosition().getZ(),
                            0);
                }
            }

        }

        // Check for interactable first (door, etc)
        if (PacketType.Play.Client.Util.isBlockPlace(event.getPacketId())) {
            WrappedPacketInBlockPlace place = new WrappedPacketInBlockPlace(event.getNMSPacket());

            ItemStack placedWith = player.bukkitPlayer.getInventory().getItem(player.packetStateData.lastSlotSelected);
            Material material = transformMaterial(placedWith);

            boolean onlyAir = material == null || material == Material.AIR;

            if (XMaterial.supports(9)) {
                ItemStack offhand = player.bukkitPlayer.getInventory().getItemInOffHand();
                onlyAir = onlyAir && offhand.getType() == Material.AIR;
            }

            // The offhand is unable to interact with blocks like this... try to stop some desync points before they happen
            if ((!player.isSneaking || onlyAir) && place.getHand() == Hand.MAIN_HAND) {
                Vector3i blockPosition = place.getBlockPosition();
                BlockPlace blockPlace = new BlockPlace(player, blockPosition, place.getDirection(), material, getNearestHitResult(player, null, true));

                // Right-clicking a trapdoor/door/etc.
                if (Materials.checkFlag(blockPlace.getPlacedAgainstMaterial(), Materials.CLIENT_SIDE_INTERACTABLE)) {
                    Vector3i location = blockPlace.getPlacedAgainstBlockLocation();
                    player.compensatedWorld.tickOpenable(location.getX(), location.getY(), location.getZ());
                    return;
                }

                // This also has side effects
                // This method is for when the block doesn't always consume the click
                // This causes a ton of desync's but mojang doesn't seem to care...
                if (ConsumesBlockPlace.consumesPlace(player, player.compensatedWorld.getWrappedBlockStateAt(blockPlace.getPlacedAgainstBlockLocation()), blockPlace)) {
                    return;
                }
            }
        }

        if (packetID == PacketType.Play.Client.BLOCK_PLACE) {
            WrappedPacketInBlockPlace place = new WrappedPacketInBlockPlace(event.getNMSPacket());

            // TODO: Support offhand!
            ItemStack placedWith = player.bukkitPlayer.getInventory().getItem(player.packetStateData.lastSlotSelected);
            Material material = transformMaterial(placedWith);

            // Lilypads are USE_ITEM (THIS CAN DESYNC, WTF MOJANG)
            if (material == XMaterial.LILY_PAD.parseMaterial()) {
                placeLilypad(player); // Pass a block place because lily pads have a hitbox
                return;
            }

            Material toBucketMat = Materials.transformBucketMaterial(material);
            if (toBucketMat != null) {
                placeWaterLavaSnowBucket(player, toBucketMat);
            }

            if (material == Material.BUCKET) {
                placeBucket(player);
            }
        }

        if (PacketType.Play.Client.Util.isBlockPlace(event.getPacketId())) {
            WrappedPacketInBlockPlace place = new WrappedPacketInBlockPlace(event.getNMSPacket());
            Vector3i blockPosition = place.getBlockPosition();
            Direction face = place.getDirection();

            // TODO: Support offhand!
            ItemStack placedWith = player.bukkitPlayer.getInventory().getItem(player.packetStateData.lastSlotSelected);
            Material material = transformMaterial(placedWith);
            BlockPlace blockPlace = new BlockPlace(player, blockPosition, face, material, getNearestHitResult(player, null, true));

            if (placedWith != null && material.isBlock()) {
                player.checkManager.onBlockPlace(blockPlace);

                if (!blockPlace.isCancelled()) {
                    BlockPlaceResult.getMaterialData(material).applyBlockPlaceToWorld(player, blockPlace);
                }
            }
        }

        // Call the packet checks last as they can modify the contents of the packet
        // Such as the NoFall check setting the player to not be on the ground
        player.checkManager.onPacketReceive(event);
    }

    private void placeWaterLavaSnowBucket(GrimPlayer player, Material toPlace) {
        HitData data = getNearestHitResult(player, toPlace, false);
        if (data != null) {
            BlockPlace blockPlace = new BlockPlace(player, data.getPosition(), Direction.valueOf(data.getClosestDirection().name()), toPlace, data);

            // If we hit a waterloggable block, then the bucket is directly placed
            // Otherwise, use the face to determine where to place the bucket
            if (Materials.isPlaceableLiquidBucket(blockPlace.getMaterial()) && ServerVersion.getVersion().isNewerThanOrEquals(ServerVersion.v_1_13)) {
                BlockData existing = blockPlace.getExistingBlockBlockData();
                if (existing instanceof Waterlogged) {
                    Waterlogged waterlogged = (Waterlogged) existing.clone(); // Don't corrupt palette
                    waterlogged.setWaterlogged(true);
                    blockPlace.set(waterlogged);
                    return;
                }
            }

            // Powder snow, lava, and water all behave like placing normal blocks after checking for waterlogging
            blockPlace.set(toPlace);
        }
    }

    private void placeBucket(GrimPlayer player) {
        HitData data = getNearestHitResult(player, null, true);
        if (data != null) {
            BlockPlace blockPlace = new BlockPlace(player, data.getPosition(), Direction.valueOf(data.getClosestDirection().name()), Material.BUCKET, data);

            if (data.getState().getMaterial() == Material.POWDER_SNOW) {
                blockPlace.set(Material.AIR);
                return;
            }

            // We didn't hit fluid
            if (player.compensatedWorld.getFluidLevelAt(data.getPosition().getX(), data.getPosition().getY(), data.getPosition().getZ()) == 0)
                return;

            if (ServerVersion.getVersion().isNewerThanOrEquals(ServerVersion.v_1_13)) {
                BlockData existing = blockPlace.getExistingBlockBlockData();
                if (existing instanceof Waterlogged) {
                    Waterlogged waterlogged = (Waterlogged) existing.clone(); // Don't corrupt palette
                    waterlogged.setWaterlogged(false);
                    blockPlace.set(waterlogged);
                    return;
                }
            }

            // Therefore, not waterlogged and is a fluid, and is therefore a source block
            blockPlace.set(Material.AIR);
        }
    }

    private void placeLilypad(GrimPlayer player) {
        HitData data = getNearestHitResult(player, null, true);
        if (data != null) {
            // A lilypad cannot replace a fluid
            if (player.compensatedWorld.getFluidLevelAt(data.getPosition().getX(), data.getPosition().getY() + 1, data.getPosition().getZ()) > 0)
                return;

            BlockPlace blockPlace = new BlockPlace(player, data.getPosition(), Direction.valueOf(data.getClosestDirection().name()), Material.LILY_PAD, data);

            // We checked for a full fluid block below here.
            if (player.compensatedWorld.getWaterFluidLevelAt(data.getPosition().getX(), data.getPosition().getY(), data.getPosition().getZ()) > 0
                    || data.getState().getMaterial() == Material.ICE || data.getState().getMaterial() == Material.FROSTED_ICE) {
                Vector3i pos = data.getPosition().clone();
                pos.setY(pos.getY() + 1);

                blockPlace.set(pos, BlockStateHelper.create(blockPlace.getMaterial()));
            }
        }
    }

    // For example, placing seeds to place wheat
    // TODO: Make this compatible with previous versions by using XMaterial
    private Material transformMaterial(ItemStack stack) {
        if (stack == null) return null;
        if (stack.getType() == Material.COCOA_BEANS) return Material.COCOA;
        if (stack.getType() == Material.INK_SAC && stack.getDurability() == 3) return Material.COCOA;
        if (stack.getType() == Material.FIRE_CHARGE) return Material.FIRE;
        if (stack.getType() == Material.POTATO) return Material.POTATOES;
        if (stack.getType() == Material.BEETROOT_SEEDS) return Material.BEETROOTS;
        if (stack.getType() == Material.CARROT) return Material.CARROTS;
        if (stack.getType() == Material.PUMPKIN_SEEDS) return Material.PUMPKIN_STEM;
        if (stack.getType() == Material.MELON_SEEDS) return Material.MELON_STEM;
        if (stack.getType() == Material.WHEAT_SEEDS) return Material.WHEAT;
        if (stack.getType() == Material.REDSTONE) return Material.REDSTONE_WIRE;
        if (stack.getType() == Material.POWDER_SNOW_BUCKET) return Material.POWDER_SNOW;
        if (stack.getType() == Material.SWEET_BERRIES) return Material.SWEET_BERRY_BUSH;

        return stack.getType();
    }

    private HitData getNearestHitResult(GrimPlayer player, Material heldItem, boolean sourcesHaveHitbox) {
        // TODO: When we do this post-tick (fix desync) switch to lastX
        Vector3d startingPos = new Vector3d(player.x, player.y + player.getEyeHeight(), player.z);
        Vector startingVec = new Vector(startingPos.getX(), startingPos.getY(), startingPos.getZ());
        Ray trace = new Ray(player, startingPos.getX(), startingPos.getY(), startingPos.getZ(), player.xRot, player.yRot);
        Vector endVec = trace.getPointAtDistance(6);
        Vector3d endPos = new Vector3d(endVec.getX(), endVec.getY(), endVec.getZ());

        return traverseBlocks(player, startingPos, endPos, (block, vector3i) -> {
            CollisionBox data = HitboxData.getBlockHitbox(player, heldItem, player.getClientVersion(), block, vector3i.getX(), vector3i.getY(), vector3i.getZ());
            List<SimpleCollisionBox> boxes = new ArrayList<>();
            data.downCast(boxes);

            double bestHitResult = Double.MAX_VALUE;
            Vector bestHitLoc = null;
            BlockFace bestFace = null;

            for (SimpleCollisionBox box : boxes) {
                Pair<Vector, BlockFace> intercept = ReachUtils.calculateIntercept(box, trace.getOrigin(), trace.getPointAtDistance(6));
                if (intercept.getFirst() == null) continue; // No intercept

                Vector hitLoc = intercept.getFirst();

                if (hitLoc.distanceSquared(startingVec) < bestHitResult) {
                    bestHitResult = hitLoc.distanceSquared(startingVec);
                    bestHitLoc = hitLoc;
                    bestFace = intercept.getSecond();
                }
            }
            if (bestHitLoc != null) {
                return new HitData(vector3i, bestHitLoc, bestFace, block);
            }

            if (sourcesHaveHitbox &&
                    (player.compensatedWorld.isWaterSourceBlock(vector3i.getX(), vector3i.getY(), vector3i.getZ())
                            || player.compensatedWorld.getLavaFluidLevelAt(vector3i.getX(), vector3i.getY(), vector3i.getZ()) == (8 / 9f))) {
                double waterHeight = player.compensatedWorld.getFluidLevelAt(vector3i.getX(), vector3i.getY(), vector3i.getZ());
                SimpleCollisionBox box = new SimpleCollisionBox(vector3i.getX(), vector3i.getY(), vector3i.getZ(), vector3i.getX() + 1, vector3i.getY() + waterHeight, vector3i.getZ() + 1);

                Pair<Vector, BlockFace> intercept = ReachUtils.calculateIntercept(box, trace.getOrigin(), trace.getPointAtDistance(6));

                if (intercept.getFirst() != null) {
                    return new HitData(vector3i, intercept.getFirst(), intercept.getSecond(), block);
                }
            }

            return null;
        });
    }

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        GrimPlayer player = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(event.getPlayer());
        if (player == null) return;

        player.checkManager.onPacketSend(event);
    }
}
