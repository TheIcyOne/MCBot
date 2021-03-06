package com.blamejared.mcbot.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.blamejared.mcbot.commands.api.Command;
import com.blamejared.mcbot.commands.api.CommandContext;
import com.blamejared.mcbot.commands.api.CommandException;
import com.blamejared.mcbot.commands.api.CommandPersisted;
import com.blamejared.mcbot.commands.api.Flag;
import com.blamejared.mcbot.util.BakedMessage;
import com.blamejared.mcbot.util.PaginatedMessageFactory;
import com.blamejared.mcbot.util.PaginatedMessageFactory.PaginatedMessage;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;

@Command
public class CommandSlap extends CommandPersisted<List<String>> {
    
    private static final Flag FLAG_ADD = new SimpleFlag("add", false);
    private static final Flag FLAG_LS = new SimpleFlag("ls", false);
    
    private static final int PER_PAGE = 10;

	private List<String> options = Lists.newArrayList();
    private Random rand = new Random();

    public CommandSlap() {
        super("slap", false, Lists.newArrayList(FLAG_ADD, FLAG_LS), ArrayList::new);
        options.add("with a large trout!");
        options.add("with a big bat!");
        options.add("with a frying pan!");
        options.add("like a little bitch!");
    }
    
    @Override
    public TypeToken<List<String>> getDataType() {
        return new TypeToken<List<String>>(){};
    }
    
    @Override
    public void process(CommandContext ctx) throws CommandException {
        if (ctx.hasFlag(FLAG_LS)) {
            StringBuilder builder = new StringBuilder();
            PaginatedMessageFactory.Builder paginatedBuilder = PaginatedMessageFactory.INSTANCE.builder(ctx.getChannel());
            int i = 0;
            for (String suffix : storage.get(ctx.getMessage())) {
                if (i % PER_PAGE == 0) {
                    if (builder.length() > 0) {
                        paginatedBuilder.addPage(new BakedMessage(builder.toString(), null, false));
                    }
                    builder = new StringBuilder();
                    builder.append("List of custom slap suffixes (Page " + ((i / PER_PAGE) + 1) + "):\n");
                }
                builder.append(i++ + 1).append(") ").append(suffix).append("\n");
            }
            if (builder.length() > 0) {
                paginatedBuilder.addPage(new BakedMessage(builder.toString(), null, false));
            }
            paginatedBuilder.setParent(ctx.getMessage()).build().send();
            return;
        }

        if (ctx.argCount() < 1) {
            throw new CommandException("Not enough arguments.");
        }
        if (ctx.hasFlag(FLAG_ADD)) {
        	storage.get(ctx.getGuild()).add(Joiner.on(' ').join(ctx.getArgs()));
        	ctx.reply("Added new slap suffix.");
        	return;
        }

        StringBuilder builder = new StringBuilder(ctx.getMessage().getAuthor().getName());
        List<String> suffixes = Lists.newArrayList(options);
        suffixes.addAll(storage.get(ctx.getGuild()));
        
        builder.append(" slapped ").append(Joiner.on(' ').join(ctx.getArgs())).append(" " + suffixes.get(rand.nextInt(suffixes.size())));
        ctx.reply(builder.toString());
    }
    
    public String getUsage() {
        return "<user>";
    }
}
