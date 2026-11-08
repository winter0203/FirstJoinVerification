package cn.frostcraft.firstjoinverification;

import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Floodgate API 适配层。只在服务端检测到 Floodgate 已安装时调用。
 */
final class FloodgateApiHook {

    private FloodgateApiHook() {
    }

    static boolean isFloodgatePlayer(UUID uuid) {
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }

    static boolean sendVerificationForm(
            UUID uuid,
            String title,
            String label,
            String placeholder,
            Consumer<String> onSubmit,
            Runnable onClosed
    ) {
        FloodgateApi api = FloodgateApi.getInstance();
        if (api == null || !api.isFloodgatePlayer(uuid)) {
            return false;
        }

        CustomForm form = BedrockFormFactory.create(title, label, placeholder, onSubmit, onClosed);
        return api.sendForm(uuid, form);
    }
}
