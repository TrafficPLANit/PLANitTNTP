package org.goplanit.tntp.input;

import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.goplanit.component.event.PlanitComponentEvent;
import org.goplanit.component.event.PopulateDemandsEvent;
import org.goplanit.component.event.PopulateNetworkEvent;
import org.goplanit.component.event.PopulatePhysicalCostEvent;
import org.goplanit.component.event.PopulateZoningEvent;
import org.goplanit.cost.physical.AbstractPhysicalCost;
import org.goplanit.cost.physical.BPRLinkTravelTimeCost;
import org.goplanit.demands.Demands;
import org.goplanit.input.InputBuilderListener;
import org.goplanit.network.MacroscopicNetwork;
import org.goplanit.tntp.converter.demands.TntpDemandsReader;
import org.goplanit.tntp.converter.network.TntpNetworkReader;
import org.goplanit.tntp.converter.network.TntpNetworkReaderSettings;
import org.goplanit.tntp.converter.zoning.TntpZoningReader;
import org.goplanit.tntp.enums.CapacityPeriod;
import org.goplanit.tntp.enums.LengthUnits;
import org.goplanit.tntp.enums.NetworkFileColumnType;
import org.goplanit.tntp.enums.SpeedUnits;
import org.goplanit.utils.exceptions.PlanItException;
import org.goplanit.utils.misc.LoggingUtils;
import org.goplanit.utils.misc.Pair;
import org.goplanit.utils.mode.Mode;
import org.goplanit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.goplanit.utils.network.layer.physical.LinkSegment;
import org.goplanit.zoning.Zoning;

/**
 * Class which reads input from TNTP files
 *
 * @author gman6028, markr
 *
 */
public class TntpInputBuilder extends InputBuilderListener {

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(TntpInputBuilder.class.getCanonicalName());
  
  private final String networkFileLocation;
  
  private final String nodeCoordinateFileLocation;
  
  private final TntpNetworkReaderSettings networkSettings;
  
  private final String demandsFileLocation;
  
  /** track parsed BPR parameters from network parsing and make available to cost initialisation */
  private Map<LinkSegment, Pair<Double, Double>> bprParametersPerLinkSegment;

  
  /**
   * Creates the physical network object from the data in the input file
   *
   * @param macroscopicNetwork the network object to be populated from the input data
   * @throws PlanItException thrown if there is an error reading the input file
   */
  protected void populateMacroscopicNetwork( final MacroscopicNetwork macroscopicNetwork) throws PlanItException {
       
    TntpNetworkReader networkReader = new TntpNetworkReader(networkFileLocation, nodeCoordinateFileLocation, networkSettings);    
    networkReader.getSettings().setNetworkToPopulate(macroscopicNetwork);

    /* parse */
    networkReader.read();
    this.bprParametersPerLinkSegment = networkReader.getParsedBprParameters();
    
    networkReader.reset();
  }

  /**
   * Populates the Demands object from the input file
   *
   * @param demands the Demands object to be populated from the input data
   * @param zoning Zoning object previously defined
   * @param network Network to use
   * @throws PlanItException thrown if there is an error reading the input file
   */
  protected void populateDemands( final Demands demands, final Zoning zoning, final MacroscopicNetwork network) throws PlanItException {
    
    TntpDemandsReader demandsReader = new TntpDemandsReader(demandsFileLocation);
    /* prep */
    demandsReader.getSettings().setDemandsToPopulate(demands);
    demandsReader.getSettings().setReferenceNetwork(network);
    demandsReader.getSettings().setReferenceZoning(zoning);
        
    /* parse */
    demandsReader.read();
    demandsReader.reset();
  }

