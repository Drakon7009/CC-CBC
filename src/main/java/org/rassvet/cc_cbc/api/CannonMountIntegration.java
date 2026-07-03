package org.rassvet.cc_cbc.api;

import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public final class CannonMountIntegration {
    private static final List<Function<BlockEntity, ControlledCannonMount>> ADAPTERS = new CopyOnWriteArrayList<>();

    private CannonMountIntegration() {
    }

    /**
     * Adds support for a custom cannon mount block entity.
     *
     * Forge 1.20.1 mods only need to call this during common setup. NeoForge 1.21.1 mods must
     * also register ComputerCraft's peripheral capability for their own BlockEntityType and then
     * return {@code CannonMountControlManager.getPeripheral(CannonMountIntegration.find(blockEntity))}.
     */
    public static void registerAdapter(Function<BlockEntity, ControlledCannonMount> adapter) {
        ADAPTERS.add(adapter);
    }

    public static ControlledCannonMount find(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return null;
        }
        for (Function<BlockEntity, ControlledCannonMount> adapter : ADAPTERS) {
            ControlledCannonMount mount = adapter.apply(blockEntity);
            if (mount != null) {
                return mount;
            }
        }
        return null;
    }
}
