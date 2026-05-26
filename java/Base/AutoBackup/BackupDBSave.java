package Base.AutoBackup;

import com.l2jmega.Config;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;

public class BackupDBSave
{
    private static String dumpPath;
    private static String host;
    private static int port;
    private static String user;
    private static String pass;
    private static String dbName;
    private static String outputFolder;
    private static int intervalMinutes;
    private static String dailyTime;
    private static int deleteOlderThanDays;
    private static String discordWebhookUrl;
    private static boolean zipBackup;
    private static boolean isWindows;

    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    public static void init()
    {
        if (!Config.AUTO_BACKUP_ENABLE)
        {
            System.out.println("[AutoBackup] Disabled.");
            return;
        }

        dumpPath = Config.AUTO_BACKUP_DUMP_PATH;
        host = Config.AUTO_BACKUP_DB_HOST;
        port = Config.AUTO_BACKUP_DB_PORT;
        user = Config.AUTO_BACKUP_DB_USER;
        pass = Config.AUTO_BACKUP_DB_PASSWORD;
        dbName = Config.AUTO_BACKUP_DB_NAME;
        outputFolder = Config.AUTO_BACKUP_FOLDER;
        intervalMinutes = Config.AUTO_BACKUP_INTERVAL_MINUTES;
        dailyTime = Config.AUTO_BACKUP_DAILY_TIME;
        deleteOlderThanDays = Config.AUTO_BACKUP_DELETE_OLDER_THAN_DAYS;
        discordWebhookUrl = Config.AUTO_BACKUP_DISCORD_WEBHOOK;
        zipBackup = Config.AUTO_BACKUP_ZIP;

        if (!outputFolder.endsWith(File.separator))
            outputFolder += File.separator;

        detectOS();
        autoDetectDump();
        scheduleBackup();
    }

    private static void detectOS()
    {
        isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        System.out.println("[AutoBackup] OS: " + (isWindows ? "Windows" : "Linux"));
    }

    private static void autoDetectDump()
    {
        if (dumpPath != null && !dumpPath.isEmpty() && new File(dumpPath).exists())
        {
            System.out.println("[AutoBackup] Using dump from config: " + dumpPath);
            return;
        }

        List<File> candidates = new ArrayList<>();

        if (isWindows)
        {
            String[] roots = {System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)")};
            for (String root : roots)
                if (root != null) scanForDump(new File(root), candidates);
        }
        else
        {
            String[] linuxPaths = {"/usr/bin", "/usr/local/bin", "/opt/mysql/bin", "/opt/mariadb/bin"};
            for (String p : linuxPaths)
                scanForDump(new File(p), candidates);
        }

        if (candidates.isEmpty())
            throw new RuntimeException("[AutoBackup] mysqldump / mariadb-dump NOT FOUND!");

        candidates.sort(Comparator.comparingDouble(BackupDBSave::extractVersion).reversed());
        dumpPath = candidates.get(0).getAbsolutePath();
        System.out.println("[AutoBackup] Auto-detected dump: " + dumpPath);
    }

