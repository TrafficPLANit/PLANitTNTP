package org.planit.tntp.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.Test;
import org.planit.logging.PlanItLogger;
import org.planit.output.enums.OutputTimeUnit;
import org.planit.output.enums.OutputType;
import org.planit.output.formatter.MemoryOutputFormatter;
import org.planit.output.property.OutputProperty;
import org.planit.time.TimePeriod;
import org.planit.tntp.project.TntpProject;
import org.planit.utils.misc.IdGenerator;
import org.planit.utils.misc.Pair;
import org.planit.utils.network.physical.Mode;

/**
 * Unit test for TNTP model.
 *
 * February 2020: We only use Chicago Sketch as a test case. This is the only configuration which
 * has published output results.
 *
 * @author gman6028
 *
 */
public class TNTPTest {

  /**
   * Compare the results for Chicago-Sketch TNTP network with previous results.
   *
   * Only check results for links of type 3. We have tested the published results for type 3 links
   * against our BPR theoretical model and they agree. We do not yet understand how the
   * published results for type 1 and type 2 links are calculated.
   *
   * The published results can be found at
   * https://github.com/bstabler/TransportationNetworks/blob/master/Chicago-Sketch/ChicagoSketch_flow.tntp.
   */
  @Test
  public void ChicagoSketchTest() {
    final String networkFileLocation = "src\\test\\resources\\ChicagoSketch\\ChicagoSketch_net.tntp";
    final String demandFileLocation = "src\\test\\resources\\ChicagoSketch\\ChicagoSketch_trips.tntp";
    final String standardResultsFileLocation = "src\\test\\resources\\ChicagoSketch\\ChicagoSketch_flow.tntp";
    final OutputTimeUnit outputTimeUnit = null;
    final String logfileLocation = "logs\\ChicagoSketchTest.log";
    final int maxIterations = 100;
    final double epsilon = TNTPTestHelper.DEFAULT_CONVERGENCE_EPSILON;
    final double defaultMaximumSpeed = 25.0;
    IdGenerator.reset();

    try {
      PlanItLogger.setLogging(logfileLocation, TNTPTest.class);
      final Pair<MemoryOutputFormatter, TntpProject> testOutput =
          TNTPTestHelper.execute(networkFileLocation, demandFileLocation, standardResultsFileLocation,
              maxIterations, epsilon, outputTimeUnit, defaultMaximumSpeed);
      final MemoryOutputFormatter memoryOutputFormatter = testOutput.getFirst();
      final TntpProject project = testOutput.getSecond();

      final Map<Long, Map<Long, double[]>> resultsMap =
          TNTPTestHelper.createStandardResultsFile(standardResultsFileLocation);
      final TimePeriod timePeriod = TimePeriod.getByExternalId(1);
      final int iterationIndex = memoryOutputFormatter.getLastIteration();
      final Mode mode = project.physicalNetworks.getFirstNetwork().modes.findModeByExternalIdentifier(1);

      final OutputProperty[] outputKeyProperties = memoryOutputFormatter.getOutputKeyProperties(OutputType.LINK);
      final Object[] keyValues = new Object[outputKeyProperties.length];

      for (final long upstreamNodeExternalId : resultsMap.keySet()) {
        for (final long downstreamNodeExternalId : resultsMap.get(upstreamNodeExternalId).keySet()) {
          keyValues[0] = downstreamNodeExternalId;
          keyValues[1] = upstreamNodeExternalId;
          final double runFlow = (Double) memoryOutputFormatter.getOutputDataValue(mode, timePeriod, iterationIndex,
              OutputType.LINK, OutputProperty.FLOW, keyValues);
          final double runCost = (Double) memoryOutputFormatter.getOutputDataValue(mode, timePeriod, iterationIndex,
              OutputType.LINK, OutputProperty.LINK_COST, keyValues);
          final String runLinkType = (String) memoryOutputFormatter.getOutputDataValue(mode, timePeriod, iterationIndex,
              OutputType.LINK, OutputProperty.LINK_TYPE, keyValues);
          if (runLinkType.equals("3")) {
            final double standardResultsFlow = resultsMap.get(upstreamNodeExternalId).get(downstreamNodeExternalId)[0];
            final double standardResultsCost = resultsMap.get(upstreamNodeExternalId).get(downstreamNodeExternalId)[1];
            assertEquals(runFlow, standardResultsFlow, 0.001);
            assertEquals(runCost, standardResultsCost, 0.001);
          }
        }
      }

      PlanItLogger.close();
      final String rootPath = System.getProperty("user.dir");
      final Path path = FileSystems.getDefault().getPath(rootPath + "\\" + logfileLocation);
      Files.delete(path);
    } catch (final Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }
}
