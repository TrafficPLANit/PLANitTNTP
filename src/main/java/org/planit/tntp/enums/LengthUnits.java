package org.planit.tntp.enums;

public enum LengthUnits {

	KM(1.0),
	M(0.001),
	MILES(1.61),
	FEET(0.00030492424);
	
	private final double multiplier;

	LengthUnits(double multiplier) {
		this.multiplier = multiplier;
	 }

	 public double getMultiplier() {
	     return multiplier;
	 }
}