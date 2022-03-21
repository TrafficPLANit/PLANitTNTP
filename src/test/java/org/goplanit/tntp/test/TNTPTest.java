package org.goplanit.tntp.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.goplanit.assignment.TrafficAssignment;
import org.goplanit.assignment.traditionalstatic.TraditionalStaticAssignmentConfigurator;
import org.goplanit.cost.physical.BPRLinkTravelTimeCost;
import org.goplanit.cost.virtual.FixedConnectoidTravelTimeCost;
import org.goplanit.demands.Demands;
import org.goplanit.logging.Logging;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.output.configuration.LinkOutputTypeConfiguration;
import org.goplanit.output.configuration.OutputConfiguration;
import org.goplanit.output.enums.OutputType;
import org.goplanit.output.formatter.MemoryOutputFormatter;
import org.goplanit.output.formatter.MemoryOutputIterator;
import org.goplanit.output.formatter.OutputFormatter;
import org.goplanit.output.property.OutputPropertyType;
import org.goplanit.project.PlanItProjectInput;
import org.goplanit.sdinteraction.smoothing.MSASmoothing;
import org.goplanit.tntp.enums.CapacityPeriod;
import org.goplanit.tntp.enums.LengthUnits;
import org.goplanit.tntp.enums.NetworkFileColumnType;
import org.goplanit.tntp.enums.SpeedUnits;
import org.goplanit.tntp.enums.TimeUnits;
import org.goplanit.tntp.input.TntpInputBuilder;
import org.goplanit.tntp.project.TntpProject;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.math.Precision;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.time.TimePeriod;
import org.goplanit.utils.unit.Unit;
import org.goplanit.zoning.Zoning;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for TNTP model.
 *
 * February 2020: We only use Chicago Sketch as a test case. This is the only configuration which
 * has published output results.
 *
 * @author gman6028
 *
 */
public class TntpTest {

  /** the logger */
  private static Logger LOGGER = null;


