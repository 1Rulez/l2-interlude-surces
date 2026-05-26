package phantom.model;

import com.l2jmega.commons.random.Rnd;

/**
 * Система эмоций для фантомов - имитирует эмоциональное состояние реального игрока.
 * Влияет на поведение в бою, чате и взаимодействии с другими игроками.
 */
public class FakeEmotion {
   
   // Основные параметры эмоций (0-100)
   private int morale;        // Боевой дух
   private int aggression;    // Агрессивность
   private int confidence;    // Уверенность
   private int fear;          // Страх (для ситуации когда HP низко)
   
   // Текущее настроение
   private MoodType currentMood;
   
   // Таймеры и состояния
   private long lastMoodChange;
   private long lastCombatAction;
   private int consecutiveKills;
   private int consecutiveDeaths;
   
   // Эмоциональные триггеры
   private boolean isFrustrated;
   private boolean isExcited;
   private boolean isTired;
   
   public enum MoodType {
      HAPPY("happy", "gg", "ez", "lets go"),
      ANGRY("angry", "wtf", "lag", "hack"),
      CONFIDENT("confident", "too ez", "skill issue", "git gud"),
      FRUSTRATED("frustrated", "cope", "uninstall", "ff"),
      FOCUSED("focused", "concentrate", "lock in", "one more"),
      TIRED("tired", "afk", "brb", "sleep"),
      EXCITED("excited", "OMG", "LETS GOOO", "HYPE"),
      SAD("sad", "rip", "unlucky", "close one"),
      NEUTRAL("neutral", "ok", "sure", "whatever");
      
      private final String name;
      private final String[] phrases;
      
      MoodType(String name, String... phrases) {
         this.name = name;
         this.phrases = phrases;
      }
      
      public String getName() {
         return name;
      }
      
      public String getRandomPhrase() {
         return phrases[Rnd.get(phrases.length)];
      }
   }
   
   public FakeEmotion() {
      this.morale = 50;
      this.aggression = 50;
      this.confidence = 50;
      this.fear = 0;
      this.currentMood = MoodType.NEUTRAL;
      this.lastMoodChange = System.currentTimeMillis();
      this.lastCombatAction = System.currentTimeMillis();
      this.consecutiveKills = 0;
      this.consecutiveDeaths = 0;
      this.isFrustrated = false;
      this.isExcited = false;
      this.isTired = false;
   }
   
   /**
    * Обновление эмоционального состояния
    */
   public void update() {
      long now = System.currentTimeMillis();
      
      // Постепенное восстановление морали
      if (morale < 50 && now - lastCombatAction > 60000) {
         morale = Math.min(100, morale + 1);
      }
      
      // Снижение страха со временем
      if (fear > 0 && now - lastCombatAction > 30000) {
         fear = Math.max(0, fear - 2);
      }
      
      // Проверка на усталость (долгая сессия)
      if (now - lastMoodChange > 3600000) { // 1 час
         isTired = true;
         if (currentMood != MoodType.TIRED) {
            currentMood = MoodType.TIRED;
            lastMoodChange = now;
         }
      }
      
      updateMoodType();
   }
   
   /**
    * Обновление типа настроения на основе параметров
    */
   private void updateMoodType() {
      if (isTired && morale < 30) {
         currentMood = MoodType.TIRED;
      } else if (consecutiveKills >= 3 && confidence > 70) {
         currentMood = MoodType.EXCITED;
         isExcited = true;
      } else if (consecutiveDeaths >= 3) {
         currentMood = MoodType.FRUSTRATED;
         isFrustrated = true;
      } else if (morale > 70 && confidence > 60) {
         currentMood = MoodType.CONFIDENT;
      } else if (morale < 30) {
         currentMood = MoodType.SAD;
      } else if (aggression > 70) {
         currentMood = MoodType.ANGRY;
      } else if (fear > 50) {
         currentMood = MoodType.FOCUSED; // Паника
      } else if (morale > 60) {
         currentMood = MoodType.HAPPY;
      } else {
         currentMood = MoodType.NEUTRAL;
      }
   }
   
   // ==================== События ====================
   
   /**
    * Обработка убийства цели
    * @param isPlayer true if killed target is a player
    */
   public void onKill(boolean isPlayer) {
      consecutiveKills++;
      consecutiveDeaths = 0;
      
      if (isPlayer) {
         morale = Math.min(100, morale + 10);
         confidence = Math.min(100, confidence + 8);
         aggression = Math.min(100, aggression + 5);
      } else {
         morale = Math.min(100, morale + 3);
         confidence = Math.min(100, confidence + 2);
      }
      
      isFrustrated = false;
      lastCombatAction = System.currentTimeMillis();
      updateMoodType();
   }
   
