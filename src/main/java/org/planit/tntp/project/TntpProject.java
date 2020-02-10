package org.planit.tntp.project;

import java.util.Map;

import org.planit.exceptions.PlanItException;
import org.planit.project.CustomPlanItProject;
import org.planit.tntp.enums.CapacityPeriod;
import org.planit.tntp.enums.LengthUnits;
import org.planit.tntp.enums.NetworkFileColumns;
import org.planit.tntp.enums.SpeedUnits;
import org.planit.tntp.enums.TimeUnits;
import org.planit.tntp.input.Tntp;

/**
 * TNTP implementation of CustomPlanItProject
 * 
 * @author gman6028
 *
 */
public class TntpProject extends CustomPlanItProject {

 /**
  * Constructor
  * 
  * @param networkFileLocation network input file location
  * @param demandFileLocation demand input file location
  * @param nodeCoordinateFileLocation node coordinate file location
  * @param networkFileColumns Map specifying which columns in the network input file represent which quantities
  * @param speedUnits units of speed
  * @param lengthUnits units of link length
  * @param timeUnits units of free flow travel time
  * @param capacityPeriod time period for link capacity
  * @throws PlanItException thrown if there is an error
  */
  public TntpProject(String networkFileLocation, String demandFileLocation, String nodeCoordinateFileLocation,
      Map<NetworkFileColumns, Integer> networkFileColumns, SpeedUnits speedUnits, LengthUnits lengthUnits,
      TimeUnits timeUnits, CapacityPeriod capacityPeriod) throws PlanItException {
    super(new Tntp(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation, networkFileColumns, speedUnits,
        lengthUnits, timeUnits, capacityPeriod));
  }

  /**
   * Constructor
   * 
   * @param networkFileLocation network input file location
   * @param demandFileLocation demand input file location
   * @param networkFileColumns Map specifying which columns in the network input file represent which quantities
   * @param speedUnits units of speed
   * @param lengthUnits units of link length
   * @param timeUnits units of free flow travel time
  * @param capacityPeriod time period for link capacity
   * @throws PlanItException thrown if there is an error
   */
  public TntpProject(String networkFileLocation, String demandFileLocation,
      Map<NetworkFileColumns, Integer> networkFileColumns, SpeedUnits speedUnits, LengthUnits lengthUnits,
      TimeUnits timeUnits, CapacityPeriod capacityPeriod) throws PlanItException {
    super(new Tntp(networkFileLocation, demandFileLocation, networkFileColumns, speedUnits, lengthUnits, timeUnits, capacityPeriod));
  }

}