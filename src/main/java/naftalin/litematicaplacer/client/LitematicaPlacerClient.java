package naftalin.litematicaplacer.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.*;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;

import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import static java.lang.Math.floor;

public class LitematicaPlacerClient implements ClientModInitializer {
    public static final String MOD_ID = "litematicaplacer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final MinecraftClient MC = MinecraftClient.getInstance();
    public static long tickSleep = 0;
    public static float savedYaw = 0;
    public static float savedPitch = 0;
    private static KeyBinding litematicaplacerBind;
    public static boolean litematicaplacerEnabled = false;
    double playerReach = 20.25;
    public static Collection<Property<?>> propertiesAbleToBeChangedByRightClick = new ArrayList<>();




    @Override
    public void onInitializeClient() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        propertiesAbleToBeChangedByRightClick.add(Properties.NOTE);
        propertiesAbleToBeChangedByRightClick.add(Properties.OPEN);
        propertiesAbleToBeChangedByRightClick.add(Properties.COMPARATOR_MODE);
        propertiesAbleToBeChangedByRightClick.add(Properties.DELAY);
        propertiesAbleToBeChangedByRightClick.add(Properties.POWERED); //lever


        litematicaplacerBind = KeyBindingHelper.registerKeyBinding(new KeyBinding("Toggle Litematica Placer", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_KP_5, "Litematica Placer"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (MC.player != null) {
                tickSleep++;
                if (tickSleep % 3 != 0) {
                    return;
                }

                if (litematicaplacerBind.wasPressed()) {
                    toggle();

                    while (litematicaplacerBind.wasPressed()) {}
                }

                if (!litematicaplacerEnabled) {
                    return;
                }

                int x = (int) floor(MC.player.getX());
                int y = (int) floor(MC.player.getY());
                int z = (int) floor(MC.player.getZ());


                checkRadius(x, y, z, 8, 8);

            }
        });
        LOGGER.info("Hello Fabric world!");
    }

    public void removeOneFromHand() {
        ItemStack stack = MC.player.getMainHandStack();
        if (stack != null && !MC.player.isCreative()) {
            if (stack.getCount() <= 1) {
                MC.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                stack.decrement(1);
            }
        }
    }

    public boolean lookThroughHotbar(Item item) {
        if (item == Items.AIR) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
                if (MC.player.getInventory().getStack(i).isOf(item)) {
                    MC.player.getInventory().selectedSlot = i;
                   return true;
                }
        }
        return false;
    }

    public void placeBlock(BlockPos blockPos, Direction direction) {

        if (MC.player.canPlaceOn(blockPos, direction, MC.player.getMainHandStack())) {
        Vec3d position = new Vec3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        MC.interactionManager.interactBlock(MC.player, Hand.MAIN_HAND, new BlockHitResult(position, direction, blockPos, false));
        }
    }

    public void checkRadius(int x, int y, int z, int radiusHorizontal, int radiusVertical) {

        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();

        x = (int) (x - (Math.ceil(radiusHorizontal / 2)) - 1);
        y = (int) (y - (Math.ceil(radiusVertical / 2)) - 1);
        z = (int) (z - (Math.ceil(radiusHorizontal / 2)) - 1);

        int savedX = x;
        int savedZ = z;

        for (int v = 0; v < radiusVertical; v++) {
            y++;
            x = savedX;
            for (int j = 0; j < radiusHorizontal; j++) {
                x++;
                z = savedZ;
                for (int k = 0; k < radiusHorizontal; k++) {
                    z++;

                    BlockPos blockPos = new BlockPos(x, y, z);

                    if (blockPos.getSquaredDistance(MC.player.getEyePos()) >= playerReach) {
                        continue;
                    }

                    BlockState stateLite = worldSchematic.getBlockState(blockPos);
                    BlockState stateReal = MC.world.getBlockState(blockPos);

                    Block blockReal = stateReal.getBlock();
                    Block blockLite = stateLite.getBlock();

                    if (stateReal == stateLite) {
                        continue;
                    }
                    if (blockReal instanceof BlockWithEntity || blockReal instanceof BedBlock) {
                        continue;
                    }
                    if (blockLite instanceof BedBlock) {
                        if (stateLite.get(Properties.BED_PART) == BedPart.HEAD) {
                            continue;
                        }
                    }

                    if (blockReal == blockLite ) {
                        handleChangeState(stateReal, stateLite, blockPos);
                    } else {
                        handlePlaceBlock(stateReal, stateLite, blockPos);
                    }
                }
            }
        }
    }


    public Direction getDirectionHorizontal(BlockState state, BlockPos blockPos){
        DirectionProperty property = fi.dy.masa.malilib.util.BlockUtils.getFirstDirectionProperty(state);
        Direction direction;

        if (property != null) {
            direction = state.get(property);

            if (tryDirection(state, Direction.UP, direction, blockPos)) {
                return Direction.UP;
            } else if (tryDirection(state, Direction.DOWN, direction, blockPos)) {
                return Direction.DOWN;
            } else if (tryDirection(state, Direction.NORTH, direction, blockPos)) {
                return Direction.NORTH;
            } else if (tryDirection(state, Direction.EAST, direction, blockPos)) {
                return Direction.EAST;
            } else if (tryDirection(state, Direction.SOUTH, direction, blockPos)) {
                return Direction.SOUTH;
            } else if (tryDirection(state, Direction.WEST, direction, blockPos)) {
                return Direction.WEST;
            }

        }
        return Direction.UP;
    }
    public Direction getDirectionVertical(BlockState state) {
        if (state.getProperties().contains(Properties.SLAB_TYPE)) {
            if (state.get(Properties.SLAB_TYPE) == SlabType.BOTTOM) {
                return Direction.UP;
            } else {
                return Direction.DOWN;
            }
        }
        if (state.getProperties().contains(Properties.BLOCK_HALF)) {
            if (state.get(Properties.BLOCK_HALF) == BlockHalf.BOTTOM) {
                return Direction.UP;
            } else {
                return Direction.DOWN;
            }
        }
        return null;
    }

    public void handleChangeState(BlockState stateReal, BlockState stateLite, BlockPos blockPos) {

        Collection<Property<?>> propertiesReal = stateReal.getProperties();
        Collection<Property<?>> propertiesLite = stateLite.getProperties();

        if (!(propertiesReal.containsAll(propertiesLite))) {
            return;
        }

        Collection<Property<?>> differentProperties = new ArrayList<>();

        Iterator<Property<?>> iteratorReal = propertiesReal.iterator();

        while (iteratorReal.hasNext()) {
            Property<?> property = iteratorReal.next();
            if (stateReal.get(property) != stateLite.get(property)) {
                differentProperties.add(property);
            }
        }
        Iterator<Property<?>> iteratorDiff = differentProperties.iterator();

        while (iteratorDiff.hasNext()) {
            Property<?> propertyDiff = iteratorDiff.next();
            if (propertiesAbleToBeChangedByRightClick.contains(propertyDiff)) {

                if (propertyDiff == Properties.POWERED && stateReal.getBlock() != Blocks.LEVER) {
                    continue;
                }

                ItemStack savedStack = MC.player.getMainHandStack();
                MC.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);

                MC.interactionManager.interactBlock(MC.player, Hand.OFF_HAND, new BlockHitResult(new Vec3d(blockPos.getX(), blockPos.getY(), blockPos.getZ()), Direction.UP, blockPos, false));

                MC.player.setStackInHand(Hand.MAIN_HAND, savedStack);
                return;
            }
        }

    }
    public void handlePlaceBlock(BlockState stateReal, BlockState stateLite, BlockPos blockPos) {

        Block blockReal = stateReal.getBlock();
        Block blockLite = stateLite.getBlock();

        int slot = MC.player.getInventory().selectedSlot;

        if (!(lookThroughHotbar(blockLite.asItem()))) {
            return;
        }

        Direction directionHorizontal = getDirectionHorizontal(stateLite, blockPos);
        Direction directionVertical = getDirectionVertical(stateLite);


        float yaw = directionHorizontal.asRotation();
        savedYaw = MC.player.getYaw();
        savedPitch = MC.player.getPitch();
        float pitch;


        if (directionVertical == Direction.UP || directionHorizontal == Direction.UP) {
            pitch = -90f;
        } else if (directionVertical == Direction.DOWN || directionHorizontal == Direction.DOWN) {
            pitch = 90f;
        } else {
            pitch = MC.player.getPitch();
        }

        MC.player.setYaw(yaw);
        MC.player.setPitch(pitch);
        MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, MC.player.isOnGround()));

        //blockPos.offset(direction);
        if (directionVertical != null) {
            placeBlock(blockPos, directionVertical);
        } else {
            placeBlock(blockPos, directionHorizontal);
        }

        if (MC.world.getBlockState(blockPos).getBlock() != blockReal) {
            removeOneFromHand();
        }

        MC.player.getInventory().selectedSlot = slot;
        MC.player.setYaw(savedYaw);
        MC.player.setPitch(savedPitch);
        MC.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(savedYaw, pitch, MC.player.isOnGround()));
    }

    public boolean tryDirection(BlockState state, Direction desiredDirection, Direction testDirection, BlockPos blockPos) {

        Vec3d position = new Vec3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());

        float yaw = testDirection.asRotation();
        float pitch;

        savedPitch = MC.player.getPitch();
        savedYaw = MC.player.getYaw();

        MC.player.setYaw(yaw);


        if (testDirection == Direction.UP) {
            pitch = -90f;
        } else if (testDirection == Direction.DOWN) {
            pitch = 90f;
        } else {
            pitch = MC.player.getPitch();
        }

        MC.player.setPitch(pitch);

        BlockState stateAfterPlacement = state.getBlock().getPlacementState(new ItemPlacementContext(MC.player, Hand.MAIN_HAND, MC.player.getMainHandStack(), new BlockHitResult(position, testDirection, blockPos, false)));
        if (stateAfterPlacement == null) {
            return false;
        }
        DirectionProperty propertyAfterPlacement = fi.dy.masa.malilib.util.BlockUtils.getFirstDirectionProperty(stateAfterPlacement);
        Direction directionAfterPlacement = stateAfterPlacement.get(propertyAfterPlacement);

        MC.player.setYaw(savedYaw);
        MC.player.setPitch(savedPitch);
        return desiredDirection == directionAfterPlacement;
    }
    public void toggle() {
        if (!litematicaplacerEnabled) {
            MC.player.sendMessage(Text.literal("Litematica Placer enabled"), true);
            litematicaplacerEnabled = true;
        } else {
            MC.player.sendMessage(Text.literal("Litematica Placer disabled"), true);
            litematicaplacerEnabled = false;
        }
    }
}