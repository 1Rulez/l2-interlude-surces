package phantom.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.serverpackets.CreatureSay;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;

/**
 * Продвинутая система чата для фантомов.
 * Реализует: context-aware ответы, память разговоров, сленг
 */
@SuppressWarnings({"javadoc", "unused", "static-method"})
public class AdvancedChatManager {
   
   // Типы контекстных триггеров
   public enum ContextType {
      GREETING,           // Приветствия
      FAREWELL,           // Прощания
      QUESTION,           // Вопросы
      TRADE,              // Торговля
      PVP,                // PvP ситуации
      DEATH,              // Смерть
      KILL,               // Убийство
      BUFF,               // Баффы
      PARTY,              // Партия
      CLAN,               // Клан
      LOCATION,           // Локации
      ITEM,               // Предметы
      SKILL,              // Скиллы
      COMPLIMENT,         // Комплименты
      INSULT,             // Оскорбления
      HELP,               // Просьбы о помощи
      GENERAL             // Общие фразы
   }
   
   // Запись разговора
   public static class ConversationEntry {
      public long timestamp;
      public String message;
      public boolean fromPlayer; // true = от игрока, false = от фантома
      public ContextType context;
      
      public ConversationEntry(String message, boolean fromPlayer, ContextType context) {
         this.timestamp = System.currentTimeMillis();
         this.message = message;
         this.fromPlayer = fromPlayer;
         this.context = context;
      }
   }
   
   // Состояние разговора
   public static class ConversationState {
      public List<ConversationEntry> history;
      public long lastPlayerMessage;
      public long lastFakeMessage;
      public int messageCount;
      public ContextType lastContext;
      public String currentTopic;
      public Map<String, Object> contextData;
      
      public ConversationState() {
         this.history = new ArrayList<>();
         this.lastPlayerMessage = System.currentTimeMillis();
         this.lastFakeMessage = 0;
         this.messageCount = 0;
         this.lastContext = ContextType.GENERAL;
         this.currentTopic = null;
         this.contextData = new HashMap<>();
      }
      
      public void addEntry(String message, boolean fromPlayer, ContextType context) {
         history.add(new ConversationEntry(message, fromPlayer, context));
         
         // Хранить последние 20 сообщений
         if (history.size() > 20) {
            history.remove(0);
         }
         
         messageCount++;
         lastContext = context;
         
         if (fromPlayer) {
            lastPlayerMessage = System.currentTimeMillis();
         } else {
            lastFakeMessage = System.currentTimeMillis();
         }
      }
      
      public boolean hasRecentMessage(long timeoutMs) {
         return System.currentTimeMillis() - lastPlayerMessage < timeoutMs;
      }
      
      public String getLastPlayerMessage() {
         for (int i = history.size() - 1; i >= 0; i--) {
            ConversationEntry entry = history.get(i);
            if (entry.fromPlayer) {
               return entry.message;
            }
         }
         return null;
      }
      
      public List<String> getPlayerMessages(int count) {
         List<String> messages = new ArrayList<>();
         for (int i = history.size() - 1; i >= 0 && messages.size() < count; i--) {
            if (history.get(i).fromPlayer) {
               messages.add(0, history.get(i).message);
            }
         }
         return messages;
      }
   }
   
   // Контекстные фразы
   private static Map<ContextType, List<String>> _contextPhrases;
   private static Map<String, List<String>> _keywordResponses;
   private static Map<Integer, ConversationState> _conversations;
   private static Map<Integer, Map<String, Integer>> _playerKeywords;
   
   private static final int SAY2_TELL = 2;
   private static final int SAY2_PARTY = 3;
   private static final int SAY2_SHOUT = 1;
   private static final int SAY2_CLAN = 11;
   
   public AdvancedChatManager() {
      _conversations = new ConcurrentHashMap<>();
      _playerKeywords = new ConcurrentHashMap<>();
      initializeContextPhrases();
      initializeKeywordResponses();
   }
   
