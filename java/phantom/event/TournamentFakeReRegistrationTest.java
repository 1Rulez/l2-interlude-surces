package phantom.event;

import com.l2jmega.events.Arena1x1;
import com.l2jmega.events.Arena2x2;
import com.l2jmega.events.Arena5x5;
import phantom.FakePlayer;

/**
 * Manual/unit tests for fake player re-registration in tournament (1x1, 2x2, 5x5) after first match.
 * Fix: AbstractArenaPair clears flags with forEach (all members) when value=false; FakePlayer
 * defensively clears stale arena state in canRegisterTournament() when not in any arena list.
 *
 * To run as JUnit tests: add JUnit to lib/ and build.xml, then uncomment @Test and assertions.
 *
 * Manual verification (in-game):
 * 1. Start tournament event, spawn tournament fakes (1x1, 2x2, 5x5).
 * 2. Let first matches run to completion (pairs removed from registered, finishDuel clears flags).
 * 3. Trigger or wait for fakes to register again (e.g. next ArenaTask cycle or move to NPC again).
 * 4. Confirm fakes can register again in 1x1, 2x2, 5x5 and appear in subsequent matches.
 */
public final class TournamentFakeReRegistrationTest {

	// --- Manual re-registration check (no JUnit) ---
	public static boolean checkArenaListsDoNotContain(FakePlayer fake) {
		return !Arena1x1.getInstance().isRegistered(fake)
				&& !Arena2x2.getInstance().isRegistered(fake)
				&& !Arena5x5.getInstance().isRegistered(fake);
	}

	/**
	 * After a duel, fake should have no arena flags so canRegisterTournament() would allow re-register.
	 * Call this from debug or a GM command to verify state: all should be false after duel end.
	 * @param fake the fake player to check
	 * @return true if all arena-related flags are cleared
	 */
	public static boolean expectClearedArenaState(FakePlayer fake) {
		return !fake.isArena1x1() && !fake.isArena2x2() && !fake.isArena5x5()
				&& !fake.isInArenaEvent() && !fake.isArenaAttack() && !fake.isArenaProtection();
	}

	// Uncomment when JUnit is on classpath:
	// @Test
	// public void fakePlayerStaleStateClearedWhenNotInAnyArena() {
	//     FakePlayer fake = FakePlayerManager.spawnEventPlayer(0, 0, 0);
	//     fake.setArena1x1(true);
	//     fake.setArenaProtection(true);
	//     assertFalse(Arena1x1.getInstance().isRegistered(fake)); // not in list
	//     fake.registerTournament(); // canRegisterTournament clears stale state then registers
	//     assertTrue(Arena1x1.getInstance().isRegistered(fake));
	//     fake.despawnPlayer();
	// }
}