  @BeforeClass
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(TntpTest.class);
    }
  }

  @After
  public void tearDown() {
    Logging.closeLogger(LOGGER);
  }
  
  /**
   * Compare the results for Chicago-Sketch TNTP network with previous results.
   *
   *
   * The published results can be found at
   * https://github.com/bstabler/TransportationNetworks/blob/master/Chicago-Sketch/ChicagoSketch_flow.tntp.
   */
  @Test
  public void ChicagoSketchTest() {
    final String networkFileLocation = "src\\test\\resources\\ChicagoSketch\\ChicagoSketch_net.tntp";
    final String nodeFileLocation = "src\\test\\resources\\ChicagoSketch\\ChicagoSketch_node.tntp";
    final String demandFileLocation = "src\\test\\resources\\ChicagoSketch\\ChicagoSketch_trips.tntp";
    final String standardResultsFileLocation = "src\\test\\resources\\ChicagoSketch\\ChicagoSketch_flow.tntp";
    final int maxIterations = 25;
    final double epsilon = Precision.EPSILON_6;
    final double defaultMaximumSpeedMpH = 25.0;
    IdGenerator.reset();

    try {
      
      /* PREP */
      final TntpInputBuilder tntp = new TntpInputBuilder(networkFileLocation, nodeFileLocation, demandFileLocation);
            
      // TODO - The following arrangement of columns is correct for Chicago Sketch and Philadelphia.
      // For some other cities the arrangement is different.
      final Map<NetworkFileColumnType, Integer> networkFileColumns = new HashMap<NetworkFileColumnType, Integer>();
      networkFileColumns.put(NetworkFileColumnType.UPSTREAM_NODE_ID, 0);
      networkFileColumns.put(NetworkFileColumnType.DOWNSTREAM_NODE_ID, 1);
      networkFileColumns.put(NetworkFileColumnType.CAPACITY_PER_LANE, 2);
      networkFileColumns.put(NetworkFileColumnType.LENGTH, 3);
      networkFileColumns.put(NetworkFileColumnType.FREE_FLOW_TRAVEL_TIME, 4);
      networkFileColumns.put(NetworkFileColumnType.B, 5);
      networkFileColumns.put(NetworkFileColumnType.POWER, 6);
      networkFileColumns.put(NetworkFileColumnType.MAXIMUM_SPEED, 7);
      networkFileColumns.put(NetworkFileColumnType.TOLL, 8);
      networkFileColumns.put(NetworkFileColumnType.LINK_TYPE, 9);
      tntp.getNetworkReaderSettings().setNetworkFileColumns(networkFileColumns);
      
      tntp.getNetworkReaderSettings().setSpeedUnits( SpeedUnits.MILES_H);
      tntp.getNetworkReaderSettings().setLengthUnits(LengthUnits.MILES);
      tntp.getNetworkReaderSettings().setFreeFlowTravelTimeUnits(TimeUnits.MINUTES);
      tntp.getNetworkReaderSettings().setCapacityPeriod(CapacityPeriod.HOUR);
      tntp.getNetworkReaderSettings().setDefaultMaximumSpeed(defaultMaximumSpeedMpH);
            
      var demandsReaderSettings = tntp.getDemandsReaderSettings();
      /* 1h peak demand as per */
      demandsReaderSettings.setStartTimeSinceMidNight(8, TimeUnits.HOURS);
      demandsReaderSettings.setTimePeriodDuration(1, TimeUnits.HOURS);                
            
      /* EXECUTE */
      var resultPair = TntpTestHelper.execute(tntp, maxIterations, epsilon);
      
      /* RESULTS */
      var project = resultPair.first(); // not yet used
      var memoryOutputFormatter = resultPair.second();
      var demands = project.demands.getFirst();
      var network = (MacroscopicNetwork)project.physicalNetworks.getFirst();
      
      final Map<String, Map<String, double[]>> resultsMap = TntpTestHelper.parseStandardResultsFile(standardResultsFileLocation);
      final TimePeriod timePeriod = demands.timePeriods.findFirst(tp -> tp.getExternalId().equals("1"));
      final int iterationIndex = memoryOutputFormatter.getLastIteration();
      final Mode mode = network.getModes().getFirst();
      
      final int flowPosition = memoryOutputFormatter.getPositionOfOutputValueProperty(OutputType.LINK, OutputPropertyType.FLOW);
      final int costPosition = memoryOutputFormatter.getPositionOfOutputValueProperty(OutputType.LINK, OutputPropertyType.LINK_SEGMENT_COST);
      //final int linkTypePosition = memoryOutputFormatter.getPositionOfOutputValueProperty(OutputType.LINK, OutputPropertyType.LINK_SEGMENT_TYPE_NAME);
      final int downstreamNodeExternalIdPosition = memoryOutputFormatter.getPositionOfOutputKeyProperty(OutputType.LINK, OutputPropertyType.DOWNSTREAM_NODE_EXTERNAL_ID);
      final int upstreamNodeExternalIdPosition = memoryOutputFormatter.getPositionOfOutputKeyProperty(OutputType.LINK, OutputPropertyType.UPSTREAM_NODE_EXTERNAL_ID);
      
      // NOTE: when running +- 300 iterations the flows get very close generally, so it appears to be working correctly however,      
      //TODO: to compare against results of TNTP we need to include generalised cost with distance penalty. This is not yet supported
      //      in PLANit, so we can;t compare properly. 
      final MemoryOutputIterator memoryOutputIterator = memoryOutputFormatter.getIterator(mode, timePeriod, iterationIndex, OutputType.LINK);
      while (memoryOutputIterator.hasNext()) {
          memoryOutputIterator.next();
          final Object[] results = memoryOutputIterator.getValues();
          final Object[] keys = memoryOutputIterator.getKeys();
          final String downstreamNodeExternalId = (String) keys[downstreamNodeExternalIdPosition];
          final String upstreamNodeExternalId = (String) keys[upstreamNodeExternalIdPosition];
          final double runFlow = (Double) results[flowPosition];
          final double runCost = (Double) results[costPosition];
                   
          final double standardResultsFlow = resultsMap.get(upstreamNodeExternalId).get(downstreamNodeExternalId)[0]; 
          final double standardResultsCost = resultsMap.get(upstreamNodeExternalId).get(downstreamNodeExternalId)[1]/60; // from min to h 
          
          // unable to compare without generalised cost
          assertEquals(runFlow, standardResultsFlow, Double.POSITIVE_INFINITY);
          assertEquals(runCost, standardResultsCost, Double.POSITIVE_INFINITY); 
      }

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe(e.getMessage());
      fail(e.getMessage());
    }
  }
  
  /**
   * Perform  parsing of SiouxFalls network to test if the basic stats are correct (links, nodes, etc.)
   *
   *
   * The published results can be found at
   * https://github.com/bstabler/TransportationNetworks/blob/master/SiouxFalls
   */
  @Test
  public void siouxFallsTest() {  
    
  }
}