    private static void scanForDump(File dir, List<File> result)
    {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files)
        {
            if (f.isDirectory()) scanForDump(f, result);
            else
            {
                String n = f.getName().toLowerCase();
                if (n.equals("mysqldump.exe") || n.equals("mariadb-dump.exe") ||
                    n.equals("mysqldump") || n.equals("mariadb-dump"))
                    result.add(f);
            }
        }
    }

    private static double extractVersion(File file)
    {
        try
        {
            String path = file.getParentFile().getParent();
            String[] parts = path.split("[^0-9.]");
            for (String p : parts)
                if (!p.isEmpty()) return Double.parseDouble(p);
        }
        catch (Exception ignored) {}
        return 0.0;
    }

    // =========================
    // SCHEDULER WITH ANNOUNCE
    // =========================
    private static void scheduleBackup()
    {
        if (intervalMinutes > 0)
        {
            long nextIntervalMinutes = intervalMinutes;
            System.out.println("[AutoBackup] Next interval backup in: " + nextIntervalMinutes + " minutes");

            scheduler.scheduleAtFixedRate(() ->
            {
                System.out.println("[AutoBackup] Starting interval backup now...");
                try { createBackup(); }
                catch (Exception e) { e.printStackTrace(); }
            }, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
        }

        long delay = computeDelayToNextDailyBackup();
        Calendar nextBackup = Calendar.getInstance();
        nextBackup.add(Calendar.MINUTE, (int) delay);
        System.out.println("[AutoBackup] Next daily backup scheduled at: " +
                new SimpleDateFormat("yyyy-MM-dd HH:mm").format(nextBackup.getTime()));

        scheduler.scheduleAtFixedRate(() ->
        {
            System.out.println("[AutoBackup] Starting daily backup now...");
            try { createBackup(); }
            catch (Exception e) { e.printStackTrace(); }
        }, delay, 1440, TimeUnit.MINUTES);
    }

    private static long computeDelayToNextDailyBackup()
    {
        try
        {
            String[] t = dailyTime.split(":");
            Calendar now = Calendar.getInstance();
            Calendar next = Calendar.getInstance();
            next.set(Calendar.HOUR_OF_DAY, Integer.parseInt(t[0]));
            next.set(Calendar.MINUTE, Integer.parseInt(t[1]));
            next.set(Calendar.SECOND, 0);

            if (next.before(now)) next.add(Calendar.DAY_OF_MONTH, 1);

            return (next.getTimeInMillis() - now.getTimeInMillis()) / 60000;
        }
        catch (Exception e)
        {
            return 60;
        }
    }

    // =========================
    // BACKUP METHOD
    // =========================
    public static void createBackup() throws Exception
    {
        Files.createDirectories(Paths.get(outputFolder));

        String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String sqlFile = outputFolder + time + ".sql";
        String finalFile = sqlFile;

        List<String> cmd = new ArrayList<>();
        cmd.add(dumpPath);
        cmd.add("-h"); cmd.add(host);
        cmd.add("-P"); cmd.add(String.valueOf(port));
        cmd.add("-u" + user);
        cmd.add("-p" + pass);
        cmd.add(dbName);

        System.out.println("[AutoBackup] Backup started: " + sqlFile);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (InputStream is = p.getInputStream();
             FileOutputStream fos = new FileOutputStream(sqlFile))
        {
            is.transferTo(fos);
        }

        if (p.waitFor() == 0)
        {
            System.out.println("[AutoBackup] Backup finished: " + sqlFile);

            if (zipBackup)
            {
                String zip = outputFolder + "Backup_" + time + ".zip";
                zipFile(sqlFile, zip);
                new File(sqlFile).delete();
                finalFile = zip;
                System.out.println("[AutoBackup] ZIP created: " + finalFile);
            }

            cleanupOldBackups();
            sendDiscordMessage("✅ Backup created: `" + finalFile + "`");
        }
        else
        {
            System.out.println("[AutoBackup] Backup FAILED!");
            sendDiscordMessage("❌ Backup FAILED");
        }
    }

    private static void zipFile(String input, String output) throws Exception
    {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output));
             FileInputStream fis = new FileInputStream(input))
        {
            zos.putNextEntry(new ZipEntry(new File(input).getName()));
            fis.transferTo(zos);
            zos.closeEntry();
        }
    }

    private static void cleanupOldBackups()
    {
        File[] files = new File(outputFolder).listFiles();
        if (files == null) return;

        long cutoff = System.currentTimeMillis() - deleteOlderThanDays * 86400000L;

        for (File f : files)
            if (f.lastModified() < cutoff) f.delete();
    }

    @SuppressWarnings("resource")
	private static void sendDiscordMessage(String msg)
    {
        if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) return;

        try
        {
            HttpURLConnection con = (HttpURLConnection)
                    URI.create(discordWebhookUrl).toURL().openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");

            String json = "{\"content\":\"" + msg.replace("\"", "\\\"") + "\"}";
            con.getOutputStream().write(json.getBytes());
            con.getInputStream().close();
        }
        catch (Exception ignored) {}
    }
}