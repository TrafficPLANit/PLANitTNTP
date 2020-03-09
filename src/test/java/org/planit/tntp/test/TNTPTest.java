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
import org.planit.output.formatter.MemoryOutputIterator;
import org.planit.output.property.OutputProperty;
import org.planit.time.TimePeriod;
import org.planit.tntp.input.Tntp;
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
      final Pair<MemoryOutputFormatter, Tntp> testOutput =
          TNTPTestHelper.execute(networkFileLocation, demandFileLocation, maxIterations, epsilon, outputTimeUnit,
              defaultMaximumSpeed);
      final MemoryOutputFormatter memoryOutputFormatter = testOutput.getFirst();
      final Tntp tntp = testOutput.getSecond();

      final Map<Long, Map<Long, double[]>> resultsMap = TNTPTestHelper.parseStandardResultsFile(standardResultsFileLocation);
      final TimePeriod timePeriod = tntp.getTimePeriodByExternalId((long) 1);
      final int iterationIndex = memoryOutputFormatter.getLastIteration();
      final Mode mode = tntp.getModeByExternalId((long) 1);
      final int flowPosition = memoryOutputFormatter.getPositionOfOutputValueProperty(mode, timePeriod, iterationIndex, OutputType.LINK, OutputProperty.FLOW);
      final int costPosition = memoryOutputFormatter.getPositionOfOutputValueProperty(mode, timePeriod, iterationIndex, OutputType.LINK, OutputProperty.LINK_COST);
      final int linkTypePosition = memoryOutputFormatter.getPositionOfOutputValueProperty(mode, timePeriod, iterationIndex, OutputType.LINK, OutputProperty.LINK_TYPE);
      final int downstreamNodeExternalIdPosition = memoryOutputFormatter.getPositionOfOutputKeyProperty(mode, timePeriod, iterationIndex, OutputType.LINK, OutputProperty.DOWNSTREAM_NODE_EXTERNAL_ID);
      final int upstreamNodeExternalIdPosition = memoryOutputFormatter.getPositionOfOutputKeyProperty(mode, timePeriod, iterationIndex, OutputType.LINK, OutputProperty.UPSTREAM_NODE_EXTERNAL_ID);
      final MemoryOutputIterator memoryOutputIterator = memoryOutputFormatter.getIterator(mode, timePeriod, iterationIndex, OutputType.LINK);
      while (memoryOutputIterator.hasNext()) {
          final Object[] results = memoryOutputIterator.getValues();
          final String runLinkType = (String) results[linkTypePosition];
          if (runLinkType.equals("3")) {
            final Object[] keys = memoryOutputIterator.getKeys();
            final long downstreamNodeExternalId = (Long) keys[downstreamNodeExternalIdPosition];
            final long upstreamNodeExternalId = (Long) keys[upstreamNodeExternalIdPosition];
            final double runFlow = (Double) results[flowPosition];
            final double runCost = (Double) results[costPosition];
            final double standardResultsFlow = resultsMap.get(upstreamNodeExternalId).get(downstreamNodeExternalId)[0];
            final double standardResultsCost = resultsMap.get(upstreamNodeExternalId).get(downstreamNodeExternalId)[1];
            assertEquals(runFlow, standardResultsFlow, 0.001);
            assertEquals(runCost, standardResultsCost, 0.001);
          }
      }

      PlanItLogger.close();
      final String rootPath = System.getProperty("user.dir");
      final Path path = FileSystems.getDefault().getPath(rootPath + "\\" + logfileLocation);
      Files.delete(path);
    } catch (final Exception e) {
      fail(e.getMessage());
    }
  }
}