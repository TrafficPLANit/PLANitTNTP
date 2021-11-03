package org.goplanit.tntp.converter.network;

import java.util.Map;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.tntp.enums.CapacityPeriod;
import org.goplanit.tntp.enums.LengthUnits;
import org.goplanit.tntp.enums.NetworkFileColumnType;
import org.goplanit.tntp.enums.SpeedUnits;

/**
 * Settings for Tntp network reader
 * 
 * @author gman, markr
 *
 */
public class TntpNetworkReaderSettings implements ConverterReaderSettings {
  
  /** the network to populate */
  private MacroscopicNetwork networkToPopulate;
  
  /**
   * Map specifying which columns in the network file contain which values
   */
  private Map<NetworkFileColumnType, Integer> networkFileColumns;

  /**
   * Units of speed used in network input file
   */
  private SpeedUnits speedUnits;

  /**
   * Units of length used in network input file
   */
  private LengthUnits lengthUnits;

  /**
   * Time period for link capacity, default HOUR
   */
  private CapacityPeriod capacityPeriod = CapacityPeriod.HOUR;  
  
  /**
   * Default maximum speed across links
   */
  private double defaultMaximumSpeed;  

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {

  }
  
  // GETTERS/SETTERS
  
  public Map<NetworkFileColumnType, Integer> getNetworkFileColumns() {
    return networkFileColumns;
  }

  public void setNetworkFileColumns(final Map<NetworkFileColumnType, Integer> networkFileColumns) {
    this.networkFileColumns = networkFileColumns;
  }

  public SpeedUnits getSpeedUnits() {
    return speedUnits;
  }

  public void setSpeedUnits(final SpeedUnits speedUnits) {
    this.speedUnits = speedUnits;
  }

  public LengthUnits getLengthUnits() {
    return lengthUnits;
  }

  public void setLengthUnits(final LengthUnits lengthUnits) {
    this.lengthUnits = lengthUnits;
  }

  public CapacityPeriod getCapacityPeriod() {
    return capacityPeriod;
  }

  public void setCapacityPeriod(final CapacityPeriod capacityPeriod) {
    this.capacityPeriod = capacityPeriod;
  }

  public double getDefaultMaximumSpeed() {
    return defaultMaximumSpeed;
  }

  public void setDefaultMaximumSpeed(double defaultMaximumSpeed) {
    this.defaultMaximumSpeed = defaultMaximumSpeed;
  }

  public MacroscopicNetwork getNetworkToPopulate() {
    return networkToPopulate;
  }

  public void setNetworkToPopulate(final MacroscopicNetwork networkToPopulate) {
    this.networkToPopulate = networkToPopulate;
  }  

}
