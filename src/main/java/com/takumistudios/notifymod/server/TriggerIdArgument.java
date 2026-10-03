package com.takumistudios.notifymod.server;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.takumistudios.notifymod.core.Trigger;
import net.minecraft.network.chat.Component;

/** Brigadier's word argument excludes ':'; trigger ids also support namespace and nested paths. */
public final class TriggerIdArgument implements ArgumentType<String> {
    private static final DynamicCommandExceptionType INVALID = new DynamicCommandExceptionType(
            id -> Component.translatable("notifymod.trigger.invalid_id", id));

    @Override public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
        String raw = reader.getString().substring(start, reader.getCursor());
        String id = NotifyCommand.normalizeId(raw);
        if (!Trigger.isValidId(id)) { reader.setCursor(start); throw INVALID.createWithContext(reader, raw); }
        return id;
    }
}
