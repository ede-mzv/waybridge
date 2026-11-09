package dev.waybridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Fabric 1.20.1 teleport commands for dedicated and integrated servers.
 * Does not replace /tp, FTB Essentials or Xaero. Only teleports the executor.
 */
public final class WaybridgeMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("waybridge");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("waybridge.json");
    private static final Path LEGACY_CONFIG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("servercito-teleport.json");
    private static final Map<UUID, Long> LAST_TELEPORT = new HashMap<>();
    private static Settings settings = new Settings();

    private static final class Settings {
        // UUIDs, NOT player names. Commands /stadmin grant/revoke maintain this list.
        Set<String> authorized_uuids = new LinkedHashSet<>();
        int cooldown_seconds = 10;
        // 0 means no distance restriction. Applies to /stwp only.
        int max_waypoint_distance = 0;
        int safe_search_horizontal_radius = 2;
        int safe_search_vertical_radius = 6;
    }

    @Override
    public void onInitialize() {
        try {
            settings = loadConfig();
        } catch (Exception ex) {
            // Fail closed: an unreadable config MUST NOT grant access.
            settings = new Settings();
            LOGGER.error("No se pudo leer {}. Acceso por UUID deshabilitado hasta corregirlo y usar /stadmin reload.", CONFIG_FILE, ex);
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("stp")
                .requires(source -> source.getPlayer() != null)
                .then(argument("jugador", EntityArgumentType.player())
                    .executes(ctx -> teleportToPlayer(ctx.getSource(),
                            EntityArgumentType.getPlayer(ctx, "jugador")))));

            dispatcher.register(literal("stwp")
                .requires(source -> source.getPlayer() != null)
                .then(argument("pos", Vec3ArgumentType.vec3())
                    .executes(ctx -> teleportToWaypoint(ctx.getSource(),
                            Vec3ArgumentType.getVec3(ctx, "pos")))));

            dispatcher.register(literal("stadmin")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("grant")
                    .then(argument("jugador", EntityArgumentType.player())
                        .executes(ctx -> setAuthorized(ctx.getSource(),
                                EntityArgumentType.getPlayer(ctx, "jugador"), true))))
                .then(literal("revoke")
                    .then(argument("jugador", EntityArgumentType.player())
                        .executes(ctx -> setAuthorized(ctx.getSource(),
                                EntityArgumentType.getPlayer(ctx, "jugador"), false))))
                .then(literal("list").executes(ctx -> showAllowed(ctx.getSource())))
                .then(literal("reload").executes(ctx -> reload(ctx.getSource()))));
        });

        LOGGER.info("Waybridge listo: /stp, /stwp, /stadmin. Config: {}", CONFIG_FILE);
    }

    private static boolean allowed(ServerCommandSource source, ServerPlayerEntity player) {
        return source.hasPermissionLevel(2)
                || settings.authorized_uuids.contains(player.getUuid().toString());
    }

    private static boolean canTeleport(ServerCommandSource source, ServerPlayerEntity player) {
        if (!allowed(source, player)) {
            source.sendError(Text.literal("No tienes permiso de Waybridge. Pide a un administrador que use /stadmin grant."));
            return false;
        }
        int seconds = Math.max(0, settings.cooldown_seconds);
        long now = System.currentTimeMillis();
        long last = LAST_TELEPORT.getOrDefault(player.getUuid(), 0L);
        long remaining = last + seconds * 1000L - now;
        if (remaining > 0) {
            source.sendError(Text.literal("Espera " + ((remaining + 999) / 1000) + " segundos antes del siguiente TP."));
            return false;
        }
        return true;
    }

    private static void markTeleport(ServerPlayerEntity player) {
        LAST_TELEPORT.put(player.getUuid(), System.currentTimeMillis());
        player.fallDistance = 0;
    }

    private static int teleportToPlayer(ServerCommandSource source, ServerPlayerEntity target)
            throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!canTeleport(source, player)) return 0;
        if (player.getUuid().equals(target.getUuid())) {
            source.sendError(Text.literal("Ya eres ese jugador."));
            return 0;
        }
        // Only moves the command executor, never the target. Cross-dimension is supported.
        boolean success = player.teleport(target.getServerWorld(), target.getX(), target.getY(), target.getZ(),
                Set.of(), player.getYaw(), player.getPitch());
        if (!success) {
            source.sendError(Text.literal("Minecraft rechazo el teletransporte."));
            return 0;
        }
        markTeleport(player);
        source.sendFeedback(() -> Text.literal("Te teletransportaste a " + target.getGameProfile().getName() + "."), false);
        return 1;
    }

    private static int teleportToWaypoint(ServerCommandSource source, Vec3d waypoint)
            throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!canTeleport(source, player)) return 0;
        ServerWorld world = player.getServerWorld();

        if (!Double.isFinite(waypoint.x) || !Double.isFinite(waypoint.y) || !Double.isFinite(waypoint.z)
                || !world.getWorldBorder().contains(waypoint.x, waypoint.z)) {
            source.sendError(Text.literal("Coordenadas fuera del borde del mundo."));
            return 0;
        }
        int limit = Math.max(0, settings.max_waypoint_distance);
        if (limit > 0 && player.squaredDistanceTo(waypoint) > (double) limit * limit) {
            source.sendError(Text.literal("El waypoint supera el limite configurado de " + limit + " bloques."));
            return 0;
        }
        if (waypoint.y < world.getBottomY() || waypoint.y >= world.getTopY()) {
            source.sendError(Text.literal("La altura del waypoint esta fuera del mundo."));
            return 0;
        }
        Vec3d safe = findSafeLanding(world, waypoint);
        if (safe == null) {
            source.sendError(Text.literal("No encontre un sitio seguro cerca del waypoint (suelo firme, 2 bloques libres)."));
            return 0;
        }
        boolean success = player.teleport(world, safe.x, safe.y, safe.z,
                Set.of(), player.getYaw(), player.getPitch());
        if (!success) {
            source.sendError(Text.literal("Minecraft rechazo el teletransporte."));
            return 0;
        }
        markTeleport(player);
        source.sendFeedback(() -> Text.literal(String.format(java.util.Locale.ROOT,
                "Waypoint: X %.1f, Y %.1f, Z %.1f (%s).", safe.x, safe.y, safe.z,
                world.getRegistryKey().getValue())), false);
        return 1;
    }

    /** Find a 2-block-high landing spot near the requested coordinate, in the CURRENT dimension. */
    private static Vec3d findSafeLanding(ServerWorld world, Vec3d requested) {
        int x0 = (int) Math.floor(requested.x);
        int y0 = (int) Math.floor(requested.y);
        int z0 = (int) Math.floor(requested.z);
        int horizontal = Math.max(0, Math.min(4, settings.safe_search_horizontal_radius));
        int vertical = Math.max(0, Math.min(12, settings.safe_search_vertical_radius));

        for (int radius = 0; radius <= horizontal; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int step = 0; step <= vertical * 2; step++) {
                        int dy = step == 0 ? 0 : ((step + 1) / 2) * (step % 2 == 1 ? 1 : -1);
                        BlockPos feet = new BlockPos(x0 + dx, y0 + dy, z0 + dz);
                        if (safeAt(world, feet)) {
                            return new Vec3d(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean safeAt(ServerWorld world, BlockPos feet) {
        if (feet.getY() <= world.getBottomY() || feet.getY() + 1 >= world.getTopY()) return false;
        if (!world.getWorldBorder().contains(feet)) return false;
        BlockPos below = feet.down();
        BlockPos head = feet.up();
        BlockState ground = world.getBlockState(below);
        if (!ground.isSideSolidFullSquare(world, below, Direction.UP)) return false;
        if (ground.isOf(Blocks.MAGMA_BLOCK) || ground.isOf(Blocks.CAMPFIRE)
                || ground.isOf(Blocks.SOUL_CAMPFIRE) || ground.isOf(Blocks.CACTUS)) return false;
        BlockState footState = world.getBlockState(feet);
        BlockState headState = world.getBlockState(head);
        return footState.getCollisionShape(world, feet).isEmpty()
                && headState.getCollisionShape(world, head).isEmpty()
                && footState.getFluidState().isEmpty()
                && headState.getFluidState().isEmpty();
    }

    private static int setAuthorized(ServerCommandSource source, ServerPlayerEntity target, boolean grant) {
        String uuid = target.getUuid().toString();
        boolean changed = grant ? settings.authorized_uuids.add(uuid)
                : settings.authorized_uuids.remove(uuid);
        if (changed) {
            try {
                saveConfig(settings);
            } catch (IOException error) {
                // Revert in-memory mutation if persistence fails.
                if (grant) settings.authorized_uuids.remove(uuid);
                else settings.authorized_uuids.add(uuid);
                LOGGER.error("Error guardando permisos", error);
                source.sendError(Text.literal("No pude guardar permisos: " + error.getMessage()));
                return 0;
            }
        }
        source.sendFeedback(() -> Text.literal((grant ? "Autorizado: " : "Revocado: ")
                + target.getGameProfile().getName() + " (" + uuid + ")"), true);
        return 1;
    }

    private static int showAllowed(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("UUID autorizados (" + settings.authorized_uuids.size()
                + "): " + String.join(", ", settings.authorized_uuids)), false);
        return 1;
    }

    private static int reload(ServerCommandSource source) {
        try {
            Settings reloaded = loadConfig();
            settings = reloaded;
            source.sendFeedback(() -> Text.literal("Configuracion de Waybridge recargada."), false);
            return 1;
        } catch (Exception error) {
            LOGGER.error("Error recargando la configuracion", error);
            source.sendError(Text.literal("Error en JSON: se mantiene la configuracion anterior. " + error.getMessage()));
            return 0;
        }
    }

    private static Settings loadConfig() throws IOException {
        // Waybridge config takes precedence. Migrate only when the new file does not exist.
        if (Files.exists(CONFIG_FILE)) {
            return readAndValidateConfig(CONFIG_FILE);
        }
        if (Files.exists(LEGACY_CONFIG_FILE)) {
            Settings migrated = readAndValidateConfig(LEGACY_CONFIG_FILE);
            // Preserve the original config as a rollback copy; never delete it.
            saveConfig(migrated);
            LOGGER.info("Permisos migrados de {} a {}. El archivo original se conserva.",
                    LEGACY_CONFIG_FILE, CONFIG_FILE);
            return migrated;
        }
        Settings fresh = new Settings();
        saveConfig(fresh);
        return fresh;
    }

    private static Settings readAndValidateConfig(Path path) throws IOException {
        Settings loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Settings.class);
        if (loaded == null || loaded.authorized_uuids == null) {
            throw new IOException("JSON vacio o sin authorized_uuids");
        }
        // Reject invalid IDs instead of treating them as access grants.
        for (String id : loaded.authorized_uuids) {
            if (id == null || !UUID.fromString(id).toString().equals(id)) {
                throw new IOException("UUID invalido en configuracion: " + id);
            }
        }
        loaded.cooldown_seconds = Math.max(0, Math.min(3600, loaded.cooldown_seconds));
        loaded.max_waypoint_distance = Math.max(0, loaded.max_waypoint_distance);
        loaded.safe_search_horizontal_radius = Math.max(0, Math.min(4, loaded.safe_search_horizontal_radius));
        loaded.safe_search_vertical_radius = Math.max(0, Math.min(12, loaded.safe_search_vertical_radius));
        return loaded;
    }

    private static void saveConfig(Settings next) throws IOException {
        Files.createDirectories(CONFIG_FILE.getParent());
        Path temp = CONFIG_FILE.resolveSibling(CONFIG_FILE.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(next), StandardCharsets.UTF_8);
        try {
            Files.move(temp, CONFIG_FILE, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temp, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