   /**
    * Обработка смерти
    * @param killedByPlayer true if killer is a player
    */
   public void onDeath(boolean killedByPlayer) {
      consecutiveDeaths++;
      consecutiveKills = 0;
      
      if (killedByPlayer) {
         morale = Math.max(0, morale - 15);
         confidence = Math.max(0, confidence - 12);
         fear = Math.min(100, fear + 20);
         
         if (consecutiveDeaths >= 2) {
            isFrustrated = true;
         }
      } else {
         morale = Math.max(0, morale - 8);
         confidence = Math.max(0, confidence - 5);
      }
      
      lastCombatAction = System.currentTimeMillis();
      updateMoodType();
   }
   
   /**
    * Обработка получения урона
    * @param damagePercent damage percent (0.0-1.0)
    */
   public void onDamageReceived(double damagePercent) {
      if (damagePercent > 0.5) { // Получил больше 50% HP урона
         fear = Math.min(100, fear + 15);
         aggression = Math.min(100, aggression + 10);
      }
      
      if (damagePercent > 0.8) { // Почти умер
         confidence = Math.max(0, confidence - 10);
      }
      
      lastCombatAction = System.currentTimeMillis();
   }
   
   /**
    * Обработка нанесения урона
    * @param damagePercent damage percent (0.0-1.0)
    */
   public void onDamageDealt(double damagePercent) {
      if (damagePercent > 0.7) { // Нанес много урона
         confidence = Math.min(100, confidence + 5);
         aggression = Math.min(100, aggression + 3);
      }
      
      lastCombatAction = System.currentTimeMillis();
   }
   
   /**
    * Обработка получения хила
    * @param healPercent heal percent (0.0-1.0)
    */
   public void onHealReceived(double healPercent) {
      if (healPercent > 0.5) {
         morale = Math.min(100, morale + 3);
         fear = Math.max(0, fear - 10);
      }
   }
   
   /**
    * Обработка сообщения в чате
    */
   public void onChatMessage() {
      // Reserved hook for future chat-driven emotion reactions.
   }
   
   // ==================== Геттеры ====================
   
   public int getMorale() {
      return morale;
   }
   
   public int getAggression() {
      return aggression;
   }
   
   public int getConfidence() {
      return confidence;
   }
   
   public int getFear() {
      return fear;
   }
   
   public MoodType getCurrentMood() {
      return currentMood;
   }
   
   public String getMoodName() {
      return currentMood.getName();
   }
   
   public String getMoodPhrase() {
      return currentMood.getRandomPhrase();
   }
   
   public boolean isFrustrated() {
      return isFrustrated;
   }
   
   public boolean isExcited() {
      return isExcited;
   }
   
   public boolean isTired() {
      return isTired;
   }
   
   public int getConsecutiveKills() {
      return consecutiveKills;
   }
   
   public int getConsecutiveDeaths() {
      return consecutiveDeaths;
   }
   
   public long getLastCombatAction() {
      return lastCombatAction;
   }
   
   // ==================== Сеттеры ====================
   
   public void setAggression(int aggression) {
      this.aggression = aggression;
   }
   
   public void reset() {
      morale = 50;
      aggression = 50;
      confidence = 50;
      fear = 0;
      consecutiveKills = 0;
      consecutiveDeaths = 0;
      isFrustrated = false;
      isExcited = false;
      isTired = false;
      currentMood = MoodType.NEUTRAL;
      lastMoodChange = System.currentTimeMillis();
   }
   
   /**
    * Получить модификатор урона на основе эмоций
    * (агрессивные игроки наносят больше урона, напуганные - меньше)
    * @return damage multiplier
    */
   public double getDamageModifier() {
      double modifier = 1.0;
      
      if (aggression > 70) {
         modifier += 0.1; // +10% урона
      } else if (aggression < 30) {
         modifier -= 0.1; // -10% урона
      }
      
      if (fear > 50) {
         modifier -= 0.15; // -15% урона от страха
      }
      
      if (confidence > 70) {
         modifier += 0.05; // +5% урона от уверенности
      }
      
      return modifier;
   }
   
   /**
    * Получить модификатор точности на основе эмоций
    * @return accuracy multiplier
    */
   public double getAccuracyModifier() {
      double modifier = 1.0;
      
      if (fear > 60) {
         modifier -= 0.2; // -20% точности от страха
      }
      
      if (confidence > 70) {
         modifier += 0.1; // +10% точности от уверенности
      }
      
      if (isFrustrated) {
         modifier -= 0.15; // -15% точности от фрустрации
      }
      
      return modifier;
   }
   
   /**
    * Шанс использования эскейпа/отступления
    * @return escape chance in percent
    */
   public int getEscapeChance() {
      if (fear > 70) {
         return 50; // 50% шанс сбежать
      } else if (fear > 50) {
         return 25;
      } else if (morale < 30) {
         return 20;
      }
      return 5; // Базовый 5%
   }
   
   /**
    * Шанс агрессивного поведения (все-ин)
    * @return aggressive action chance in percent
    */
   public int getAggressiveChance() {
      if (aggression > 80 && confidence > 60) {
         return 40;
      } else if (aggression > 60) {
         return 20;
      }
      return 5;
   }
}
