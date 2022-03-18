package org.goplanit.tntp.enums;

/**
 * Time units helper class with multiplier towards hours
 * 
 * @author gman
 *
 */
public enum TimeUnits {
  HOURS(1.0),
  MINUTES(0.0166666667),
  SECONDS(0.0002777778);
  
  private final double multiplier;

  TimeUnits(double multiplier) {
    this.multiplier = multiplier;
   }

   public double getMultiplier() {
       return multiplier;
   }
}