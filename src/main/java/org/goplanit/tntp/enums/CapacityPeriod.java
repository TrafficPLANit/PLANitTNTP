package org.goplanit.tntp.enums;

public enum CapacityPeriod {
  
  HOUR(1.0),
  DAY(0.04166667);
  
  private final double multiplier;

  CapacityPeriod(double multiplier) {
    this.multiplier = multiplier;
   }

   public double getMultiplier() {
       return multiplier;
   }
}