  /**
   * Creates the Zoning object and connectoids from the data in the network file
   *
   * In TNTP this method does not need to read a TAZ file. It simply uses the number of zones, which
   * has already been read in from the network file.
   *
   * @param zoning the Zoning object to be populated from the input data
   * @param parameter1 the physical network object previously created
   * @throws PlanItException thrown if there is an error reading the input file
   */
  protected void populateZoning(final Zoning zoning, final Object parameter1) throws PlanItException {
    
    if (!(parameter1 instanceof MacroscopicNetwork)) {
      throw new PlanItException("TNTP reader currently only supports writing macroscopic networks");
    }
    final MacroscopicNetwork macroscopicNetwork = (MacroscopicNetwork) parameter1;
    
    TntpZoningReader zoningReader = new TntpZoningReader(networkFileLocation);    
    /* prep */
    zoningReader.getSettings().setZoningToPopulate(zoning);
    zoningReader.getSettings().setReferenceNetwork(macroscopicNetwork);
        
    /* parse */
    zoningReader.read();
    zoningReader.reset();
  }

  /**
   * Populate the BPR parameters
   *
   * @param costComponent the BPRLinkTravelTimeCost to be populated
   * @param parentNetwork top use for costs
   * @throws PlanItException thrown if there is an error
   */
  protected void populatePhysicalCost( final AbstractPhysicalCost costComponent, final MacroscopicNetwork parentNetwork) throws PlanItException {
    LOGGER.info(LoggingUtils.getClassNameWithBrackets(this)+"populating BPR link costs");
    
    Mode mode = parentNetwork.getModes().getFirst();
    if (bprParametersPerLinkSegment == null) {
      LOGGER.warning("BPR parameters not available upon populating physical costs in TNTP input builder, ignore");
      return;
    }
    if(!(costComponent instanceof BPRLinkTravelTimeCost)) {
      LOGGER.warning(String.format("Expected BPR cost to be populated by found %s, ignore", costComponent.getClass().getCanonicalName()));
      return;
    }
    
    final BPRLinkTravelTimeCost bprLinkTravelTimeCost = (BPRLinkTravelTimeCost) costComponent;
    for (final Entry<LinkSegment, Pair<Double, Double>> entry : bprParametersPerLinkSegment.entrySet()) {
      final Pair<Double, Double> alphaBeta = entry.getValue();
      final MacroscopicLinkSegment macroscopicLinkSegment = (MacroscopicLinkSegment) entry.getKey();
      bprLinkTravelTimeCost.setParameters(macroscopicLinkSegment, mode, alphaBeta.first(), alphaBeta.second());
    }
  }

  /**
   * Constructor
   *
   * @param networkFileLocation network file location
   * @param demandFileLocation demand file location
   * @param networkFileColumns Map specifying which columns in the network file contain which values
   * @param speedUnits speed units being used
   * @param lengthUnits length units being used
   * @param capacityPeriod time period for link capacity
   * @param defaultMaximumSpeed default maximum speed along a link
   * @throws PlanItException thrown if there is an error during running
   */
  public TntpInputBuilder(final String networkFileLocation, final String demandFileLocation,
      final Map<NetworkFileColumnType, Integer> networkFileColumns, final SpeedUnits speedUnits,
      final LengthUnits lengthUnits,
      final CapacityPeriod capacityPeriod, final double defaultMaximumSpeed)
      throws PlanItException {
    this(networkFileLocation, demandFileLocation, null, networkFileColumns, speedUnits, lengthUnits,
        capacityPeriod, defaultMaximumSpeed);
  }

  /**
   * Constructor
   *
   * @param networkFileLocation network file location
   * @param demandFileLocation demand file location
   * @param networkFileColumns Map specifying which columns in the network file contain which values
   * @param speedUnits speed units being used
   * @param lengthUnits length units being used
   * @param defaultMaximumSpeed default maximum speed along a link
   * @throws PlanItException thrown if there is an error during running
   */
  public TntpInputBuilder(final String networkFileLocation, final String demandFileLocation,
      final Map<NetworkFileColumnType, Integer> networkFileColumns, final SpeedUnits speedUnits,
      final LengthUnits lengthUnits,
      final double defaultMaximumSpeed)
      throws PlanItException {
    this(networkFileLocation, demandFileLocation, null, networkFileColumns, speedUnits, lengthUnits, null,
        defaultMaximumSpeed);
  }

