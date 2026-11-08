package cn.frostcraft.firstjoinverification;

import org.geysermc.cumulus.form.CustomForm;

import java.util.function.Consumer;

/**
 * 生成供 Floodgate 或 Geyser API 发送的 Bedrock 原生验证码输入表单。
 */
final class BedrockFormFactory {

    private BedrockFormFactory() {
    }

    static CustomForm create(
            String title,
            String label,
            String placeholder,
            Consumer<String> onSubmit,
            Runnable onClosed
    ) {
        return CustomForm.builder()
                .title(title)
                .input(label, placeholder, "")
                .closedOrInvalidResultHandler(onClosed)
                .validResultHandler(response -> onSubmit.accept(response.asInput(0)))
                .build();
    }
}
