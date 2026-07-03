package org.rassvet.cc_cbc.peripheral;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.rassvet.cc_cbc.api.CannonMountIntegration;
import org.rassvet.cc_cbc.api.ControlledCannonMount;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CannonMountControlManager {
    private static final Map<MountKey, ControlState> STATES = new ConcurrentHashMap<>();
    private static final Map<MountKey, CannonMountPeripheral> PERIPHERALS = new ConcurrentHashMap<>();

    private CannonMountControlManager() {
    }

    public static void setComputerControl(ControlledCannonMount mount, boolean enabled) {
        ControlState state = getState(mount);

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
        syncMountedRotation(mount, capturedYaw, capturedPitch);
        mount.setChanged();
        mount.sendData();
    }

    public static CannonMountPeripheral getPeripheral(ControlledCannonMount mount) {
        return PERIPHERALS.computeIfAbsent(new MountKey(mount.getCcCbcLevel().dimension(), mount.getCcCbcBlockPos()), ignored -> new CannonMountPeripheral(mount));
    }

    public static boolean isComputerControl(ControlledCannonMount mount) {
        return getState(mount).computerControl;
    }

    public static void setTargetAngles(ControlledCannonMount mount, double yaw, double pitch) {
        setTargetAngles(mount, yaw, pitch, normalizeYaw(yaw));
    }

    public static void setTargetAngles(ControlledCannonMount mount, double worldYaw, double pitch, double displayYaw) {
        ControlState state = getState(mount);
        state.targetYaw = normalizeYaw(worldYaw);
        state.targetPitch = pitch;
        state.displayTargetYaw = normalizeYaw(displayYaw);
        if (!state.computerControl) {
            state.currentYaw = getMountedYaw(mount);
            state.currentPitch = getMountedPitch(mount);
        }
    }

    public static double getTargetYaw(ControlledCannonMount mount) {
        return getState(mount).targetYaw;
    }

    public static double getTargetPitch(ControlledCannonMount mount) {
        return getState(mount).targetPitch;
    }

    public static double getDisplayTargetYaw(ControlledCannonMount mount) {
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

            ControlledCannonMount mount = CannonMountIntegration.find(level.getBlockEntity(key.pos));
            if (mount == null || mount.isCcCbcRemoved()) {
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

            ControlledCannonMount mount = CannonMountIntegration.find(level.getBlockEntity(key.pos));
            if (mount == null || mount.isCcCbcRemoved()) {
                iterator.remove();
                STATES.remove(key);
                continue;
            }

            entry.getValue().tick(level.getGameTime());
        }
    }

    private static void tickMount(ControlledCannonMount mount, ControlState state) {
        if (!state.computerControl) {
            return;
        }

        setDriveLock(mount, true);

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (!mount.isRunning() || contraption == null) {
            state.lastContraptionId = -1;
            return;
        }

        // Если contraption только что собрана (новая entity) — берём её реальные углы как отправную точку,
        // чтобы не было резкого прыжка к сохранённому state.currentYaw
        if (state.lastContraptionId != contraption.getId()) {
            state.lastContraptionId = contraption.getId();
            float pitchSign = getPitchSign(contraption);
            state.currentYaw = normalizeYaw(contraption.yaw);
            state.currentPitch = contraption.pitch / pitchSign;
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
        syncMountedRotation(mount, state.currentYaw, state.currentPitch);
        mount.setChanged();
        mount.sendData();
    }

    private static ControlState getState(ControlledCannonMount mount) {
        MountKey key = new MountKey(mount.getCcCbcLevel().dimension(), mount.getCcCbcBlockPos());
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

    public static void scheduleDisconnectCleanup(ControlledCannonMount mount, long executeAtGameTime) {
        getState(mount).disconnectCleanupAtGameTime = executeAtGameTime;
    }

    public static void cancelDisconnectCleanup(ControlledCannonMount mount) {
        getState(mount).disconnectCleanupAtGameTime = -1;
    }

    public static boolean shouldRunDisconnectCleanup(ControlledCannonMount mount, long gameTime) {
        ControlState state = getState(mount);
        return state.disconnectCleanupAtGameTime >= 0 && gameTime >= state.disconnectCleanupAtGameTime;
    }

    public static void finishDisconnectCleanup(ControlledCannonMount mount) {
        getState(mount).disconnectCleanupAtGameTime = -1;
    }

    private static ControlState createInitialState(ControlledCannonMount mount) {
        ControlState state = new ControlState();
        state.currentYaw = getMountedYaw(mount);
        state.currentPitch = getMountedPitch(mount);
        state.targetYaw = state.currentYaw;
        state.targetPitch = state.currentPitch;
        state.displayTargetYaw = state.currentYaw;
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

    private static boolean isDriveLocked(ControlledCannonMount mount) {
        return mount.isDriveLocked();
    }

    private static double getMountedYaw(ControlledCannonMount mount) {
        PitchOrientedContraptionEntity contraption = mount.getContraption();
        return normalizeYaw(contraption != null ? contraption.yaw : mount.getYaw());
    }

    private static double getMountedPitch(ControlledCannonMount mount) {
        return mount.getPitch();
    }

    private static void setDriveLock(ControlledCannonMount mount, boolean locked) {
        mount.setDriveLock(locked);
    }

    private static void syncMountedRotation(ControlledCannonMount mount, double yaw, double pitch) {
        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) {
            return;
        }

        float syncedYaw = (float) normalizeYaw(yaw);
        float syncedPitch = (float) pitch;
        float pitchSign = getPitchSign(contraption);

        contraption.yaw = syncedYaw;
        contraption.pitch = syncedPitch * pitchSign;
        contraption.setYRot(syncedYaw);
        contraption.setXRot(contraption.pitch);
        contraption.yRotO = contraption.getYRot();
        contraption.xRotO = contraption.getXRot();
    }

    private static float getPitchSign(PitchOrientedContraptionEntity contraption) {
        return contraption.getInitialOrientation().getAxis() == net.minecraft.core.Direction.Axis.X
            && contraption.getInitialOrientation().getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE
            || contraption.getInitialOrientation().getAxis() != net.minecraft.core.Direction.Axis.X
            && contraption.getInitialOrientation().getAxisDirection() == net.minecraft.core.Direction.AxisDirection.NEGATIVE
            ? 1.0f
            : -1.0f;
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
        private int lastContraptionId = -1;
    }
}
