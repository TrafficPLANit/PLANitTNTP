package org.planit.tntp.project;

import java.util.Map;

import org.planit.project.CustomPlanItProject;
import org.planit.tntp.enums.CapacityPeriod;
import org.planit.tntp.enums.LengthUnits;
import org.planit.tntp.enums.NetworkFileColumns;
import org.planit.tntp.enums.SpeedUnits;
import org.planit.tntp.input.TntpInputBuilder;
import org.planit.utils.exceptions.PlanItException;

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
  * @param capacityPeriod time period for link capacity
  * @param defaultMaximumSpeed the default maximum speed along link segments
  * @throws PlanItException thrown if there is an error
  */
  public TntpProject(final String networkFileLocation, final String demandFileLocation, final String nodeCoordinateFileLocation,
      final Map<NetworkFileColumns, Integer> networkFileColumns, final SpeedUnits speedUnits, final LengthUnits lengthUnits,
      final CapacityPeriod capacityPeriod, final double defaultMaximumSpeed) throws PlanItException {
    super(new TntpInputBuilder(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation, networkFileColumns, speedUnits, lengthUnits,capacityPeriod, defaultMaximumSpeed));
  }

  /**
   * Constructor
   *
   * @param networkFileLocation network input file location
   * @param demandFileLocation demand input file location
   * @param networkFileColumns Map specifying which columns in the network input file represent which quantities
   * @param speedUnits units of speed
   * @param lengthUnits units of link length
   * @param capacityPeriod time period for link capacity
   * @param defaultMaximumSpeed the default maximum speed along link segments
   * @throws PlanItException thrown if there is an error
   */
  public TntpProject(final String networkFileLocation, final String demandFileLocation,
      final Map<NetworkFileColumns, Integer> networkFileColumns, final SpeedUnits speedUnits, final LengthUnits lengthUnits,
      final CapacityPeriod capacityPeriod, final double defaultMaximumSpeed) throws PlanItException {
    super(new TntpInputBuilder(networkFileLocation, demandFileLocation, null, networkFileColumns, speedUnits, lengthUnits, capacityPeriod, defaultMaximumSpeed));
  }

  /**
   * Constructor
   *
   * @param tntp Tntp object already instantiated
   */
  public TntpProject(final TntpInputBuilder tntp) {
    super(tntp);
  }

}