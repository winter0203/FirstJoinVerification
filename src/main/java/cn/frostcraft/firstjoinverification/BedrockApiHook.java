package cn.frostcraft.firstjoinverification;

import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.geyser.api.GeyserApi;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Geyser/Floodgate 可选 API 适配。
 * Cumulus CustomForm 在基岩版客户端显示为原生表单输入窗口。
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

    static boolean sendVerificationForm(
            UUID uuid,
            String title,
            String label,
            String placeholder,
            Consumer<String> onSubmit,
            Runnable onClosed
    ) {
        CustomForm form = CustomForm.builder()
                .title(title)
                .input(label, placeholder, "")
                .closedOrInvalidResultHandler(onClosed)
                .validResultHandler(response -> onSubmit.accept(response.asInput(0)))
                .build();

        // 首选 Floodgate；Geyser API 也提供相同的 Cumulus 表单发送能力。
        FloodgateApi floodgate = FloodgateApi.getInstance();
        if (floodgate != null && floodgate.isFloodgatePlayer(uuid)) {
            return floodgate.sendForm(uuid, form);
        }

        GeyserApi geyser = GeyserApi.api();
        return geyser != null && geyser.isBedrockPlayer(uuid) && geyser.sendForm(uuid, form);
    }
}
