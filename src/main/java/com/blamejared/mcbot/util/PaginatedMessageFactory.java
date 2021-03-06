package com.blamejared.mcbot.util;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.BooleanSupplier;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionAddEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IReaction;
import sx.blah.discord.util.RequestBuffer;
import sx.blah.discord.util.RequestBuilder;

import com.blamejared.mcbot.MCBot;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Booleans;

public enum PaginatedMessageFactory {

	INSTANCE;

	private Queue<PaginatedMessage> sent = new PriorityQueue<PaginatedMessage>();
	
	private TLongObjectMap<PaginatedMessage> byMessageId = new TLongObjectHashMap<PaginatedMessage>();

	@RequiredArgsConstructor
	public class PaginatedMessage implements Comparable<PaginatedMessage> {
		@NonNull
		private final List<@NonNull BakedMessage> messages;
		@NonNull
		private final IChannel channel;
		@Getter
		private final IMessage parent;
		private final boolean isProtected;

		@Getter
		private int page;
		@Nullable
		private IMessage sentMessage;
		private long lastUpdate;

		@Override
		public int compareTo(PaginatedMessage other) {
			return Long.compare(lastUpdate, other.lastUpdate);
		}
		
		@SuppressWarnings("null")
        public void send() {
			Preconditions.checkArgument(sentMessage == null, "Paginated message has already been sent!");
			new RequestBuilder(MCBot.instance).shouldBufferRequests(true)
			.doAction(() -> {
				this.sentMessage = messages.get(page).send(channel);
				byMessageId.put(this.sentMessage.getLongID(), PaginatedMessage.this);
				return true;
			}).andThen(() -> {
				this.sentMessage.addReaction(LEFT_ARROW);
				return true;
			}).andThen(() -> {
				this.sentMessage.addReaction(X);
				return true;
			}).andThen(() -> {
				this.sentMessage.addReaction(RIGHT_ARROW);
				return true;
			}).build();
			this.lastUpdate = System.currentTimeMillis();
		}
		
		@SuppressWarnings("null")
        public boolean setPage(int page) {
			Preconditions.checkPositionIndex(page, messages.size());
			BakedMessage message = messages.get(page);
			if (sentMessage != null) {
		         message.update(sentMessage);
			}
			this.page = page;
			this.lastUpdate = System.currentTimeMillis();
			return true;
		}
		
		public boolean pageUp() {
			if (page < messages.size() - 1) {
				return setPage(page + 1);
			}
			return true;
		}
		
		public boolean pageDn() {
			if (page > 0) {
				return setPage(page - 1);
			}
			return true;
		}
		
        @SuppressWarnings("null")
        public boolean delete() {
            if (sentMessage != null) {
                sentMessage.delete();
                this.sentMessage = null;
            }
            if (parent != null) {
                parent.delete();
            }
            return true;
        }
        
        public boolean isProtected() {
            return getParent() != null && isProtected;
        }
	}
	
	@RequiredArgsConstructor
    @Accessors(chain = true)
	public class Builder {
		private final @NonNull List<BakedMessage> messages = new ArrayList<>();
		private final @NonNull IChannel channel;

		@Setter
		private IMessage parent;
		
		@Setter
		private boolean isProtected = true;
		
		@Setter
		private int page;
		
		@SuppressWarnings("null")
        public PaginatedMessage build() {
			PaginatedMessage ret = new PaginatedMessage(Lists.newArrayList(messages), channel, parent, isProtected);
			ret.setPage(page);
			return ret;
		}
		
		public Builder addPage(BakedMessage msg) {
			this.messages.add(msg);
			return this;
		}
		
		public Builder addPages(Collection<? extends BakedMessage> msgs) {
			this.messages.addAll(msgs);
			return this;
		}
	}
	
	public Builder builder(@NonNull IChannel channel) {
		return new Builder(channel);
	}
	
	/* == Event Handlers == */

	private static final String LEFT_ARROW = "\u2B05";
	private static final String RIGHT_ARROW = "\u27A1";
	private static final String X = "\u274C";

	@EventSubscriber
	public void onReactAdd(ReactionAddEvent event) {
		IReaction reaction = event.getReaction();
		if (!event.getClient().getOurUser().equals(event.getUser())) {
		    if (reaction.isCustomEmoji()) {
		        RequestBuffer.request(() -> reaction.getMessage().removeReaction(event.getUser(), reaction));
		        return;
		    }
			String unicode = reaction.getUnicodeEmoji().getUnicode();
			PaginatedMessage message = byMessageId.get(event.getMessage().getLongID());
			RequestBuilder builder = new RequestBuilder(event.getClient()).shouldBufferRequests(true);
            if (message != null) {
                if (!message.isProtected() || message.getParent().getAuthor().equals(event.getUser())) {
                    switch (unicode) {
                        case LEFT_ARROW:
                            builder.doAction(message::pageDn);
                            break;
                        case RIGHT_ARROW:
                            builder.doAction(message::pageUp);
                            break;
                        case X:
                            builder.doAction(message::delete);
                            byMessageId.remove(event.getMessage().getLongID());
                            break;
                    }
                } else {
                    // Because it has to have an initial action
                    builder.doAction(() -> true);
                }
                builder.andThen(() -> {
                    if (event.getMessage().isDeleted()) {
                        return false;
                    }
                    event.getMessage().removeReaction(event.getUser(), event.getReaction());
                    return true;
	            });
	            builder.execute();
			}
		}
	}
}
