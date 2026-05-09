package org.rassvet.cc_cbc.peripheral;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.level.block.state.BlockState;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CannonMountPeripheral implements IPeripheral {
    private static final int STRONG_SIGNAL = 15;

    private final CannonMountBlockEntity mount;
    private final Set<IComputerAccess> attachedComputers = new HashSet<>();

    public CannonMountPeripheral(CannonMountBlockEntity mount) {
        this.mount = mount;
    }

    @Override
    public String getType() {
        return "cannon_mount";
    }

    @LuaFunction(mainThread = true)
    public void setComputerControl(boolean enabled) {
        CannonMountControlManager.setComputerControl(this.mount, enabled);
    }

    @LuaFunction(mainThread = true)
    public boolean isComputerControl() {
        return CannonMountControlManager.isComputerControl(this.mount);
    }

    @LuaFunction(mainThread = true)
    public void setTargetAngles(double yaw, double pitch) throws LuaException {
        validateFinite(yaw, "yaw");
        validateFinite(pitch, "pitch");
        CannonMountControlManager.setTargetAngles(this.mount, yaw, pitch, yaw);
    }

    @LuaFunction(mainThread = true)
    public void setTargetYaw(double yaw) throws LuaException {
        validateFinite(yaw, "yaw");
        CannonMountControlManager.setTargetAngles(
            this.mount,
            yaw,
            CannonMountControlManager.getTargetPitch(this.mount),
            yaw
        );
    }

    @LuaFunction(mainThread = true)
    public void setTargetPitch(double pitch) throws LuaException {
        validateFinite(pitch, "pitch");
        CannonMountControlManager.setTargetAngles(this.mount, CannonMountControlManager.getTargetYaw(this.mount), pitch);
    }

    @LuaFunction(mainThread = true)
    public Map<String, Object> getInfo() {
        Map<String, Object> info = new HashMap<>();
        PitchOrientedContraptionEntity contraption = this.mount.getContraption();

        info.put("computerControl", CannonMountControlManager.isComputerControl(this.mount));
        info.put("assembled", contraption != null);
    
        info.put("yaw", toGameYaw(this.mount.getYawOffset(0)));
        info.put("pitch", (double) this.mount.getDisplayPitch());
        info.put("targetYaw", toGameYaw(CannonMountControlManager.getDisplayTargetYaw(this.mount)));
        info.put("targetPitch", CannonMountControlManager.getTargetPitch(this.mount));

        info.put("yawShaftSpeed", (double) this.mount.getYawSpeed());
        info.put("pitchShaftSpeed", (double) this.mount.getPitchSpeed());

        info.put("x", this.mount.getBlockPos().getX());
        info.put("y", this.mount.getBlockPos().getY());
        info.put("z", this.mount.getBlockPos().getZ());
        return info;
    }

    @LuaFunction(mainThread = true)
    public boolean assemble(boolean enabled) {
        BlockState state = this.mount.getBlockState();

        if (enabled) {
            if (!this.mount.isRunning()) {
                boolean firePowered = state.getValue(CannonMountBlock.FIRE_POWERED);
                this.mount.onRedstoneUpdate(true, false, firePowered, firePowered, firePowered ? STRONG_SIGNAL : 0);
            }

            // Do not keep ASSEMBLY_POWERED latched, otherwise neighbour updates may force disassembly.
            clearAssemblyPowered();
            return this.mount.isRunning();
        }

        if (this.mount.isRunning()) {
            this.mount.disassemble();
        }
        clearAssemblyPowered();
        return this.mount.isRunning();
    }

    @LuaFunction(mainThread = true)
    public boolean fire(boolean enabled) {
        BlockState state = this.mount.getBlockState();
        boolean assemblyPowered = state.getValue(CannonMountBlock.ASSEMBLY_POWERED);
        boolean prevFirePowered = state.getValue(CannonMountBlock.FIRE_POWERED);

        this.mount.onRedstoneUpdate(assemblyPowered, assemblyPowered, enabled, prevFirePowered, enabled ? STRONG_SIGNAL : 0);
        return this.mount.getBlockState().getValue(CannonMountBlock.FIRE_POWERED);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof CannonMountPeripheral peripheral && peripheral.mount == this.mount;
    }

    @Override
    public void attach(IComputerAccess computer) {
        synchronized (this.attachedComputers) {
            this.attachedComputers.add(computer);
        }
    }

    @Override
    public void detach(IComputerAccess computer) {
        boolean noComputersLeft;
        synchronized (this.attachedComputers) {
            this.attachedComputers.remove(computer);
            noComputersLeft = this.attachedComputers.isEmpty();
        }

        if (!noComputersLeft) {
            return;
        }

        if (this.mount.getLevel() == null || this.mount.getLevel().getServer() == null) {
            return;
        }

        // Ignore disconnects during shutdown/relog; keep persisted CC mode across world re-entry.
        if (this.mount.getLevel().getServer().getPlayerCount() <= 0) {
            return;
        }

        if (CannonMountControlManager.isComputerControl(this.mount)) {
            CannonMountControlManager.setComputerControl(this.mount, false);
        }

        // Return to redstone-driven behavior on disconnect: no redstone -> disassemble via CBC redstone path.
        if (!this.mount.getLevel().hasNeighborSignal(this.mount.getBlockPos()) && this.mount.isRunning()) {
            forceRedstoneDisassemble();
            clearAssemblyPowered();
        }
    }

    private void forceRedstoneDisassemble() {
        BlockState state = this.mount.getBlockState();
        boolean firePowered = state.getValue(CannonMountBlock.FIRE_POWERED);
        // Simulate assembly power falling edge (true -> false) to use CBC's safe disassembly path.
        this.mount.onRedstoneUpdate(false, true, firePowered, firePowered, 0);
    }

    private void clearAssemblyPowered() {
        BlockState state = this.mount.getBlockState();
        if (state.getValue(CannonMountBlock.ASSEMBLY_POWERED) && this.mount.getLevel() != null) {
            this.mount.getLevel().setBlock(this.mount.getBlockPos(), state.setValue(CannonMountBlock.ASSEMBLY_POWERED, false), 3);
        }
    }

    private static void validateFinite(double value, String name) throws LuaException {
        if (!Double.isFinite(value)) {
            throw new LuaException(name + " must be a finite number");
        }
    }

    private static double toGameYaw(double yaw) {
        double wrapped = yaw % 360.0;
        if (wrapped < 0) wrapped += 360.0;
        // Keep canonical 0 instead of 360 due to floating-point artifacts.
        return Math.abs(wrapped - 360.0) < 1.0e-6 ? 0.0 : wrapped;
    }
}
