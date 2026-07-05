# compat_loaders.py -- ultimate-sleep NeoForge + Forge loader-flavour drift brain (ASCII only).
# Scope: the pre-26 NeoForge cells (1.20.6 .. 1.21.11) and the Forge cells (1.20.1 .. 1.21.11).
# Shared vanilla predicates come from compat.py (same sys.path); do NOT duplicate them here.
#
# Evidence (byte-grepped neoforge universal jars in the local gradle cache, 2026-07-05):
#   20.6.139  21.1.234  21.2.1-beta  21.5.97  21.8.53  21.9.16-beta  21.11.42
#
# UNIFORM across the whole 1.20.6-1.21.11 range (and 26) -- verified, so NO cog emitters:
#   - @Mod ctor (ModContainer, IEventBus, Dist)   [bank-vault built 1.20.6..26 with it]
#   - net.neoforged.neoforge.event.tick.ServerTickEvent.Post (getServer() on the base class)
#   - CanPlayerSleepEvent: getProblem() / setProblem(Player.BedSleepingProblem) / getPos()
#   - RegisterPayloadHandlersEvent + PayloadRegistrar .versioned/.playToServer/.playToClient
#   - IPayloadContext.player() / .enqueueWork(Runnable)
#   - PacketDistributor.sendToPlayer(ServerPlayer, payload)
#   - player.connection.hasChannel(CustomPacketPayload.Type)  [ICommonPacketListener, 20.6+]
#
# SOLE loader drift axis in range: the client->server send.
#   <  1.21.8: net.neoforged.neoforge.network.PacketDistributor.sendToServer
#   >= 1.21.8: net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer
#              (PacketDistributor.sendToServer is GONE at 21.8.53; ClientPacketDistributor
#               present 21.8.53/21.9.16/21.11.42, absent 21.5.97 and below)
#
# 1.20.4 (RegisterPayloadHandlerEvent / IPayloadRegistrar / PLAYER.with(p).send era) is OUTSIDE
# this mod's NeoForge matrix -- check_range() raises instead of emitting silently-wrong code.

import compat


def check_range(ver):
    if compat._vt(ver) < (1, 20, 6):
        raise ValueError(
            "compat_loaders: NeoForge flavour supports 1.20.6+ only, got %s "
            "(neo 20.5.x beta lacks CanPlayerSleepEvent -- boot-proven NoClassDefFoundError "
            "2026-07-05; 1.20.4 is the IPayloadRegistrar era)" % ver)
    return ver


def client_modern_send(ver):
    check_range(ver)
    return compat._vt(ver) >= (1, 21, 8)


def client_send_import(ver):
    if client_modern_send(ver):
        return "import net.neoforged.neoforge.client.network.ClientPacketDistributor;"
    return "import net.neoforged.neoforge.network.PacketDistributor;"


def client_sender_ref(ver):
    if client_modern_send(ver):
        return "ClientPacketDistributor::sendToServer"
    return "PacketDistributor::sendToServer"


def sleep_pred(ver):
    # vanilla arity drift (compat.sl2): isPreventingPlayerRest(ServerLevel,Player) from 1.21.2
    check_range(ver)
    if compat.sl2(ver):
        return "mob.isPreventingPlayerRest(sl, sp)"
    return "mob.isPreventingPlayerRest(sp)"


# ---------------------------------------------------------------------------
# Forge (LexForge) flavour -- cells 1.20.1, 1.20.6, 1.21.1, 1.21.5, 1.21.8, 1.21.10, 1.21.11.
#
# Evidence (javap on the FG6 recomp jars in forge_gradle/minecraft_user_repo, 2026-07-05):
#   47.3.0  50.2.8  52.1.14  55.1.10  58.1.18  60.1.9  61.1.0
#
# Sleep surface (bytecode of the PATCHED ServerPlayer.startSleepInBed, all 7 versions):
#   - Forge has NO CanPlayerSleepEvent on ANY version 47-61.
#   - bedInRange(BlockPos,Direction)Z INVOKE SURVIVES the Forge patch on every version ->
#     the ignore_bed_too_far redirect stays a mixin (forge twin of ServerPlayerSleepMixin).
#   - The daytime gate is PATCHED OUT on every version: isDay/isBrightOutside (<=60) and
#     BedRule.canSleep (61) are replaced by ForgeEventFactory.fireSleepingTimeCheck (47) /
#     onSleepingTimeCheck (50+; 61 adds a BedRule arg). DEFAULT preserves vanilla, ALLOW
#     bypasses -> sleep_anytime is a SleepingTimeCheckEvent listener, NOT a mixin.
#   - The NOT_SAFE monsters block stays vanilla -> ForgeSleepMonstersMixin redirects the
#     getEntitiesOfClass call. Receiver drift (FORGE-patched): Level <=1.21.5, ServerLevel >=1.21.6.
#
# Event bus eras (bank-vault gate-proof 2026-07-03 + M1 1.21.10/1.21.11 builds):
#   - EB6 (<= Forge 55 / MC 1.21.5): MinecraftForge.EVENT_BUS.addListener; base-Event
#     setResult(Event.Result). ServerTickEvent.Post exists from Forge 50; 1.20.1 has only the
#     Phase field on the abstract ServerTickEvent.
#   - EB7 (>= Forge 56 / MC 1.21.6): per-event static BUS fields; HasResult with
#     net.minecraftforge.common.util.Result. At Forge 60+ the tick events are RECORDS:
#     ServerTickEvent.Post.getServer() is GONE -> server(). (ServerStartedEvent kept a
#     getServer() accessor at 60/61; RegisterCommandsEvent kept getDispatcher().)
#
# Networking:
#   - 1.20.1 (SRG, Forge 47): NetworkRegistry.newSimpleChannel + registerMessage -- whole
#     forge_legacy_net flavour (plain holder records + FriendlyByteBuf codecs).
#   - 1.20.6+ (mojmap, Forge 50-61): ChannelBuilder.named(...).payloadChannel().play() --
#     verified IDENTICAL shapes at 50/58/60/61 (named takes ResourceLocation <=60, Identifier
#     at 61 -- Era.id() already returns the era-correct type). isRemotePresent(Connection);
#     Connection via p.connection.getConnection() (Forge patch, present 50+; 1.20.1 uses the
#     public field p.connection.connection instead, in the legacy flavour).

