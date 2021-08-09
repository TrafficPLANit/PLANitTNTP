package org.planit.tntp.input;

import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.planit.cost.physical.BPRLinkTravelTimeCost;
import org.planit.component.event.PlanitComponentEvent;
import org.planit.component.event.PopulateDemandsEvent;
import org.planit.component.event.PopulateNetworkEvent;
import org.planit.component.event.PopulatePhysicalCostEvent;
import org.planit.component.event.PopulateZoningEvent;
import org.planit.cost.physical.AbstractPhysicalCost;
import org.planit.demands.Demands;
import org.planit.input.InputBuilderListener;
import org.planit.network.MacroscopicNetwork;
import org.planit.tntp.converter.demands.TntpDemandsReader;
import org.planit.tntp.converter.network.TntpNetworkReader;
import org.planit.tntp.converter.network.TntpNetworkReaderSettings;
import org.planit.tntp.converter.zoning.TntpZoningReader;
import org.planit.tntp.enums.CapacityPeriod;
import org.planit.tntp.enums.LengthUnits;
import org.planit.tntp.enums.NetworkFileColumnType;
import org.planit.tntp.enums.SpeedUnits;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.misc.LoggingUtils;
import org.planit.utils.misc.Pair;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.network.layer.physical.LinkSegment;
import org.planit.zoning.Zoning;

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

  
  /**
   * Creates the physical network object from the data in the input file
   *
   * @param network the network object to be populated from the input data
   * @throws PlanItException thrown if there is an error reading the input file
   */
  protected void populateMacroscopicNetwork( final MacroscopicNetwork macroscopicNetwork) throws PlanItException {
       
    TntpNetworkReader networkReader = new TntpNetworkReader(networkFileLocation, nodeCoordinateFileLocation, networkSettings);    
    networkReader.getSettings().setNetworkToPopulate(macroscopicNetwork);

    /* parse */
    networkReader.read();
  }

  /**
   * Populates the Demands object from the input file
   *
   * @param demands the Demands object to be populated from the input data
   * @param zoning Zoning object previously defined
   * @param parameter2 Network to use
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
  }

  /**
   * Populate the BPR parameters
   *
   * @param costComponent the BPRLinkTravelTimeCost to be populated
   * @throws PlanItException thrown if there is an error
   */
  protected void populatePhysicalCost( final AbstractPhysicalCost costComponent) throws PlanItException {
    LOGGER.info(LoggingUtils.getClassNameWithBrackets(this)+"populating BPR link costs");
    
    Mode mode = getTntpZoningReader().getSettings().getReferenceNetwork().getTransportLayers().getFirst().getFirstSupportedMode();
    Map<LinkSegment, Pair<Double, Double>> bprParametersForLinkSegmentAndMode = getTntpNetworkReader().getParsedBprParameters();
    if (bprParametersForLinkSegmentAndMode != null) {
      final BPRLinkTravelTimeCost bprLinkTravelTimeCost = (BPRLinkTravelTimeCost) costComponent;
      for (final Entry<LinkSegment, Pair<Double, Double>> entry : bprParametersForLinkSegmentAndMode.entrySet()) {
        final Pair<Double, Double> alphaBeta = entry.getValue();
        final MacroscopicLinkSegment macroscopicLinkSegment = (MacroscopicLinkSegment) entry.getKey();
        bprLinkTravelTimeCost.setParameters(macroscopicLinkSegment, mode, alphaBeta.first(), alphaBeta.second());
      }
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
   * @param demandFileLocation demand file location
   * @param nodeCoordinateFileLocation node coordinate file location
   * @param networkFileColumns Map specifying which columns in the network file contain which values
   * @param speedUnits speed units being used
   * @param lengthUnits length units being used
   * @param capacityPeriod time period for link capacity
   * @param defaultMaximumSpeed default maximum speed along links
   * @throws PlanItException thrown if there is an error during running
   */
  public TntpInputBuilder(final String networkFileLocation, final String demandFileLocation,
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
      populatePhysicalCost(physicalCostEvent.getPhysicalCostToPopulate());
    } else {      
      /* generic case */
      LOGGER.fine("Event component " + event.getClass().getCanonicalName() + " ignored by TNTP InputBuilder");
    }
  }

}
