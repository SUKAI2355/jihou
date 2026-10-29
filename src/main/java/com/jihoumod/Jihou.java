package com.jihoumod;

import com.jihoumod.command.JihouCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Jihou implements ModInitializer {
	public static final String MOD_ID = "jihou";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private JihouRuntime runtime;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
				JihouCommand.register(dispatcher, () -> runtime));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			runtime = new JihouRuntime(FabricLoader.getInstance().getConfigDir().resolve("jihou.conf"));
			runtime.start();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (runtime != null) runtime.tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> runtime = null);
	}
}