def forge_check_range(ver):
    t = compat._vt(ver)
    if t < (1, 20, 1) or t >= (26,):
        raise ValueError(
            "compat_loaders: Forge flavour supports 1.20.1-1.21.11 only, got %s" % ver)
    return ver


def forge_eb7(ver):
    # EventBus 7 landed with Forge 56 (MC 1.21.6) -- bank-vault gate-proof 2026-07-03
    forge_check_range(ver)
    return compat._vt(ver) >= (1, 21, 6)


def forge_tick_post(ver):
    # TickEvent.ServerTickEvent.Post exists from Forge 50 (MC 1.20.6); 1.20.1 = Phase idiom
    forge_check_range(ver)
    return compat._vt(ver) >= (1, 20, 5)


def forge_record_events(ver):
    # Forge 60 (MC 1.21.10) reworked tick events into records: Post.server(), getServer() GONE
    forge_check_range(ver)
    return compat._vt(ver) >= (1, 21, 10)


def forge_modern_net_check(ver):
    forge_check_range(ver)
    if compat._vt(ver) < (1, 20, 5):
        raise ValueError(
            "compat_loaders: forge modern net twin is 1.20.5+ only, got %s "
            "(1.20.1 must use the forge_legacy_net flavour)" % ver)
    return ver


def forge_sleep_result_expr(ver):
    # EB6: result lives on the eventbus base Event class; EB7: common.util.Result
    if forge_eb7(ver):
        return "Result.ALLOW"
    return "Event.Result.ALLOW"


def forge_entry_imports(ver):
    if forge_eb7(ver):
        return ["import net.minecraftforge.common.util.Result;"]
    return ["import net.minecraftforge.common.MinecraftForge;",
            "import net.minecraftforge.eventbus.api.Event;"]


def forge_entry_wiring(ver):
    ind = "        "
    lines = []
    if forge_eb7(ver):
        acc = "server()" if forge_record_events(ver) else "getServer()"
        lines.append(ind + "TickEvent.ServerTickEvent.Post.BUS.addListener(e -> onServerTick(e.%s));" % acc)
        lines.append(ind + "ServerStartedEvent.BUS.addListener(e -> ENGINE.applyConfig(e.getServer()));")
        lines.append(ind + "SleepingTimeCheckEvent.BUS.addListener(this::onSleepingTimeCheck);")
        lines.append(ind + "RegisterCommandsEvent.BUS.addListener(this::onRegisterCommands);")
    elif forge_tick_post(ver):
        lines.append(ind + "MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post e) -> onServerTick(e.getServer()));")
        lines.append(ind + "MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> ENGINE.applyConfig(e.getServer()));")
        lines.append(ind + "MinecraftForge.EVENT_BUS.addListener(this::onSleepingTimeCheck);")
        lines.append(ind + "MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);")
    else:
        lines.append(ind + "MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {")
        lines.append(ind + "    if (e.phase == TickEvent.Phase.END) onServerTick(e.getServer());")
        lines.append(ind + "});")
        lines.append(ind + "MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> ENGINE.applyConfig(e.getServer()));")
        lines.append(ind + "MinecraftForge.EVENT_BUS.addListener(this::onSleepingTimeCheck);")
        lines.append(ind + "MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);")
    return lines


def forge_entities_receiver(ver):
    # vanilla receiver of the NOT_SAFE getEntitiesOfClass call in startSleepInBed
    forge_check_range(ver)
    return "ServerLevel" if compat._vt(ver) >= (1, 21, 6) else "Level"   # r2 smoketest: Forge 56/57 patch already ServerLevel-owned


def forge_entities_target(ver):
    cls = ("net/minecraft/server/level/ServerLevel"
           if forge_entities_receiver(ver) == "ServerLevel"
           else "net/minecraft/world/level/Level")
    return ("L%s;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;"
            "Ljava/util/function/Predicate;)Ljava/util/List;") % cls


if __name__ == "__main__":
    for v in ["1.20.6", "1.21.1", "1.21.2", "1.21.5", "1.21.9", "1.21.11"]:
        print("%-8s pred=%-38s sender=%s" % (v, sleep_pred(v), client_sender_ref(v)))
    for v in ["1.20.1", "1.20.6", "1.21.1", "1.21.5", "1.21.8", "1.21.10", "1.21.11"]:
        print("forge %-8s eb7=%-5s post=%-5s rec=%-5s result=%-18s rcv=%s" % (
            v, forge_eb7(v), forge_tick_post(v), forge_record_events(v),
            forge_sleep_result_expr(v), forge_entities_receiver(v)))
