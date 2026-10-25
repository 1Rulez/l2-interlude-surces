package phantom.model;

import com.l2jmega.commons.random.Rnd;

/**
 * Per-bot "character" profile. Every human-ish parameter (reaction latency,
 * error rate, retreat threshold, target switch speed) is a multiplier on top
 * of the global config, so a crowd of fakes has a spread instead of one
 * identical behaviour.
 */
public enum FakePersonality
{
   /** Slow, panicky, forgets shots, retreats early. */
   NEWBIE(2.20, 2.50, 1.35, 900, 0.60),
   /** Average player: slightly sloppy, average reflexes. */
   CASUAL(1.40, 1.40, 1.10, 600, 1.00),
   /** Fast, precise, rarely errs, holds ground. */
   VETERAN(0.70, 0.35, 0.85, 250, 1.20),
   /** Pushes in, low retreat threshold, fights outnumbered. */
   AGRESSOR(0.85, 0.90, 0.70, 300, 1.45),
   /** Kites, retreats early, careful target picking. */
   CAREFUL(1.10, 0.80, 1.45, 700, 0.75),
   /** Laggy / distracted: long delays, random pauses, extra mistakes. */
   DISTRACTED(1.90, 2.10, 1.20, 1100, 0.90);

   private final double _reactionDelayMult;
   private final double _errorRateMult;
   private final double _retreatHpMult;
   private final int _targetSwitchDelayMs;
   private final double _aggression;

   FakePersonality(double reactionDelayMult, double errorRateMult, double retreatHpMult, int targetSwitchDelayMs, double aggression)
   {
      _reactionDelayMult = reactionDelayMult;
      _errorRateMult = errorRateMult;
      _retreatHpMult = retreatHpMult;
      _targetSwitchDelayMs = targetSwitchDelayMs;
      _aggression = aggression;
   }

   public double getReactionDelayMult()
   {
      return _reactionDelayMult;
   }

   public double getErrorRateMult()
   {
      return _errorRateMult;
   }

   public double getRetreatHpMult()
   {
      return _retreatHpMult;
   }

   public int getTargetSwitchDelayMs()
   {
      return _targetSwitchDelayMs;
   }

   public double getAggression()
   {
      return _aggression;
   }

   /** Random per-bot phase offset so a batch spawn does not act in lockstep. */
   public static int randomPhaseOffsetMs()
   {
      return Rnd.get(0, 900);
   }

   /**
    * Weighted roll: mostly average players, few extremes.
    */
   public static FakePersonality roll()
   {
      int r = Rnd.get(100);
      if (r < 12)
      {
         return NEWBIE;
      }
      if (r < 47)
      {
         return CASUAL;
      }
      if (r < 67)
      {
         return VETERAN;
      }
      if (r < 82)
      {
         return AGRESSOR;
      }
      if (r < 92)
      {
         return CAREFUL;
      }
      return DISTRACTED;
   }
}
