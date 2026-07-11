package com.todolistmod.exec;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 提供预定义的游戏变量值，通过 {@link MinecraftClient} 读取客户端状态。
 */
public class GameVariables {

    /** 获取游戏变量值，不存在或客户端不可用时返回 null */
    public static Object get(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return null;
        ClientPlayerEntity player = client.player;
        World world = player != null ? player.getWorld() : null;

        if (player == null || world == null) {
            // 仍可获取 FPS
            if ("world.fps".equals(name)) {
                return client.getCurrentFps();
            }
            return null;
        }

        switch (name) {
            case "player.health": return (int) player.getHealth();
            case "player.max_health": return (int) player.getMaxHealth();
            case "player.hunger": return player.getHungerManager().getFoodLevel();
            case "player.saturation": return (int) player.getHungerManager().getSaturationLevel();
            case "player.armor": return player.getArmor();
            case "player.x": return player.getBlockX();
            case "player.y": return player.getBlockY();
            case "player.z": return player.getBlockZ();
            case "player.xp_level": return player.experienceLevel;
            case "player.gamemode": return client.interactionManager != null
                    ? client.interactionManager.getCurrentGameMode().getName() : "UNKNOWN";
            case "player.sneaking": return player.isSneaking();
            case "player.sprinting": return player.isSprinting();
            case "player.on_ground": return player.isOnGround();
            case "player.in_water": return player.isTouchingWater();
            case "player.dimension": return world.getRegistryKey().getValue().toString();
            case "world.time": return (int) (world.getTimeOfDay() % 24000);
            case "world.day": return world.getTime() / 24000;  // 返回 long，避免 int 溢出
            case "world.weather":
                if (world.isThundering()) return "thunder";
                if (world.isRaining()) return "rain";
                return "clear";
            case "world.difficulty": return world.getDifficulty().getName();
            case "world.fps": return client.getCurrentFps();
            default: return null;
        }
    }

    /** 获取所有预定义变量名（用于文档/参考面板） */
    public static List<String> names() {
        List<String> list = new ArrayList<>();
        list.add("player.health");
        list.add("player.max_health");
        list.add("player.hunger");
        list.add("player.saturation");
        list.add("player.armor");
        list.add("player.x");
        list.add("player.y");
        list.add("player.z");
        list.add("player.xp_level");
        list.add("player.gamemode");
        list.add("player.sneaking");
        list.add("player.sprinting");
        list.add("player.on_ground");
        list.add("player.in_water");
        list.add("player.dimension");
        list.add("world.time");
        list.add("world.day");
        list.add("world.weather");
        list.add("world.difficulty");
        list.add("world.fps");
        return list;
    }
}
