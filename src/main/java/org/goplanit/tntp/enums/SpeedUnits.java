package org.goplanit.tntp.enums;

public enum SpeedUnits {
	KM_H(1.0),
	M_SEC(3.6),
	MILES_H(1.61),
	FEET_MIN(0.01829545455);
	
	private final double multiplier;

	SpeedUnits(double multiplier) {
		this.multiplier = multiplier;
	 }

	public double getMultiplier() {
	     return multiplier;
	 }
}