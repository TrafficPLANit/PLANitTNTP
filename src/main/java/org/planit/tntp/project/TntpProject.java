package org.planit.tntp.project;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.planit.exceptions.PlanItException;
import org.planit.project.CustomPlanItProject;
import org.planit.tntp.enums.CapacityPeriod;
import org.planit.tntp.enums.LengthUnits;
import org.planit.tntp.enums.NetworkFileColumns;
import org.planit.tntp.enums.SpeedUnits;
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
  * @param capacityPeriod time period for link capacity
  * @throws PlanItException thrown if there is an error
  */
  public TntpProject(final String networkFileLocation, final String demandFileLocation, final String nodeCoordinateFileLocation,
      final Map<NetworkFileColumns, Integer> networkFileColumns, final SpeedUnits speedUnits, final LengthUnits lengthUnits,
      final CapacityPeriod capacityPeriod, final double defaultMaximumSpeed) throws PlanItException {
    super(new Tntp(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation, networkFileColumns, speedUnits, lengthUnits,capacityPeriod, defaultMaximumSpeed));
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
   * @throws PlanItException thrown if there is an error
   */
  public TntpProject(final String networkFileLocation, final String demandFileLocation,
      final Map<NetworkFileColumns, Integer> networkFileColumns, final SpeedUnits speedUnits, final LengthUnits lengthUnits,
      final CapacityPeriod capacityPeriod, final double defaultMaximumSpeed) throws PlanItException {
    super(new Tntp(networkFileLocation, demandFileLocation, null, networkFileColumns, speedUnits, lengthUnits, capacityPeriod, defaultMaximumSpeed));
  }


/**
 * Store the standard results given for testing
 *
 * @param standardResultsFileLocation location of file containing standard results
 * @return Map of containing flow and cost values for each upstream and downstream node
 * @throws PlanItException thrown if there is an error
 */
  public Map<Long, Map<Long, double[]>> createStandardResultsFile(final String standardResultsFileLocation) throws PlanItException  {
    final Map<Long, Map<Long, double[]>> resultsMap = new HashMap<Long, Map<Long, double[]>>();
    try (Scanner scanner = new Scanner(new File(standardResultsFileLocation).getCanonicalFile())) {
      String line = scanner.nextLine();
      while (scanner.hasNextLine()) {
        line = scanner.nextLine().trim();
        final String[] cols = line.split("\\s+");
        final long upstreamNodeExternalId = Long.parseLong(cols[0]);
        if (!resultsMap.containsKey(upstreamNodeExternalId)) {
          resultsMap.put(upstreamNodeExternalId, new HashMap<Long, double[]>());
        }
        final long downstreamNodeExternalId = Long.parseLong(cols[1]);
        final double[] flowCost = {Double.parseDouble(cols[2]), Double.parseDouble(cols[3])};
        resultsMap.get(upstreamNodeExternalId).put(downstreamNodeExternalId, flowCost);
      }
   } catch (final Exception ex) {
      throw new PlanItException(ex);
    }
    return resultsMap;
  }
}