  /**
   * Constructor
   *
   * @param networkFileLocation network file location
   * @param demandFileLocation demand file location
   * @param nodeCoordinateFileLocation node coordinate file location
   * @param standardResultsFileLocation the location of the standard results file
   * @param networkFileColumns Map specifying which columns in the network file contain which values
   * @param speedUnits speed units being used
   * @param lengthUnits length units being used
   * @param defaultMaximumSpeed default maximum speed along a link
   * @throws PlanItException thrown if there is an error during running
   */
  public TntpInputBuilder(final String networkFileLocation, final String demandFileLocation,
      final String nodeCoordinateFileLocation, final String standardResultsFileLocation,
      final Map<NetworkFileColumnType, Integer> networkFileColumns,
      final SpeedUnits speedUnits,
      final LengthUnits lengthUnits, final double defaultMaximumSpeed) throws PlanItException {
    this(networkFileLocation, demandFileLocation, nodeCoordinateFileLocation, networkFileColumns, speedUnits,
        lengthUnits, null, defaultMaximumSpeed);
  }

  /**
   * Constructor
   *
   * @param networkFileLocation network file location
   * @param demandsFileLocation demand file location
   * @param nodeCoordinateFileLocation node coordinate file location
   * @param networkFileColumns Map specifying which columns in the network file contain which values
   * @param speedUnits speed units being used
   * @param lengthUnits length units being used
   * @param capacityPeriod time period for link capacity
   * @param defaultMaximumSpeed default maximum speed along links
   * @throws PlanItException thrown if there is an error during running
   */
  public TntpInputBuilder(final String networkFileLocation, final String demandsFileLocation,
      final String nodeCoordinateFileLocation, final Map<NetworkFileColumnType, Integer> networkFileColumns,
      final SpeedUnits speedUnits, final LengthUnits lengthUnits, final CapacityPeriod capacityPeriod,
      final double defaultMaximumSpeed) throws PlanItException {

    super();

    /* network reader */
    this.networkSettings = new TntpNetworkReaderSettings();
    this.networkFileLocation = networkFileLocation;
    this.nodeCoordinateFileLocation = nodeCoordinateFileLocation;
    
    networkSettings.setCapacityPeriod((capacityPeriod == null) ? CapacityPeriod.HOUR : capacityPeriod);
    networkSettings.setLengthUnits(lengthUnits);
    networkSettings.setNetworkFileColumns(networkFileColumns);
    networkSettings.setSpeedUnits(speedUnits);
    networkSettings.setDefaultMaximumSpeed(defaultMaximumSpeed);
        
    this.demandsFileLocation = demandsFileLocation;
  }

  /**
   * Whenever a project component is created this method will be invoked
   *
   * @param event event containing the created (and empty) project component
   * @throws PlanItException thrown if there is an error
   */
  @Override
  public void onPlanitComponentEvent(final PlanitComponentEvent event) throws PlanItException {
    // registered for create notifications
    if (event.getType().equals(PopulateNetworkEvent.EVENT_TYPE)) {
      populateMacroscopicNetwork(((PopulateNetworkEvent)event).getNetworkToPopulate());
    }else if(event.getType().equals(PopulateZoningEvent.EVENT_TYPE)){
      PopulateZoningEvent zoningEvent = ((PopulateZoningEvent) event);
      populateZoning(zoningEvent.getZoningToPopulate(), zoningEvent.getParentNetwork());
    }else if(event.getType().equals(PopulateDemandsEvent.EVENT_TYPE)){
      PopulateDemandsEvent demandsEvent = ((PopulateDemandsEvent) event);
      populateDemands(demandsEvent.getDemandsToPopulate(), demandsEvent.getParentZoning(), demandsEvent.getParentNetwork());
    }else if(event.getType().equals(PopulatePhysicalCostEvent.EVENT_TYPE)){
      PopulatePhysicalCostEvent physicalCostEvent = ((PopulatePhysicalCostEvent) event);
      populatePhysicalCost(physicalCostEvent.getPhysicalCostToPopulate(), physicalCostEvent.getParentNetwork());
    } else {      
      /* generic case */
      LOGGER.fine("Event component " + event.getClass().getCanonicalName() + " ignored by TNTP InputBuilder");
    }
  }

}
