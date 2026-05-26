package phantom;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.l2jmega.Config;
import com.l2jmega.commons.lang.StringUtil;
import com.l2jmega.gameserver.data.PlayerNameTable;
import com.l2jmega.commons.random.Rnd;
import phantom.ai.FakePlayerUtilsAI;

public enum FakePlayerNameManager {
   INSTANCE;

   private static final int MIN_FAKE_NAME_LENGTH = 3;
   private static final int MAX_FAKE_NAME_LENGTH = 16;
   private static final String FALLBACK_NAME_PREFIX = "Fake";
   public static final Logger _log = Logger.getLogger(FakePlayerNameManager.class.getName());
   private List<String> _fakePlayerNames;
   private List<String> _fakePlayerTitles;

   public void initialise() {
      this.loadNamelist();
      this.loadTitlelist();
      FakePlayerUtilsAI.load();
   }

   public String getRandomAvailableName() {
      if (this._fakePlayerNames == null || this._fakePlayerNames.isEmpty()) {
         return buildUniqueFakeName(null, Rnd.get(1000, 999999));
      }

      for (int attempt = 0; attempt < this._fakePlayerNames.size() * 2; attempt++) {
         final String name = buildUniqueFakeName(this.getRandomNameFromWordlist(), Rnd.get(1000, 999999));
         if (name != null) {
            return name;
         }
      }

      return buildUniqueFakeName(null, Rnd.get(1000, 999999));
   }

   public String getRandomAvailableTitle() {
      return this.getRandomTitleFromWordlist();
   }

   private String getRandomNameFromWordlist() {
      return this._fakePlayerNames.get(Rnd.get(0, this._fakePlayerNames.size() - 1));
   }

   public String getRandomTitleFromWordlist() {
      return this._fakePlayerTitles.get(Rnd.get(0, this._fakePlayerTitles.size() - 1));
   }

   public List<String> getFakePlayerNames() {
      return this._fakePlayerNames;
   }

   public static String sanitizeFakeName(String rawName) {
      if (rawName == null) {
         return null;
      }

      final String trimmedName = rawName.trim();
      if (trimmedName.isEmpty()) {
         return null;
      }

      final StringBuilder builder = new StringBuilder(Math.min(trimmedName.length(), MAX_FAKE_NAME_LENGTH));
      for (int i = 0; i < trimmedName.length() && builder.length() < MAX_FAKE_NAME_LENGTH; i++) {
         final char current = trimmedName.charAt(i);
         if (Character.isDigit(current)) {
            continue;
         }

         if (current != '\uFFFD' && Config.PLAYER_NAME_ALLOWED_CHARS.indexOf(current) >= 0) {
            builder.append(current);
         }
      }

      final String sanitizedName = builder.toString();
      return StringUtil.isValidPlayerName(sanitizedName, MIN_FAKE_NAME_LENGTH, MAX_FAKE_NAME_LENGTH) ? sanitizedName : null;
   }

   public static String buildUniqueFakeName(String requestedName, int uniqueSeed) {
      String baseName = sanitizeFakeName(requestedName);

      if (baseName == null) {
         baseName = FALLBACK_NAME_PREFIX;
      }

      if (!nameAlreadyExists(baseName)) {
         return baseName;
      }

      String candidate = appendAlphabeticSuffix(baseName, Math.abs(uniqueSeed));
      if (!nameAlreadyExists(candidate)) {
         return candidate;
      }

      for (int attempt = 1; attempt <= 1000; attempt++) {
         candidate = appendAlphabeticSuffix(baseName, Math.abs(uniqueSeed) + attempt);
         if (!nameAlreadyExists(candidate)) {
            return candidate;
         }
      }

      return appendAlphabeticSuffix(FALLBACK_NAME_PREFIX, Math.abs(uniqueSeed) + Rnd.get(10, 99));
   }

   private void loadNamelist() {
      try (LineNumberReader lnr = new LineNumberReader(new BufferedReader(new FileReader(new File("./config/custom/phantom/names/fakenamewordlist.txt"))))) {
         ArrayList<String> playersList = new ArrayList<>();
         int skippedEntries = 0;

         String line;
         while ((line = lnr.readLine()) != null) {
            if (line.trim().length() != 0 && !line.startsWith("#")) {
               final String sanitizedName = sanitizeFakeName(line);
               if (sanitizedName != null) {
                  playersList.add(sanitizedName);
               } else {
                  skippedEntries++;
               }
            }
         }

         this._fakePlayerNames = playersList;
         _log.log(Level.INFO, String.format("[Fake Players]: Carregou %s Fake Player Names.", this._fakePlayerNames.size()));
         if (skippedEntries > 0) {
            _log.log(Level.WARNING, String.format("[Fake Players]: Ignorou %s nomes invalidos do wordlist.", skippedEntries));
         }
      } catch (Exception var6) {
         var6.printStackTrace();
      }
   }

   private void loadTitlelist() {
      try (LineNumberReader lnr = new LineNumberReader(new BufferedReader(new FileReader(new File("./config/custom/phantom/names/faketitlewordlist.txt"))))) {
         ArrayList<String> playersList = new ArrayList<>();

         String line;
         while ((line = lnr.readLine()) != null) {
            if (line.trim().length() != 0 && !line.startsWith("#")) {
               playersList.add(line);
            }
         }

         this._fakePlayerTitles = playersList;
         _log.log(Level.INFO, String.format("[Fake Players]: Carregou %s fake player titles.", this._fakePlayerTitles.size()));
      } catch (Exception var6) {
         var6.printStackTrace();
      }
   }

   private static boolean nameAlreadyExists(String name) {
      return PlayerNameTable.getInstance().getPlayerObjectId(name) > 0;
   }

   private static String appendAlphabeticSuffix(String baseName, int sequence) {
      return appendSuffix(sanitizeFakeName(baseName), toAlphabeticSuffix(sequence));
   }

   private static String appendSuffix(String baseName, String suffix) {
      String safeBaseName = sanitizeFakeName(baseName);
      if (safeBaseName == null) {
         safeBaseName = FALLBACK_NAME_PREFIX;
      }

      final int maxBaseLength = Math.max(MIN_FAKE_NAME_LENGTH, MAX_FAKE_NAME_LENGTH - suffix.length());
      if (safeBaseName.length() > maxBaseLength) {
         safeBaseName = safeBaseName.substring(0, maxBaseLength);
      }

      String candidate = safeBaseName + suffix;
      if (candidate.length() > MAX_FAKE_NAME_LENGTH) {
         candidate = candidate.substring(0, MAX_FAKE_NAME_LENGTH);
      }

      if (candidate.length() < MIN_FAKE_NAME_LENGTH) {
         candidate = FALLBACK_NAME_PREFIX;
      }

      return candidate;
   }

   private static String toAlphabeticSuffix(int value) {
      final int normalized = Math.max(0, value);
      final StringBuilder suffix = new StringBuilder();
      int current = normalized;

      do {
         suffix.append((char) ('A' + (current % 26)));
         current = (current / 26) - 1;
      } while (current >= 0);

      return suffix.reverse().toString();
   }
}
