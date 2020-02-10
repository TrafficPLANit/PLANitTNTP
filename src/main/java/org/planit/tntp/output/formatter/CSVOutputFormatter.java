package org.planit.tntp.output.formatter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.apache.commons.csv.CSVPrinter;
import org.planit.exceptions.PlanItException;
import org.planit.logging.PlanItLogger;
import org.planit.network.physical.LinkSegment;
import org.planit.output.adapter.LinkOutputTypeAdapter;
import org.planit.output.adapter.OutputAdapter;
import org.planit.output.configuration.OutputTypeConfiguration;
import org.planit.output.enums.OutputType;
import org.planit.output.enums.OutputTypeEnum;
import org.planit.output.formatter.CsvFileOutputFormatter;
import org.planit.output.formatter.CsvTextFileOutputFormatter;
import org.planit.output.property.BaseOutputProperty;
import org.planit.output.property.OutputProperty;
import org.planit.time.TimePeriod;
import org.planit.userclass.Mode;
import org.planit.utils.OutputUtils;

/**
 * Output formatter for CSV output, i.e. this class is capable of persisting
 * output in the CSV data type
 * 
 * @author markr
 */
public class CSVOutputFormatter extends CsvFileOutputFormatter implements CsvTextFileOutputFormatter {

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
	private Map<OutputType, CSVPrinter> printer;

	/**
	 * Write link results for the current time period to the CSV file
	 * 
	 * @param outputTypeConfiguration OutputTypeConfiguration for current  persistence
	 * @param currentOutputType active OutputTypeEnum of the configuration we are persisting for (can be a SubOutputTypeEnum or an OutputType)
	 * @param outputTypeAdapter OutputTypeAdapter for current persistence
	 * @param modes                   Set of modes of travel
	 * @param timePeriod              current time period
	 * @param iterationIndex current iteration index
	 * @throws PlanItException thrown if there is an error
	 */
	@Override
	protected void writeLinkResultsForCurrentTimePeriod(
	            OutputTypeConfiguration outputTypeConfiguration, OutputTypeEnum currentOutputType, OutputAdapter outputAdapter, Set<Mode> modes, TimePeriod timePeriod, int iterationIndex) throws PlanItException {
		
		LinkOutputTypeAdapter linkOutputTypeAdapter = (LinkOutputTypeAdapter) outputAdapter.getOutputTypeAdapter(outputTypeConfiguration.getOutputType());
		SortedSet<BaseOutputProperty> outputProperties = outputTypeConfiguration.getOutputProperties();
		try {
			for (Mode mode : modes) {
				double totalCost = 0.0;
				for (LinkSegment linkSegment : linkOutputTypeAdapter.getLinkSegments()) {
					if (outputTypeConfiguration.isRecordLinksWithZeroFlow() || linkOutputTypeAdapter.isFlowPositive(linkSegment, mode)) {
						totalCost += ((Double) linkOutputTypeAdapter.getLinkOutputPropertyValue(OutputProperty.FLOW, linkSegment, mode, timePeriod, outputTimeUnit.getMultiplier())) *
					                         ((Double) linkOutputTypeAdapter.getLinkOutputPropertyValue(OutputProperty.LINK_COST, linkSegment, mode, timePeriod, outputTimeUnit.getMultiplier()));
						List<Object> rowValues = new ArrayList<Object>();
						for (BaseOutputProperty outputProperty : outputProperties) {
							if (outputProperty.getOutputProperty().equals(OutputProperty.TOTAL_COST_TO_END_NODE)) {
								rowValues.add(OutputUtils.formatObject(totalCost));
							} else {
								rowValues.add(OutputUtils.formatObject(linkOutputTypeAdapter.getLinkOutputPropertyValue(outputProperty.getOutputProperty(), linkSegment, mode, timePeriod, outputTimeUnit.getMultiplier())));
							}
 						}
						printer.get(outputTypeConfiguration.getOutputType()).printRecord(rowValues);
					} 
				}
			}
		} catch (Exception e) {
			throw new PlanItException(e);
		}
	}	
	
