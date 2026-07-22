package com.github.epsilon.utils.misc;

import com.github.epsilon.Constants;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.LevelUpdateEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.holders.ModuleHolder;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.Setting;
import net.minecraft.IdentifierException;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.meteordev.starscript.Script;
import org.meteordev.starscript.Section;
import org.meteordev.starscript.StandardLib;
import org.meteordev.starscript.Starscript;
import org.meteordev.starscript.compiler.Compiler;
import org.meteordev.starscript.compiler.Parser;
import org.meteordev.starscript.utils.Error;
import org.meteordev.starscript.utils.StarscriptError;
import org.meteordev.starscript.value.Value;
import org.meteordev.starscript.value.ValueMap;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.github.epsilon.Constants.mc;

/**
 * 基于 Meteor Client 的 MeteorStarscript 移植，变量命名与原 Text HUD 保持兼容。
 */
public final class EpsilonStarscript {

    public static final Starscript ENGINE = new Starscript();
    private static final EpsilonStarscript INSTANCE = new EpsilonStarscript();
    private static final BlockPos.MutableBlockPos MUTABLE_POS = new BlockPos.MutableBlockPos();
    private static boolean initialized;
    private static long lastRequestedStatsTime;
    private static long lastServerTickTime;
    private static double serverTps = 20.0;

    private EpsilonStarscript() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;

