package org.goplanit.tntp.test;

import org.goplanit.converter.demands.DemandsConverterFactory;
import org.goplanit.converter.network.NetworkConverter;
import org.goplanit.converter.network.NetworkConverterFactory;
import org.goplanit.converter.zoning.ZoningConverter;
import org.goplanit.converter.zoning.ZoningConverterFactory;
import org.goplanit.io.converter.demands.PlanitDemandsWriter;
import org.goplanit.io.converter.demands.PlanitDemandsWriterFactory;
import org.goplanit.io.converter.network.PlanitNetworkWriter;
import org.goplanit.io.converter.network.PlanitNetworkWriterFactory;
import org.goplanit.io.converter.zoning.PlanitZoningWriter;
import org.goplanit.io.converter.zoning.PlanitZoningWriterFactory;
import org.goplanit.io.converter.zoning.PlanitZoningWriterSettings;
import org.goplanit.io.test.PlanitAssertionUtils;
import org.goplanit.logging.Logging;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.tntp.converter.demands.TntpDemandsReader;
import org.goplanit.tntp.converter.demands.TntpDemandsReaderFactory;
import org.goplanit.tntp.converter.network.TntpNetworkReader;
import org.goplanit.tntp.converter.network.TntpNetworkReaderFactory;
import org.goplanit.tntp.converter.zoning.TntpZoningReader;
import org.goplanit.tntp.converter.zoning.TntpZoningReaderFactory;
import org.goplanit.tntp.enums.LengthUnits;
import org.goplanit.tntp.enums.NetworkFileColumnType;
import org.goplanit.tntp.enums.SpeedUnits;
import org.goplanit.tntp.enums.TimeUnits;
import org.goplanit.utils.id.IdGenerator;
import org.goplanit.utils.id.IdGroupingToken;
import org.goplanit.utils.locale.CountryNames;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * JUnit test cases for converting various Leuven test network from TNTP format to another format
 * 
 * @author markr
 *
 */
public class TntpLeuvenConversionTest {

  /** the logger */
  private static Logger LOGGER = null;

  private static final Path RESOURCE_PATH = Path.of("src","test","resources");

  private static final Path LEUVEN_RESOURCE_PATH = Path.of(RESOURCE_PATH.toString(),"Leuven");
     
  private static final Path LEUVEN_NETWORK_FILE = Path.of(LEUVEN_RESOURCE_PATH.toString(),"LeuvenTest_net.tntp");
  private static final Path LEUVEN_NODE_FILE = Path.of(LEUVEN_RESOURCE_PATH.toString(),"LeuvenTest_node.tntp");
  private static final Path LEUVEN_DEMAND_FILE = Path.of(LEUVEN_RESOURCE_PATH.toString(),"LeuvenTest_trips.tntp");

