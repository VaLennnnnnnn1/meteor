package com.macepower.addon;

import com.macepower.addon.modules.MacePower;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class MacePowerAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Mace Power");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Mace Power Addon");
        Modules.get().add(new MacePower());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.macepower.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("open-code", "mace-power-addon");
    }
}
