package org.rassvet.cc_cbc.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

public interface ControlledCannonMount {
    Level getCcCbcLevel();

    BlockPos getCcCbcBlockPos();

    BlockState getCcCbcBlockState();

    boolean isCcCbcRemoved();

    boolean isRunning();

    PitchOrientedContraptionEntity getContraption();

    double getYaw();

    double getPitch();

    double getYawSpeed();

    double getPitchSpeed();

    double getAngularSpeed(double value, double clientDiff);

    void setYaw(float yaw);

    void setPitch(float pitch);

    void setDriveLock(boolean locked);

    boolean isDriveLocked();

    void setChanged();

    void sendData();

    void assemble() throws Exception;

    void disassemble();

    void fire(boolean enabled);

    void clearAssemblyPowered();
}