   /**
    * Инициализация контекстных фраз
    */
   private void initializeContextPhrases() {
      _contextPhrases = new HashMap<>();
      
      // GREETING
      List<String> greetings = new ArrayList<>();
      greetings.add("yo bro");
      greetings.add("hey whats up");
      greetings.add("sup");
      greetings.add("hey there");
      greetings.add("yo yo");
      greetings.add("hows it going");
      greetings.add("whats good");
      greetings.add("hey hey");
      greetings.add("wsg");
      greetings.add("yo fam");
      _contextPhrases.put(ContextType.GREETING, greetings);
      
      // FAREWELL
      List<String> farewells = new ArrayList<>();
      farewells.add("cya later");
      farewells.add("peace out");
      farewells.add("gg");
      farewells.add("catch you later");
      farewells.add("im out");
      farewells.add("gotta go");
      farewells.add("brb");
      farewells.add("afk");
      farewells.add("ttyl");
      farewells.add("later bro");
      _contextPhrases.put(ContextType.FAREWELL, farewells);
      
      // QUESTION
      List<String> questions = new ArrayList<>();
      questions.add("hmm good question");
      questions.add("let me think");
      questions.add("not sure tbh");
      questions.add("idk man");
      questions.add("good point");
      questions.add("what do you think?");
      questions.add("depends tbh");
      questions.add("why u asking?");
      questions.add("does it matter?");
      questions.add("honestly no idea");
      _contextPhrases.put(ContextType.QUESTION, questions);
      
      // TRADE
      List<String> trades = new ArrayList<>();
      trades.add("what u selling?");
      trades.add("im buying");
      trades.add("whats the price?");
      trades.add("too expensive bro");
      trades.add("can u discount?");
      trades.add("pm me prices");
      trades.add("add me for trade");
      trades.add("wts anything good?");
      trades.add("wtb cheap");
      trades.add("u have donate items?");
      _contextPhrases.put(ContextType.TRADE, trades);
      
      // PVP
      List<String> pvps = new ArrayList<>();
      pvps.add("lets duel");
      pvps.add("1v1 me");
      pvps.add("u scared?");
      pvps.add("come pvp zone");
      pvps.add("skill issue lol");
      pvps.add("git gud");
      pvps.add("too ez");
      pvps.add("nice try");
      pvps.add("lag excuse?");
      pvps.add("rematch?");
      _contextPhrases.put(ContextType.PVP, pvps);
      
      // DEATH
      List<String> deaths = new ArrayList<>();
      deaths.add("rip");
      deaths.add("unlucky");
      deaths.add("close one");
      deaths.add("lag...");
      deaths.add("hacker probably");
      deaths.add("my bad");
      deaths.add("they got lucky");
      deaths.add("ffs");
      deaths.add("bruh");
      deaths.add("smh");
      _contextPhrases.put(ContextType.DEATH, deaths);
      
      // KILL
      List<String> kills = new ArrayList<>();
      kills.add("ez clap");
      kills.add("too easy");
      kills.add("get rekt");
      kills.add("skill diff");
      kills.add("gg");
      kills.add("w");
      kills.add("lets gooo");
      kills.add("another one");
      kills.add("dont cry");
      kills.add("touch grass");
      _contextPhrases.put(ContextType.KILL, kills);
      
      // BUFF
      List<String> buffs = new ArrayList<>();
      buffs.add("need buffs?");
      buffs.add("come get buffs");
      buffs.add("free buffs here");
      buffs.add("buff plz");
      buffs.add("u buffed?");
      buffs.add("need HE");
      buffs.add("need acumen");
      buffs.add("buff me plz");
      buffs.add("got buffs?");
      buffs.add("buffs for adena");
      _contextPhrases.put(ContextType.BUFF, buffs);
      
      // PARTY
      List<String> partys = new ArrayList<>();
      partys.add("lf party");
      partys.add("join my party");
      partys.add("need healer");
      partys.add("need tank");
      partys.add("lvl party?");
      partys.add("farm party");
      partys.add("rb party");
      partys.add("party for dv");
      partys.add("who wants to party?");
      partys.add("party up");
      _contextPhrases.put(ContextType.PARTY, partys);
      
      // CLAN
      List<String> clans = new ArrayList<>();
      clans.add("recruiting for clan");
      clans.add("join our clan");
      clans.add("active clan");
      clans.add("clan wars");
      clans.add("siege clan");
      clans.add("need clan members");
      clans.add("clan lvl?");
      clans.add("what clan u in?");
      clans.add("looking for clan");
      clans.add("best clan on server");
      _contextPhrases.put(ContextType.CLAN, clans);
      
      // LOCATION
      List<String> locations = new ArrayList<>();
      locations.add("where u at?");
      locations.add("come to giran");
      locations.add("meet at aden");
      locations.add("im at dv");
      locations.add("farm spot?");
      locations.add("where to farm?");
      locations.add("good spot here");
      locations.add("come pvp zone");
      locations.add("im in town");
      locations.add("at rb spawn");
      _contextPhrases.put(ContextType.LOCATION, locations);
      
      // ITEM
      List<String> items = new ArrayList<>();
      items.add("nice item");
      items.add("where u get that?");
      items.add("how much for that?");
      items.add("selling?");
      items.add("wtb");
      items.add("wts");
      items.add("need that item");
      items.add("got spare?");
      items.add("item drop?");
      items.add("rare item");
      _contextPhrases.put(ContextType.ITEM, items);
      
      // COMPLIMENT
      List<String> compliments = new ArrayList<>();
      compliments.add("nice gear bro");
      compliments.add("u cracked");
      compliments.add("goated");
      compliments.add("respect");
      compliments.add("well played");
      compliments.add("good stuff");
      compliments.add("cool");
      compliments.add("awesome");
      compliments.add("impressive");
      compliments.add("mad skills");
      _contextPhrases.put(ContextType.COMPLIMENT, compliments);
      
      // INSULT (mild, no slurs)
      List<String> insults = new ArrayList<>();
      insults.add("skill issue");
      insults.add("cope harder");
      insults.add("stay mad");
      insults.add("touch grass");
      insults.add("get good");
      insults.add("noob");
      insults.add("bot behavior");
      insults.add("lag excuse again?");
      insults.add("uninstall maybe?");
      insults.add("cry more");
      _contextPhrases.put(ContextType.INSULT, insults);
      
      // HELP
      List<String> helps = new ArrayList<>();
      helps.add("can u help me?");
      helps.add("need help");
      helps.add("pls assist");
      helps.add("someone help");
      helps.add("stuck here");
      helps.add("need assistance");
      helps.add("can someone buff?");
      helps.add("need rez");
      helps.add("help plz");
      helps.add("anyone online?");
      _contextPhrases.put(ContextType.HELP, helps);
      
      // GENERAL
      List<String> generals = new ArrayList<>();
      generals.add("lol");
      generals.add("lmao");
      generals.add("fr fr");
      generals.add("no cap");
      generals.add("bet");
      generals.add("aight");
      generals.add("say less");
      generals.add("facts");
      generals.add("mood");
      generals.add("same bro");
      generals.add("relatable");
      generals.add("real");
      generals.add("true");
      generals.add("agreed");
      generals.add("exactly");
      generals.add("yea");
      generals.add("nah");
      generals.add("maybe");
      generals.add("sure");
      generals.add("why not");
      generals.add("lets go");
      generals.add("nice one");
      generals.add("W");
      generals.add("L");
      generals.add("W server");
      generals.add("L balance");
      generals.add("this server slaps");
      generals.add("gmshop is cracked");
      generals.add("pvp is mid");
      generals.add("rates are good");
      generals.add("community is chill");
      generals.add("toxic lobbies");
      generals.add("im addicted");
      generals.add("one more hour");
      generals.add("its 4am send help");
      generals.add("sleep is for the weak");
      _contextPhrases.put(ContextType.GENERAL, generals);
   }
   
