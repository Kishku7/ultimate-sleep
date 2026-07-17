package com.kishku7.ultimatesleep.sleep;

import com.kishku7.ultimatesleep.mixin.EntityFlagsAccessor;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Per-viewer mob highlight for the highlight_blocking_mobs accessibility option.
 *
 * Rather than applying a real (everyone-sees) Glowing effect, we send the target player ALONE a
 * synthetic entity-data packet with the shared GLOWING flag (bit 6) set on each blocking mob, so
 * only the player trying to sleep sees the outline. After a short window we resend the mob's true
 * flags to that player to clear the highlight. Server-side only -- no client mod required.
 */
public final class MobHighlight {

    private MobHighlight() {}

    private static final int FLAG_GLOWING = 6;
    private record Entry(UUID viewer, ServerLevel level, int entityId, long expire) {}
    private static final List<Entry> active = new ArrayList<>();

    public static void glowFor(ServerPlayer viewer, Entity mob, int ticks) {
        byte real = mob.getEntityData().get(EntityFlagsAccessor.usleep$sharedFlags());
        send(viewer, mob.getId(), (byte) (real | (1 << FLAG_GLOWING)));
        MinecraftServer server = viewer.level().getServer();
        long expire = (server != null ? server.getTickCount() : 0) + ticks;
        active.add(new Entry(viewer.getUUID(), (ServerLevel) mob.level(), mob.getId(), expire));
    }

    public static void tick(MinecraftServer server) {
        if (active.isEmpty()) return;
        long now = server.getTickCount();
        active.removeIf(e -> {
            if (now < e.expire()) return false;
            ServerPlayer viewer = server.getPlayerList().getPlayer(e.viewer());
            Entity mob = e.level().getEntity(e.entityId());
            if (viewer != null && mob != null) {
                // resend the mob's true flags to clear the client-only glow
                send(viewer, e.entityId(), mob.getEntityData().get(EntityFlagsAccessor.usleep$sharedFlags()));
            }
            return true;
        });
    }

    private static void send(ServerPlayer viewer, int entityId, byte flags) {
        SynchedEntityData.DataValue<Byte> dv =
                SynchedEntityData.DataValue.create(EntityFlagsAccessor.usleep$sharedFlags(), flags);
        viewer.connection.send(new ClientboundSetEntityDataPacket(
                entityId, List.<SynchedEntityData.DataValue<?>>of(dv)));
    }
}
