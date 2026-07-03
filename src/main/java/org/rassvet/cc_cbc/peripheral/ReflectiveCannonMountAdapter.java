package org.rassvet.cc_cbc.peripheral;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.rassvet.cc_cbc.api.ControlledCannonMount;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ReflectiveCannonMountAdapter implements ControlledCannonMount {
    private static final int STRONG_SIGNAL = 15;

    private final BlockEntity blockEntity;
    private final Class<?> type;

    private ReflectiveCannonMountAdapter(BlockEntity blockEntity) {
        this.blockEntity = blockEntity;
        this.type = blockEntity.getClass();
    }

    public static ControlledCannonMount create(BlockEntity blockEntity) {
        String name = blockEntity.getClass().getName();
        if (name.equals("com.cubester.cbc_compact_mount.content.CompactCannonMountBlockEntity")
            || name.equals("com.cbcfirepowercomponents.content.compact_cannon_mount.CompactCannonMountBlockEntity")) {
            return new ReflectiveCannonMountAdapter(blockEntity);
        }
        return null;
    }

    @Override public Level getCcCbcLevel() { return this.blockEntity.getLevel(); }
    @Override public BlockPos getCcCbcBlockPos() { return this.blockEntity.getBlockPos(); }
    @Override public BlockState getCcCbcBlockState() { return this.blockEntity.getBlockState(); }
    @Override public boolean isCcCbcRemoved() { return this.blockEntity.isRemoved(); }
    @Override public boolean isRunning() { return (Boolean) invoke("isRunning"); }
    @Override public PitchOrientedContraptionEntity getContraption() { return (PitchOrientedContraptionEntity) invoke("getContraption"); }
    @Override public double getYaw() { return hasMethod("getYawOffset", float.class) ? ((Number) invoke("getYawOffset", new Class<?>[]{float.class}, 0f)).doubleValue() : fixedYaw(); }
    @Override public double getPitch() { return ((Number) invoke("getDisplayPitch")).doubleValue(); }
    @Override public double getYawSpeed() { Object yawInterface = getField("yawInterface"); return yawInterface == null ? 0 : ((Number) invoke(yawInterface, "getSpeed")).doubleValue(); }
    @Override public double getPitchSpeed() { Object pitchInterface = getField("pitchInterface"); return pitchInterface == null ? ((Number) invoke("getSpeed")).doubleValue() : ((Number) invoke(pitchInterface, "getSpeed")).doubleValue(); }
    @Override public double getAngularSpeed(double value, double clientDiff) { return ((Number) invoke("getAngularSpeed", new Class<?>[]{float.class, float.class}, (float) value, (float) clientDiff)).doubleValue(); }
    @Override public void setYaw(float yaw) { if (hasMethod("setLimitedYaw", float.class)) invoke("setLimitedYaw", new Class<?>[]{float.class}, yaw); }
    @Override public void setPitch(float pitch) { if (hasMethod("setLimitedPitch", float.class)) invoke("setLimitedPitch", new Class<?>[]{float.class}, pitch); else setField("cannonPitch", pitch); }
    @Override public void setChanged() { this.blockEntity.setChanged(); }
    @Override public void sendData() { invoke("sendData"); }
    @Override public void disassemble() { invoke("disassemble"); }

    @Override
    public void setDriveLock(boolean locked) {
        double limit = locked ? 0.0 : -1.0;
        Object yawInterface = getField("yawInterface");
        Object pitchInterface = getField("pitchInterface");
        if (yawInterface != null) invoke(yawInterface, "setSequencedAngleLimit", new Class<?>[]{double.class}, limit);
        if (pitchInterface != null) invoke(pitchInterface, "setSequencedAngleLimit", new Class<?>[]{double.class}, limit);
        if (yawInterface == null && pitchInterface == null) setField("sequencedAngleLimit", limit);
    }

    @Override
    public boolean isDriveLocked() {
        Object yawInterface = getField("yawInterface");
        Object pitchInterface = getField("pitchInterface");
        if (yawInterface != null && pitchInterface != null) {
            return ((Number) invoke(yawInterface, "getSequencedAngleLimit")).doubleValue() == 0.0
                && ((Number) invoke(pitchInterface, "getSequencedAngleLimit")).doubleValue() == 0.0;
        }
        Object limit = getField("sequencedAngleLimit");
        return limit instanceof Number number && number.doubleValue() == 0.0;
    }

    @Override
    public void assemble() throws Exception {
        BlockState state = getCcCbcBlockState();
        boolean firePowered = getBooleanProperty(state, "fire_powered");
        invoke("onRedstoneUpdate", new Class<?>[]{boolean.class, boolean.class, boolean.class, boolean.class, int.class},
            true, false, firePowered, firePowered, firePowered ? STRONG_SIGNAL : 0);
        clearAssemblyPowered();
    }

    @Override
    public void fire(boolean enabled) {
        BlockState state = getCcCbcBlockState();
        boolean assemblyPowered = getBooleanProperty(state, "assembly_powered");
        boolean firePowered = getBooleanProperty(state, "fire_powered");
        invoke("onRedstoneUpdate", new Class<?>[]{boolean.class, boolean.class, boolean.class, boolean.class, int.class},
            assemblyPowered, assemblyPowered, enabled, firePowered, enabled ? STRONG_SIGNAL : 0);
    }

    @Override
    public void clearAssemblyPowered() {
        BlockState state = getCcCbcBlockState();
        if (getBooleanProperty(state, "assembly_powered") && getCcCbcLevel() != null) {
            state.getProperties().stream()
                .filter(property -> property.getName().equals("assembly_powered"))
                .findFirst()
                .ifPresent(property -> getCcCbcLevel().setBlock(getCcCbcBlockPos(), setBoolean(state, property, false), 3));
        }
    }

    private double fixedYaw() {
        PitchOrientedContraptionEntity contraption = getContraption();
        return contraption == null ? 0 : contraption.yaw;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setBoolean(BlockState state, net.minecraft.world.level.block.state.properties.Property property, boolean value) {
        return state.setValue(property, value);
    }

    private static boolean getBooleanProperty(BlockState state, String name) {
        return state.getProperties().stream()
            .filter(property -> property.getName().equals(name))
            .findFirst()
            .map(property -> state.getValue((net.minecraft.world.level.block.state.properties.BooleanProperty) property))
            .orElse(false);
    }

    private boolean hasMethod(String name, Class<?>... params) {
        try {
            this.type.getMethod(name, params);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private Object invoke(String name) { return invoke(this.blockEntity, name, new Class<?>[0]); }
    private Object invoke(String name, Class<?>[] params, Object... args) { return invoke(this.blockEntity, name, params, args); }
    private static Object invoke(Object target, String name) { return invoke(target, name, new Class<?>[0]); }

    private static Object invoke(Object target, String name, Class<?>[] params, Object... args) {
        try {
            Method method = target.getClass().getMethod(name, params);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("CC-CBC mount adapter failed calling " + name, e);
        }
    }

    private Object getField(String name) {
        Class<?> current = this.type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(this.blockEntity);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("CC-CBC mount adapter failed reading " + name, e);
            }
        }
        return null;
    }

    private void setField(String name, Object value) {
        Class<?> current = this.type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                field.set(this.blockEntity, value);
                return;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("CC-CBC mount adapter failed writing " + name, e);
            }
        }
    }
}