   /**
    * Инициализация ключевых слов для ответов
    */
   private void initializeKeywordResponses() {
      _keywordResponses = new HashMap<>();
      
      // Приветствия
      List<String> hiResponses = new ArrayList<>();
      hiResponses.add("yo whats up");
      hiResponses.add("hey bro");
      hiResponses.add("sup");
      hiResponses.add("hey there");
      _keywordResponses.put("hi", hiResponses);
      _keywordResponses.put("hey", hiResponses);
      _keywordResponses.put("hello", hiResponses);
      _keywordResponses.put("yo", hiResponses);
      _keywordResponses.put("sup", hiResponses);
      
      // Вопросы "где"
      List<String> whereResponses = new ArrayList<>();
      whereResponses.add("im at giran usually");
      whereResponses.add("farm spot is dv");
      whereResponses.add("pvp zone is hot");
      whereResponses.add("try aden castle");
      whereResponses.add("check gmshop npc");
      _keywordResponses.put("where", whereResponses);
      _keywordResponses.put("whereto", whereResponses);
      
      // Вопросы "как"
      List<String> howResponses = new ArrayList<>();
      howResponses.add("just farm daily");
      howResponses.add("grind mostly");
      howResponses.add("pvp zone helps");
      howResponses.add("gmshop is op");
      howResponses.add("dont give up");
      _keywordResponses.put("how", howResponses);
      
      // Торговля
      List<String> tradeResponses = new ArrayList<>();
      tradeResponses.add("check my store");
      tradeResponses.add("pm for prices");
      tradeResponses.add("wtb or wts?");
      tradeResponses.add("add me");
      _keywordResponses.put("sell", tradeResponses);
      _keywordResponses.put("buy", tradeResponses);
      _keywordResponses.put("trade", tradeResponses);
      _keywordResponses.put("price", tradeResponses);
      
      // Фарм
      List<String> farmResponses = new ArrayList<>();
      farmResponses.add("dv is good");
      farmResponses.add("try giant cave");
      farmResponses.add("farm zone event");
      farmResponses.add("antharas lair");
      farmResponses.add("valakas is ok");
      _keywordResponses.put("farm", farmResponses);
      _keywordResponses.put("grind", farmResponses);
      _keywordResponses.put("adena", farmResponses);
      
      // PvP
      List<String> pvpResponses = new ArrayList<>();
      pvpResponses.add("come pvp zone");
      pvpResponses.add("1v1 me");
      pvpResponses.add("skill issue lol");
      pvpResponses.add("lets duel");
      _keywordResponses.put("pvp", pvpResponses);
      _keywordResponses.put("duel", pvpResponses);
      _keywordResponses.put("fight", pvpResponses);
      
      // Смерть
      List<String> deathResponses = new ArrayList<>();
      deathResponses.add("rip bro");
      deathResponses.add("unlucky");
      deathResponses.add("lag probably");
      deathResponses.add("they got lucky");
      _keywordResponses.put("die", deathResponses);
      _keywordResponses.put("dead", deathResponses);
      _keywordResponses.put("rip", deathResponses);
      _keywordResponses.put("kill", deathResponses);
      
      // Баффы
      List<String> buffResponses = new ArrayList<>();
      buffResponses.add("come get buffs");
      buffResponses.add("free buffs here");
      buffResponses.add("need what?");
      buffResponses.add("buffs for adena");
      _keywordResponses.put("buff", buffResponses);
      _keywordResponses.put("he", buffResponses);
      _keywordResponses.put("acumen", buffResponses);
      
      // Клан
      List<String> clanResponses = new ArrayList<>();
      clanResponses.add("recruiting active players");
      clanResponses.add("our clan does siege");
      clanResponses.add("join us");
      clanResponses.add("we need members");
      _keywordResponses.put("clan", clanResponses);
      _keywordResponses.put("recruit", clanResponses);
      _keywordResponses.put("guild", clanResponses);
   }
   
