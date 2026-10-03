package com.takumistudios.notifymod.server;

import com.takumistudios.notifymod.NotifyMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Nodos de permiso (§14.2) con la API de permisos de Fabric, que usa LuckPerms cuando está instalado. Sin gestor de
 * permisos se usa el nivel de OP de respaldo.
 */
public final class NotifyPermissions {
    public static final String COMMAND = "command";
    public static final String SEND = "send";
    public static final String HUD = "hud";
    public static final String SHOWCASE = "showcase";
    public static final String PLACEMENT = "showcase.placement";
    public static final String PRIORITY_HIGH = "priority.high";
    public static final String PRIORITY_CRITICAL = "priority.critical";
    public static final String CANCEL = "cancel";
    public static final String CLEAR = "clear";
    public static final String RELOAD = "reload";
    public static final String VALIDATE = "validate";
    public static final String RECEIVE_CHAT = "receive.chat";
    public static final String RECEIVE_HUD = "receive.hud";
    public static final String RECEIVE_SHOWCASE = "receive.showcase";

    private NotifyPermissions() {
    }

    public static Identifier node(String name) {
        return Identifier.fromNamespaceAndPath(NotifyMod.MOD_ID, name);
    }

    public static boolean check(CommandSourceStack source, String name, PermissionLevel fallback) {
        try {
            return source.checkPermission(node(name), fallback);
        } catch (RuntimeException e) {
            NotifyMod.LOGGER.warn("Error al comprobar el permiso {}", name, e);
            return false;
        }
    }

    /** Permisos de recepción: por defecto todos los tienen; quitárselos a un grupo lo deja sin ese canal. */
    public static boolean canReceive(ServerPlayer player, String name) {
        try {
            return player.checkPermission(node(name), PermissionLevel.ALL);
        } catch (RuntimeException e) {
            NotifyMod.LOGGER.warn("Error al comprobar el permiso {}", name, e);
            return true;
        }
    }
}

