package org.rassvet.cc_cbc;

import com.mojang.logging.LogUtils;
import dan200.computercraft.api.ForgeComputerCraftAPI;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import org.rassvet.cc_cbc.api.CannonMountIntegration;
import org.rassvet.cc_cbc.peripheral.CannonMountControlManager;
import org.rassvet.cc_cbc.peripheral.CbcCannonMountAdapter;
import org.rassvet.cc_cbc.peripheral.ReflectiveCannonMountAdapter;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import org.slf4j.Logger;

@Mod(Cc_cbc.MODID)
public class Cc_cbc {
    public static final String MODID = "cc_cbc";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Cc_cbc() {
        CannonMountIntegration.registerAdapter(blockEntity ->
            blockEntity instanceof CannonMountBlockEntity mount ? new CbcCannonMountAdapter(mount) : null);
        CannonMountIntegration.registerAdapter(ReflectiveCannonMountAdapter::create);
        ForgeComputerCraftAPI.registerPeripheralProvider(this::getPeripheral);
        MinecraftForge.EVENT_BUS.addListener(this::onLevelTick);
        MinecraftForge.EVENT_BUS.addListener(this::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        LOGGER.info("CC-CBC peripheral integration loaded for Forge 1.20.1");
    }

    private LazyOptional<IPeripheral> getPeripheral(Level level, BlockPos pos, Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        var mount = CannonMountIntegration.find(blockEntity);
        if (mount != null && !mount.isCcCbcRemoved()) {
            return LazyOptional.of(() -> CannonMountControlManager.getPeripheral(mount));
        }

        return LazyOptional.empty();
    }

    private void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (event.level instanceof ServerLevel serverLevel) {
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