   /**
    * Обработка сообщения от игрока
    */
   public void onPlayerMessage(Player player, FakePlayer fake, String message) {
      if (player == null || fake == null || message == null) {
         return;
      }
      
      int fakeId = fake.getObjectId();
      int playerId = player.getObjectId();
      
      // Получить или создать состояние разговора
      ConversationState state = _conversations.computeIfAbsent(fakeId, k -> new ConversationState());
      
      // Определить контекст сообщения
      ContextType context = detectContext(message);
      
      // Добавить сообщение в историю
      state.addEntry(message, true, context);
      
      // Сохранить ключевые слова игрока
      trackPlayerKeywords(playerId, message);
      
      // Запланировать ответ
      scheduleResponse(player, fake, message, context, state);
   }
   
   /**
    * Определить контекст сообщения
    */
   private ContextType detectContext(String message) {
      String lower = message.toLowerCase();
      
      // Приветствия
      if (lower.matches("^(hi|hey|hello|yo|sup|wsg).*$")) {
         return ContextType.GREETING;
      }
      
      // Прощания
      if (lower.matches(".*(bye|cya|later|gg|afk|brb|out).*$")) {
         return ContextType.FAREWELL;
      }
      
      // Вопросы
      if (lower.matches("^(what|where|when|why|how|who|is|are|can|do|does).*$") || 
          lower.contains("?")) {
         return ContextType.QUESTION;
      }
      
      // Торговля
      if (lower.contains("sell") || lower.contains("buy") || lower.contains("trade") || 
          lower.contains("wts") || lower.contains("wtb") || lower.contains("price") ||
          lower.contains("adena") || lower.contains("coin")) {
         return ContextType.TRADE;
      }
      
      // PvP
      if (lower.contains("pvp") || lower.contains("duel") || lower.contains("fight") ||
          lower.contains("1v1") || lower.contains("skill") || lower.contains("lag")) {
         return ContextType.PVP;
      }
      
      // Смерть
      if (lower.contains("die") || lower.contains("dead") || lower.contains("rip") ||
          lower.contains("kill") || lower.contains("unlucky")) {
         return ContextType.DEATH;
      }
      
      // Баффы
      if (lower.contains("buff") || lower.contains("he") || lower.contains("acumen") ||
          lower.contains("bless") || lower.contains("might")) {
         return ContextType.BUFF;
      }
      
      // Партия
      if (lower.contains("party") || lower.contains("lf") || lower.contains("need healer") ||
          lower.contains("need tank")) {
         return ContextType.PARTY;
      }
      
      // Клан
      if (lower.contains("clan") || lower.contains("recruit") || lower.contains("guild") ||
          lower.contains("siege")) {
         return ContextType.CLAN;
      }
      
      // Локации
      if (lower.contains("where") || lower.contains("at ") || lower.contains("girvan") ||
          lower.contains("aden") || lower.contains("dv") || lower.contains("farm")) {
         return ContextType.LOCATION;
      }
      
      // Предметы
      if (lower.contains("item") || lower.contains("gear") || lower.contains("weapon") ||
          lower.contains("armor") || lower.contains("drop")) {
         return ContextType.ITEM;
      }
      
      // Комплименты
      if (lower.contains("nice") || lower.contains("cool") || lower.contains("good") ||
          lower.contains("awesome") || lower.contains("respect")) {
         return ContextType.COMPLIMENT;
      }
      
      // Оскорбления
      if (lower.contains("noob") || lower.contains("bad") || lower.contains("hack") ||
          lower.contains("bot") || lower.contains("trash")) {
         return ContextType.INSULT;
      }
      
      // Помощь
      if (lower.contains("help") || lower.contains("assist") || lower.contains("pls") ||
          lower.contains("please") || lower.contains("need")) {
         return ContextType.HELP;
      }
      
      return ContextType.GENERAL;
   }
   