	/**
	 * Write Origin-Destination results for the time period to the CSV file
	 * 
	 * @param outputTypeConfiguration OutputTypeConfiguration for current  persistence
	 * @param currentOutputType active OutputTypeEnum of the configuration we are persisting for (can be a SubOutputTypeEnum or an OutputType)
	 * @param outputTypeAdapter OutputTypeAdapter for current persistence
	 * @param modes                   Set of modes of travel
	 * @param timePeriod              current time period
	 * @param iterationIndex current iteration index
	 * @throws PlanItException thrown if there is an error
	 */
	@Override
	protected void writeOdResultsForCurrentTimePeriod(
            OutputTypeConfiguration outputTypeConfiguration, OutputTypeEnum currentOutputType, OutputAdapter outputAdapter, Set<Mode> modes, TimePeriod timePeriod, int iterationIndex) throws PlanItException {	     	   
		PlanItException pe = writeOdResultsForCurrentTimePeriodToCsvPrinter(outputTypeConfiguration, currentOutputType, outputAdapter, modes, timePeriod, printer.get(outputTypeConfiguration.getOutputType()));
		if (pe != null) {
			throw pe;
		}
	}
	
	/**
	 * Write Path results for the time period to the CSV file
	 * 
	 * @param outputTypeConfiguration OutputTypeConfiguration for current  persistence
	 * @param currentOutputType active OutputTypeEnum of the configuration we are persisting for (can be a SubOutputTypeEnum or an OutputType)
	 * @param outputTypeAdapter OutputTypeAdapter for current persistence
	 * @param modes                   Set of modes of travel
	 * @param timePeriod              current time period
	 * @param iterationIndex current iteration index
	 * @throws PlanItException thrown if there is an error
	 */
	@Override
	protected void writePathResultsForCurrentTimePeriod(
	            OutputTypeConfiguration outputTypeConfiguration, OutputTypeEnum currentOutputType, OutputAdapter outputAdapter, Set<Mode> modes, TimePeriod timePeriod, int iterationIndex) throws PlanItException {
		PlanItException pe = writePathResultsForCurrentTimePeriodToCsvPrinter(outputTypeConfiguration, currentOutputType, outputAdapter, modes, timePeriod, printer.get(outputTypeConfiguration.getOutputType()));
		if (pe != null) {
			throw pe;
		}
	}
	
	/**
	 * Write General results for the current time period to the CSV file
	 * 
	 * @param outputTypeConfiguration OutputTypeConfiguration for current  persistence
	 * @param currentOutputType active OutputTypeEnum of the configuration we are persisting for (can be a SubOutputTypeEnum or an OutputType)
	 * @param outputTypeAdapter OutputTypeAdapter for current persistence
	 * @param modes                   Set of modes of travel
	 * @param timePeriod              current time period
	 * @param iterationIndex current iteration index
	 * @throws PlanItException thrown if there is an error
	 */
	@Override
     protected void writeGeneralResultsForCurrentTimePeriod(
	            OutputTypeConfiguration outputTypeConfiguration, OutputTypeEnum currentOutputType, OutputAdapter outputAdapter, Set<Mode> modes, TimePeriod timePeriod, int iterationIndex) throws PlanItException {
		PlanItLogger.info("CSV Output for OutputType GENERAL has not been implemented yet.");
	}

	/**
	 * Write Simulation results for the current time period to the CSV file
	 * 
	 * @param outputTypeConfiguration OutputTypeConfiguration for current  persistence
	 * @param currentOutputType active OutputTypeEnum of the configuration we are persisting for (can be a SubOutputTypeEnum or an OutputType)
	 * @param outputTypeAdapter OutputTypeAdapter for current persistence
	 * @param modes                   Set of modes of travel
	 * @param timePeriod              current time period
	 * @param iterationIndex current iteration index
	 * @throws PlanItException thrown if there is an error
	 */
	@Override
	protected void writeSimulationResultsForCurrentTimePeriod(
	            OutputTypeConfiguration outputTypeConfiguration, OutputTypeEnum currentOutputType, OutputAdapter outputAdapter, Set<Mode> modes, TimePeriod timePeriod, int iterationIndex) throws PlanItException {
		PlanItLogger.info("CSV Output for OutputType SIMULATION has not been implemented yet.");
	}

