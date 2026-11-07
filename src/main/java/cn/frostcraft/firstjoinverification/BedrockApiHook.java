package cn.frostcraft.firstjoinverification;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;

import java.util.UUID;

/**
 * 可选的 Geyser/Floodgate API 适配层。
 * 单独放在此类中，主插件在对应服务端插件存在时才调用它。
 */
final class BedrockApiHook {

    private BedrockApiHook() {
    }

    static boolean isFloodgatePlayer(UUID uuid) {
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }

    static boolean isGeyserPlayer(UUID uuid) {
        GeyserApi api = GeyserApi.api();
        return api != null && api.connectionByUuid(uuid) != null;
    }
}
