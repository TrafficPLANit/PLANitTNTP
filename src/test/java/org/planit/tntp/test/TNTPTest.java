package org.planit.tntp.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.FileReader;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.Test;
import org.planit.logging.PlanItLogger;
import org.planit.output.enums.OutputTimeUnit;
import org.planit.output.property.FlowOutputProperty;
import org.planit.output.property.LinkCostOutputProperty;
import org.planit.output.property.LinkTypeOutputProperty;
import org.planit.tntp.TntpMain;
import org.planit.utils.misc.IdGenerator;

public class TNTPTest {

  /**
   * Compare the results for Chicago-Sketch TNTP network with previous results.
   *
   * Only check results for links of type 3. We have tested the published results for type 3 links
   * against our BPR theoretical model
   * and they agree. We do not yet understand how the published results for type 1 and type 2 links
   * are calculated.
   *
   * The published results can be found at
   * https://github.com/bstabler/TransportationNetworks/blob/master/Chicago-Sketch/ChicagoSketch_flow.tntp.
   * The standard results file src\test\t\resources\ChicagoSketch\ChicagoSketchLink100iterations.csv
   * contains results from previous runs.
   */
  @Test
  public void ChicagoSketchTest() {
    final TntpMain tntpMain = new TntpMain();
    final String networkFileLocation = "src\\test\\resources\\ChicagoSketch\\ChicagoSketch_net.tntp";
    final String demandFileLocation = "src\\test\\resources\\ChicagoSketch\\ChicagoSketch_trips.tntp";
    final String nodeCoordinateFileLocation = "src\\test\\resources\\ChicagoSketch\\ChicagoSketch_node.tntp";
    final String runResultsFileName = "src\\test\\resources\\ChicagoSketch\\testResults.csv";
    final String standardResultsFileName = "src\\test\\resources\\ChicagoSketch\\ChicagoSketchLink100iterations.csv";
    final OutputTimeUnit outputTimeUnit = null;
    final String odOutputFileName = null;
    final String odPathOutputFileName = null;
    final String logfileLocation = "logs\\ChicagoSketchTest.log";
    final int maxIterations = 100;
    final double epsilon = TntpMain.DEFAULT_CONVERGENCE_EPSILON;
    IdGenerator.reset();

    try {
      PlanItLogger.setLogging(logfileLocation, TNTPTest.class);
      tntpMain.execute(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation, runResultsFileName,
          odOutputFileName, odPathOutputFileName, maxIterations, epsilon, outputTimeUnit);
      final Reader standardResults = new FileReader(standardResultsFileName);
      final CSVParser standardResultsParser = CSVParser.parse(standardResults, CSVFormat.DEFAULT
          .withFirstRecordAsHeader());
      final Reader runResults = new FileReader(runResultsFileName);
      final CSVParser runResultsParser = CSVParser.parse(runResults, CSVFormat.DEFAULT.withFirstRecordAsHeader());
      final String costHeader = LinkCostOutputProperty.LINK_COST;
      final String flowHeader = FlowOutputProperty.FLOW;
      final String typeHeader = LinkTypeOutputProperty.LINK_TYPE;
      final Iterator<CSVRecord> standardResultsIterator = standardResultsParser.iterator();
      for (final CSVRecord runRecord : runResultsParser) {
        final double runFlow = Double.parseDouble(runRecord.get(flowHeader));
        final double runCost = Double.parseDouble(runRecord.get(costHeader));
        final int runLinkType = Integer.parseInt(runRecord.get(typeHeader));
        try {
          final CSVRecord standardResultsRecord = standardResultsIterator.next();
          final double standardResultsFlow = Double.parseDouble(standardResultsRecord.get(flowHeader));
          final double standardResultsCost = Double.parseDouble(standardResultsRecord.get(costHeader));
          final int standardResultsLinkType = Integer.parseInt(standardResultsRecord.get(typeHeader));
          assertEquals(runLinkType, standardResultsLinkType);
          if (runLinkType == 3) {
            assertEquals(runFlow, standardResultsFlow, 0.0001);
            assertEquals(runCost, standardResultsCost, 0.0001);
          }
        } catch (final NoSuchElementException nsee) {
          fail(runResultsFileName + "has more records than " + standardResultsFileName);
          break;
        }
      }
      if (standardResultsIterator.hasNext()) {
        fail(standardResultsFileName + " has more records than " + runResultsFileName);
      }
      PlanItLogger.close();
      runResultsParser.close();
      standardResultsParser.close();
      final String rootPath = System.getProperty("user.dir");
      Path path = FileSystems.getDefault().getPath(rootPath + "\\" + runResultsFileName);
      Files.delete(path);
      path = FileSystems.getDefault().getPath(rootPath + "\\" + logfileLocation);
      Files.delete(path);
    } catch (final Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }
}
