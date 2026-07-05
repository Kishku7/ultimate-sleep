# compat.py -- ultimate-sleep drift brain (ASCII only).
# Ground truth: Temp/usleep-backport/era-boundaries.md (grepped from decompiled MC-Java trees).
# Boundaries: 1.20.5 components/JDK21/modern-net; 1.21 RL.fromNamespaceAndPath; 1.21.2 ServerLevel
# furnace param + 2-arg isPreventingPlayerRest; 1.21.5 isBrightOutside + SPEED + RespawnConfig(B);
# 1.21.8 EditBox.setCentered; 1.21.11 Identifier/gamerules-pkg/Permissions/BedRule/record-problem/
# RespawnData(C); 26 sendOverlayMessage + ServerClockManager.

def _vt(ver):
    core = str(ver).split("-")[0]
    return tuple(int(x) for x in core.split("."))

def is26(v):        return _vt(v)[0] >= 26
def renamed(v):     return _vt(v) >= (1, 21, 11)          # includes 26
def has_bright(v):  return _vt(v) >= (1, 21, 5)
def sl2(v):         return _vt(v) >= (1, 21, 2)           # ServerLevel furnace param, 2-arg isPreventingPlayerRest
def modern_fx(v):   return _vt(v) >= (1, 20, 5)           # Holder<MobEffect>, data components
def modern_net(v):  return _vt(v) >= (1, 20, 5)           # CustomPacketPayload records + StreamCodec
def rl_factory(v):  return _vt(v) >= (1, 21)              # ResourceLocation.fromNamespaceAndPath
def scroll4(v):     return _vt(v) >= (1, 20, 4)           # mouseScrolled 4 doubles
def centered(v):    return _vt(v) >= (1, 21, 8)           # EditBox.setCentered
def java17(v):      return _vt(v) < (1, 20, 5)

def respawn_era(v):
    t = _vt(v)
    if t >= (1, 21, 9): return "C"   # RespawnData(GlobalPos,yaw,pitch) verified at 1.21.9, not 1.21.11
    if t >= (1, 21, 5):  return "B"
    return "A"

def id_cls(v):      return "Identifier" if renamed(v) else "ResourceLocation"
def id_import(v):   return "net.minecraft.resources." + id_cls(v)

def make_id(v, ns_expr, path_expr):
    if renamed(v):    return "Identifier.fromNamespaceAndPath(%s, %s)" % (ns_expr, path_expr)
    if rl_factory(v): return "ResourceLocation.fromNamespaceAndPath(%s, %s)" % (ns_expr, path_expr)
    return "new ResourceLocation(%s, %s)" % (ns_expr, path_expr)

def bright_call(v, level_expr):
    return level_expr + (".isBrightOutside()" if has_bright(v) else ".isDay()")

# ---- Era.java bodies ----

def era_id_method(v):
    return [
        "    public static %s id(String path) {" % id_cls(v),
        "        return %s;" % make_id(v, '"ultimate_sleep"', "path"),
        "    }",
    ]

def era_overlay_body(v):
    if is26(v): return ["        p.sendOverlayMessage(msg);"]
    return ["        p.displayClientMessage(msg, true);"]

def era_speed_field(v):
    return "MobEffects.SPEED" if has_bright(v) else "MobEffects.MOVEMENT_SPEED"

def era_perm_body(v, tier):
    if renamed(v):
        which = "COMMANDS_GAMEMASTER" if tier == 2 else "COMMANDS_ADMIN"
        return ["        return src.permissions().hasPermission(Permissions.%s);" % which]
    return ["        return src.hasPermission(%d);" % tier]