        StandardLib.init(ENGINE);
        registerGeneralValues();
        registerClientValues();
        registerCameraValues();
        registerPlayerValues();
        registerCrosshairValues();
        registerServerValues();
        EventBus.INSTANCE.subscribe(INSTANCE);
    }

    private static void registerGeneralValues() {
        ENGINE.set("mc_version", SharedConstants.getCurrentVersion().name());
        ENGINE.set("fps", () -> Value.number(mc.getFps()));
        ENGINE.set("ping", EpsilonStarscript::ping);
        ENGINE.set("time", () -> Value.string(LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))));
    }

    private static void registerClientValues() {
        ValueMap client = new ValueMap()
                .set("name", Constants.NAME)
                .set("version", Constants.VERSION)
                .set("modules", () -> Value.number(ModuleHolder.INSTANCE.getModules().size()))
                .set("active_modules", () -> Value.number(ModuleHolder.INSTANCE.getModules().stream().filter(Module::isEnabled).count()))
                .set("is_module_active", EpsilonStarscript::isModuleActive)
                .set("get_module_info", EpsilonStarscript::getModuleInfo)
                .set("get_module_setting", EpsilonStarscript::getModuleSetting);

        ENGINE.set("epsilon", client);
        // 保留 Meteor 名称别名，使原有 Text HUD 脚本可以直接迁移。
        ENGINE.set("meteor", client);
    }

    private static void registerCameraValues() {
        ENGINE.set("camera", new ValueMap()
                .set("pos", new ValueMap()
                        .set("_toString", () -> posString(false, true))
                        .set("x", () -> Value.number(mc.gameRenderer.getMainCamera().position().x))
                        .set("y", () -> Value.number(mc.gameRenderer.getMainCamera().position().y))
                        .set("z", () -> Value.number(mc.gameRenderer.getMainCamera().position().z)))
                .set("opposite_dim_pos", new ValueMap()
                        .set("_toString", () -> posString(true, true))
                        .set("x", () -> oppositeCoordinate(true, true))
                        .set("y", () -> Value.number(mc.gameRenderer.getMainCamera().position().y))
                        .set("z", () -> oppositeCoordinate(false, true)))
                .set("yaw", () -> yaw(true))
                .set("pitch", () -> pitch(true))
                .set("direction", () -> direction(true)));
    }

    private static void registerPlayerValues() {
        ENGINE.set("player", new ValueMap()
                .set("_toString", () -> Value.string(mc.getUser().getName()))
                .set("health", () -> Value.number(mc.player != null ? mc.player.getHealth() : 0))
                .set("absorption", () -> Value.number(mc.player != null ? mc.player.getAbsorptionAmount() : 0))
                .set("hunger", () -> Value.number(mc.player != null ? mc.player.getFoodData().getFoodLevel() : 0))
                .set("saturation", () -> Value.number(mc.player != null ? mc.player.getFoodData().getSaturationLevel() : 0))
                .set("speed", () -> Value.number(playerSpeed().horizontalDistance()))
                .set("speed_all", new ValueMap()
                        .set("_toString", () -> Value.string(playerSpeed().toString()))
                        .set("x", () -> Value.number(playerSpeed().x))
                        .set("y", () -> Value.number(playerSpeed().y))
                        .set("z", () -> Value.number(playerSpeed().z)))
                .set("breaking_progress", () -> Value.number(mc.gameMode != null && mc.gameMode.isDestroying() ? mc.gameMode.destroyProgress : 0))
                .set("biome", EpsilonStarscript::biome)
                .set("dimension", () -> Value.string(dimension().id))
                .set("opposite_dimension", () -> Value.string(dimension().opposite().id))
                .set("gamemode", EpsilonStarscript::gameMode)
                .set("pos", new ValueMap()
                        .set("_toString", () -> posString(false, false))
                        .set("x", () -> Value.number(mc.player != null ? mc.player.getX() : 0))
                        .set("y", () -> Value.number(mc.player != null ? mc.player.getY() : 0))
                        .set("z", () -> Value.number(mc.player != null ? mc.player.getZ() : 0)))
                .set("opposite_dim_pos", new ValueMap()
                        .set("_toString", () -> posString(true, false))
                        .set("x", () -> oppositeCoordinate(true, false))
                        .set("y", () -> Value.number(mc.player != null ? mc.player.getY() : 0))
                        .set("z", () -> oppositeCoordinate(false, false)))
                .set("yaw", () -> yaw(false))
                .set("pitch", () -> pitch(false))
                .set("direction", () -> direction(false))
                .set("hand", () -> mc.player != null ? wrap(mc.player.getMainHandItem()) : Value.null_())
                .set("offhand", () -> mc.player != null ? wrap(mc.player.getOffhandItem()) : Value.null_())
                .set("hand_or_offhand", EpsilonStarscript::handOrOffhand)
                .set("get_item", EpsilonStarscript::getItem)
                .set("count_items", EpsilonStarscript::countItems)
                .set("xp", new ValueMap()
                        .set("level", () -> Value.number(mc.player != null ? mc.player.experienceLevel : 0))
                        .set("progress", () -> Value.number(mc.player != null ? mc.player.experienceProgress : 0))
                        .set("total", () -> Value.number(mc.player != null ? mc.player.totalExperience : 0)))
                .set("has_potion_effect", EpsilonStarscript::hasPotionEffect)
                .set("get_potion_effect", EpsilonStarscript::getPotionEffect)
                .set("get_stat", EpsilonStarscript::getStat));
    }

    private static void registerCrosshairValues() {
        ENGINE.set("crosshair_target", new ValueMap()
                .set("type", EpsilonStarscript::crosshairType)
                .set("value", EpsilonStarscript::crosshairValue));
    }

    private static void registerServerValues() {
        ENGINE.set("server", new ValueMap()
                .set("_toString", EpsilonStarscript::worldName)
                .set("tps", () -> Value.number(serverTps))
                .set("time", EpsilonStarscript::worldTime)
                .set("weather", EpsilonStarscript::weather)
                .set("player_count", () -> Value.number(mc.getConnection() != null ? mc.getConnection().getOnlinePlayers().size() : 0))
                .set("difficulty", () -> Value.string(mc.level != null ? mc.level.getDifficulty().getSerializedName() : "")));
    }

    public static Script compile(String source) {
        Parser.Result result = Parser.parse(source == null ? "" : source);
        if (result.hasErrors()) {
            for (Error error : result.errors) Constants.LOGGER.error("Starscript compilation error: {}", error);
            return null;
        }
        return Compiler.compile(result);
    }

    public static Section runSection(Script script) {
        if (script == null) return null;
        try {
            return ENGINE.run(script);
        } catch (StarscriptError error) {
            Constants.LOGGER.error("Starscript runtime error: {}", error.getMessage());
            return null;
        }
    }

    public static String run(Script script) {
        Section section = runSection(script);
        return section == null ? null : section.toString();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundSetTimePacket)) return;

        long now = System.currentTimeMillis();
        if (lastServerTickTime > 0) {
            long elapsed = now - lastServerTickTime;
            if (elapsed > 0) {
                double measured = 20_000.0 / elapsed;
                serverTps = Mth.clamp(serverTps * 0.7 + measured * 0.3, 0.0, 20.0);
            }
        }
        lastServerTickTime = now;
    }

    @EventHandler
    private void onLevelUpdate(LevelUpdateEvent event) {
        lastServerTickTime = 0;
        serverTps = 20.0;
    }

    private static Value hasPotionEffect(Starscript script, int argCount) {
        if (argCount < 1) script.error("player.has_potion_effect() requires 1 argument, got %d.", argCount);
        if (mc.player == null) return Value.bool(false);

        Identifier id = popIdentifier(script, "First argument to player.has_potion_effect() needs to be a string.");
        Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.get(id);
        return Value.bool(effect.isPresent() && mc.player.getEffect(effect.get()) != null);
    }

    private static Value getPotionEffect(Starscript script, int argCount) {
        if (argCount < 1) script.error("player.get_potion_effect() requires 1 argument, got %d.", argCount);
        if (mc.player == null) return Value.null_();

        Identifier id = popIdentifier(script, "First argument to player.get_potion_effect() needs to be a string.");
        Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.get(id);
        if (effect.isEmpty()) return Value.null_();
        MobEffectInstance instance = mc.player.getEffect(effect.get());
        return instance == null ? Value.null_() : wrap(instance);
    }

    private static Value getStat(Starscript script, int argCount) {
        if (argCount < 1) script.error("player.get_stat() requires at least 1 argument, got %d.", argCount);
        if (mc.player == null) return Value.number(0);

        long now = System.currentTimeMillis();
        if (now - lastRequestedStatsTime >= 1_000 && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
            lastRequestedStatsTime = now;
        }

        String type = argCount > 1 ? script.popString("First argument to player.get_stat() needs to be a string.") : "custom";
        Identifier id = popIdentifier(script, (argCount > 1 ? "Second" : "First") + " argument to player.get_stat() needs to be a string.");
        Stat<?> stat = switch (type) {
            case "mined" -> Stats.BLOCK_MINED.get(BuiltInRegistries.BLOCK.getValue(id));
            case "crafted" -> Stats.ITEM_CRAFTED.get(BuiltInRegistries.ITEM.getValue(id));
            case "used" -> Stats.ITEM_USED.get(BuiltInRegistries.ITEM.getValue(id));
            case "broken" -> Stats.ITEM_BROKEN.get(BuiltInRegistries.ITEM.getValue(id));
            case "picked_up" -> Stats.ITEM_PICKED_UP.get(BuiltInRegistries.ITEM.getValue(id));
            case "dropped" -> Stats.ITEM_DROPPED.get(BuiltInRegistries.ITEM.getValue(id));
            case "killed" -> Stats.ENTITY_KILLED.get(BuiltInRegistries.ENTITY_TYPE.getValue(id));
            case "killed_by" -> Stats.ENTITY_KILLED_BY.get(BuiltInRegistries.ENTITY_TYPE.getValue(id));
            case "custom" -> {
                Identifier custom = BuiltInRegistries.CUSTOM_STAT.getValue(id);
                yield custom == null ? null : Stats.CUSTOM.get(custom);
            }
            default -> null;
        };
        return Value.number(stat == null ? 0 : mc.player.getStats().getValue(stat));
    }

    private static Value isModuleActive(Starscript script, int argCount) {
        if (argCount != 1) script.error("epsilon.is_module_active() requires 1 argument, got %d.", argCount);
        Module module = findModule(script.popString("First argument needs to be a module name."));
        return Value.bool(module != null && module.isEnabled());
    }

    private static Value getModuleInfo(Starscript script, int argCount) {
        if (argCount != 1) script.error("epsilon.get_module_info() requires 1 argument, got %d.", argCount);
        Module module = findModule(script.popString("First argument needs to be a module name."));
        if (module == null || !module.isEnabled()) return Value.string("");
        String info = module.getInfo();
        return Value.string(info == null ? "" : info);
    }

    private static Value getModuleSetting(Starscript script, int argCount) {
        if (argCount != 2) script.error("epsilon.get_module_setting() requires 2 arguments, got %d.", argCount);

        String settingName = script.popString("Second argument needs to be a setting name.");
        String moduleName = script.popString("First argument needs to be a module name.");
        Module module = findModule(moduleName);
        if (module == null) script.error("Unable to find module %s.", moduleName);

        Setting<?> setting = module.getSettings().stream()
                .filter(candidate -> candidate.getName().equalsIgnoreCase(settingName))
                .findFirst()
                .orElse(null);
        if (setting == null) script.error("Unable to find setting %s in module %s.", settingName, moduleName);

        Object value = setting.getValue();
        return switch (value) {
            case Number number -> Value.number(number.doubleValue());
            case Boolean bool -> Value.bool(bool);
            case List<?> list -> Value.number(list.size());
            case null -> Value.null_();
            default -> Value.string(value.toString());
        };
    }

    private static Module findModule(String name) {
        if (name == null) return null;
        String normalized = normalizeName(name);
        return ModuleHolder.INSTANCE.getModules().stream()
                .filter(module -> normalizeName(module.getName()).equals(normalized)
                        || normalizeName(module.getTranslatedName()).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private static String normalizeName(String value) {
        return value.replace("_", "").replace("-", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private static Value getItem(Starscript script, int argCount) {
        if (argCount != 1) script.error("player.get_item() requires 1 argument, got %d.", argCount);
        int slot = (int) script.popNumber("First argument needs to be a slot number.");
        if (slot < 0) script.error("Slot number must be non-negative.");
        if (mc.player == null || slot >= mc.player.getInventory().getContainerSize()) return Value.null_();
        return wrap(mc.player.getInventory().getItem(slot));
    }

    private static Value countItems(Starscript script, int argCount) {
        if (argCount != 1) script.error("player.count_items() requires 1 argument, got %d.", argCount);
        Identifier id = Identifier.tryParse(script.popString("First argument needs to be an item identifier."));
        if (id == null || mc.player == null) return Value.number(0);
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == Items.AIR) return Value.number(0);

        int count = 0;
        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return Value.number(count);
    }

    private static Value biome() {
        if (mc.player == null || mc.level == null) return Value.string("");
        MUTABLE_POS.set(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        return mc.level.registryAccess().lookup(Registries.BIOME)
                .map(registry -> {
                    Identifier id = registry.getKey(mc.level.getBiome(MUTABLE_POS).value());
                    return Value.string(id == null ? "Unknown" : prettify(id.getPath()));
                })
                .orElse(Value.string("Unknown"));
    }

    private static Value gameMode() {
        if (mc.player == null || mc.getConnection() == null) return Value.null_();
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        GameType mode = info == null ? null : info.getGameMode();
        return mode == null ? Value.null_() : Value.string(prettify(mode.getName()));
    }

    private static Value handOrOffhand() {
        if (mc.player == null) return Value.null_();
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) stack = mc.player.getOffhandItem();
        return wrap(stack);
    }

    private static Value crosshairType() {
        if (mc.hitResult == null) return Value.string("miss");
        return Value.string(switch (mc.hitResult.getType()) {
            case MISS -> "miss";
            case BLOCK -> "block";
            case ENTITY -> "entity";
        });
    }

    private static Value crosshairValue() {
        if (mc.level == null || mc.hitResult == null) return Value.null_();
        if (mc.hitResult.getType() == HitResult.Type.MISS) return Value.string("");
        if (mc.hitResult instanceof BlockHitResult hit) {
            return wrap(hit.getBlockPos(), mc.level.getBlockState(hit.getBlockPos()));
        }
        return wrap(((EntityHitResult) mc.hitResult).getEntity());
    }

    private static Value weather() {
        if (mc.level == null) return Value.string("");
        return Value.string(mc.level.isThundering() ? "Thunder" : mc.level.isRaining() ? "Rain" : "Clear");
    }

    private static Value worldName() {
        if (mc.getSingleplayerServer() != null) {
            return Value.string(mc.getSingleplayerServer().getWorldData().getLevelName());
        }
        ServerData server = mc.getCurrentServer();
        return Value.string(server == null ? "" : server.ip);
    }

    private static Value worldTime() {
        if (mc.level == null) return Value.string("");
        long ticks = Math.floorMod(mc.level.getOverworldClockTime(), 24_000L);
        long minutes = (ticks * 60L / 1_000L + 360L) % 1_440L;
        return Value.string(String.format(Locale.ROOT, "%02d:%02d", minutes / 60L, minutes % 60L));
    }

    private static Value ping() {
        if (mc.getConnection() == null || mc.player == null) return Value.number(0);
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return Value.number(info == null ? 0 : info.getLatency());
    }

    private static Vec3 playerSpeed() {
        return mc.player == null ? Vec3.ZERO : mc.player.getKnownSpeed();
    }

    private static Dimension dimension() {
        if (mc.level == null) return Dimension.Overworld;
        if (mc.level.dimension() == Level.NETHER) return Dimension.Nether;
        if (mc.level.dimension() == Level.END) return Dimension.End;
        return Dimension.Overworld;
    }

    private static Value oppositeCoordinate(boolean xAxis, boolean camera) {
        Vec3 position = camera ? mc.gameRenderer.getMainCamera().position()
                : mc.player == null ? Vec3.ZERO : mc.player.position();
        double value = xAxis ? position.x : position.z;
        if (dimension() == Dimension.Overworld) value /= 8.0;
        else if (dimension() == Dimension.Nether) value *= 8.0;
        return Value.number(value);
    }

    private static Value yaw(boolean camera) {
        float value = camera ? mc.gameRenderer.getMainCamera().yRot() : mc.player != null ? mc.player.getYRot() : 0;
        return Value.number(Mth.wrapDegrees(value));
    }

    private static Value pitch(boolean camera) {
        float value = camera ? mc.gameRenderer.getMainCamera().xRot() : mc.player != null ? mc.player.getXRot() : 0;
        return Value.number(Mth.wrapDegrees(value));
    }

    private static Value direction(boolean camera) {
        float value = camera ? mc.gameRenderer.getMainCamera().yRot() : mc.player != null ? mc.player.getYRot() : 0;
        return wrap(HorizontalDirection.fromYaw(value));
    }

    private static Value posString(boolean opposite, boolean camera) {
        Vec3 position = camera ? mc.gameRenderer.getMainCamera().position()
                : mc.player == null ? Vec3.ZERO : mc.player.position();
        double x = position.x;
        double z = position.z;
        if (opposite) {
            if (dimension() == Dimension.Overworld) {
                x /= 8.0;
                z /= 8.0;
            } else if (dimension() == Dimension.Nether) {
                x *= 8.0;
                z *= 8.0;
            }
        }
        return posString(x, position.y, z);
    }

    private static Value posString(double x, double y, double z) {
        return Value.string(String.format(Locale.ROOT, "X: %.0f Y: %.0f Z: %.0f", x, y, z));
    }

    public static Identifier popIdentifier(Starscript script, String errorMessage) {
        try {
            return Identifier.parse(script.popString(errorMessage));
        } catch (IdentifierException exception) {
            script.error(exception.getMessage());
            return null;
        }
    }

    public static Value wrap(ItemStack stack) {
        String name = stack.isEmpty() ? "" : stack.getHoverName().getString();
        int durability = stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : 0;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return Value.map(new ValueMap()
                .set("_toString", Value.string(stack.getCount() <= 1 ? name : String.format(Locale.ROOT, "%s %dx", name, stack.getCount())))
                .set("name", Value.string(name))
                .set("id", Value.string(id == null ? "" : id.toString()))
                .set("count", Value.number(stack.getCount()))
                .set("durability", Value.number(durability))
                .set("max_durability", Value.number(stack.getMaxDamage())));
    }

    public static Value wrap(BlockPos pos, BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return Value.map(new ValueMap()
                .set("_toString", Value.string(state.getBlock().getName().getString()))
                .set("id", Value.string(id == null ? "" : id.toString()))
                .set("pos", Value.map(new ValueMap()
                        .set("_toString", posString(pos.getX(), pos.getY(), pos.getZ()))
                        .set("x", Value.number(pos.getX()))
                        .set("y", Value.number(pos.getY()))
                        .set("z", Value.number(pos.getZ())))));
    }

    public static Value wrap(Entity entity) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return Value.map(new ValueMap()
                .set("_toString", Value.string(entity.getName().getString()))
                .set("id", Value.string(id == null ? "" : id.toString()))
                .set("health", Value.number(entity instanceof LivingEntity living ? living.getHealth() : 0))
                .set("absorption", Value.number(entity instanceof LivingEntity living ? living.getAbsorptionAmount() : 0))
                .set("pos", Value.map(new ValueMap()
                        .set("_toString", posString(entity.getX(), entity.getY(), entity.getZ()))
                        .set("x", Value.number(entity.getX()))
                        .set("y", Value.number(entity.getY()))
                        .set("z", Value.number(entity.getZ())))));
    }

    public static Value wrap(MobEffectInstance effect) {
        return Value.map(new ValueMap()
                .set("duration", effect.getDuration())
                .set("level", effect.getAmplifier() + 1));
    }

    private static Value wrap(HorizontalDirection direction) {
        return Value.map(new ValueMap()
                .set("_toString", Value.string(direction.displayName + " " + direction.axis))
                .set("name", Value.string(direction.displayName))
                .set("axis", Value.string(direction.axis)));
    }

    private static String prettify(String value) {
        String[] parts = value.split("[_ ]");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private enum Dimension {
        Overworld("minecraft:overworld"),
        Nether("minecraft:the_nether"),
        End("minecraft:the_end");

        private final String id;

        Dimension(String id) {
            this.id = id;
        }

        private Dimension opposite() {
            return switch (this) {
                case Overworld -> Nether;
                case Nether -> Overworld;
                case End -> End;
            };
        }
    }

    private enum HorizontalDirection {
        South("South", "Z+"),
        SouthWest("South West", "X- Z+"),
        West("West", "X-"),
        NorthWest("North West", "X- Z-"),
        North("North", "Z-"),
        NorthEast("North East", "X+ Z-"),
        East("East", "X+"),
        SouthEast("South East", "X+ Z+");

        private final String displayName;
        private final String axis;

        HorizontalDirection(String displayName, String axis) {
            this.displayName = displayName;
            this.axis = axis;
        }

        private static HorizontalDirection fromYaw(float yaw) {
            int index = Mth.floor(Mth.wrapDegrees(yaw) / 45.0f + 0.5f) & 7;
            return values()[index];
        }
    }
}
