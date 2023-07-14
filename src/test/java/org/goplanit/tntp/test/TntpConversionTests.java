package org.goplanit.tntp.test;

import org.goplanit.converter.demands.DemandsConverter;
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
 * JUnit test cases for converting Chicago network from TNTP format to another
 * 
 * @author markr
 *
 */
public class TntpConversionTests {

  /** the logger */
  private static Logger LOGGER = null;

  private static final Path RESOURCE_PATH = Path.of("src","test","resources");
     
  private static final Path CHICAGO_NETWORK_FILE = Path.of(RESOURCE_PATH.toString(),"ChicagoSketch","ChicagoSketch_net.tntp");
  private static final Path CHICAGO_NODE_FILE = Path.of(RESOURCE_PATH.toString(),"ChicagoSketch","ChicagoSketch_node.tntp");
  private static final Path CHICAGO_DEMAND_FILE = Path.of(RESOURCE_PATH.toString(),"ChicagoSketch","ChicagoSketch_trips.tntp");
  
  private static final Path SIOUXFALLS_NETWORK_FILE = Path.of(RESOURCE_PATH.toString(),"SiouxFalls","SiouxFalls_net.tntp");
  private static final Path SIOUXFALLS_NODE_FILE = Path.of(RESOURCE_PATH.toString(),"SiouxFalls","SiouxFalls_node.tntp");
  private static final Path SIOUXFALLS_DEMAND_FILE = Path.of(RESOURCE_PATH.toString(),"SiouxFalls","SiouxFalls_trips.tntp");
  
