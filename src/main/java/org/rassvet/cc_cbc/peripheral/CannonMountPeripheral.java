package org.rassvet.cc_cbc.peripheral;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import org.rassvet.cc_cbc.api.ControlledCannonMount;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CannonMountPeripheral implements IPeripheral {
    private final ControlledCannonMount mount;
    private final Set<IComputerAccess> attachedComputers = new HashSet<>();

    public CannonMountPeripheral(ControlledCannonMount mount) {
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
        info.put("yaw", toGameYaw(this.mount.getYaw()));
        info.put("pitch", this.mount.getPitch());
        info.put("targetYaw", toGameYaw(CannonMountControlManager.getDisplayTargetYaw(this.mount)));
        info.put("targetPitch", CannonMountControlManager.getTargetPitch(this.mount));
        info.put("yawShaftSpeed", (double) this.mount.getYawSpeed());
        info.put("pitchShaftSpeed", (double) this.mount.getPitchSpeed());
        info.put("x", this.mount.getCcCbcBlockPos().getX());
        info.put("y", this.mount.getCcCbcBlockPos().getY());
        info.put("z", this.mount.getCcCbcBlockPos().getZ());
        return info;
    }

    @LuaFunction(mainThread = true)
    public boolean assemble(boolean enabled) {
        if (enabled) {
            try {
                this.mount.assemble();
            } catch (Exception e) {
                return false;
            }
            return this.mount.isRunning();
        }

        if (this.mount.isRunning()) {
            this.mount.disassemble();
        }
        this.mount.clearAssemblyPowered();
        return this.mount.isRunning();
    }

    @LuaFunction(mainThread = true)
    public boolean fire(boolean enabled) {
        this.mount.fire(enabled);
        return enabled;
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
        CannonMountControlManager.cancelDisconnectCleanup(this.mount);
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

        if (this.mount.getCcCbcLevel() == null || this.mount.getCcCbcLevel().getServer() == null) {
            return;
        }

        CannonMountControlManager.scheduleDisconnectCleanup(this.mount, this.mount.getCcCbcLevel().getGameTime() + 1);
    }

    public void tick(long gameTime) {
        if (!CannonMountControlManager.shouldRunDisconnectCleanup(this.mount, gameTime)) {
            return;
        }
        CannonMountControlManager.finishDisconnectCleanup(this.mount);

        if (hasAttachedComputers()) {
            return;
        }

        if (this.mount.isCcCbcRemoved() || this.mount.getCcCbcLevel() == null || this.mount.getCcCbcLevel().getServer() == null) {
            return;
        }

        if (this.mount.getCcCbcLevel().getServer().getPlayerCount() <= 0) {
            return;
        }

        if (CannonMountControlManager.isComputerControl(this.mount)) {
            CannonMountControlManager.setComputerControl(this.mount, false);
        }

        if (!this.mount.getCcCbcLevel().hasNeighborSignal(this.mount.getCcCbcBlockPos()) && this.mount.isRunning()) {
            this.mount.disassemble();
            this.mount.clearAssemblyPowered();
        }
    }

    private boolean hasAttachedComputers() {
        synchronized (this.attachedComputers) {
            return !this.attachedComputers.isEmpty();
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
        return Math.abs(wrapped - 360.0) < 1.0e-6 ? 0.0 : wrapped;
    }
}
