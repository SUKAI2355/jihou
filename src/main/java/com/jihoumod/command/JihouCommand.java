package com.jihoumod.command;

import com.jihoumod.Jihou;
import com.jihoumod.JihouRuntime;
import com.jihoumod.config.ConfigException;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.util.function.Supplier;

public final class JihouCommand {
    private JihouCommand() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Supplier<JihouRuntime> runtime) {
        dispatcher.register(Commands.literal("jihou")
                .requires(Commands.hasPermission(Commands.LEVEL_MODERATORS))
                .then(Commands.literal("reload").executes(context -> {
                    JihouRuntime active = runtime.get();
                    if (active == null) {
                        context.getSource().sendFailure(Component.literal("[Jihou] Server is not ready."));
                        return 0;
                    }
                    try {
                        active.reload();
                        context.getSource().sendSuccess(() -> Component.literal("[Jihou] 設定をリロードしました。"), true);
                        return 1;
                    } catch (IOException | ConfigException ex) {
                        Jihou.LOGGER.warn("[Jihou] Reload failed: {}. Keeping the last valid configuration.", ex.getMessage());
                        context.getSource().sendFailure(Component.literal("[Jihou] " + ex.getMessage()
                                + " — リロード失敗。最後に正常だった設定を維持します。"));
                        return 0;
                    }
                })));
    }
}