  public static final double DEFAULT_MAXIMUM_SPEED = 25.0;
 
 
  @BeforeAll
  public static void setUp() throws Exception {
    if (LOGGER == null) {
      LOGGER = Logging.createLogger(TntpConversionTests.class);
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

  private static TntpNetworkReader createChicagoTntpNetworkReader(Path networkFileLocation, Path nodeFileLocation, double defaultMaxSpeedMpH, IdGroupingToken idToken) {
    return createChicagoTntpNetworkReader(networkFileLocation.toAbsolutePath().toString(),nodeFileLocation.toAbsolutePath().toString(), defaultMaxSpeedMpH, idToken);
  }
  
  /** Create TNTP reader suitable for Chicago network
   * 
   * @param networkFileLocation to use
   * @param nodeFileLocation to use
   * @param defaultMaxSpeedMpH to use
   * @param idToken 
   * @return pre-configured network reader
   */
  private static TntpNetworkReader createChicagoTntpNetworkReader(String networkFileLocation, String nodeFileLocation, double defaultMaxSpeedMpH, IdGroupingToken idToken) {
    var tntpReader = TntpNetworkReaderFactory.create(networkFileLocation, nodeFileLocation, idToken);
    
    // The following arrangement of columns is correct for Chicago Sketch (and Philadelphia), for other cities the arrangement can be different.
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
    
    /* reader configuration */
    tntpReader.getSettings().setNetworkFileColumns(networkFileColumns);
    tntpReader.getSettings().setSpeedUnits(SpeedUnits.MILES_H);
    tntpReader.getSettings().setLengthUnits(LengthUnits.MILES);
    tntpReader.getSettings().setCapacityPeriod(1, TimeUnits.HOURS);
    tntpReader.getSettings().setFreeFlowTravelTimeUnits(TimeUnits.MINUTES);
    tntpReader.getSettings().setDefaultMaximumSpeed(defaultMaxSpeedMpH);    
    tntpReader.getSettings().setCoordinateReferenceSystem("EPSG:26971");
    
    return tntpReader;
  }

  /** Create TNTP reader suitable for Chicago network
   * 
   * @param networkFileLocation to use
   * @param nodeFileLocation to use
   * @param defaultMaxSpeedMpH to use
   * @param idToken 
   * @return pre-configured network reader
   */  
  private static TntpNetworkReader createSiouxFallsTntpNetworkReader(Path networkFileLocation, Path nodeFileLocation, double defaultMaxSpeedMpH, IdGroupingToken idToken) {
    var tntpReader = TntpNetworkReaderFactory.create(networkFileLocation.toAbsolutePath().toString(), nodeFileLocation.toAbsolutePath().toString(), idToken);
    
    // The following arrangement of columns is correct for SiouxFalls
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
    tntpReader.getSettings().setNetworkFileColumns(networkFileColumns);
    
    /* reader configuration */
    tntpReader.getSettings().setNetworkFileColumns(networkFileColumns);
    tntpReader.getSettings().setSpeedUnits(SpeedUnits.MILES_H);
    tntpReader.getSettings().setLengthUnits(LengthUnits.MILES);
    tntpReader.getSettings().setCapacityPeriod(1, TimeUnits.HOURS);
    tntpReader.getSettings().setFreeFlowTravelTimeUnits(TimeUnits.MINUTES);
    tntpReader.getSettings().setDefaultMaximumSpeed(defaultMaxSpeedMpH);    
                  
    return tntpReader;
  }  
    
  /**
   * Test case which parses the TNTP Chicago network, zoning and trips files, loads it into PLANit memory model and persists it as a PLANit network
   */
  @Test
  public void testTntp2PlanitNetworkChicago() {
    
    final Path PLANIT_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(),"testcases","planit","chicago");
    final Path PLANIT_REF_DIR = Path.of(RESOURCE_PATH.toString(),"planit","chicago");
    try {
            
      /* TNTP reader */
      var idToken = IdGenerator.createIdGroupingToken("testTntp2PlanitNetworkChicago");
      TntpNetworkReader tntpReader = createChicagoTntpNetworkReader(CHICAGO_NETWORK_FILE, CHICAGO_NODE_FILE, DEFAULT_MAXIMUM_SPEED, idToken );
      
      /* PLANit writer */
      PlanitNetworkWriter planitWriter = PlanitNetworkWriterFactory.create(PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), CountryNames.UNITED_STATES_OF_AMERICA);
      
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
   * Test case which parses the TNTP Chicago network, zoning and trips files, loads it into PLANit memory model and persists it as a PLANit network
   */
  @Test
  public void testTntp2PlanitZoningChicago() {

    final Path PLANIT_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(),"testcases","planit","chicago");
    final Path PLANIT_REF_DIR = Path.of(RESOURCE_PATH.toString(),"planit","chicago");

    try {
      
      
      var idToken = IdGenerator.createIdGroupingToken("testTntp2PlanitZoningChicago");
      TntpNetworkReader tntpNetworkReader = createChicagoTntpNetworkReader(CHICAGO_NETWORK_FILE, CHICAGO_NODE_FILE, DEFAULT_MAXIMUM_SPEED, idToken);
      var planitNetwork = (MacroscopicNetwork) tntpNetworkReader.read();
      
      /* TNTP ZONING reader */
      TntpZoningReader tntpZoningReader = TntpZoningReaderFactory.create(CHICAGO_NETWORK_FILE.toAbsolutePath().toString(), planitNetwork, idToken);
      
      /* PLANit ZONING writer */
      PlanitZoningWriter planitWriter = PlanitZoningWriterFactory.create(PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), CountryNames.UNITED_STATES_OF_AMERICA, planitNetwork.getCoordinateReferenceSystem());
      
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
   * Test case which parses the TNTP Chicago network, zoning and trips files, loads it into PLANit memory model and persists it as a PLANit network
   */
  @Test
  public void testTntp2PlanitDemandsChicago() {

    final Path PLANIT_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(),"testcases","planit","chicago");
    final Path PLANIT_REF_DIR = Path.of(RESOURCE_PATH.toString(),"planit","chicago");

    try {
      
      /* TNTP network.zoning reader */
      var idToken = IdGenerator.createIdGroupingToken("testTntp2PlanitDemandsChicago");
      TntpNetworkReader tntpNetworkReader = createChicagoTntpNetworkReader(CHICAGO_NETWORK_FILE, CHICAGO_NODE_FILE, DEFAULT_MAXIMUM_SPEED, idToken);
      var planitNetwork = (MacroscopicNetwork) tntpNetworkReader.read();      
      
      TntpZoningReader tntpZoningReader = TntpZoningReaderFactory.create(CHICAGO_NETWORK_FILE.toAbsolutePath().toString(), planitNetwork, idToken);
      var zoning = tntpZoningReader.read();
      
      /* TNTP DEMAND reader */
      TntpDemandsReader tntpDemandsReader = TntpDemandsReaderFactory.create(CHICAGO_DEMAND_FILE.toAbsolutePath().toString(), planitNetwork, zoning, idToken);
      tntpDemandsReader.getSettings().setStartTimeSinceMidNight(8, TimeUnits.HOURS);
      tntpDemandsReader.getSettings().setTimePeriodDuration(1, TimeUnits.HOURS);     
      
      /* PLANit DEMAND writer */
      PlanitDemandsWriter planitWriter = PlanitDemandsWriterFactory.create(PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), zoning);
      
      /* convert */
      DemandsConverter theConverter = DemandsConverterFactory.create(tntpDemandsReader, planitWriter);
      theConverter.convert();

      PlanitAssertionUtils.assertNetworkFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertZoningFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertDemandsFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe( e.getMessage());
      fail(e.getMessage());
    }    
  }  
  