  public static final double DEFAULT_MAXIMUM_SPEED_KPH = 50.0;
 
 
  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(TntpLeuvenConversionTest.class);
    } 
  }

  /**
   * run garbage collection after each test as it apparently is not triggered properly within
   * Eclipse (or takes too long before being triggered)
   */
  @AfterEach
  public void afterTest() {
    IdGenerator.reset();
    System.gc();
  }

  @AfterAll
  public static void tearDown() {
    Logging.closeLogger(LOGGER);
  }

  /** Create TNTP reader suitable for Leuven network
   * 
   * @param networkFileLocation to use
   * @param nodeFileLocation to use
   * @param defaultMaxSpeedKmH to use
   * @param idToken to use
   * @return pre-configured network reader
   */
  private static TntpNetworkReader createLeuvenTntpNetworkReader(
      String networkFileLocation, String nodeFileLocation, double defaultMaxSpeedKmH, IdGroupingToken idToken) {

    var tntpReader = TntpNetworkReaderFactory.create(networkFileLocation, nodeFileLocation, idToken);

    // The following arrangement of columns is correct for Leuven, for other cities the arrangement can be different.
    final Map<NetworkFileColumnType, Integer> networkFileColumns = new HashMap<>();
    networkFileColumns.put(NetworkFileColumnType.UPSTREAM_NODE_ID, 0);
    networkFileColumns.put(NetworkFileColumnType.DOWNSTREAM_NODE_ID, 1);
    networkFileColumns.put(NetworkFileColumnType.LENGTH, 3);
    networkFileColumns.put(NetworkFileColumnType.FREE_FLOW_TRAVEL_TIME, 4);
    networkFileColumns.put(NetworkFileColumnType.B, 5);
    networkFileColumns.put(NetworkFileColumnType.POWER, 6);
    networkFileColumns.put(NetworkFileColumnType.MAXIMUM_SPEED, 12);      // freespeed
    networkFileColumns.put(NetworkFileColumnType.TOLL, 8);
    networkFileColumns.put(NetworkFileColumnType.LINK_TYPE, 9);
    networkFileColumns.put(NetworkFileColumnType.NUMBER_OF_LANES, 10);
    networkFileColumns.put(NetworkFileColumnType.CAPACITY_PER_LANE, 11);  // satflow
    networkFileColumns.put(NetworkFileColumnType.CRITICAL_SPEED, 13);     // speed at capacity
    
    /* reader configuration */
    tntpReader.getSettings().setNetworkFileColumns(networkFileColumns);
    tntpReader.getSettings().setSpeedUnits(SpeedUnits.KM_H);
    tntpReader.getSettings().setLengthUnits(LengthUnits.KM);
    tntpReader.getSettings().setCapacityPeriod(1, TimeUnits.HOURS);
    tntpReader.getSettings().setFreeFlowTravelTimeUnits(TimeUnits.HOURS);
    tntpReader.getSettings().setDefaultMaximumSpeed(defaultMaxSpeedKmH);
    tntpReader.getSettings().setCoordinateReferenceSystem("EPSG:28992"); // RDS
    
    return tntpReader;
  }

  /**
   * Test case which parses the TNTP Leuven network, zoning and trips files, loads it into PLANit memory model and
   * persists it as a PLANit network
   */
  @Test
  public void testTntp2PlanitNetworkLeuven() {
    
    final Path PLANIT_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(),"testcases","planit","leuven");
    final Path PLANIT_REF_DIR = Path.of(RESOURCE_PATH.toString(),"planit","leuven");
    try {
            
      /* TNTP reader */
      var idToken = IdGenerator.createIdGroupingToken("testTntp2PlanitNetworkLeuven");
      TntpNetworkReader tntpReader = createLeuvenTntpNetworkReader(
              LEUVEN_NETWORK_FILE.toAbsolutePath().toString(),
              LEUVEN_NODE_FILE.toAbsolutePath().toString(),
              DEFAULT_MAXIMUM_SPEED_KPH,
              idToken );
      
      /* PLANit writer */
      PlanitNetworkWriter planitWriter = PlanitNetworkWriterFactory.create(
              PLANIT_OUTPUT_DIR.toAbsolutePath().toString(),
              CountryNames.BELGIUM);
      
      /* convert */
      NetworkConverter theConverter = NetworkConverterFactory.create(tntpReader, planitWriter);
      theConverter.convert();

      PlanitAssertionUtils.assertNetworkFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe( e.getMessage());
      fail(e.getMessage());
    }    
  }
   
  /**
   * Test case which parses the TNTP Leuven network, zoning and trips files, loads it into PLANit memory model and
   * persists it as a PLANit network
   */
  @Test
  public void testTntp2PlanitZoningLeuven() {

    final Path PLANIT_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(),"testcases","planit","leuven");
    final Path PLANIT_REF_DIR = Path.of(RESOURCE_PATH.toString(),"planit","leuven");

    try {

      var idToken = IdGenerator.createIdGroupingToken("testTntp2PlanitZoningLeuven");
      TntpNetworkReader tntpNetworkReader = createLeuvenTntpNetworkReader(
              LEUVEN_NETWORK_FILE.toAbsolutePath().toString(),
              LEUVEN_NODE_FILE.toAbsolutePath().toString(),
              DEFAULT_MAXIMUM_SPEED_KPH,
              idToken);

      var planitNetwork = (MacroscopicNetwork) tntpNetworkReader.read();
      
      /* TNTP ZONING reader */
      TntpZoningReader tntpZoningReader = TntpZoningReaderFactory.create(
          LEUVEN_NETWORK_FILE.toAbsolutePath().toString(), planitNetwork, idToken);
      
      /* PLANit ZONING writer */
      PlanitZoningWriter planitWriter = PlanitZoningWriterFactory.create(
          new PlanitZoningWriterSettings(PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), CountryNames.BELGIUM),
          planitNetwork);
      
      /* convert */
      ZoningConverter theConverter = ZoningConverterFactory.create(tntpZoningReader, planitWriter);
      theConverter.convert();

      PlanitAssertionUtils.assertZoningFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe( e.getMessage());
      fail(e.getMessage());
    }    
  }  
  
  /**
   * Test case which parses the TNTP Leuven network, zoning and trips files, loads it into PLANit memory model and
   * persists it as a PLANit network
   */
  @Test
  public void testTntp2PlanitDemandsLeuven() {

    final Path PLANIT_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(),"testcases","planit","leuven");
    final Path PLANIT_REF_DIR = Path.of(RESOURCE_PATH.toString(),"planit","leuven");

    try {
      
      /* TNTP NETWORK and ZONING reader */
      var idToken = IdGenerator.createIdGroupingToken("testTntp2PlanitDemandsLeuven");
      TntpNetworkReader tntpNetworkReader = createLeuvenTntpNetworkReader(
              LEUVEN_NETWORK_FILE.toAbsolutePath().toString(),
              LEUVEN_NODE_FILE.toAbsolutePath().toString(),
              DEFAULT_MAXIMUM_SPEED_KPH, idToken);
      TntpZoningReader tntpZoningReader = TntpZoningReaderFactory.createFromTntpNetworkReader(tntpNetworkReader);

      /* TNTP DEMAND reader */
      TntpDemandsReader tntpDemandsReader = TntpDemandsReaderFactory.create(tntpZoningReader);
      tntpDemandsReader.getSettings().setDemandFileLocation(LEUVEN_DEMAND_FILE.toAbsolutePath().toString());
      tntpDemandsReader.getSettings().setStartTimeSinceMidnight(8, TimeUnits.HOURS);
      tntpDemandsReader.getSettings().setTimePeriodDuration(1, TimeUnits.HOURS);     
      
      /* PLANit DEMAND writer */
      PlanitDemandsWriter planitWriter = PlanitDemandsWriterFactory.create();
      planitWriter.getSettings().setOutputDirectory(PLANIT_OUTPUT_DIR.toAbsolutePath().toString());

      /* convert demand (which creates PLANit network and zoning in the process as well)*/
      DemandsConverterFactory.create(tntpDemandsReader, planitWriter).convert();
      PlanitAssertionUtils.assertDemandsFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);
      
      /* write and verify PLANit network */
      var  networkWriter = PlanitNetworkWriterFactory.create(PLANIT_OUTPUT_DIR.toAbsolutePath().toString());
      networkWriter.write(tntpDemandsReader.getReferenceNetwork());
      PlanitAssertionUtils.assertNetworkFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);

      /* write and verify PLANit zoning */
      var zoningWriter = PlanitZoningWriterFactory.create(
          new PlanitZoningWriterSettings(PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), CountryNames.BELGIUM),
          tntpDemandsReader.getReferenceNetwork());
      zoningWriter.write(tntpDemandsReader.getReferenceZoning());
      PlanitAssertionUtils.assertZoningFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe( e.getMessage());
      fail(e.getMessage());
    }    
  }  

}