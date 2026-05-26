package phantom.ai;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.serverpackets.CreatureSay;
import com.l2jmega.gameserver.util.Broadcast;
import com.l2jmega.commons.random.Rnd;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;

public class FakePlayerUtilsAI {
   private static ArrayList<String> _fakesTellPhrases = new ArrayList<>();
   private static ArrayList<String> _fakesPeacePhrases = new ArrayList<>();
   private static ArrayList<String> _fakesDiedPhrases = new ArrayList<>();
   private static ArrayList<String> _fakesKillPhrases = new ArrayList<>();
   private static ArrayList<String> _fakesPrivateBuyTitles = new ArrayList<>();
   private static ArrayList<String> _fakesPrivateSellTitles = new ArrayList<>();
   private static ArrayList<String> _fakesNormalChatPhrases = new ArrayList<>();
   private static List<String> _fakePlayerColorName;
   private static List<String> _fakePlayerColorTitle;
   private static List<String> _fakeClanTitles;
   private static final Map<Integer, Long> _normalChatCooldowns = new ConcurrentHashMap<>();

   public static void load() {
      _fakesTellPhrases.clear();
      parseFile("tell", _fakesTellPhrases);
      parseFile("peace", _fakesPeacePhrases);
      parseFile("dead", _fakesDiedPhrases);
      parseFile("kill", _fakesKillPhrases);
      parseFile("buy", _fakesPrivateBuyTitles);
      parseFile("sell", _fakesPrivateSellTitles);
      _fakesNormalChatPhrases.clear();
      parseFile("normalchat", _fakesNormalChatPhrases);
      loadColorNamelist();
      loadColorTitlelist();
      loadClanTitleList();
   }

