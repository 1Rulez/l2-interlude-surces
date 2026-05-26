package phantom.ai;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.serverpackets.CreatureSay;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;

/**
 * Manages private chat between players and fake players.
 * - Players can write to fakes, fakes sometimes respond
 * - Fakes can initiate conversation if player was silent for a while
 * - Conversation stops if player doesn't respond for too long
 */
public enum FakePlayerChatManager {
	INSTANCE;

	private static final int SAY2_TELL = 2;
	private static final int SAY2_PARTY = 3;
	private final AdvancedChatManager _advancedChatManager = new AdvancedChatManager();
	private final Map<Integer, Map<Integer, ConversationState>> _conversations = new ConcurrentHashMap<>();

	private static class ConversationState {
		long lastPlayerMsgTime;
		long lastFakeMsgTime;
	}

	public void onPlayerWroteToFake(Player player, FakePlayer fake, String text) {
		if (player == null || fake == null || !player.isOnline())
			return;
		
		_advancedChatManager.onPlayerMessage(player, fake, text);

		int fakeId = fake.getObjectId();
		int playerId = player.getObjectId();

		_conversations.computeIfAbsent(fakeId, _ -> new ConcurrentHashMap<>())
			.compute(playerId, (_, state) -> {
				ConversationState s = state != null ? state : new ConversationState();
				s.lastPlayerMsgTime = System.currentTimeMillis();
				return s;
			});

		if (!FakePlayerUtilsAI.hasTellPhrases())
			return;

		if (Rnd.get(100) < FakePlayerConfig.FAKE_PARTY_RESPOND_CHANCE) {
			int delayMs = Rnd.get(FakePlayerConfig.FAKE_TELL_RESPONSE_DELAY_MIN, FakePlayerConfig.FAKE_TELL_RESPONSE_DELAY_MAX) * 1000;
			ThreadPool.schedule(() -> sendFakeReply(fake, player), delayMs);
		}
	}

	public static void onPlayerWroteInParty(Player player, FakePlayer fake, String text) {
		if (player == null || fake == null || !player.isOnline())
			return;
		if (!player.isInParty() || fake.getParty() != player.getParty())
			return;
		if (!FakePlayerUtilsAI.hasTellPhrases())
			return;

		if (Rnd.get(100) < FakePlayerConfig.FAKE_TELL_RESPOND_CHANCE) {
			int delayMs = Rnd.get(FakePlayerConfig.FAKE_TELL_RESPONSE_DELAY_MIN, FakePlayerConfig.FAKE_TELL_RESPONSE_DELAY_MAX) * 1000;
			ThreadPool.schedule(() -> sendFakePartyReply(fake, player), delayMs);
		}
	}

	private static void sendFakePartyReply(FakePlayer fake, Player player) {
		if (fake == null || player == null || !player.isOnline() || fake.isDead())
			return;
		if (fake.getParty() == null || player.getParty() != fake.getParty())
			return;

		String phrase = FakePlayerUtilsAI.getRandomTellPhrase();
		if (phrase.isEmpty())
			return;

		fake.getParty().broadcastToPartyMembers(new CreatureSay(fake.getObjectId(), SAY2_PARTY, fake.getName(), phrase));
	}

	private void sendFakeReply(FakePlayer fake, Player player) {
		if (fake == null || player == null || !player.isOnline() || fake.isDead())
			return;

		String phrase = FakePlayerUtilsAI.getRandomTellPhrase();
		if (phrase.isEmpty())
			return;

		// For the player we show a single incoming PM from the fake.
		player.sendPacket(new CreatureSay(fake.getObjectId(), SAY2_TELL, fake.getName(), phrase));

		updateLastFakeMsg(fake.getObjectId(), player.getObjectId());
	}

	private void updateLastFakeMsg(int fakeId, int playerId) {
		Map<Integer, ConversationState> players = _conversations.get(fakeId);
		if (players != null) {
			ConversationState s = players.get(playerId);
			if (s != null)
				s.lastFakeMsgTime = System.currentTimeMillis();
		}
	}

	public void processFakeInitiatedMessages() {
		_advancedChatManager.cleanupOldConversations();
		long now = System.currentTimeMillis();
		int timeoutMs = FakePlayerConfig.FAKE_TELL_CONVERSATION_TIMEOUT * 1000;
		int initiateAfterMs = FakePlayerConfig.FAKE_TELL_INITIATE_AFTER_SILENCE * 1000;
		int minFakeMsgGapMs = FakePlayerConfig.FAKE_TELL_MIN_GAP_BETWEEN_MESSAGES * 1000;

		Iterator<Map.Entry<Integer, Map<Integer, ConversationState>>> it = _conversations.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Map<Integer, ConversationState>> entry = it.next();
			int fakeId = entry.getKey();
			FakePlayer fake = (FakePlayer) World.getInstance().getObject(fakeId);
			if (fake == null || fake.isDead()) {
				it.remove();
				continue;
			}

			Map<Integer, ConversationState> players = entry.getValue();
			Iterator<Map.Entry<Integer, ConversationState>> pit = players.entrySet().iterator();
			while (pit.hasNext()) {
				Map.Entry<Integer, ConversationState> pe = pit.next();
				int playerId = pe.getKey();
				ConversationState state = pe.getValue();

				Player player = World.getInstance().getPlayer(playerId);
				if (player == null || !player.isOnline()) {
					pit.remove();
					continue;
				}

				long silence = now - state.lastPlayerMsgTime;
				if (silence > timeoutMs) {
					pit.remove();
					continue;
				}

				if (silence > initiateAfterMs) {
					long sinceLastFake = now - state.lastFakeMsgTime;
					if (sinceLastFake > minFakeMsgGapMs && Rnd.get(100) < FakePlayerConfig.FAKE_TELL_INITIATE_CHANCE) {
						sendFakeReply(fake, player);
					}
				}
			}
			if (players.isEmpty())
				it.remove();
		}
	}

	public void onFakeDespawned(FakePlayer fake) {
		if (fake != null)
			_conversations.remove(fake.getObjectId());
	}
}
