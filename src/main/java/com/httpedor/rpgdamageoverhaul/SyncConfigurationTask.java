package com.httpedor.rpgdamageoverhaul;

import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;

public record SyncConfigurationTask(ServerConfigurationPacketListener listener) implements ICustomConfigurationTask {
    public static final Type TYPE = new Type(ResourceLocation.fromNamespaceAndPath(RPGDamageOverhaul.MODID, "sync_task"));

    @Override
    public void run(Consumer<CustomPacketPayload> sender) {
        sender.accept(SyncPacket.fromData(RPGDamageOverhaul.dl));
        listener.finishCurrentTask(type());
    }

    @Override
    public Type type() {
        return TYPE;
    }
}