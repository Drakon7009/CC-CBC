package org.rassvet.cc_cbc.mixin;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountInterfaceBlockEntity;

/**
 * Fixes visual rotation of the cannon barrel when computer (CC) control mode is active.
 *
 * Root cause: CannonMountControlManager uses sequencedAngleLimit=0.0 ("drive lock") to prevent
 * the kinetic shaft from spinning the cannon during computer control.  The same limit value is
 * synced to the client, where CannonMountBlockEntity.tick() clamps yawSpeed to 0 (because
 * limit >= 0 → clamp to [-limit, limit] = [0, 0]).  Simultaneously, read() resets cannonYaw to
 * the *previous* value and stores the difference in clientYawDiff for smooth interpolation – but
 * that interpolation is then completely blocked by the zero limit.  The cannon appears frozen on
 * the client even though the server is correctly rotating it.
 *
 * Fix: after read() has run with a client packet and the drive is confirmed locked
 * (limit == 0.0 on both yaw and pitch interfaces), restore cannonYaw/cannonPitch to the
 * authoritative server values that were just received, bypassing the frozen-clientYawDiff path.
 * CC sends data every tick, so the one-tick-latency "step" rendering is imperceptibly smooth.
 */
@Mixin(value = CannonMountBlockEntity.class, remap = false)
public abstract class CannonMountBlockEntityMixin {

    @Shadow
    protected float cannonYaw;

    @Shadow
    protected float cannonPitch;

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Z)V", at = @At("TAIL"), remap = false)
    private void cc_cbc_onRead(CompoundTag tag, boolean clientPacket, CallbackInfo ci) {
        if (!clientPacket) {
            return;
        }

        CannonMountBlockEntity self = (CannonMountBlockEntity) (Object) this;

        // Both interfaces must be locked (limit == 0.0) for this to be computer control mode.
        boolean yawLocked = self.getYawInterface() instanceof CannonMountInterfaceBlockEntity yawIface
                && yawIface.getSequencedAngleLimit() == 0.0;
        boolean pitchLocked = self.getPitchInterface() instanceof CannonMountInterfaceBlockEntity pitchIface
                && pitchIface.getSequencedAngleLimit() == 0.0;

        if (!yawLocked || !pitchLocked) {
            return;
        }

        // Drive is locked → computer control is active.
        // The normal read() mechanism has reset cannonYaw/cannonPitch to their pre-packet values
        // and stored clientYawDiff/clientPitchDiff.  With limit=0, yawSpeed is clamped to 0 every
        // tick, so the diff is never consumed and the cannon appears frozen.
        // Restore the authoritative values from the server packet directly.
        this.cannonYaw = tag.getFloat("CannonYaw");
        this.cannonPitch = tag.getFloat("CannonPitch");
    }
}

