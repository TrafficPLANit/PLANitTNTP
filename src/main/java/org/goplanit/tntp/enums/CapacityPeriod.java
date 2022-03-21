package org.goplanit.tntp.enums;

public enum CapacityPeriod {
  
  HOUR(1.0),
  DAY_24H(1.0/24.0),
  DAY_12H(1.0/12.0);
  
  
  private final double multiplier;

  CapacityPeriod(double multiplier) {
    this.multiplier = multiplier;
   }

   public double getMultiplier() {
       return multiplier;
   }
}