   /**
    * Запланировать ответ фантома
    */
   private void scheduleResponse(Player player, FakePlayer fake, String message, 
                                  ContextType context, ConversationState state) {
      // Проверить шанс ответа
      if (Rnd.get(100) >= FakePlayerConfig.FAKE_TELL_RESPOND_CHANCE) {
         return;
      }
      
      // Задержка перед ответом (имитация набора текста)
      int delayMs = Rnd.get(2000, 8000);
      
      ThreadPool.schedule(() -> {
         // Проверить, онлайн ли еще игрок
         if (!player.isOnline() || fake.isDead()) {
            return;
         }
         
         // Сгенерировать ответ
         String response = generateResponse(message, context, state);
         
         if (response != null && !response.isEmpty()) {
            // Отправить ответ
            player.sendPacket(new CreatureSay(fake.getObjectId(), SAY2_TELL, fake.getName(), response));
            
            // Добавить в историю
            state.addEntry(response, false, context);
            
            fake.getEmotion().onChatMessage();
         }
      }, delayMs);
   }
   
   /**
    * Сгенерировать ответ на сообщение
    */
   private String generateResponse(String message, ContextType context, ConversationState state) {
      String lower = message.toLowerCase();
      
      // Проверить ключевые слова
      for (Map.Entry<String, List<String>> entry : _keywordResponses.entrySet()) {
         if (lower.contains(entry.getKey())) {
            List<String> responses = entry.getValue();
            return responses.get(Rnd.get(responses.size()));
         }
      }
      
      // Ответ по контексту
      List<String> contextResponses = _contextPhrases.get(context);
      if (contextResponses != null && !contextResponses.isEmpty()) {
         return contextResponses.get(Rnd.get(contextResponses.size()));
      }
      
      // Универсальные ответы
      List<String> general = _contextPhrases.get(ContextType.GENERAL);
      return general.get(Rnd.get(general.size()));
   }
   
