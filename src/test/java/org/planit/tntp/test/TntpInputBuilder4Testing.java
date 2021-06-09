package org.planit.tntp.test;

import java.util.Map;

import org.planit.tntp.converter.demands.TntpDemandsReader;
import org.planit.tntp.converter.network.TntpNetworkReader;
import org.planit.tntp.converter.zoning.TntpZoningReader;
import org.planit.tntp.enums.CapacityPeriod;
import org.planit.tntp.enums.LengthUnits;
import org.planit.tntp.enums.NetworkFileColumnType;
import org.planit.tntp.enums.SpeedUnits;
import org.planit.tntp.input.TntpInputBuilder;
import org.planit.utils.exceptions.PlanItException;

/**
 * Input builder exposing internals for testing purposes only within this package
 * 
 * @author markr
 *
 */
public class TntpInputBuilder4Testing extends TntpInputBuilder{
  
  private static final long serialVersionUID = -2291921157378532120L;

  /** Constructor 
   * @param networkFileLocation to use
   * @param demandFileLocation to use
   * @param nodeCoordinateFileLocation to use
   * @param networkFileColumns to use
   * @param speedUnits to use
   * @param lengthUnits to use
   * @param capacityPeriod to use
   * @param defaultMaximumSpeed to use
   * @throws PlanItException thrown if error
   */
  public TntpInputBuilder4Testing(String networkFileLocation, String demandFileLocation,
      String nodeCoordinateFileLocation, Map<NetworkFileColumnType, Integer> networkFileColumns, SpeedUnits speedUnits,
      LengthUnits lengthUnits, CapacityPeriod capacityPeriod, double defaultMaximumSpeed) throws PlanItException {
    super(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation, networkFileColumns, speedUnits, lengthUnits,
        capacityPeriod, defaultMaximumSpeed);
  }

  /** get TNTP network reader instance
   * 
   * @return TNTP network reader
   */
  public TntpNetworkReader getTntpNetworkReader() {
    return super.getTntpNetworkReader();
  }
  
  /** get TNTP zoning reader instance
   * 
   * @return TNTP zoning reader
   */
  public TntpZoningReader getTntpZoningReader() {
    return super.getTntpZoningReader();
  }  
  
  /** get TNTP demands reader instance
   * 
   * @return TNTP demands reader
   */
  public TntpDemandsReader getTntpDemandsReader() {
    return super.getTntpDemandsReader();
  }   

  
}
