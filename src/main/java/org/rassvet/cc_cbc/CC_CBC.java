package org.rassvet.cc_cbc;

import com.mojang.logging.LogUtils;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.rassvet.cc_cbc.api.CannonMountIntegration;
import org.rassvet.cc_cbc.peripheral.CannonMountControlManager;
import org.rassvet.cc_cbc.peripheral.CbcCannonMountAdapter;
import org.rassvet.cc_cbc.peripheral.ReflectiveCannonMountAdapter;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.index.CBCBlockEntities;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;

@Mod(CC_CBC.MODID)
public class CC_CBC {
    public static final String MODID = "cc_cbc";
    private static final Logger LOGGER = LogUtils.getLogger();

    public CC_CBC(IEventBus modEventBus) {
        CannonMountIntegration.registerAdapter(blockEntity ->
            blockEntity instanceof CannonMountBlockEntity mount ? new CbcCannonMountAdapter(mount) : null);
        CannonMountIntegration.registerAdapter(ReflectiveCannonMountAdapter::create);
        modEventBus.addListener(this::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(this::onLevelTick);
        NeoForge.EVENT_BUS.addListener(this::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        LOGGER.info("CC-CBC peripheral integration loaded");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            PeripheralCapability.get(),
            CBCBlockEntities.CANNON_MOUNT.get(),
            (mount, side) -> CannonMountControlManager.getPeripheral(new CbcCannonMountAdapter(mount))
        );
        registerOptionalMount(event, "cbc_firepower_components", "compact_cannon_mount");
        registerOptionalMount(event, "cbc_compact_mount", "compact_cannon_mount");
    }

    private void registerOptionalMount(RegisterCapabilitiesEvent event, String namespace, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        if (!BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)) {
            return;
        }
        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id);
        event.registerBlockEntity(
            PeripheralCapability.get(),
            type,
            (blockEntity, side) -> {
                var mount = CannonMountIntegration.find(blockEntity);
                return mount == null ? null : CannonMountControlManager.getPeripheral(mount);
            }
        );
    }

    private void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            CannonMountControlManager.tickLevel(serverLevel);
        }
    }

    private void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            CannonMountControlManager.clearLevel(serverLevel.dimension());
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        CannonMountControlManager.clearAll();
    }
}
