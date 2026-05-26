package phantom;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.l2jmega.commons.concurrent.ThreadPool;

import phantom.ai.shop.PrivateStoreBuyAI;
import phantom.ai.shop.PrivateStoreSellAI;
import phantom.ai.walker.CitizenAI;

public class AdminSpawnedFakesStorage {
      /**
       * Remove a fake from storage file by type and coordinates.
       *
       * @param type fake type (e.g. "archer_clan", "citizen", etc.)
       * @param x X coordinate
       * @param y Y coordinate
       * @param z Z coordinate
       */
      public static void remove(String type, int x, int y, int z) {
         File f = new File(FILE_PATH);
         if (!f.exists() || f.length() == 0) return;
         List<String> lines = new ArrayList<>();
         try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
               line = line.trim();
               if (!line.isEmpty() && !line.startsWith("#"))
                  lines.add(line);
            }
         } catch (Exception e) {
            _log.warning("[AdminSpawnedFakes] Failed to read for remove: " + e.getMessage());
            return;
         }
         // Remove matching line
         String target = type + "," + x + "," + y + "," + z;
         boolean removed = lines.removeIf(l -> l.equalsIgnoreCase(target));
         if (removed) {
            try (BufferedWriter w = new BufferedWriter(new FileWriter(f, false))) {
               for (String l : lines) {
                  w.write(l);
                  w.newLine();
               }
            } catch (Exception e) {
               _log.warning("[AdminSpawnedFakes] Failed to write after remove: " + e.getMessage());
            }
         }
      }
   
   /**
    * Removes one stored fake entry around provided coordinates.
    * First tries to match by type+radius, then (optionally) any type+radius.
    *
    * @param type preferred fake type
    * @param x current x
    * @param y current y
    * @param z current z
    * @param radius max distance for nearby match
    * @param fallbackAnyType if true, removes nearest entry even if type differs
    * @return true if at least one line was removed.
    */
   public static boolean removeNear(String type, int x, int y, int z, int radius, boolean fallbackAnyType) {
      File f = new File(FILE_PATH);
      if (!f.exists() || f.length() == 0)
         return false;
      
      List<String> lines = new ArrayList<>();
      try (BufferedReader r = new BufferedReader(new FileReader(f))) {
         String line;
         while ((line = r.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#"))
               lines.add(line);
         }
      } catch (Exception e) {
         _log.warning("[AdminSpawnedFakes] Failed to read for removeNear: " + e.getMessage());
         return false;
      }
      
      if (lines.isEmpty())
         return false;
      
      int primaryIdx = findClosestIndex(lines, type, x, y, z, radius, false);
      int idxToRemove = primaryIdx;
      
      if (idxToRemove < 0 && fallbackAnyType)
         idxToRemove = findClosestIndex(lines, type, x, y, z, radius, true);
      
      if (idxToRemove < 0)
         return false;
      
      lines.remove(idxToRemove);
      try (BufferedWriter w = new BufferedWriter(new FileWriter(f, false))) {
         for (String l : lines) {
            w.write(l);
            w.newLine();
         }
      } catch (Exception e) {
         _log.warning("[AdminSpawnedFakes] Failed to write after removeNear: " + e.getMessage());
         return false;
      }
      return true;
   }
   
   private static int findClosestIndex(List<String> lines, String type, int x, int y, int z, int radius, boolean ignoreType) {
      int bestIdx = -1;
      long bestDist2 = Long.MAX_VALUE;
      final long radius2 = (long) radius * radius;
      
      for (int i = 0; i < lines.size(); i++) {
         String[] parts = lines.get(i).split(",");
         if (parts.length < 4)
            continue;
         
         final String storedType = parts[0].trim();
         if (!ignoreType && (type == null || !storedType.equalsIgnoreCase(type)))
            continue;
         
         int sx, sy, sz;
         try {
            sx = Integer.parseInt(parts[1].trim());
            sy = Integer.parseInt(parts[2].trim());
            sz = Integer.parseInt(parts[3].trim());
         } catch (Exception e) {
            continue;
         }
         
         final long dx = (long) sx - x;
         final long dy = (long) sy - y;
         final long dz = (long) sz - z;
         final long dist2 = dx * dx + dy * dy + dz * dz;
         
         if (dist2 <= radius2 && dist2 < bestDist2) {
            bestDist2 = dist2;
            bestIdx = i;
         }
      }
      
      return bestIdx;
   }
   private static final Logger _log = Logger.getLogger(AdminSpawnedFakesStorage.class.getName());
   private static final String FILE_PATH = "./config/custom/phantom/AdminSpawnedFakes.txt";

   public static void save(String type, int x, int y, int z) {
      if (!FakePlayerConfig.SAVE_ADMIN_SPAWNED_FAKES)
         return;
      if (isPersistentClanType(type))
         return;
      try (BufferedWriter w = new BufferedWriter(new FileWriter(FILE_PATH, true))) {
         w.write(type + "," + x + "," + y + "," + z);
         w.newLine();
      } catch (Exception e) {
         _log.warning("[AdminSpawnedFakes] Failed to save: " + e.getMessage());
      }
   }

   public static void loadAndSpawn() {
      List<String> lines = readStoredLines();
      if (lines.isEmpty())
         return;

      int count = 0;
      for (String line : lines) {
         String[] parts = line.split(",");
         if (parts.length < 4)
            continue;
         String type = parts[0].trim().toLowerCase();
         int x, y, z;
         try {
            x = Integer.parseInt(parts[1].trim());
            y = Integer.parseInt(parts[2].trim());
            z = Integer.parseInt(parts[3].trim());
         } catch (NumberFormatException e) {
            continue;
         }
         try {
            FakePlayer fake = spawnByType(type, x, y, z);
            if (fake != null)
               count++;
         } catch (Exception e) {
            _log.warning("[AdminSpawnedFakes] Failed to spawn " + type + " at " + x + "," + y + "," + z + ": " + e.getMessage());
         }
      }
      if (count > 0)
         _log.info("[AdminSpawnedFakes] Loaded and spawned " + count + " fake(s).");
   }

   public static void loadAndSpawnGradual(int batchSize, long intervalMs) {
      final List<String> lines = readStoredLines();
      if (lines.isEmpty())
         return;

      final int safeBatch = Math.max(1, batchSize);
      final long safeInterval = Math.max(200L, intervalMs);
      spawnBatch(lines, 0, safeBatch, safeInterval, 0);
   }

   private static void spawnBatch(List<String> lines, int fromIndex, int batchSize, long intervalMs, int totalSpawned) {
      int spawnedInThisBatch = 0;
      int toIndex = Math.min(lines.size(), fromIndex + batchSize);

      for (int i = fromIndex; i < toIndex; i++) {
         String line = lines.get(i);
         String[] parts = line.split(",");
         if (parts.length < 4)
            continue;

         String type = parts[0].trim().toLowerCase();
         int x, y, z;
         try {
            x = Integer.parseInt(parts[1].trim());
            y = Integer.parseInt(parts[2].trim());
            z = Integer.parseInt(parts[3].trim());
         } catch (NumberFormatException e) {
            continue;
         }

         try {
            FakePlayer fake = spawnByType(type, x, y, z);
            if (fake != null)
               spawnedInThisBatch++;
         } catch (Exception e) {
            _log.warning("[AdminSpawnedFakes] Failed to spawn " + type + " at " + x + "," + y + "," + z + ": " + e.getMessage());
         }
      }

      final int nextTotal = totalSpawned + spawnedInThisBatch;
      if (toIndex < lines.size()) {
         ThreadPool.schedule(() -> spawnBatch(lines, toIndex, batchSize, intervalMs, nextTotal), intervalMs);
      } else if (nextTotal > 0) {
         _log.info("[AdminSpawnedFakes] Loaded and spawned " + nextTotal + " fake(s) in startup batches.");
      }
   }

   private static List<String> readStoredLines() {
      File f = new File(FILE_PATH);
      if (!f.exists() || f.length() == 0)
         return java.util.Collections.emptyList();

      List<String> lines = new ArrayList<>();
      boolean sanitized = false;
      try (BufferedReader r = new BufferedReader(new FileReader(f))) {
         String line;
         while ((line = r.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
               if (isPersistentClanTypeLine(line)) {
                  sanitized = true;
                  continue;
               }
               lines.add(line);
            }
         }
      } catch (Exception e) {
         _log.warning("[AdminSpawnedFakes] Failed to load: " + e.getMessage());
         return java.util.Collections.emptyList();
      }

      if (sanitized)
         rewriteStorage(lines);

      return lines;
   }

   private static FakePlayer spawnByType(String type, int x, int y, int z) {
      FakePlayer fake = null;
      boolean clan = type.endsWith("_clan");
      String base = clan ? type.substring(0, type.length() - 5) : type;

      if (clan && !FakePlayerManager.isClanFakeSystemEnabled()) {
         return null;
      }

      switch (base) {
         case "archer":
            fake = clan ? FakePlayerManager.spawnClanArcher(x, y, z) : FakePlayerManager.spawnArcher(x, y, z);
            if (fake != null) fake.setFakePvp(true);
            break;
         case "nuker":
            fake = clan ? FakePlayerManager.spawnClanNuker(x, y, z) : FakePlayerManager.spawnNuker(x, y, z);
            if (fake != null) fake.setFakePvp(true);
            break;
         case "warrior":
            fake = clan ? FakePlayerManager.spawnClanWarrior(x, y, z) : FakePlayerManager.spawnWarrior(x, y, z);
            if (fake != null) fake.setFakePvp(true);
            break;
         case "dagger":
            fake = clan ? FakePlayerManager.spawnClanDagger(x, y, z) : FakePlayerManager.spawnDagger(x, y, z);
            if (fake != null) fake.setFakePvp(true);
            break;
         case "tanker":
            fake = clan ? FakePlayerManager.spawnClanTanker(x, y, z) : FakePlayerManager.spawnTanker(x, y, z);
            if (fake != null) fake.setFakePvp(true);
            break;
         case "healer":
            fake = clan ? FakePlayerManager.spawnClanHealer(x, y, z) : FakePlayerManager.spawnHealer(x, y, z);
            if (fake != null) fake.setFakePvp(true);
            break;
         case "archer_farm":
            fake = clan ? FakePlayerManager.spawnClanArcher(x, y, z) : FakePlayerManager.spawnArcher(x, y, z);
            if (fake != null) fake.setFakeFarm(true);
            break;
         case "nuker_farm":
            fake = clan ? FakePlayerManager.spawnClanNuker(x, y, z) : FakePlayerManager.spawnNuker(x, y, z);
            if (fake != null) fake.setFakeFarm(true);
            break;
         case "warrior_farm":
            fake = clan ? FakePlayerManager.spawnClanWarrior(x, y, z) : FakePlayerManager.spawnWarrior(x, y, z);
            if (fake != null) fake.setFakeFarm(true);
            break;
         case "dagger_farm":
            fake = clan ? FakePlayerManager.spawnClanDagger(x, y, z) : FakePlayerManager.spawnDagger(x, y, z);
            if (fake != null) fake.setFakeFarm(true);
            break;
         case "tanker_farm":
            fake = clan ? FakePlayerManager.spawnClanTanker(x, y, z) : FakePlayerManager.spawnTanker(x, y, z);
            if (fake != null) fake.setFakeFarm(true);
            break;
         case "healer_farm":
            fake = clan ? FakePlayerManager.spawnClanHealer(x, y, z) : FakePlayerManager.spawnHealer(x, y, z);
            if (fake != null) fake.setFakeFarm(true);
            break;
         case "buyer":
            fake = clan ? FakePlayerManager.spawnClanPlayer(x, y, z) : FakePlayerManager.spawnPlayer(x, y, z);
            if (fake != null) fake.setFakeAi(new PrivateStoreBuyAI(fake));
            break;
         case "seller":
            fake = clan ? FakePlayerManager.spawnClanPlayer(x, y, z) : FakePlayerManager.spawnPlayer(x, y, z);
            if (fake != null) fake.setFakeAi(new PrivateStoreSellAI(fake));
            break;
         case "citizen":
            fake = clan ? FakePlayerManager.spawnClanPlayer(x, y, z) : FakePlayerManager.spawnPlayer(x, y, z);
            if (fake != null) fake.setFakeAi(new CitizenAI(fake));
            break;
         default:
            return null;
      }
      if (fake != null && (base.contains("archer") || base.contains("nuker") || base.contains("warrior") || base.contains("dagger") || base.contains("tanker") || base.contains("healer")))
         fake.assignDefaultAI();
      return fake;
   }

   private static boolean isPersistentClanType(String type) {
      return type != null && type.trim().toLowerCase().endsWith("_clan");
   }

   private static boolean isPersistentClanTypeLine(String line) {
      if (line == null || line.isEmpty())
         return false;

      String[] parts = line.split(",");
      if (parts.length == 0)
         return false;

      return isPersistentClanType(parts[0]);
   }

   private static void rewriteStorage(List<String> lines) {
      try (BufferedWriter w = new BufferedWriter(new FileWriter(FILE_PATH, false))) {
         for (String line : lines) {
            w.write(line);
            w.newLine();
         }
      } catch (Exception e) {
         _log.warning("[AdminSpawnedFakes] Failed to sanitize storage: " + e.getMessage());
      }
   }
}