def era_respawn_body(v):
    e = respawn_era(v)
    if e == "C":
        return [
            "        var rc = p.getRespawnConfig();",
            "        if (rc == null || rc.respawnData() == null) return null;",
            "        return rc.respawnData().globalPos();",
        ]
    if e == "B":
        return [
            "        var rc = p.getRespawnConfig();",
            "        if (rc == null || rc.pos() == null) return null;",
            "        return GlobalPos.of(rc.dimension(), rc.pos());",
        ]
    return [
        "        if (p.getRespawnPosition() == null) return null;",
        "        return GlobalPos.of(p.getRespawnDimension(), p.getRespawnPosition());",
    ]

def era_problem_body(v):
    return ["        return problem.%s;" % ("message()" if renamed(v) else "getMessage()")]

def era_ticks_body(v):
    src = "ow.getOverworldClockTime()" if is26(v) else "ow.getDayTime()"
    return [
        "        long r = 24000L - (%s %% 24000L);" % src,
        "        return r < 1 ? 1 : r;",
    ]

def era_setrate_body(v):
    if is26(v):
        return ["        ow.dimensionType().defaultClock().ifPresent(clock -> server.clockManager().setRate(clock, rate));"]
    return ["        // pre-26: no clock-rate API; accelStep advances dayTime per tick instead."]

def era_accelstep_body(v):
    if is26(v):
        return ["        // 26: nothing -- the clock rate set in setClockRate advances time."]
    return ["        ow.setDayTime(ow.getDayTime() + Math.max(1L, (long) rate));"]

# ---- mixin pieces ----

def gamerules_import(v):
    pkg = "net.minecraft.world.level.gamerules.GameRules" if renamed(v) else "net.minecraft.world.level.GameRules"
    return "import %s;" % pkg

def progression_anchor(v):
    # UNIVERSAL anchor (2026-07-05 smoketest fix): NeoForge patches ServerLevel.tick's sleep block,
    # so the vanilla moveToTimeMarker/setDayTime INVOKEs are not reliably present there. The
    # wakeUpAllPlayers() call survives every loader's patching on every version, and injecting
    # before it preserves semantics (ticksSlept math uses gameTime, which the skip never touches).
    return "Lnet/minecraft/server/level/ServerLevel;wakeUpAllPlayers()V"

def rndtick_get(v):
    if renamed(v): return "self.getGameRules().get(GameRules.RANDOM_TICK_SPEED)"
    return "self.getGameRules().getInt(GameRules.RULE_RANDOMTICKING)"

def rndtick_set(v, val_expr):
    if renamed(v): return "self.getGameRules().set(GameRules.RANDOM_TICK_SPEED, %s, self.getServer());" % val_expr
    return "self.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(%s, self.getServer());" % val_expr

def sleep_gate_target(v):
    if renamed(v):    return "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"
    if has_bright(v): return "Lnet/minecraft/world/level/Level;isBrightOutside()Z"
    return "Lnet/minecraft/world/level/Level;isDay()Z"

def furnace_level_type(v):
    return "ServerLevel" if sl2(v) else "Level"

def furnace_descriptor(v):
    lvl = "Lnet/minecraft/server/level/ServerLevel;" if sl2(v) else "Lnet/minecraft/world/level/Level;"
    return ("serverTick(%sLnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;"
            "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;)V") % lvl

def mouse_event(v):  return _vt(v) >= (1, 21, 9)      # AbstractWidget.onClick(MouseButtonEvent,boolean)

def fab_c2s(v):      return "serverboundPlay" if is26(v) else "playC2S"
def fab_s2c(v):      return "clientboundPlay" if is26(v) else "playS2C"

if __name__ == "__main__":
    for ver in ["1.20.1", "1.20.4", "1.20.6", "1.21.1", "1.21.2", "1.21.5", "1.21.8", "1.21.11", "26.1"]:
        print("%-8s id=%-40s bright=%-5s sl2=%-5s respawn=%s perms=%-5s anchor=%s" % (
            ver, make_id(ver, "NS", "P"), has_bright(ver), sl2(ver), respawn_era(ver),
            renamed(ver), "clock" if is26(ver) else "setDayTime"))