   private static void parseFile(String file_name, ArrayList<String> phrases) {
      File data = new File("./config/custom/phantom/chat/" + file_name + ".talk");
      if (!data.exists()) {
         return;
      }

      try (LineNumberReader lnr = new LineNumberReader(new BufferedReader(new FileReader(data)))) {
         String line;
         while ((line = lnr.readLine()) != null) {
            if (line.trim().length() != 0 && !line.startsWith("#")) {
               phrases.add(line);
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public static int getRandomClan() {
      return FakePlayerConfig.LIST_CLAN_ID.isEmpty() ? 0 : FakePlayerConfig.LIST_CLAN_ID.get(Rnd.get(FakePlayerConfig.LIST_CLAN_ID.size()));
   }

   public static boolean hasTellPhrases() {
      return !_fakesTellPhrases.isEmpty();
   }

   public static void answerPlayers(Player sender, FakePlayer receiver, String text) {
      FakePlayerChatManager.INSTANCE.onPlayerWroteToFake(sender, receiver, text);
   }

   public static void maybeAnnounce(FakePlayer fake) {
      if (fake.isDead() || _fakesPeacePhrases.isEmpty()) return;
      String phrase = getRandomPeacePhrase();
      if (phrase.isEmpty()) return;
      // Shout (1) - global in all locations
      Broadcast.toAllOnlinePlayers(new CreatureSay(fake.getObjectId(), 1, fake.getName(), phrase));
   }

   public static void maybeAnnounceOnDied(FakePlayer fake) {
      if (!fake.isFakeEvent()) {
         if (Rnd.get(1, 1000000) <= FakePlayerConfig.FAKE_CHANCE_TO_TALK_DIED && fake.isDead()) {
            Broadcast.toAllOnlinePlayers(new CreatureSay(fake.getObjectId(), 1, fake.getName(), getRandomDeadPhrase()));
         }
      }
   }

   public static void maybeAnnounceOnKill(FakePlayer fake, String targetName) {
      if (!fake.isFakeEvent()) {
         if (Rnd.get(1, 1000000) <= FakePlayerConfig.FAKE_CHANCE_TO_TALK_KILLED) {
            String phrase = getRandomKillPhrase();
            if (targetName != null) {
               phrase = phrase.replace("%target", targetName);
            }
            Broadcast.toAllOnlinePlayers(new CreatureSay(fake.getObjectId(), 1, fake.getName(), phrase));
         }
      }
   }

   public static String getRandomTellPhrase() {
      return _fakesTellPhrases.isEmpty() ? "" : _fakesTellPhrases.get(Rnd.get(_fakesTellPhrases.size()));
   }

   public static String getRandomPeacePhrase() {
      return _fakesPeacePhrases.isEmpty() ? "" : _fakesPeacePhrases.get(Rnd.get(_fakesPeacePhrases.size()));
   }

   public static String getRandomDeadPhrase() {
      return _fakesDiedPhrases.isEmpty() ? "" : _fakesDiedPhrases.get(Rnd.get(_fakesDiedPhrases.size()));
   }

   public static String getRandomKillPhrase() {
      return _fakesKillPhrases.isEmpty() ? "" : _fakesKillPhrases.get(Rnd.get(_fakesKillPhrases.size()));
   }

   public static String getRandomPrivateBuyTitle() {
      return _fakesPrivateBuyTitles.isEmpty() ? "Buying" : _fakesPrivateBuyTitles.get(Rnd.get(_fakesPrivateBuyTitles.size()));
   }

   public static String getRandomPrivateSellTitle() {
      return _fakesPrivateSellTitles.isEmpty() ? "Selling" : _fakesPrivateSellTitles.get(Rnd.get(_fakesPrivateSellTitles.size()));
   }

   public static String getRandomNormalChatPhrase() {
      return _fakesNormalChatPhrases.isEmpty() ? "" : _fakesNormalChatPhrases.get(Rnd.get(_fakesNormalChatPhrases.size()));
   }

   private static void loadColorNamelist() {
      try (LineNumberReader lnr = new LineNumberReader(new BufferedReader(new FileReader(new File("./config/custom/phantom/names/fakeColornamewordlist.txt"))))) {
         ArrayList<String> playersList = new ArrayList<>();

         String line;
         while ((line = lnr.readLine()) != null) {
            if (line.trim().length() != 0 && !line.startsWith("#")) {
               playersList.add(line);
            }
         }

         _fakePlayerColorName = playersList;
      } catch (Exception var5) {
         var5.printStackTrace();
      }
   }

   private static void loadColorTitlelist() {
      try (LineNumberReader lnr = new LineNumberReader(new BufferedReader(new FileReader(new File("./config/custom/phantom/names/fakeColortitlewordlist.txt"))))) {
         ArrayList<String> playersList = new ArrayList<>();

         String line;
         while ((line = lnr.readLine()) != null) {
            if (line.trim().length() != 0 && !line.startsWith("#")) {
               playersList.add(line);
            }
         }

         _fakePlayerColorTitle = playersList;
      } catch (Exception var5) {
         var5.printStackTrace();
      }
   }

   private static void loadClanTitleList() {
      try (LineNumberReader lnr = new LineNumberReader(new BufferedReader(new FileReader(new File("./config/custom/phantom/names/clanfaketitle.txt"))))) {
         ArrayList<String> titles = new ArrayList<>();

         String line;
         while ((line = lnr.readLine()) != null) {
            if (line.trim().length() != 0 && !line.startsWith("#")) {
               titles.add(line.trim());
            }
         }

         _fakeClanTitles = titles;
      } catch (Exception e) {
         _fakeClanTitles = new ArrayList<>();
      }
   }

   public static String getRandomColorNameFromWordlist() {
      return (_fakePlayerColorName == null || _fakePlayerColorName.isEmpty()) ? "FFFFFF" : _fakePlayerColorName.get(Rnd.get(_fakePlayerColorName.size()));
   }

   public static String getRandomColorTitleFromWordlist() {
      return (_fakePlayerColorTitle == null || _fakePlayerColorTitle.isEmpty()) ? "FFFFFF" : _fakePlayerColorTitle.get(Rnd.get(_fakePlayerColorTitle.size()));
   }

   public static void maybeAnnounceNormalChat(FakePlayer fake) {
      if (!FakePlayerConfig.FAKE_NORMAL_CHAT_ENABLED || fake == null || fake.isDead() || _fakesNormalChatPhrases.isEmpty()) {
         return;
      }

      int fakeId = fake.getObjectId();
      long now = System.currentTimeMillis();
      Long lastTime = _normalChatCooldowns.get(fakeId);
      if (lastTime != null) {
         long cooldownMs = Rnd.get(
            FakePlayerConfig.FAKE_NORMAL_CHAT_MIN_INTERVAL,
            FakePlayerConfig.FAKE_NORMAL_CHAT_MAX_INTERVAL) * 1000L;
         if (now - lastTime < cooldownMs) {
            return;
         }
      }

      if (Rnd.get(1, 1000000) > FakePlayerConfig.FAKE_NORMAL_CHAT_CHANCE) {
         return;
      }

      String phrase = getRandomNormalChatPhrase();
      if (phrase.isEmpty()) {
         return;
      }

      if (phrase.contains("%player")) {
         if (Rnd.get(100) < FakePlayerConfig.FAKE_NORMAL_CHAT_TO_REAL_CHANCE) {
            Collection<Player> nearby = fake.getKnownTypeInRadius(Player.class, 1000);
            String chosen = pickRandomRealPlayerName(nearby, fake.getObjectId());
            phrase = phrase.replace("%player", chosen);
         } else {
            phrase = phrase.replace("%player", "");
         }
      }

      if (phrase.contains("%fake")) {
         if (Rnd.get(100) < FakePlayerConfig.FAKE_NORMAL_CHAT_TO_FAKE_CHANCE) {
            Collection<FakePlayer> nearbyFakes = fake.getKnownTypeInRadius(FakePlayer.class, 1000);
            String chosen = pickRandomFakeName(nearbyFakes, fake.getObjectId());
            phrase = phrase.replace("%fake", chosen);
         } else {
            phrase = phrase.replace("%fake", "");
         }
      }

      phrase = phrase.trim();
      if (phrase.isEmpty()) {
         return;
      }

      Broadcast.toSelfAndKnownPlayers(fake, new CreatureSay(fakeId, 0, fake.getName(), phrase));
      _normalChatCooldowns.put(fakeId, now);
   }

   public static void removeNormalChatCooldown(int fakeObjectId) {
      _normalChatCooldowns.remove(fakeObjectId);
   }

   private static String pickRandomRealPlayerName(Collection<Player> players, int excludeId) {
      if (players == null || players.isEmpty()) return "";
      List<Player> list = new ArrayList<>();
      for (Player p : players) {
         if (p.getObjectId() != excludeId && !(p instanceof FakePlayer)) {
            list.add(p);
         }
      }
      return list.isEmpty() ? "" : list.get(Rnd.get(list.size())).getName();
   }

   private static String pickRandomFakeName(Collection<FakePlayer> fakes, int excludeId) {
      if (fakes == null || fakes.isEmpty()) return "";
      List<FakePlayer> list = new ArrayList<>();
      for (FakePlayer fp : fakes) {
         if (fp.getObjectId() != excludeId) {
            list.add(fp);
         }
      }
      return list.isEmpty() ? "" : list.get(Rnd.get(list.size())).getName();
   }

   public static String getRandomClanTitle() {
      if (FakePlayerConfig.FAKE_PLAYER_CLAN_FIXED_TITLE != null && !FakePlayerConfig.FAKE_PLAYER_CLAN_FIXED_TITLE.isEmpty()) {
         return FakePlayerConfig.FAKE_PLAYER_CLAN_FIXED_TITLE;
      }

      if (_fakeClanTitles != null && !_fakeClanTitles.isEmpty()) {
         return _fakeClanTitles.get(Rnd.get(_fakeClanTitles.size()));
      }

      return FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE;
   }
}
