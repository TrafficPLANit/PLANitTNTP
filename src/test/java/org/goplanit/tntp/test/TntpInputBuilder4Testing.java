package org.goplanit.tntp.test;

import java.util.Map;

import org.goplanit.demands.Demands;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.tntp.enums.CapacityPeriod;
import org.goplanit.tntp.enums.LengthUnits;
import org.goplanit.tntp.enums.NetworkFileColumnType;
import org.goplanit.tntp.enums.SpeedUnits;
import org.goplanit.tntp.input.TntpInputBuilder;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.zoning.Zoning;

/**
 * Input builder exposing internals for testing purposes only within this package
 * 
 * @author markr
 *
 */
public class TntpInputBuilder4Testing extends TntpInputBuilder{
  
  protected MacroscopicNetwork macroscopicNetwork;
  protected Zoning zoning;
  protected Demands demands;
  
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

  public void setCreatedInputs(MacroscopicNetwork macroscopicNetwork, Zoning zoning, Demands demands) {
    this.macroscopicNetwork = macroscopicNetwork;
    this.zoning = zoning;
    this.demands = demands;    
  }
  
  public MacroscopicNetwork getMacroscopicNetwork() {
    return macroscopicNetwork;
  }

  public Zoning getZoning() {
    return zoning;
  }

  public Demands getDemands() {
    return demands;
  }  
  
  
}
