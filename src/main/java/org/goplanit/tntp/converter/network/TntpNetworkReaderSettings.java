package org.goplanit.tntp.converter.network;

import java.util.Map;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.tntp.enums.LengthUnits;
import org.goplanit.tntp.enums.NetworkFileColumnType;
import org.goplanit.tntp.enums.SpeedUnits;
import org.goplanit.tntp.enums.TimeUnits;
import org.goplanit.utils.misc.Pair;

/**
 * Settings for Tntp network reader
 * 
 * @author gman, markr
 *
 */
public class TntpNetworkReaderSettings implements ConverterReaderSettings {
  
  /**
   * network data file
   */
  private String networkFile;
  
  /**
   * node coordinate data file
   */
  private String nodeCoordinateFile;   
  
  /** 
   * coordinate reference system of the source node file
   */
  private String coordinateReferenceSystem;
    
  /**
   * Map specifying which columns in the network file contain which values
   */
  private Map<NetworkFileColumnType, Integer> networkFileColumns;

  /**
   * Units of speed used in network input file
   */
  private SpeedUnits speedUnits = SpeedUnits.KM_H;

  /**
   * Units of length used in network input file
   */
  private LengthUnits lengthUnits = LengthUnits.KM;
  
  /**
   * Units of free flow travel time used in network input file
   */
  private TimeUnits freeFlowTravelTimeUnits = TimeUnits.HOURS;  
      
  /**
   * Time period and unit for link capacity
   */
  private Pair<Double, TimeUnits> capacityPeriod = DEFAULT_TIME_PERIOD_DURATION;  
  
  /**
   * Default maximum speed across links
   */
  private double defaultMaximumSpeed;  
  
  /** default time period duration is set to 1 hour */
  public static Pair<Double, TimeUnits> DEFAULT_TIME_PERIOD_DURATION = Pair.of(1.0, TimeUnits.HOURS);

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

  public TimeUnits getCapacityPeriodUnits() {
    return capacityPeriod.second();
  }
  
  public double getCapacityPeriodDuration() {
    return capacityPeriod.first();
  }  

  public void setCapacityPeriod(final double duration, final TimeUnits units) {
    this.capacityPeriod = Pair.of(duration, units);
  }

  public double getDefaultMaximumSpeed() {
    return defaultMaximumSpeed;
  }

  public void setDefaultMaximumSpeed(double defaultMaximumSpeed) {
    this.defaultMaximumSpeed = defaultMaximumSpeed;
  }

  public String getNetworkFile() {
    return networkFile;
  }

  public void setNetworkFile(String networkFile) {
    this.networkFile = networkFile;
  }

  public String getNodeCoordinateFile() {
    return nodeCoordinateFile;
  }

  public void setNodeCoordinateFile(String nodeCoordinateFile) {
    this.nodeCoordinateFile = nodeCoordinateFile;
  }

  public String getCoordinateReferenceSystem() {
    return coordinateReferenceSystem;
  }

  public void setCoordinateReferenceSystem(String coordinateReferenceSystem) {
    this.coordinateReferenceSystem = coordinateReferenceSystem;
  }

  public TimeUnits getFreeFlowTravelTimeUnits() {
    return freeFlowTravelTimeUnits;
  }

  public void setFreeFlowTravelTimeUnits(TimeUnits freeFlowTravelTimeUnits) {
    this.freeFlowTravelTimeUnits = freeFlowTravelTimeUnits;
  }  

}
