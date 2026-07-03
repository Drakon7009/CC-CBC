package org.rassvet.cc_cbc.peripheral;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.rassvet.cc_cbc.api.ControlledCannonMount;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountInterfaceBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

public class CbcCannonMountAdapter implements ControlledCannonMount {
    private static final int STRONG_SIGNAL = 15;
    protected final CannonMountBlockEntity mount;

    public CbcCannonMountAdapter(CannonMountBlockEntity mount) {
        this.mount = mount;
    }

    @Override public Level getCcCbcLevel() { return this.mount.getLevel(); }
    @Override public BlockPos getCcCbcBlockPos() { return this.mount.getBlockPos(); }
    @Override public BlockState getCcCbcBlockState() { return this.mount.getBlockState(); }
    @Override public boolean isCcCbcRemoved() { return this.mount.isRemoved(); }
    @Override public boolean isRunning() { return this.mount.isRunning(); }
    @Override public PitchOrientedContraptionEntity getContraption() { return this.mount.getContraption(); }
    @Override public double getYaw() { return this.mount.getYawOffset(0); }
    @Override public double getPitch() { return this.mount.getDisplayPitch(); }
    @Override public double getYawSpeed() { return this.mount.getYawSpeed(); }
    @Override public double getPitchSpeed() { return this.mount.getPitchSpeed(); }
    @Override public double getAngularSpeed(double value, double clientDiff) { return this.mount.getAngularSpeed((float) value, (float) clientDiff); }
    @Override public void setYaw(float yaw) { this.mount.setYaw(yaw); }
    @Override public void setPitch(float pitch) { this.mount.setPitch(pitch); }
    @Override public void setChanged() { this.mount.setChanged(); }
    @Override public void sendData() { this.mount.sendData(); }
    @Override public void disassemble() { this.mount.disassemble(); }

    @Override
    public void setDriveLock(boolean locked) {
        double limit = locked ? 0.0 : -1.0;
        if (this.mount.getYawInterface() instanceof CannonMountInterfaceBlockEntity yawInterface) {
            yawInterface.setSequencedAngleLimit(limit);
            yawInterface.sendData();
        }
        if (this.mount.getPitchInterface() instanceof CannonMountInterfaceBlockEntity pitchInterface) {
            pitchInterface.setSequencedAngleLimit(limit);
            pitchInterface.sendData();
        }
    }

    @Override
    public boolean isDriveLocked() {
        double yawLimit = this.mount.getYawInterface() instanceof CannonMountInterfaceBlockEntity yawInterface
            ? yawInterface.getSequencedAngleLimit() : -1;
        double pitchLimit = this.mount.getPitchInterface() instanceof CannonMountInterfaceBlockEntity pitchInterface
            ? pitchInterface.getSequencedAngleLimit() : -1;
        return yawLimit == 0.0 && pitchLimit == 0.0;
    }

    @Override
    public void assemble() {
        BlockState state = this.mount.getBlockState();
        if (!this.mount.isRunning()) {
            boolean firePowered = state.getValue(CannonMountBlock.FIRE_POWERED);
            this.mount.onRedstoneUpdate(true, false, firePowered, firePowered, firePowered ? STRONG_SIGNAL : 0);
        }
        clearAssemblyPowered();
    }

    @Override
    public void fire(boolean enabled) {
        BlockState state = this.mount.getBlockState();
        boolean assemblyPowered = state.getValue(CannonMountBlock.ASSEMBLY_POWERED);
        boolean prevFirePowered = state.getValue(CannonMountBlock.FIRE_POWERED);
        this.mount.onRedstoneUpdate(assemblyPowered, assemblyPowered, enabled, prevFirePowered, enabled ? STRONG_SIGNAL : 0);
    }

    @Override
    public void clearAssemblyPowered() {
        BlockState state = this.mount.getBlockState();
        if (state.getValue(CannonMountBlock.ASSEMBLY_POWERED) && this.mount.getLevel() != null) {
            this.mount.getLevel().setBlock(this.mount.getBlockPos(), state.setValue(CannonMountBlock.ASSEMBLY_POWERED, false), 3);
        }
    }
}