   /**
    * Отслеживать ключевые слова игрока
    */
   private void trackPlayerKeywords(int playerId, String message) {
      Map<String, Integer> keywords = _playerKeywords.computeIfAbsent(playerId, k -> new HashMap<>());
      
      String[] words = message.toLowerCase().split("\\s+");
      for (String word : words) {
         if (word.length() > 3) {
            keywords.put(word, keywords.getOrDefault(word, 0) + 1);
         }
      }
   }
   
   /**
    * Получить тему разговора для игрока
    */
   public String getConversationTopic(int playerId) {
      Map<String, Integer> keywords = _playerKeywords.get(playerId);
      if (keywords == null || keywords.isEmpty()) {
         return null;
      }
      
      // Найти наиболее частое ключевое слово
      String topKeyword = null;
      int maxCount = 0;
      
      for (Map.Entry<String, Integer> entry : keywords.entrySet()) {
         if (entry.getValue() > maxCount) {
            maxCount = entry.getValue();
            topKeyword = entry.getKey();
         }
      }
      
      return topKeyword;
   }
   
   /**
    * Обработка смерти игрока рядом
    */
   public void onPlayerDiedNearby(Player player, FakePlayer fake) {
      if (Rnd.get(100) >= 30) { // 30% шанс
         return;
      }
      
      return;
   }
   
   /**
    * Обработка убийства игрока
    */
   public void onPlayerKilled(Player killer, Player victim, FakePlayer fake) {
      if (fake == killer && Rnd.get(100) >= 40) { // 40% шанс
         return;
      }
      
      return;
   }
   
   /**
    * Очистить старые разговоры
    */
   public void cleanupOldConversations() {
      long timeoutMs = FakePlayerConfig.FAKE_TELL_CONVERSATION_TIMEOUT * 1000L;
      long now = System.currentTimeMillis();
      
      _conversations.entrySet().removeIf(entry -> 
         now - entry.getValue().lastPlayerMessage > timeoutMs);
   }
   
   /**
    * Получить состояние разговора
    */
   public ConversationState getConversation(int fakeId) {
      return _conversations.get(fakeId);
   }
   
   /**
    * Удалить разговор
    */
   public void removeConversation(int fakeId) {
      _conversations.remove(fakeId);
   }
}
