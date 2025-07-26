package troy.autofish;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringHelper;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import troy.autofish.monitor.FishMonitorMP;
import troy.autofish.monitor.FishMonitorMPMotion;
import troy.autofish.monitor.FishMonitorMPSound;
import troy.autofish.scheduler.ActionType;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Autofish {
    // Track last tick's atCoords state to avoid spamming sound
    private boolean wasAtCoords = true;

    private MinecraftClient client;
    private FabricModAutofish modAutofish;
    private FishMonitorMP fishMonitorMP;

    private boolean hookExists = false;
    private boolean alreadyAlertOP = false;
    private boolean alreadyPassOP = false;
    private long hookRemovedAt = 0L;

    public long timeMillis = 0L;

    // Smooth rotation fields
    private boolean rotatingToToss = false;
    private boolean rotatingBack = false;
    private boolean shouldToss = true;
    private float targetYaw = 0f;
    private float targetPitch = 0f;

    // --- Auto Toss Stack Behind state ---
    private boolean rotatingToTossBehind = false;
    private boolean rotatingBackBehind = false;
    private boolean shouldTossBehind = true;
    private float targetYawBehind = 0f;
    private float targetPitchBehind = 0f;
    private int behindSlotToToss = -1;

    // --- Blossom/Auto Flower state ---
    private long lastBlossomTime = 0L;
    private int blossomStep = 0;
    private boolean rotatingToBlossom = false;
    private float blossomTargetYaw = 0f;
    private float blossomTargetPitch = 90f; // look straight down
    private float blossomReturnYaw = 0f;
    private float blossomReturnPitch = 0f;
    private boolean returningFromBlossom = false;
    private int blossomHotbarSlot = -1;
    private int blossomTicksWaited = 0;

    // --- Yaw/Pitch to return to after tosses ---
    private float autofishYaw = 0f;
    private float autofishPitch = 0f;

    // --- Smooth rotation state for persistent recast ---
    private boolean rotatingToAutofish = false;

    private float normalizeAngle(float angle) {
        angle = angle % 360f;
        if (angle >= 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }

    private float approachAngle(float current, float target, float step) {
        float delta = normalizeAngle(target - current);
        if (Math.abs(delta) <= step) {
            return target;
        }
        return current + Math.signum(delta) * step;
    }

    public Autofish(FabricModAutofish modAutofish) {
        this.modAutofish = modAutofish;
        this.client = MinecraftClient.getInstance();
        setDetection();

        // Save autofishYaw/autofishPitch when autofish is enabled
        modAutofish.getConfig().setAutofishEnabled(false); // force to false on init

        //Initiate the repeating action for persistent mode casting
        modAutofish.getScheduler().scheduleRepeatingAction(10000, () -> {
            if (!modAutofish.getConfig().isPersistentMode()) return;
            if (!modAutofish.getConfig().isAutofishEnabled()) {
                // If autofish just got enabled, save angles
                if (client.player != null) {
                    autofishYaw = client.player.getYaw();
                    autofishPitch = client.player.getPitch();
                }
                return;
            }
            // If autofish is enabled and angles not set, set them
            if (autofishYaw == 0f && autofishPitch == 0f && client.player != null) {
                autofishYaw = client.player.getYaw();
                autofishPitch = client.player.getPitch();
            }
            boolean onlyAtCoords = modAutofish.getConfig().isOnlyAutofishAtSavedCoords();
            if (onlyAtCoords) {
                double savedX = modAutofish.getConfig().getSavedX();
                double savedY = modAutofish.getConfig().getSavedY();
                double savedZ = modAutofish.getConfig().getSavedZ();
                double px = client.player != null ? client.player.getX() : 0;
                double py = client.player != null ? client.player.getY() : 0;
                double pz = client.player != null ? client.player.getZ() : 0;
                double dist = Math.sqrt(Math.pow(px - savedX, 2) + Math.pow(py - savedY, 2) + Math.pow(pz - savedZ, 2));
                boolean atCoords = dist < 1.0;
                if (!atCoords) return;
            }
            if(modAutofish.getConfig().isNoBreak() && getHeldItem().getDamage() >= 63) return;
            if(!isHoldingFishingRod()) return;
            if(hookExists){
                if(isBobberInWater()) return;
                else {
                    // Before recasting, smoothly rotate to autofishYaw/autofishPitch
                    rotatingToAutofish = true;
                    return;
                }
            } else {
                if(modAutofish.getScheduler().isRecastQueued()) return;
                // Before recasting, smoothly rotate to autofishYaw/autofishPitch
                rotatingToAutofish = true;
                return;
            }
        });
    }

    // Helper to find hotbar slot by item name substring
    private int findHotbarSlot(PlayerInventory inv, String namePart) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.getName().getString().toLowerCase().contains(namePart.toLowerCase())) {
                return i;
            }
        }
        return -1;
    }

    // Helper to check if the block below is a flower (covers all vanilla flowers)
    private boolean isFlowerBlock(MinecraftClient client, BlockPos pos) {
        if (client.world == null) return false;
        String key = client.world.getBlockState(pos).getBlock().getTranslationKey().toLowerCase();
        return key.contains("flower") || key.contains("lily") || key.contains("dandelion") || key.contains("poppy") ||
               key.contains("tulip") || key.contains("orchid") || key.contains("allium") || key.contains("rose") ||
               key.contains("peony") || key.contains("cornflower") || key.contains("azure") || key.contains("bluet") ||
               key.contains("oxeye") || key.contains("sunflower") || key.contains("lilac") || key.contains("rose bush") || 
               key.contains("wither rose") || key.contains("daisy") || key.contains("torchflower");
    }

    public void tick(MinecraftClient client) {

        // --- Blossom/Auto Flower logic ---
        if (modAutofish.getConfig().isAutoFlowerEnabled() && client.player != null) {
            boolean onlyAtCoords = modAutofish.getConfig().isOnlyAutofishAtSavedCoords();
            double savedX = modAutofish.getConfig().getSavedX();
            double savedY = modAutofish.getConfig().getSavedY();
            double savedZ = modAutofish.getConfig().getSavedZ();
            double px = client.player.getX();
            double py = client.player.getY();
            double pz = client.player.getZ();
            double dist = Math.sqrt(Math.pow(px - savedX, 2) + Math.pow(py - savedY, 2) + Math.pow(pz - savedZ, 2));
            boolean atCoords = !onlyAtCoords || dist < 1.0;
            long now = System.currentTimeMillis();
            if (atCoords && (now - lastBlossomTime > 16000) && !rotatingToBlossom && !returningFromBlossom) {
                blossomStep = 0;
                rotatingToBlossom = true;
                blossomTargetYaw = client.player.getYaw();
                blossomReturnYaw = autofishYaw;
                blossomReturnPitch = autofishPitch;
                lastBlossomTime = now;
                blossomTicksWaited = 0;
            }
            // Smoothly rotate to look down for blossom
            if (rotatingToBlossom) {
                float currentYaw = client.player.getYaw();
                float currentPitch = client.player.getPitch();
                float newYaw = approachAngle(currentYaw, blossomTargetYaw, 10.0F);
                float newPitch = approachAngle(currentPitch, 90.0F, 10.0F);
                client.player.setYaw(newYaw);
                client.player.setPitch(newPitch);
                client.player.setHeadYaw(newYaw);
                client.player.setBodyYaw(newYaw);
                double x = client.player.getX();
                double y = client.player.getY();
                double z = client.player.getZ();
                boolean onGround = client.player.isOnGround();
                client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround));
                client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(newYaw, newPitch, onGround));
                if (Math.abs(normalizeAngle(newYaw - blossomTargetYaw)) < 1.0 && Math.abs(newPitch - 90.0F) < 1.0) {
                    rotatingToBlossom = false;
                    blossomStep = 1;
                    blossomTicksWaited = 0;
                }
                return;
            }
            // Blossom sequence steps
            if (blossomStep > 0) {
                // Wait more ticks between actions for reliability (e.g., 12 ticks ≈ 0.6s)
                blossomTicksWaited++;
                if (blossomTicksWaited < 12) return;
                blossomTicksWaited = 0;
                PlayerInventory inv = client.player.getInventory();
                int slot = -1;
                BlockPos below = client.player.getBlockPos().down();
                switch (blossomStep) {
                    case 1: // Fawnbloom: swap & use (right click top face of block below)
                        slot = findHotbarSlot(inv, "fawnbloom");
                        if (slot != -1) {
                            inv.selectedSlot = slot;
                            // Look straight down at the flower
                            client.player.setPitch(90.0F);
                            client.player.setYaw(client.player.getYaw());
                            // Simulate right click on the top face of the block below, at the center
                            net.minecraft.util.math.Vec3d hitPos = new net.minecraft.util.math.Vec3d(
                                below.getX() + 0.5,
                                below.getY() + 1.0,
                                below.getZ() + 0.5
                            );
                            client.interactionManager.interactBlock(
                                client.player,
                                Hand.MAIN_HAND,
                                new net.minecraft.util.hit.BlockHitResult(
                                    hitPos,
                                    net.minecraft.util.math.Direction.UP,
                                    below,
                                    false
                                )
                            );
                        }
                        blossomStep++;
                        break;
                    case 2: // Silas Shears: swap only
                        slot = findHotbarSlot(inv, "silas shears");
                        if (slot != -1) {
                            inv.selectedSlot = slot;
                        }
                        blossomStep++;
                        break;
                    case 3: // Silas Shears: swing & break (check both below and at player pos)
                        slot = findHotbarSlot(inv, "silas shears");
                        BlockPos at = client.player.getBlockPos();
                        if (slot != -1) {
                            boolean broke = false;
                            if (isFlowerBlock(client, below)) {
                                client.player.swingHand(Hand.MAIN_HAND);
                                client.interactionManager.attackBlock(below, net.minecraft.util.math.Direction.UP);
                                client.interactionManager.attackBlock(below, client.player.getHorizontalFacing());
                                broke = true;
                            }
                            if (isFlowerBlock(client, at)) {
                                client.player.swingHand(Hand.MAIN_HAND);
                                client.interactionManager.attackBlock(at, net.minecraft.util.math.Direction.UP);
                                client.interactionManager.attackBlock(at, client.player.getHorizontalFacing());
                                broke = true;
                            }
                        }
                        blossomStep++;
                        break;
                    case 4: // Freyja's Blessing: swap & use (plant sunflower by item type)
                        // Find sunflower in hotbar by item type, not name
                        slot = -1;
                        for (int i = 0; i < 9; i++) {
                            ItemStack stack = inv.getStack(i);
                            if (!stack.isEmpty() && stack.getItem() == net.minecraft.item.Items.SUNFLOWER) {
                                slot = i;
                                break;
                            }
                        }
                        if (slot != -1) {
                            inv.selectedSlot = slot;
                            // Look straight down at the flower
                            client.player.setPitch(90.0F);
                            client.player.setYaw(client.player.getYaw());
                            // Simulate right click on the top face of the block below, at the center
                            net.minecraft.util.math.Vec3d hitPos = new net.minecraft.util.math.Vec3d(
                                below.getX() + 0.5,
                                below.getY() + 1.0,
                                below.getZ() + 0.5
                            );
                            client.interactionManager.interactBlock(
                                client.player,
                                Hand.MAIN_HAND,
                                new net.minecraft.util.hit.BlockHitResult(
                                    hitPos,
                                    net.minecraft.util.math.Direction.UP,
                                    below,
                                    false
                                )
                            );
                        }
                        blossomStep++;
                        break;
                    case 5: // Flower Wand: swap only
                        slot = findHotbarSlot(inv, "flower wand");
                        if (slot != -1) {
                            inv.selectedSlot = slot;
                        }
                        blossomStep++;
                        break;
                    case 6: // Flower Wand: swing & break (check both below and at player pos)
                        slot = findHotbarSlot(inv, "flower wand");
                        at = client.player.getBlockPos();
                        if (slot != -1) {
                            boolean broke = false;
                            if (isFlowerBlock(client, below)) {
                                client.player.swingHand(Hand.MAIN_HAND);
                                client.interactionManager.attackBlock(below, net.minecraft.util.math.Direction.UP);
                                client.interactionManager.attackBlock(below, client.player.getHorizontalFacing());
                                broke = true;
                            }
                            if (isFlowerBlock(client, at)) {
                                client.player.swingHand(Hand.MAIN_HAND);
                                client.interactionManager.attackBlock(at, net.minecraft.util.math.Direction.UP);
                                client.interactionManager.attackBlock(at, client.player.getHorizontalFacing());
                                broke = true;
                            }
                        }
                        blossomStep++;
                        break;
                    case 7: // Return to autofish view
                        returningFromBlossom = true;
                        blossomStep = 0;
                        break;
                }
                return;
            }

            // Smoothly rotate back to autofishYaw/autofishPitch after blossom
            if (returningFromBlossom) {
                float currentYaw = client.player.getYaw();
                float currentPitch = client.player.getPitch();
                float newYaw = approachAngle(currentYaw, autofishYaw, 10.0F);
                float newPitch = approachAngle(currentPitch, autofishPitch, 10.0F);
                client.player.setYaw(newYaw);
                client.player.setPitch(newPitch);
                client.player.setHeadYaw(newYaw);
                client.player.setBodyYaw(newYaw);
                double x = client.player.getX();
                double y = client.player.getY();
                double z = client.player.getZ();
                boolean onGround = client.player.isOnGround();
                client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround));
                client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(newYaw, newPitch, onGround));
                if (Math.abs(normalizeAngle(newYaw - autofishYaw)) < 1.0 && Math.abs(newPitch - autofishPitch) < 1.0) {
                    returningFromBlossom = false;
                }
                return;
            }
        }

        // --- Smoothly rotate to autofishYaw/autofishPitch before recast if needed ---
        if (rotatingToAutofish && client.player != null) {
            float currentYaw = client.player.getYaw();
            float currentPitch = client.player.getPitch();
            float newYaw = approachAngle(currentYaw, autofishYaw, 10.0F);
            float newPitch = approachAngle(currentPitch, autofishPitch, 10.0F);
            client.player.setYaw(newYaw);
            client.player.setPitch(newPitch);
            client.player.setHeadYaw(newYaw);
            client.player.setBodyYaw(newYaw);
            double x = client.player.getX();
            double y = client.player.getY();
            double z = client.player.getZ();
            boolean onGround = client.player.isOnGround();
            client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround));
            client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(newYaw, newPitch, onGround));
            if (Math.abs(normalizeAngle(newYaw - autofishYaw)) < 1.0 && Math.abs(newPitch - autofishPitch) < 1.0) {
                rotatingToAutofish = false;
                useRod();
            }
            // Don't run any other tick logic while rotating
            return;
        }

        // Auto Toss Items logic (after autofish logic)
        if (modAutofish.getConfig().isAutoTossEnabled() && client.player != null && modAutofish.getConfig().isAutofishEnabled()) {
            PlayerInventory inv = client.player.getInventory();
            // Consider inventory full if all main inventory slots (9+) are non-empty
            boolean full = true;
            for (int i = 9; i < inv.main.size(); i++) {
                if (inv.main.get(i).isEmpty()) {
                    full = false;
                    break;
                }
            }

            // Check if there are any matching items to toss
            boolean hasMatching = false;
            String[] tossList = modAutofish.getConfig().getAutoTossItems().toLowerCase().split(",");
            if (full) {
                outer: for (int i = 9; i < inv.main.size(); i++) {
                    ItemStack stack = inv.main.get(i);
                    if (!stack.isEmpty()) {
                        String name = stack.getName().getString().toLowerCase();
                        for (String s : tossList) {
                            if (!s.trim().isEmpty() && name.contains(s.trim())) {
                                hasMatching = true;
                                break outer;
                            }
                        }
                    }
                }
            }

            float currentYaw = client.player.getYaw();
            float currentPitch = client.player.getPitch();

            // Only trigger toss logic once per full event AND if there are matching items
            if (full && hasMatching) {
                // Set up target rotation if not already rotating
                if (!rotatingToToss && !rotatingBack && shouldToss) {
                    targetYaw = normalizeAngle(currentYaw - 90.0F); // 90 deg left
                    targetPitch = 0.0F;
                    rotatingToToss = true;
                    shouldToss = false;
                }
            } else if (!rotatingBack && !rotatingToToss) {
                // Reset toss state if inventory is not full or no matching items and not rotating back
                shouldToss = true;
            }

            // Always allow rotation to finish if in progress
            if (rotatingToToss) {
                // Smoothly rotate to toss direction
                float newYaw = approachAngle(currentYaw, targetYaw, 10.0F);
                float newPitch = approachAngle(currentPitch, targetPitch, 10.0F);

                client.player.setYaw(newYaw);
                client.player.setPitch(newPitch);
                client.player.setHeadYaw(newYaw);
                client.player.setBodyYaw(newYaw);

                double x = client.player.getX();
                double y = client.player.getY();
                double z = client.player.getZ();
                boolean onGround = client.player.isOnGround();

                client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround));
                client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(newYaw, newPitch, onGround));

                if (Math.abs(normalizeAngle(newYaw - targetYaw)) < 1.0 && Math.abs(newPitch - targetPitch) < 1.0) {
                    // Reached toss rotation — toss items now
                    int syncId = client.player.currentScreenHandler.syncId;
                    for (int i = 9; i < inv.main.size(); i++) {
                        ItemStack stack = inv.main.get(i);
                        if (!stack.isEmpty()) {
                            String name = stack.getName().getString().toLowerCase();
                            for (String s : tossList) {
                                if (!s.trim().isEmpty() && name.contains(s.trim())) {
                                    client.interactionManager.clickSlot(syncId, i, 999, net.minecraft.screen.slot.SlotActionType.THROW, client.player);
                                    break;
                                }
                            }
                        }
                    }
                    rotatingToToss = false;
                    rotatingBack = true;
                }
            }
            if (rotatingBack) {
                // Smoothly rotate back to autofishYaw/autofishPitch
                float newYaw = approachAngle(currentYaw, autofishYaw, 10.0F);
                float newPitch = approachAngle(currentPitch, autofishPitch, 10.0F);

                client.player.setYaw(newYaw);
                client.player.setPitch(newPitch);
                client.player.setHeadYaw(newYaw);
                client.player.setBodyYaw(newYaw);

                double x = client.player.getX();
                double y = client.player.getY();
                double z = client.player.getZ();
                boolean onGround = client.player.isOnGround();

                client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround));
                client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(newYaw, newPitch, onGround));

                if (Math.abs(normalizeAngle(newYaw - autofishYaw)) < 1.0 && Math.abs(newPitch - autofishPitch) < 1.0) {
                    // Finished rotating back
                    rotatingBack = false;
                    shouldToss = true; // allow next toss event
                }
            }
        }

        // Auto Toss Stack Behind logic (rotate 180 deg and toss full stacks from a separate list)
        // --- Auto Toss Stack Behind logic (rotate 180 deg and toss full stacks from a separate list) ---
        if (modAutofish.getConfig().isAutoTossStackBehindEnabled() && client.player != null && modAutofish.getConfig().isAutofishEnabled()) {
            PlayerInventory inv = client.player.getInventory();
            String[] behindList = modAutofish.getConfig().getAutoTossStackBehindItems().toLowerCase().split(",");
            int stackSize = 64; // Default MC stack size, can be improved if needed
            boolean foundFullStack = false;
            int foundSlot = -1;
            String foundName = null;
            // Find a full stack of any item in the behind list
            outer: for (int i = 9; i < inv.main.size(); i++) {
                ItemStack stack = inv.main.get(i);
                if (!stack.isEmpty() && stack.getCount() == stack.getMaxCount()) {
                    String name = stack.getName().getString().toLowerCase();
                    for (String s : behindList) {
                        if (!s.trim().isEmpty() && name.contains(s.trim())) {
                            foundFullStack = true;
                            foundSlot = i;
                            foundName = name;
                            break outer;
                        }
                    }
                }
            }

            // Initiate toss if found
            if (foundFullStack) {
                if (!rotatingToTossBehind && !rotatingBackBehind && shouldTossBehind) {
                    targetYawBehind = normalizeAngle(client.player.getYaw() + 180.0F); // 180 deg behind
                    targetPitchBehind = 0.0F;
                    rotatingToTossBehind = true;
                    shouldTossBehind = false;
                    behindSlotToToss = foundSlot;
                }
            } else if (!rotatingBackBehind && !rotatingToTossBehind) {
                // If not rotating, allow next toss event
                shouldTossBehind = true;
            }
        }

        // --- Always finish behind-toss rotation if in progress, even if stack is gone ---
        if (rotatingToTossBehind) {
            float currentYawB = client.player.getYaw();
            float currentPitchB = client.player.getPitch();
            float newYawB = approachAngle(currentYawB, targetYawBehind, 20.0F);
            float newPitchB = approachAngle(currentPitchB, targetPitchBehind, 20.0F);
            client.player.setYaw(newYawB);
            client.player.setPitch(newPitchB);
            client.player.setHeadYaw(newYawB);
            client.player.setBodyYaw(newYawB);
            double x = client.player.getX();
            double y = client.player.getY();
            double z = client.player.getZ();
            boolean onGround = client.player.isOnGround();
            client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround));
            client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(newYawB, newPitchB, onGround));
            if (Math.abs(normalizeAngle(newYawB - targetYawBehind)) < 1.0 && Math.abs(newPitchB - targetPitchBehind) < 1.0) {
                // Toss the stack if still valid
                if (behindSlotToToss >= 0 && client.player.getInventory().main.size() > behindSlotToToss) {
                    ItemStack stack = client.player.getInventory().main.get(behindSlotToToss);
                    if (!stack.isEmpty() && stack.getCount() == stack.getMaxCount()) {
                        int syncId = client.player.currentScreenHandler.syncId;
                        client.interactionManager.clickSlot(syncId, behindSlotToToss, 999, net.minecraft.screen.slot.SlotActionType.THROW, client.player);
                    }
                }
                rotatingToTossBehind = false;
                rotatingBackBehind = true;
            }
        }
        if (rotatingBackBehind) {
            float currentYawB = client.player.getYaw();
            float currentPitchB = client.player.getPitch();
            float newYawB = approachAngle(currentYawB, autofishYaw, 20.0F);
            float newPitchB = approachAngle(currentPitchB, autofishPitch, 20.0F);
            client.player.setYaw(newYawB);
            client.player.setPitch(newPitchB);
            client.player.setHeadYaw(newYawB);
            client.player.setBodyYaw(newYawB);
            double x = client.player.getX();
            double y = client.player.getY();
            double z = client.player.getZ();
            boolean onGround = client.player.isOnGround();
            client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround));
            client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(newYawB, newPitchB, onGround));
            if (Math.abs(normalizeAngle(newYawB - autofishYaw)) < 1.0 && Math.abs(newPitchB - autofishPitch) < 1.0) {
                rotatingBackBehind = false;
                shouldTossBehind = true;
                behindSlotToToss = -1;
            }
        }

        if (client.world != null && client.player != null && modAutofish.getConfig().isAutofishEnabled()) {
            boolean onlyAtCoords = modAutofish.getConfig().isOnlyAutofishAtSavedCoords();
            double savedX = modAutofish.getConfig().getSavedX();
            double savedY = modAutofish.getConfig().getSavedY();
            double savedZ = modAutofish.getConfig().getSavedZ();
            double px = client.player.getX();
            double py = client.player.getY();
            double pz = client.player.getZ();
            double dist = Math.sqrt(Math.pow(px - savedX, 2) + Math.pow(py - savedY, 2) + Math.pow(pz - savedZ, 2));
            boolean atCoords = dist < 1.0; // within 1 block

            // Play sound and stop autofishing if player leaves saved coords
            if (onlyAtCoords) {
                if (!atCoords && wasAtCoords) {
                    client.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 3.0F, 1.0F);
                    client.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_WITHER_SPAWN, 3.0F, 1.0F);
                    client.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0F, 1.0F);
                    client.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_DEATH, 3.0F, 1.0F);
                    client.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_GHAST_SCREAM, 3.0F, 1.0F);
                }
                wasAtCoords = atCoords;
                if (!atCoords) {
                    removeHook();
                    // Disable autofishing when player leaves saved coords
                    modAutofish.getConfig().setAutofishEnabled(false);
                    modAutofish.getConfigManager().writeConfig(true);
                    return;
                }
            } else {
                wasAtCoords = true; // reset if not using onlyAtCoords
            }

            timeMillis = Util.getMeasuringTimeMs(); //update current working time for this tick

            if (isHoldingFishingRod()) {
                if (client.player.fishHook != null) {
                    hookExists = true;
                    //MP catch listener
                    if (shouldUseMPDetection()) {//multiplayer only, send tick event to monitor
                        fishMonitorMP.hookTick(this, client, client.player.fishHook);
                    }
                } else {
                    removeHook();
                }
            } else { //not holding fishing rod
                removeHook();
            }
        } else {
            wasAtCoords = true;
        }
    }

    /**
     * Callback from mixin for the catchingFish method of the EntityFishHook
     * for singleplayer detection only
     */
    public void tickFishingLogic(Entity owner, int ticksCatchable) {
        //This callback will come from the Server thread. Use client.execute() to run this action in the Render thread
        client.execute(() -> {
            if (modAutofish.getConfig().isAutofishEnabled() && !shouldUseMPDetection()) {
                //null checks for sanity
                if (client.player != null && client.player.fishHook != null) {
                    //hook is catchable and player is correct
                    if (ticksCatchable > 0 && owner.getUuid().compareTo(client.player.getUuid()) == 0) {
                        catchFish();
                    }
                }
            }
        });
    }

    /**
     * Callback from mixin when sound and motion packets are received
     * For multiplayer detection only
     */
    public void handlePacket(Packet<?> packet) {
        if (modAutofish.getConfig().isAutofishEnabled()) {
            if (shouldUseMPDetection()) {
                fishMonitorMP.handlePacket(this, packet, client);
            }
        }
    }

    /**
     * Callback from mixin when chat packets are received
     * For multiplayer detection only
     */
    public void handleChat(GameMessageS2CPacket packet) {
        if (modAutofish.getConfig().isAutofishEnabled()) {
            if (!client.isInSingleplayer()) {
                if (isHoldingFishingRod()) {
                    //check that either the hook exists, or it was just removed
                    //this prevents false casts if we are holding a rod but not fishing
                    if (hookExists || (timeMillis - hookRemovedAt < 2000)) {
                        //make sure there is actually something there in the regex field
                        if (org.apache.commons.lang3.StringUtils.deleteWhitespace(modAutofish.getConfig().getClearLagRegex()).isEmpty())
                            return;
                        //check if it matches
                        Matcher matcher = Pattern.compile(modAutofish.getConfig().getClearLagRegex(), Pattern.CASE_INSENSITIVE).matcher(StringHelper.stripTextFormat(packet.content().getString()));
                        if (matcher.find()) {
                            queueRecast();
                        }
                    }
                }
            }
        }
    }

    public void catchFish() {
            if(!modAutofish.getScheduler().isRecastQueued()) { //prevents double reels
                if (client.player != null) {
                    detectOpenWater(client.player.fishHook);
                }
                //queue actions
                queueRodSwitch();
                queueRecast();
                modAutofish.getScheduler().scheduleAction(ActionType.REEL_IN, modAutofish.getConfig().getReelInDelay(), this::useRod);
            }
    }

    public void queueRecast() {
        modAutofish.getScheduler().scheduleAction(ActionType.RECAST, getRandomDelay()
                + modAutofish.getConfig().getReelInDelay(), () -> {
            //State checks to ensure we can still fish once this runs
            if(hookExists) return;
            if(!isHoldingFishingRod()) return;
            if(modAutofish.getConfig().isNoBreak() && getHeldItem().getDamage() >= 63) return;

            useRod();
        });
    }

    private void queueRodSwitch(){
        modAutofish.getScheduler().scheduleAction(ActionType.ROD_SWITCH, (long) (getRandomDelay() * 0.83)
                + modAutofish.getConfig().getReelInDelay(), () -> {
            if(!modAutofish.getConfig().isMultiRod()) return;

            switchToFirstRod(client.player);
        });
    }

    private void detectOpenWater(FishingBobberEntity bobber){
        /*
         * To catch items in the treasure category, the bobber must be in open water,
         * defined as the 5×4×5 vicinity around the bobber resting on the water surface
         * (2 blocks away horizontally, 2 blocks above the water surface, and 2 blocks deep).
         * Each horizontal layer in this area must consist only of air and lily pads or water source blocks,
         * waterlogged blocks without collision (such as signs, kelp, or coral fans), and bubble columns.
         * (from Minecraft wiki)
         * */
        if(!modAutofish.getConfig().isOpenWaterDetectEnabled()) return;

        int x = bobber.getBlockX();
        int y = bobber.getBlockY();
        int z = bobber.getBlockZ();
        boolean flag = true;
        for(int yi = -2; yi <= 2; yi++){
            if(!(BlockPos.stream(x - 2, y + yi, z - 2, x + 2, y + yi, z + 2).allMatch((blockPos ->
                    // every block is water
                        bobber.getEntityWorld().getBlockState(blockPos).getBlock() == Blocks.WATER
                    )) || BlockPos.stream(x - 2, y + yi, z - 2, x + 2, y + yi, z + 2).allMatch((blockPos ->
                    // or every block is air or lily pad
                        bobber.getEntityWorld().getBlockState(blockPos).getBlock() == Blocks.AIR
                        || bobber.getEntityWorld().getBlockState(blockPos).getBlock() == Blocks.LILY_PAD
            )))){
                // didn't pass the check
                if(!alreadyAlertOP){
                    Objects.requireNonNull(bobber.getPlayerOwner()).sendMessage(Text.translatable("info.autofish.open_water_detection.fail"),true);
                    alreadyAlertOP = true;
                    alreadyPassOP = false;
                }
                flag = false;
            }
        }
        if(flag && !alreadyPassOP) {
            Objects.requireNonNull(bobber.getPlayerOwner()).sendMessage(Text.translatable("info.autofish.open_water_detection.success"),true);
            alreadyPassOP = true;
            alreadyAlertOP = false;
        }


    }

    /**
     * Call this when the hook disappears
     */
    private void removeHook() {
        if (hookExists) {
            hookExists = false;
            hookRemovedAt = timeMillis;
            fishMonitorMP.handleHookRemoved();
        }
    }

    public void switchToFirstRod(ClientPlayerEntity player) {
        if(player != null) {
            PlayerInventory inventory = player.getInventory();
            for (int i = 0; i < inventory.main.size(); i++) {
                ItemStack slot = inventory.main.get(i);
                if (slot.getItem() == Items.FISHING_ROD) {
                    if (i < 9) { //hotbar only
                        if (modAutofish.getConfig().isNoBreak()) {
                            if (slot.getDamage() < 63) {
                                inventory.selectedSlot = i;
                                return;
                            }
                        } else {
                            inventory.selectedSlot = i;
                            return;
                        }
                    }
                }
            }
        }
    }

    public boolean isBobberInWater(){
        if(client.player != null && client.world != null && client.player.fishHook != null) {
            return client.world.getBlockState(client.player.fishHook.getBlockPos()).getBlock() == Blocks.WATER;
        } else{
            return false;
        }
    }

    public void useRod() {
        if(client.player != null && client.world != null) {
            Hand hand = getCorrectHand();
            ActionResult actionResult = null;
            if (client.interactionManager != null) {
                actionResult = client.interactionManager.interactItem(client.player, hand);
            }
            if (actionResult != null && actionResult.isAccepted()) {
                if (actionResult.shouldSwingHand()) {
                    client.player.swingHand(hand);
                }
                client.gameRenderer.firstPersonRenderer.resetEquipProgress(hand);
            }
        }
    }

    public boolean isHoldingFishingRod() {
        return isItemFishingRod(getHeldItem().getItem());
    }

    private Hand getCorrectHand() {
        if (!modAutofish.getConfig().isMultiRod()) {
            if (client.player != null && isItemFishingRod(client.player.getOffHandStack().getItem()))
                return Hand.OFF_HAND;
        }
        return Hand.MAIN_HAND;
    }

    private ItemStack getHeldItem() {
        if (client.player == null) return ItemStack.EMPTY;

        if (!modAutofish.getConfig().isMultiRod()) {
            if (isItemFishingRod(client.player.getOffHandStack().getItem()))
                return client.player.getOffHandStack();
        }
        return client.player.getMainHandStack();
    }

    private boolean isItemFishingRod(Item item) {
        return item == Items.FISHING_ROD || item instanceof FishingRodItem;
    }

    public void setDetection() {
        if (modAutofish.getConfig().isUseSoundDetection()) {
            fishMonitorMP = new FishMonitorMPSound();
        } else {
            fishMonitorMP = new FishMonitorMPMotion();
        }
    }

    private boolean shouldUseMPDetection(){
        if(modAutofish.getConfig().isForceMPDetection()) return true;
        return !client.isInSingleplayer();
    }

    private long getRandomDelay(){
        return Math.random() >=0.5 ?
                (long) (modAutofish.getConfig().getRecastDelay() * (1 - (Math.random() * modAutofish.getConfig().getRandomDelay() * 0.01))) :
                (long) (modAutofish.getConfig().getRecastDelay() * (1 + (Math.random() * modAutofish.getConfig().getRandomDelay() * 0.01)));

    }
}
