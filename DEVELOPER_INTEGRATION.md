# Adding CC:CBC Support to a Custom Cannon Mount

This file is for mod developers who want their own cannon mount block entity to expose the CC:CBC `cannon_mount` ComputerCraft peripheral.

CC:CBC support is optional. Your mod does not need to depend on any private CC:CBC internals, but it must provide a small adapter for its mount block entity.

## What CC:CBC Expects

later

```java
import org.rassvet.cc_cbc.api.ControlledCannonMount;
```

Your adapter should wrap your own block entity and provide:

- world, position, block state, and removed state
- assembled/running state
- mounted `PitchOrientedContraptionEntity`
- current yaw and pitch
- yaw and pitch kinetic speeds
- setters for yaw and pitch
- a drive lock used by CC:CBC computer control
- assemble, disassemble, fire, and clear-assembly-power operations

The drive lock should stop normal kinetic rotation while CC:CBC is controlling the mount. CC:CBC uses this when `setComputerControl(true)` is called from Lua.

## Register the Adapter

Register your adapter during common mod setup:

```java
import org.rassvet.cc_cbc.api.CannonMountIntegration;

CannonMountIntegration.registerAdapter(blockEntity -> {
    if (blockEntity instanceof MyCannonMountBlockEntity mount) {
        return new MyCcCbcMountAdapter(mount);
    }
    return null;
});
```

The adapter can live in your mod. CC:CBC will call it when a ComputerCraft peripheral is requested for a block entity.

## NeoForge 1.21.1 Capability Registration

On NeoForge 1.21.1, registering the adapter is not enough. Your mod must also register the ComputerCraft peripheral capability for your own `BlockEntityType`.

Example:

```java
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.rassvet.cc_cbc.api.CannonMountIntegration;
import org.rassvet.cc_cbc.peripheral.CannonMountControlManager;

private void registerCapabilities(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(
        PeripheralCapability.get(),
        MY_CANNON_MOUNT_BLOCK_ENTITY_TYPE.get(),
        (blockEntity, side) -> {
            var mount = CannonMountIntegration.find(blockEntity);
            return mount == null ? null : CannonMountControlManager.getPeripheral(mount);
        }
    );
}
```

## Forge 1.20.1

On Forge 1.20.1, CC:CBC uses ComputerCraft's peripheral provider lookup. In most cases, registering the adapter is enough:

```java
CannonMountIntegration.registerAdapter(blockEntity ->
    blockEntity instanceof MyCannonMountBlockEntity mount
        ? new MyCcCbcMountAdapter(mount)
        : null
);
```

## Minimal Adapter Skeleton

```java
public final class MyCcCbcMountAdapter implements ControlledCannonMount {
    private final MyCannonMountBlockEntity mount;

    public MyCcCbcMountAdapter(MyCannonMountBlockEntity mount) {
        this.mount = mount;
    }

    // Implement all ControlledCannonMount methods by delegating to your mount.
    // Keep all values in degrees. Yaw may be wrapped by CC:CBC.
    // setDriveLock(true) should stop kinetic yaw/pitch movement.
}
```

## Lua Compatibility

Once registered, ComputerCraft sees your mount as the same peripheral type as the built-in CBC mount:

```lua
local mount = peripheral.find("cannon_mount")
mount.setComputerControl(true)
mount.setTargetAngles(90, 15)
mount.fire(true)
```

The existing CC:CBC Lua API is reused unchanged.
