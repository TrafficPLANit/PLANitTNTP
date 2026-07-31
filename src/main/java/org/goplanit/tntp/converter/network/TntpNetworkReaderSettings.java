package org.goplanit.tntp.converter.network;

import java.util.Map;
import java.util.logging.Logger;

import org.goplanit.converter.ConverterReaderSettings;
import org.goplanit.tntp.enums.LengthUnits;
import org.goplanit.tntp.enums.NetworkFileColumnType;
import org.goplanit.tntp.enums.SpeedUnits;
import org.goplanit.tntp.enums.TimeUnits;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;

/**
 * Settings for Tntp network reader
 * 
 * @author gman, markr
 *
 */
public class TntpNetworkReaderSettings implements ConverterReaderSettings {

  private static final Logger LOGGER = Logger.getLogger(TntpNetworkReaderSettings.class.getCanonicalName());
  
  /**
   * network data file
   */
  private String networkFile;
  
  /**
   * node coordinate data file
   */
  private String nodeCoordinateFile;

  /**
   * Option to swap node coordinates when processing in case they are provided in non-default order
   */
  private boolean swapNodeCoordinates;
  
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
    //todo
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void logSettings(int level) {
    LOGGER.info(LoggingUtils.settingsHeader("TNTP Network Reader Settings"));
    LOGGER.info(LoggingUtils.settingsValue("Network file", getNetworkFile(), level));
    LOGGER.info(LoggingUtils.settingsValue("Node coordinate file", getNodeCoordinateFile(), level));
    LOGGER.info(LoggingUtils.settingsValue("Coordinate reference system", getCoordinateReferenceSystem(), level));
    LOGGER.info(LoggingUtils.settingsValue("Speed units", getSpeedUnits(), level));
    LOGGER.info(LoggingUtils.settingsValue("Length units", getLengthUnits(), level));
    LOGGER.info(LoggingUtils.settingsValue("Free flow travel time units", getFreeFlowTravelTimeUnits(), level));
    LOGGER.info(LoggingUtils.settingsValue("Capacity period units", getCapacityPeriodUnits(), level));
    LOGGER.info(LoggingUtils.settingsValue("Capacity period duration", getCapacityPeriodDuration(), level));
    LOGGER.info(LoggingUtils.settingsValue("Default max speed", getDefaultMaximumSpeed(), level));
    LOGGER.info(LoggingUtils.settingsValue("Swap node coordinates", isSwapNodeCoordinates(), level));
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

  /**
   * Determine how the capacity is defined in terms of units, e.g., 1, TimeUnits.Hour reflects that capacity is
   * given in PCU volume per (1) hour.
   *
   * @param duration duration of the time period for expressing capacity in the unit provided
   * @param units unit to use for the time period duration
   */
  public void setCapacityPeriod(final Number duration, final TimeUnits units) {
    this.capacityPeriod = Pair.of(duration.doubleValue(), units);
  }

  public double getDefaultMaximumSpeed() {
    return defaultMaximumSpeed;
  }

  public void setDefaultMaximumSpeed(Number defaultMaximumSpeed) {
    this.defaultMaximumSpeed = defaultMaximumSpeed.doubleValue();
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

  public boolean isSwapNodeCoordinates() {
    return swapNodeCoordinates;
  }

  public void setSwapNodeCoordinates(boolean flag){
    this.swapNodeCoordinates = flag;
  }

  /**
   * CRS set
   *
   * @return coordinateReferenceSystem crs
   */
  public String getCoordinateReferenceSystem() {
    return coordinateReferenceSystem;
  }

  /**
   * CRS in EPSG format to use
   *
   * @param coordinateReferenceSystem crs, e.g., "EPSG:26971"
   */
  public void setCoordinateReferenceSystem(String coordinateReferenceSystem) {
    this.coordinateReferenceSystem = coordinateReferenceSystem;
  }

  public TimeUnits getFreeFlowTravelTimeUnits() {
    return freeFlowTravelTimeUnits;
  }

  /**
   * Identify how to interpret the free flow travel time column units in the input file
   *
   * @param freeFlowTravelTimeUnits to use
   */
  public void setFreeFlowTravelTimeUnits(TimeUnits freeFlowTravelTimeUnits) {
    this.freeFlowTravelTimeUnits = freeFlowTravelTimeUnits;
  }

}
