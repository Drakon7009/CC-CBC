package org.rassvet.cc_cbc;

import com.mojang.logging.LogUtils;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.rassvet.cc_cbc.peripheral.CannonMountControlManager;
import org.rassvet.cc_cbc.peripheral.CannonMountPeripheral;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.index.CBCBlockEntities;

@Mod(CC_CBC.MODID)
public class CC_CBC {
    public static final String MODID = "cc_cbc";
    private static final Logger LOGGER = LogUtils.getLogger();

    public CC_CBC(IEventBus modEventBus) {
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
            (mount, side) -> new CannonMountPeripheral(mount)
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
