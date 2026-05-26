package phantom;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.l2jmega.L2DatabaseFactory;
import com.l2jmega.gameserver.data.CharTemplateTable;
import com.l2jmega.gameserver.idfactory.IdFactory;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.actor.template.PlayerTemplate;
import com.l2jmega.gameserver.model.base.ClassId;
import com.l2jmega.gameserver.model.base.Sex;

/**
 * Helper for creating and deleting persistent fake player characters in DB.
 *
 * Used by {@link FakePlayerManager} and {@link FakePlayer} to integrate fakes
 * into all DB-based statistics (online, PvP/PK, rankings, etc.).
 */
public final class FakeAccountService {

    private static final Logger _log = Logger.getLogger(FakeAccountService.class.getName());

    private static final String FAKE_ACCOUNT_PREFIX = "fakeacc";
    private static final String INSERT_ACCOUNT = "INSERT INTO accounts (login,password,lastactive,access_level) VALUES (?,?,CURRENT_TIMESTAMP,0)";
    private static final String SELECT_ACCOUNT = "SELECT login FROM accounts WHERE login=?";
    private static final String SELECT_MAX_FAKE_ACCOUNT_INDEX = "SELECT MAX(CAST(SUBSTRING(login, ?) AS UNSIGNED)) AS max_idx FROM accounts WHERE login LIKE ?";
    private static final AtomicInteger NEXT_FAKE_ACCOUNT_INDEX = new AtomicInteger();
    private static volatile boolean FAKE_ACCOUNT_INDEX_INITIALIZED = false;

    private FakeAccountService() {
    }

    /**
     * Ensure account row exists for a fake.
     * Password is a dummy constant because detached clients never authenticate.
     *
     * @param login account login to ensure.
     */
    public static void ensureFakeAccountExists(String login) {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_ACCOUNT)) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        } catch (Exception e) {
            _log.log(Level.WARNING, "Could not check fake account " + login, e);
            return;
        }

        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_ACCOUNT)) {
            ps.setString(1, login);
            ps.setString(2, "fake");
            ps.executeUpdate();
        } catch (Exception e) {
            _log.log(Level.WARNING, "Could not insert fake account " + login, e);
        }
    }

    /**
     * Create a new persistent fake character in DB using the normal Player.create path.
     *
     * @param accountName target account (must exist in accounts table)
     * @param name        character name (must be unique)
     * @param classId     class of the fake player
     * @param sex         sex
     * @param appearance  already prepared appearance
     * @return created Player (not yet added to world) or null on failure
     */
    public static Player createPersistentFakeCharacter(String accountName, String name, ClassId classId, Sex sex,
                                                       com.l2jmega.gameserver.model.actor.appearance.PcAppearance appearance) {
        final PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
        if (template == null) {
            _log.warning("FakeAccountService: no template for classId " + classId);
            return null;
        }

        // Reserve new objectId.
        final int objectId = IdFactory.getInstance().getNextId();
        final String safeName = FakePlayerNameManager.buildUniqueFakeName(name, objectId);

        // Player.create handles INSERT into characters and CharNameTable.
        final Player player = Player.create(objectId, template, accountName, safeName,
                appearance.getHairStyle(), appearance.getHairColor(), appearance.getFace(), sex);
        if (player == null) {
            return null;
        }

        return player;
    }

    /**
     * Helper to generate a unique fake account name like "fakeacc1", "fakeacc2", ...
     * This method checks the accounts table directly.
     *
     * @return next available fake account login.
     */
    public static String generateNextFakeAccount() {
        ensureFakeAccountIndexInitialized();

        while (true) {
            final int idx = NEXT_FAKE_ACCOUNT_INDEX.incrementAndGet();
            final String login = FAKE_ACCOUNT_PREFIX + idx;
            if (!accountExists(login)) {
                return login;
            }
        }
    }

    private static void ensureFakeAccountIndexInitialized() {
        if (FAKE_ACCOUNT_INDEX_INITIALIZED) {
            return;
        }

        synchronized (NEXT_FAKE_ACCOUNT_INDEX) {
            if (FAKE_ACCOUNT_INDEX_INITIALIZED) {
                return;
            }

            int maxIndex = 0;
            try (Connection con = L2DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(SELECT_MAX_FAKE_ACCOUNT_INDEX)) {
                ps.setInt(1, FAKE_ACCOUNT_PREFIX.length() + 1);
                ps.setString(2, FAKE_ACCOUNT_PREFIX + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        maxIndex = Math.max(0, rs.getInt("max_idx"));
                    }
                }
            } catch (Exception e) {
                _log.log(Level.WARNING, "Could not initialize fake account index", e);
            }

            NEXT_FAKE_ACCOUNT_INDEX.set(maxIndex);
            FAKE_ACCOUNT_INDEX_INITIALIZED = true;
        }
    }

    private static boolean accountExists(String login) {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_ACCOUNT)) {
            ps.setString(1, login);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            _log.log(Level.WARNING, "Could not check existing account " + login, e);
            return true;
        }
    }

    /**
     * Helper to delete persistent fake character completely from DB.
     *
     * @param objectId character object id to delete.
     */
    public static void deletePersistentFakeCharacter(int objectId) {
        com.l2jmega.gameserver.network.L2GameClient.deleteCharByObjId(objectId);
    }
}

