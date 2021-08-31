package org.planit.tntp.output.formatter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVPrinter;
import org.planit.output.adapter.MacroscopicLinkOutputTypeAdapter;
import org.planit.output.adapter.OutputAdapter;
import org.planit.output.configuration.OutputConfiguration;
import org.planit.output.configuration.OutputTypeConfiguration;
import org.planit.output.enums.OutputType;
import org.planit.output.enums.OutputTypeEnum;
import org.planit.output.formatter.CsvFileOutputFormatter;
import org.planit.output.formatter.CsvTextFileOutputFormatter;
import org.planit.output.property.BaseOutputProperty;
import org.planit.utils.time.TimePeriod;
import org.planit.utils.exceptions.PlanItException;
import org.planit.utils.id.IdGroupingToken;
import org.planit.utils.mode.Mode;
import org.planit.utils.network.layer.macroscopic.MacroscopicLinkSegment;
import org.planit.utils.output.OutputUtils;

/**
 * Output formatter for CSV output, i.e. this class is capable of persisting
 * output in the CSV data type
 *
 * @author markr
 */
public class CSVOutputFormatter extends CsvFileOutputFormatter implements CsvTextFileOutputFormatter {

  /** the logger */
  private static final Logger LOGGER = Logger.getLogger(CSVOutputFormatter.class.getCanonicalName());

	private static final String DEFAULT_NAME_EXTENSION = ".csv";
	private static final String DEFAULT_NAME_ROOT = "CSVOutput";
	private static final String DEFAULT_OUTPUT_DIRECTORY = "C:\\Users\\Public\\PlanIt\\Csv";

	/**
	 * Extension for the CSV output file
	 */
	private String csvNameExtension;

	/**
	 * Root name of the CSV output file
	 */
	private String csvNameRoot;

	/**
	 * Directory of the CSV output file
	 */
	private String csvOutputDirectory;

	/**
	 * CSV Printer objects results are written to
	 */
	private final Map<OutputType, CSVPrinter> printer;

