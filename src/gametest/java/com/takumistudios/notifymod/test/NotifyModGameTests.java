package com.takumistudios.notifymod.test;

import com.takumistudios.notifymod.NotifyMod;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

/** Gametests de servidor: ./gradlew :26.3:runGameTest (y con -PcompatPack en la CI). */
public class NotifyModGameTests {
    @GameTest
    public void modIsLoaded(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded(NotifyMod.MOD_ID)) {
            throw helper.assertionException("Notify Mod no está cargado");
        }
        helper.succeed();
    }
}