  /**
   * Test case which parses the TNTP SiouxFalls network, zoning and trips files, loads it into PLANit memory model and persists it as a PLANit network, demand, and zoning
   */
  @Test
  public void testTntp2PlanitSiouxFalls() {

    final Path PLANIT_OUTPUT_DIR = Path.of(RESOURCE_PATH.toString(),"testcases","planit","siouxfalls");
    final Path PLANIT_REF_DIR = Path.of(RESOURCE_PATH.toString(),"planit","siouxfalls");
    try {      
      /* TNTP network reader */
      var idToken = IdGenerator.createIdGroupingToken("testTntp2PlanitDemandsSiouxFalls");
      var tntpNetworkReader = createSiouxFallsTntpNetworkReader(SIOUXFALLS_NETWORK_FILE, SIOUXFALLS_NODE_FILE, DEFAULT_MAXIMUM_SPEED, idToken);
      tntpNetworkReader.getSettings().setCapacityPeriod(8 /* about 8 hours */, TimeUnits.HOURS);
      var planitNetwork = (MacroscopicNetwork) tntpNetworkReader.read();
      /* PLANit network writer */
      var planitNetworkWriter = PlanitNetworkWriterFactory.create(PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), CountryNames.UNITED_STATES_OF_AMERICA);
      planitNetworkWriter.write(planitNetwork);      
      
      /* TNTP zoning reader */
      var tntpZoningReader = TntpZoningReaderFactory.create(SIOUXFALLS_NETWORK_FILE.toAbsolutePath().toString(), planitNetwork, idToken);
      var zoning = tntpZoningReader.read();
      /* PLANit ZONING writer */
      var planitZoningWriter = PlanitZoningWriterFactory.create(PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), CountryNames.UNITED_STATES_OF_AMERICA, planitNetwork.getCoordinateReferenceSystem());
      planitZoningWriter.write(zoning);      
      
      /* TNTP DEMAND reader */
      var tntpDemandsReader = TntpDemandsReaderFactory.create(SIOUXFALLS_DEMAND_FILE.toAbsolutePath().toString(), planitNetwork, zoning, idToken);
      tntpDemandsReader.getSettings().setStartTimeSinceMidNight(8, TimeUnits.HOURS);
      tntpDemandsReader.getSettings().setTimePeriodDuration(12*0.1 /* 10% of daily flow/capacity as per github readme*/, TimeUnits.HOURS);           
      /* PLANit DEMAND writer */
      var planitWriter = PlanitDemandsWriterFactory.create(PLANIT_OUTPUT_DIR.toAbsolutePath().toString(), zoning);
      
      /* convert */
      var theConverter = DemandsConverterFactory.create(tntpDemandsReader, planitWriter);
      theConverter.convert();

      PlanitAssertionUtils.assertNetworkFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertZoningFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);
      PlanitAssertionUtils.assertDemandsFilesSimilar(PLANIT_OUTPUT_DIR, PLANIT_REF_DIR);

    } catch (final Exception e) {
      e.printStackTrace();
      LOGGER.severe( e.getMessage());
      fail(e.getMessage());
    }    
  }    
  
}