	/**
	 * Write link results for the current time period to the CSV file
	 *
	 * @param outputConfiguration output configuration
	 * @param outputTypeConfiguration OutputTypeConfiguration for current  persistence
	 * @param currentOutputType active OutputTypeEnum of the configuration we are persisting for (can be a SubOutputTypeEnum or an OutputType)
	 * @param outputAdapter OutputAdapter for current persistence
	 * @param modes                   Set of modes of travel
	 * @param timePeriod              current time period
	 * @param iterationIndex current iteration index
	 * @throws PlanItException thrown if there is an error
	 */
  @Override
	protected void writeLinkResultsForCurrentTimePeriod(final OutputConfiguration outputConfiguration,
	            final OutputTypeConfiguration outputTypeConfiguration, final OutputTypeEnum currentOutputType, final OutputAdapter outputAdapter, final Set<Mode> modes, final TimePeriod timePeriod, final int iterationIndex) throws PlanItException {

		final MacroscopicLinkOutputTypeAdapter linkOutputTypeAdapter = 
		    (MacroscopicLinkOutputTypeAdapter) outputAdapter.getOutputTypeAdapter(outputTypeConfiguration.getOutputType());
		
		final SortedSet<BaseOutputProperty> outputProperties = outputTypeConfiguration.getOutputProperties();
		try {
			for (final Mode mode : modes) {
			  Optional<Long> layerId = linkOutputTypeAdapter.getInfrastructureLayerIdForMode(mode);
			  layerId.orElseThrow(() -> new PlanItException("unable to retrieve layer id for mode"));
			  
				for (final MacroscopicLinkSegment linkSegment : linkOutputTypeAdapter.getPhysicalLinkSegments(layerId.get())) {
				  Optional<Boolean> flowPositive = linkOutputTypeAdapter.isFlowPositive(linkSegment, mode);
				  flowPositive.orElseThrow(() -> new PlanItException("unable to determine if flow is positive for link segment and mode"));
				  
					if (outputConfiguration.isPersistZeroFlow() || flowPositive.get()) {
					  final List<Object> rowValues = new ArrayList<Object>();						
					  for (final BaseOutputProperty outputProperty : outputProperties) {
              rowValues.add(
                  OutputUtils.formatObject(
                      linkOutputTypeAdapter.getLinkSegmentOutputPropertyValue(
                          outputProperty.getOutputProperty(), linkSegment, mode, timePeriod).get()));
 						}
						printer.get(outputTypeConfiguration.getOutputType()).printRecord(rowValues);
					}
				}
			}
		} catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error when writing link results for current time period in TNTP",e);
    }
	}

	/**
	 * Write Origin-Destination results for the time period to the CSV file
	 *
   * @param outputConfiguration output configuration
	 * @param outputTypeConfiguration OutputTypeConfiguration for current  persistence
	 * @param currentOutputType active OutputTypeEnum of the configuration we are persisting for (can be a SubOutputTypeEnum or an OutputType)
	 * @param outputAdapter OutputAdapter for current persistence
	 * @param modes                   Set of modes of travel
	 * @param timePeriod              current time period
	 * @param iterationIndex current iteration index
	 * @throws PlanItException thrown if there is an error
	 */
	@Override
	protected void writeOdResultsForCurrentTimePeriod(final OutputConfiguration outputConfiguration,
            final OutputTypeConfiguration outputTypeConfiguration, final OutputTypeEnum currentOutputType, final OutputAdapter outputAdapter, final Set<Mode> modes, final TimePeriod timePeriod, final int iterationIndex) throws PlanItException {
		final PlanItException pe = writeOdResultsForCurrentTimePeriodToCsvPrinter(outputConfiguration, outputTypeConfiguration, currentOutputType, outputAdapter, modes, timePeriod, printer.get(outputTypeConfiguration.getOutputType()));
		if (pe != null) {
			throw pe;
		}
	}

	/**
	 * Write Path results for the time period to the CSV file
	 *
   * @param outputConfiguration output configuration
	 * @param outputTypeConfiguration OutputTypeConfiguration for current  persistence
	 * @param currentOutputType active OutputTypeEnum of the configuration we are persisting for (can be a SubOutputTypeEnum or an OutputType)
	 * @param outputAdapter OutputAdapter for current persistence
	 * @param modes                   Set of modes of travel
	 * @param timePeriod              current time period
	 * @param iterationIndex current iteration index
	 * @throws PlanItException thrown if there is an error
	 */
	@Override
	protected void writePathResultsForCurrentTimePeriod(final OutputConfiguration outputConfiguration,
	            final OutputTypeConfiguration outputTypeConfiguration, final OutputTypeEnum currentOutputType, final OutputAdapter outputAdapter, final Set<Mode> modes, final TimePeriod timePeriod, final int iterationIndex) throws PlanItException {
		final PlanItException pe = writePathResultsForCurrentTimePeriodToCsvPrinter(outputConfiguration, outputTypeConfiguration, currentOutputType, outputAdapter, modes, timePeriod, printer.get(outputTypeConfiguration.getOutputType()));
		if (pe != null) {
			throw pe;
		}
	}

	/**
	 * Write General results for the current time period to the CSV file
	 *
   * @param outputConfiguration output configuration
	 * @param outputTypeConfiguration OutputTypeConfiguration for current  persistence
	 * @param currentOutputType active OutputTypeEnum of the configuration we are persisting for (can be a SubOutputTypeEnum or an OutputType)
	 * @param outputAdapter OutputAdapter for current persistence
	 * @param modes                   Set of modes of travel
	 * @param timePeriod              current time period
	 * @param iterationIndex current iteration index
	 * @throws PlanItException thrown if there is an error
	 */
	@Override
     protected void writeGeneralResultsForCurrentTimePeriod(final OutputConfiguration outputConfiguration,
	            final OutputTypeConfiguration outputTypeConfiguration, final OutputTypeEnum currentOutputType, final OutputAdapter outputAdapter, final Set<Mode> modes, final TimePeriod timePeriod, final int iterationIndex) throws PlanItException {
	  LOGGER.info("CSV Output for OutputType GENERAL has not been implemented yet.");
	}

	/**
	 * Write Simulation results for the current time period to the CSV file
	 *
   * @param outputConfiguration output configuration
	 * @param outputTypeConfiguration OutputTypeConfiguration for current  persistence
	 * @param currentOutputType active OutputTypeEnum of the configuration we are persisting for (can be a SubOutputTypeEnum or an OutputType)
	 * @param outputAdapter OutputAdapter for current persistence
	 * @param modes                   Set of modes of travel
	 * @param timePeriod              current time period
	 * @param iterationIndex current iteration index
	 * @throws PlanItException thrown if there is an error
	 */
	@Override
	protected void writeSimulationResultsForCurrentTimePeriod(final OutputConfiguration outputConfiguration,
	            final OutputTypeConfiguration outputTypeConfiguration, final OutputTypeEnum currentOutputType, final OutputAdapter outputAdapter, final Set<Mode> modes, final TimePeriod timePeriod, final int iterationIndex) throws PlanItException {
	  LOGGER.info("CSV Output for OutputType SIMULATION has not been implemented yet.");
	}

	/**
	 * Base constructor
	 *
   * @param groupId contiguous id generation within this group for instances of this class
	 * @throws PlanItException thrown if there is an error
	 */
	public CSVOutputFormatter(IdGroupingToken groupId) throws PlanItException {
		super(groupId);
		csvOutputDirectory = DEFAULT_OUTPUT_DIRECTORY;
		csvNameRoot = DEFAULT_NAME_ROOT;
		csvNameExtension = DEFAULT_NAME_EXTENSION;
		printer = new HashMap<OutputType, CSVPrinter>();
	}

	/**
	 * Close output CSV file for a specified output type configuration
	 *
	 * @param outputConfiguration OutputConfiguration of the assignment
	 * @param outputAdapter the outputAdapter
	 * @throws PlanItException thrown if the the output file cannot be closed
	 */
	@Override
	public void finaliseAfterSimulation(final OutputConfiguration outputConfiguration, OutputAdapter outputAdapter) throws PlanItException {
		try {
		    for(final OutputType outputType : outputConfiguration.getActivatedOutputTypes()) {
	            printer.get(outputType).close();
		    }
		} catch (final IOException e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error when finalising after simulation in TNTP",e);
		}
	}

	/**
	 * Open output CSV file for specified output type configuration.
	 *
	 * This method also creates the output file directory if it does not already
	 * exist.
	 *
	 * @param outputConfiguration OutputConfiguration of the assignment
	 * @throws PlanItException thrown if output file or directory cannot be opened
	 */
	@Override
	public void initialiseBeforeSimulation(final OutputConfiguration outputConfiguration, final long runId) throws PlanItException {
		try {
		    for(final OutputType outputType : outputConfiguration.getActivatedOutputTypes()) {
	            if (!csvFileNameMap.containsKey(outputType)) {
	                final String csvFileName = generateOutputFileName(csvOutputDirectory, csvNameRoot, csvNameExtension, null, outputType, runId);
	                addCsvFileNamePerOutputType(outputType, csvFileName);
	            }

	            //In CSVOutputFormatter we can only have one CSV file per output type
	            final String csvFileName = csvFileNameMap.get(outputType).get(0);
	            final CSVPrinter csvPrinter = openCsvFileAndWriteHeaders(outputConfiguration.getOutputTypeConfiguration(outputType), csvFileName);
	            printer.put(outputType, csvPrinter);
		    }
		} catch (final Exception e) {
      LOGGER.severe(e.getMessage());
      throw new PlanItException("Error when initialising before simulation in TNTP",e);
    }
	}

	/**
	 * Sets the name of the CSAV output file directory
	 *
	 * @param csvOutputDirectory the name of the output file directory
	 */
	@Override
  public void setCsvDirectory(final String csvOutputDirectory) {
		this.csvOutputDirectory = csvOutputDirectory;
	}

	/**
	 * Sets the extension of the CSV output file
	 *
	 * @param csvNameExtension the extension of the CSV output file
	 */
	@Override
  public void setCsvNameExtension(final String csvNameExtension) {
		this.csvNameExtension = csvNameExtension;
	}

	/**
	 * Sets the root name of the CSV output file
	 *
	 * @param csvNameRoot root name of CSV output file
	 */
	@Override
  public void setCsvNameRoot(final String csvNameRoot) {
		this.csvNameRoot = csvNameRoot;
	}

	/**
	 * Returns the name of the CSV output file for a specified output type
	 *
	 * @param outputType the specified output type
	 * @return the name of the output file
	 */
	@Override
  public List<String> getCsvFileName(final OutputType outputType) {
		return csvFileNameMap.get(outputType);
	}

	/**
	 * Flag to indicate whether an implementation can handle multiple iterations
	 *
	 * If this returns false, acts as though OutputConfiguration.setPersistOnlyFinalIteration() is set to true
	 *
	 * @return flag to indicate whether the OutputFormatter can handle multiple iterations
	 */
	@Override
	public boolean canHandleMultipleIterations() {
		return false;
	}
}