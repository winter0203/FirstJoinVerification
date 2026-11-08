package cn.frostcraft.firstjoinverification;

import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.cumulus.form.CustomForm;

import java.util.UUID;

/**
 * Geyser API 适配层。
 * 与 Floodgate API 分离，确保只安装 Geyser 时也能发送基岩版表单。
 */
final class BedrockApiHook {

    private BedrockApiHook() {
    }

    static boolean isGeyserPlayer(UUID uuid) {
        GeyserApi api = GeyserApi.api();
        return api != null && api.connectionByUuid(uuid) != null;
    }

    static boolean sendVerificationForm(
            UUID uuid,
            String title,
            String label,
            String placeholder,
            java.util.function.Consumer<String> onSubmit,
            Runnable onClosed
    ) {
        GeyserApi api = GeyserApi.api();
        if (api == null || !api.isBedrockPlayer(uuid)) {
            return false;
        }

        CustomForm form = BedrockFormFactory.create(title, label, placeholder, onSubmit, onClosed);
        return api.sendForm(uuid, form);
    }
}
