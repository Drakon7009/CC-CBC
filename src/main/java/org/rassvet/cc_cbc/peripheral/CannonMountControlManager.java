package org.rassvet.cc_cbc.peripheral;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountInterfaceBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CannonMountControlManager {
    private static final Map<MountKey, ControlState> STATES = new ConcurrentHashMap<>();
    private static final Map<MountKey, CannonMountPeripheral> PERIPHERALS = new ConcurrentHashMap<>();

    private CannonMountControlManager() {
    }

    public static void setComputerControl(CannonMountBlockEntity mount, boolean enabled) {
        ControlState state = getState(mount);

        // Capture in the same frame used by setYaw/setPitch to avoid frame-mismatch jumps.
        double capturedYaw = getMountedYaw(mount);
        double capturedPitch = getMountedPitch(mount);

        state.computerControl = enabled;
        if (enabled) {
            state.currentYaw = capturedYaw;
            state.currentPitch = capturedPitch;
            state.targetYaw = capturedYaw;
            state.targetPitch = capturedPitch;
            state.displayTargetYaw = capturedYaw;
        }

        setDriveLock(mount, enabled);

        // Persist lock/angle state immediately so relog does not restore stale state.
        mount.setChanged();
        mount.sendData();
    }

    public static CannonMountPeripheral getPeripheral(CannonMountBlockEntity mount) {
        return PERIPHERALS.computeIfAbsent(new MountKey(mount.getLevel().dimension(), mount.getBlockPos()), ignored -> new CannonMountPeripheral(mount));
    }

    public static boolean isComputerControl(CannonMountBlockEntity mount) {
        return getState(mount).computerControl;
    }

    public static void setTargetAngles(CannonMountBlockEntity mount, double yaw, double pitch) {
        setTargetAngles(mount, yaw, pitch, normalizeYaw(yaw));
    }

    public static void setTargetAngles(CannonMountBlockEntity mount, double worldYaw, double pitch, double displayYaw) {
        ControlState state = getState(mount);
        state.targetYaw = normalizeYaw(worldYaw);
        state.targetPitch = pitch;
        state.displayTargetYaw = normalizeYaw(displayYaw);
        if (!state.computerControl) {
            state.currentYaw = getMountedYaw(mount);
            state.currentPitch = getMountedPitch(mount);
        }
    }

    public static double getTargetYaw(CannonMountBlockEntity mount) {
        return getState(mount).targetYaw;
    }

    public static double getTargetPitch(CannonMountBlockEntity mount) {
        return getState(mount).targetPitch;
    }

    public static double getDisplayTargetYaw(CannonMountBlockEntity mount) {
        return getState(mount).displayTargetYaw;
    }

    public static void tickLevel(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        tickPeripherals(level, dimension);
        Iterator<Map.Entry<MountKey, ControlState>> iterator = STATES.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<MountKey, ControlState> entry = iterator.next();
            MountKey key = entry.getKey();
            if (!key.dimension.equals(dimension)) {
                continue;
            }

            BlockEntity be = level.getBlockEntity(key.pos);
            if (!(be instanceof CannonMountBlockEntity mount) || mount.isRemoved()) {
                STATES.remove(key);
                continue;
            }

            tickMount(mount, entry.getValue());
        }
    }

    private static void tickPeripherals(ServerLevel level, ResourceKey<Level> dimension) {
        Iterator<Map.Entry<MountKey, CannonMountPeripheral>> iterator = PERIPHERALS.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<MountKey, CannonMountPeripheral> entry = iterator.next();
            MountKey key = entry.getKey();
            if (!key.dimension.equals(dimension)) {
                continue;
            }

            BlockEntity be = level.getBlockEntity(key.pos);
            if (!(be instanceof CannonMountBlockEntity mount) || mount.isRemoved()) {
                iterator.remove();
                STATES.remove(key);
                continue;
            }

            entry.getValue().tick(level.getGameTime());
        }
    }

    private static void tickMount(CannonMountBlockEntity mount, ControlState state) {
        if (!state.computerControl) {
            return;
        }

        setDriveLock(mount, true);

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (!mount.isRunning() || contraption == null) {
            return;
        }

        double minPitch = -contraption.maximumDepression();
        double maxPitch = contraption.maximumElevation();
        state.targetPitch = Mth.clamp(state.targetPitch, minPitch, maxPitch);

        double yawStep = Math.abs(mount.getAngularSpeed(-mount.getYawSpeed(), 0));
        double pitchStep = Math.abs(mount.getAngularSpeed(mount.getPitchSpeed(), 0));

        state.currentYaw = moveTowardsAngle(state.currentYaw, state.targetYaw, yawStep);
        state.currentPitch = moveTowards(state.currentPitch, state.targetPitch, pitchStep);
        state.currentPitch = Mth.clamp(state.currentPitch, minPitch, maxPitch);

        mount.setYaw((float) state.currentYaw);
        mount.setPitch((float) state.currentPitch);
        mount.sendData();
    }

    private static ControlState getState(CannonMountBlockEntity mount) {
        MountKey key = new MountKey(mount.getLevel().dimension(), mount.getBlockPos());
        ControlState state = STATES.computeIfAbsent(key, ignored -> createInitialState(mount));

        boolean locked = isDriveLocked(mount);
        if (locked != state.computerControl) {
            state.computerControl = locked;
            if (locked) {
                state.currentYaw = getMountedYaw(mount);
                state.currentPitch = getMountedPitch(mount);
                state.targetYaw = state.currentYaw;
                state.targetPitch = state.currentPitch;
                state.displayTargetYaw = state.currentYaw;
            }
        }

        return state;
    }

    public static void scheduleDisconnectCleanup(CannonMountBlockEntity mount, long executeAtGameTime) {
        getState(mount).disconnectCleanupAtGameTime = executeAtGameTime;
    }

    public static void cancelDisconnectCleanup(CannonMountBlockEntity mount) {
        getState(mount).disconnectCleanupAtGameTime = -1;
    }

    public static boolean shouldRunDisconnectCleanup(CannonMountBlockEntity mount, long gameTime) {
        return getState(mount).disconnectCleanupAtGameTime >= 0 && gameTime >= getState(mount).disconnectCleanupAtGameTime;
    }

    public static void finishDisconnectCleanup(CannonMountBlockEntity mount) {
        getState(mount).disconnectCleanupAtGameTime = -1;
    }

    private static ControlState createInitialState(CannonMountBlockEntity mount) {
        ControlState state = new ControlState();
        state.currentYaw = getMountedYaw(mount);
        state.currentPitch = getMountedPitch(mount);
        state.targetYaw = state.currentYaw;
        state.targetPitch = state.currentPitch;
        state.displayTargetYaw = state.currentYaw;

        // Interface angle limits are serialized by CBC, so this restores CC mode after relog/chunk reload.
        state.computerControl = isDriveLocked(mount);
        return state;
    }

    public static void clearLevel(ResourceKey<Level> dimension) {
        STATES.keySet().removeIf(key -> key.dimension.equals(dimension));
        PERIPHERALS.keySet().removeIf(key -> key.dimension.equals(dimension));
    }

    public static void clearAll() {
        STATES.clear();
        PERIPHERALS.clear();
    }

    private static boolean isDriveLocked(CannonMountBlockEntity mount) {
        double yawLimit = mount.getYawInterface() instanceof CannonMountInterfaceBlockEntity yawInterface
            ? yawInterface.getSequencedAngleLimit()
            : -1;
        double pitchLimit = mount.getPitchInterface() instanceof CannonMountInterfaceBlockEntity pitchInterface
            ? pitchInterface.getSequencedAngleLimit()
            : -1;

        return yawLimit == 0.0 && pitchLimit == 0.0;
    }

    private static double getMountedYaw(CannonMountBlockEntity mount) {
        PitchOrientedContraptionEntity contraption = mount.getContraption();
        return normalizeYaw(contraption != null ? contraption.yaw : mount.getYawOffset(0));
    }

    private static double getMountedPitch(CannonMountBlockEntity mount) {
        // Use mount display frame for pitch; this is the same frame used by CC-facing controls.
        return mount.getDisplayPitch();
    }

    private static void setDriveLock(CannonMountBlockEntity mount, boolean locked) {
        double limit = locked ? 0.0 : -1.0;
        if (mount.getYawInterface() instanceof CannonMountInterfaceBlockEntity yawInterface) {
            yawInterface.setSequencedAngleLimit(limit);
        }
        if (mount.getPitchInterface() instanceof CannonMountInterfaceBlockEntity pitchInterface) {
            pitchInterface.setSequencedAngleLimit(limit);
        }
    }

    private static double moveTowards(double current, double target, double step) {
        if (step <= 0) {
            return current;
        }
        double delta = target - current;
        if (Math.abs(delta) <= step) {
            return target;
        }
        return current + Math.copySign(step, delta);
    }

    private static double moveTowardsAngle(double current, double target, double step) {
        if (step <= 0) {
            return normalizeYaw(current);
        }
        double diff = Mth.wrapDegrees((float) (target - current));
        if (Math.abs(diff) <= step) {
            return normalizeYaw(target);
        }
        return normalizeYaw(current + Math.copySign(step, diff));
    }

    private static double normalizeYaw(double yaw) {
        return Mth.wrapDegrees((float) yaw);
    }

    private record MountKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final class ControlState {
        private boolean computerControl;
        private double targetYaw;
        private double targetPitch;
        private double currentYaw;
        private double currentPitch;
        private double displayTargetYaw;
        private long disconnectCleanupAtGameTime = -1;
    }
}