	/**
	 * Base constructor
	 * 
	 * @throws PlanItException thrown if there is an error
	 */
	public CSVOutputFormatter() throws PlanItException {
		super();
		csvOutputDirectory = DEFAULT_OUTPUT_DIRECTORY;
		csvNameRoot = DEFAULT_NAME_ROOT;
		csvNameExtension = DEFAULT_NAME_EXTENSION;
		printer = new HashMap<OutputType, CSVPrinter>();
	}

	/**
	 * Close output CSV file for a specified output type configuration
	 * 
	 * @param outputTypeConfigurations OutputTypeConfigurations for the assignment that have been activated
	 * @throws PlanItException thrown if the the output file cannot be closed
	 */
	@Override
	public void finaliseAfterSimulation(Map<OutputType, OutputTypeConfiguration> outputTypeConfigurations) throws PlanItException {
		try {
		    for(Map.Entry<OutputType, OutputTypeConfiguration> entry : outputTypeConfigurations.entrySet()) {
	            printer.get(entry.getKey()).close();		        
		    }
		} catch (IOException ioe) {
			throw new PlanItException(ioe);
		}
	}

	/**
	 * Open output CSV file for specified output type configuration.
	 * 
	 * This method also creates the output file directory if it does not already
	 * exist.
	 * 
	 * @param outputTypeConfiguration OutputTypeConfiguration for the assignment to
	 *                                be saved
	 * @throws PlanItException thrown if output file or directory cannot be opened
	 */
	@Override
	public void initialiseBeforeSimulation(Map<OutputType, OutputTypeConfiguration> outputTypeConfigurations, long runId) throws PlanItException {
		try {
		    for(Map.Entry<OutputType, OutputTypeConfiguration> entry : outputTypeConfigurations.entrySet()) {
	            OutputType outputType = entry.getKey();
	            if (!csvFileNameMap.containsKey(outputType)) {
	                String csvFileName = generateOutputFileName(csvOutputDirectory, csvNameRoot, csvNameExtension, null, outputType, runId);
	                addCsvFileNamePerOutputType(outputType, csvFileName);
	            }
	            
	            //In CSVOutputFormatter we can only have one CSV file per output type
	            String csvFileName = csvFileNameMap.get(outputType).get(0);
	            CSVPrinter csvPrinter = openCsvFileAndWriteHeaders(entry.getValue(), csvFileName);
	            printer.put(outputType, csvPrinter);		        
		    }
		} catch (Exception e) {
			throw new PlanItException(e);
		}
	}

	/**
	 * Sets the name of the CSAV output file directory
	 * 
	 * @param outputDirectory the name of the output file directory
	 */
	public void setCsvDirectory(String csvOutputDirectory) {
		this.csvOutputDirectory = csvOutputDirectory;
	}

	/**
	 * Sets the extension of the CSV output file
	 * 
	 * @param nameExtension the extension of the CSV output file
	 */
	public void setCsvNameExtension(String csvNameExtension) {
		this.csvNameExtension = csvNameExtension;
	}

	/**
	 * Sets the root name of the CSV output file
	 * 
	 * @param nameRoot root name of CSV output file
	 */
	public void setCsvNameRoot(String csvNameRoot) {
		this.csvNameRoot = csvNameRoot;
	}

	/**
	 * Returns the name of the CSV output file for a specified output type
	 * 
	 * @param outputType the specified output type
	 * @return the name of the output file
	 */
	public List<String> getCsvFileName(OutputType outputType) {
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