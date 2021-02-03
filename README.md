# PLANitTNTP

PLANit Interface for TNTP format files

The input files for this format can be found at https://github.com/bstabler/TransportationNetworks

February 2020: So far we have got the standard files for Philadelphia and Chicago-Sketch to run.  The test case for Philadelphia runs using this code but we have not validated the outputs. The contents and format of the input data files is different for some data sets which we have not yet used.  
For example, the GoldCoast network file (https://github.com/bstabler/TransportationNetworks/blob/master/GoldCoast/Goldcoast_network_2016_01.tntp) has a different format from that described below.  We have not yet written code which could run the Gold Coast data set.

For more information on PLANit such as the user the manual, licensing, installation, getting started, reference documentation, and more, please visit [https://trafficplanit.github.io/PLANitManual/](https://trafficplanit.github.io/PLANitManual/)

## Maven parent

Projects need to be built from Maven before they can be run. The common maven configuration can be found in the PLANitParentPom project which acts as the parent for this project's pom.xml.

> Make sure you install the PLANitParentPom pom.xml before conducting a maven build (in Eclipse) on this project, otherwise it cannot find the references dependencies, plugins, and other resources.

# Data Files 

Comment lines in files begin with the tilde symbol '~' and are ignored.

Data files use columns which are separated by one or more spaces.  The number of spaces used to separate columns varies from row to row, so the columns in a file do not always line up in a manner which is neat to humans.  

### Network File (required, name ends _net.tntp)

Metadata Information at top of file:

<NUMBER OF ZONES>										
<NUMBER OF NODES>										
<FIRST THRU NODE>										
<NUMBER OF LINKS>										
<END OF METADATA>			

Row information, columns for each link (values required unless stated):-

* Upstream Node Number ('tail node')
* Downstream Node Number ('head node')
* Capacity (vehicles/hr)
* Length - units may be given in the header or the project notes, assume kilometres otherwise
* Free Flow Travel Time - Not currently used
* B - alpha value used in BPR function
* Power - beta value used in BPR function
* Speed Limit - units may be given in the header or the project notes, assume km/h otherwise.  ChicagoSketch uses default of 25 m/h instead of this value for Type 3 links
* Toll - Not currently used
* Link Type (an integer)

For ChicagoSketch and Philadelphia, each row defines a link which is one-way and single-lane.  In many cases one line defines a link in one direction and a later line defines another link between the same two nodes but in the other direction, but TNTP does not use a directionality property.
Gold Coast has columns 'critical_speed' and 'lanes'.  Values corresponding to these have been included in the Java enumeration, but these are not yet used.

A Java enumeration NetworkFileColumns has been defined to specify the possible roles of each column in the network input file.  In the TntpMain.execute() method, these are used as keys to a Map whose values are the corresponding columns.

### Trips File (required, name ends _trips.tntp)

This is the equivalent of the Demands file used in other input formats.

Metadata Information at top of file:

<NUMBER OF ZONES>  (Should match value in Network File)
<TOTAL OD FLOW>
<END OF METADATA>

For every origin node number, contains a list of destinations in format <destination node number>: <demand>;

For example, the start of the file for Chicago Sketch (covering the demands from node 1 to the first 20 nodes) is as follows:- 

Origin         1
    1 :     273.18;      2 :     347.31;      3 :     390.81;      4 :     204.51;      5 :     373.73;  
    6 :     199.70;      7 :     119.69;      8 :      96.23;      9 :      39.53;     10 :      75.72;  
   11 :      24.02;     12 :      27.71;     13 :      32.98;     14 :     124.31;     15 :      72.66;  
   16 :     124.83;     17 :     311.17;     18 :     157.00;     19 :      32.43;     20 :      13.38;  
   
Sometimes a destination-demand pair is not defined for an origin, in this case the demand is assumed to be zero.

The default units are trips/hr, but Philadelphia uses trips/day.

### Node Locations File (optional, name ends _node.tntp)

After the first line which is a header, all lines have three columns:-

* Node number ('node')
* X coordinate ('X')
* Y coordinate ('Y')

If this file is present, it is read and used to create coordinate pairs for each node.  But link lengths are not derived from these coordinates, the lengths given in the network file are used instead.

### Standard Results File (optional, name ends _flow.tntp);

After the first line which is a header, all lines have four columns:-

* Upstream Node External Id ("From')
* Downstream Node External Id ('To')
* Flow ('Volume') - vehicles/hr
* Cost ('Cost') - hr

At present only Chicago Sketch has a file of this type.  It is used in the PLANit unit testing.

# Single Mode and Time Period

TNTP data does not use modes or time periods.  One dummy mode and one time dummy period are created.  These are internally used where required. 

# Running the TntpMain Class

The Tntp class, which extends InputBuilderListener, contains the notify(), populatePhysicalNetwork(), populateZoning() and populateDemands() methods for TNTP format.  This called from TntpProject, which extends CustomPlanItProject.

The TntpMain class contains a main() method and can be run as a standalone program, with the locations of the input files used a run-time arguments.  But it also contains a public method execute() which calls the TntpProject, populates the components and executes the traffic assignment.
The TntpMain class can be instantiated and its execute() method run from another class.  The unit test TNTPTest does this.

If a standard results file has been specifed, TntpMain.execute() returns a Map with the standard flow and cost for the upstream and downstream nodes for each link.  TntpProject has a method createStandardResultsFile() which returns this Map.  
If the null is given for the parameter value of the standardResultsFileLocation argument to TntpMain.execute(), meaning there is no standard results file, the execute() method returns a null.  This is convenient for unit testing.  

## Command Line Arguments

The command line arguments specify the parameters used during the assignment run, particularly the input and output files.  Each argument is defined using a "name=value" format, where "name" is one from a list of possible argument types and "value" is a string containing the argument value.
  
The arguments are given in a space-separated list.  There are ten possible arguments, six of which are required.  The full list of possible arguments is:

|Name|Meaning|
|---| ---|
|network|Location of _net.tntp file (required)| 
|demands|Location of _trips.tntp file (required)| 
|nodecoordinates|Location of _node.tntp (optional)|
|linkoutput|Location where generated output CSV links file is to be placed (optional)|
|odoutput|Location where generated output CSV origin-destination skim matrix file is to be placed (optional)|
|odpathoutput|Location where generated output CSV OD path file is to be placed(optional)|
|logfile|Location where generated log file is to be placed (optional, no log file created if this argument omitted)| 
|maxiterations|Maximum number of iterations to be used in the assignment (optional, defaults to 1 if not present)|
|defaultmaximumspeed|Default maximum speed on links where 0 is given in the file (optional, defaults to 25 m/h if not present)
|epsilon|Epsilon used in convergence criterion (optional, defaults to 0.01 if not present)|
|outputtimeunit|Time unit to be used when writing link cost times to output file  - one from hour, minute, second (or h ,m ,s), not case-sensitive (optional, defaults to hours)

If a required file is missing from the argument list an error message is displayed.  The arguments can be given in any order provided they are separated by spaces.

The following is an example of a correct run call:

java -jar PLANitTNTP-x.y.z.jar NETWORK=src\test\resources\ChicagoSketch\ChicagoSketch_net.tntp DEMANDS=src\test\resources\ChicagoSketch\ChicagoSketch_trips.tntp LOGFILE=logs\ChicagoSketch100iterations.log MAXITERATIONS=100 LINKOUTPUT=src\test\resources\ChicagoSketch\ChicagoSketchLink100iterations.csv ODOUTPUT=src\test\resources\ChicagoSketch\ChicagoSketchOD100iterations.csv  ODPATHOUTPUT=src\test\resources\ChicagoSketch\ChicagoSketchPath100iterations.csv NODECOORDINATES=src\test\resources\ChicagoSketch\ChicagoSketch_node.tntp

The "name" values above are not case-sensitive, so "NETWORK" works as well as "network".  Also ":" and "-" can also be used as separators, so "logfile:logs/tntp.log" works as well as "logfile=logs/tntp.log".

All types of output (link, OD and OD path) are optional.  You may choose to include any combination of these, including none at all.  If you include none, the program will run but not generate any results file.

## Location of Input and Output Files

The above example works provided all the input files are in the same directory as the the PLANitTNTP-x.y.z.jar file, and it is run from this directory in the command prompt window.  It is also possible to specify file locations in the following ways:-

* Relative Location e.g "./inputs/ChicagoSketch_net.tntp";
* Absolute Location e.g. "C:/MetroScan/inputs/ChicagoSketch_net.tntp".

## Format of CSV Links Output File

The generated output CSV file for links can contain the following columns:-

|Column|Meaning|
|---|---|
|Run Id|Id of the assignment run|
|Time Period Id|Id of time period|
|Time Period External Id*|External Id of time period|
|Mode Id|Id of mode|
|Mode External Id*|External Id of mode|
|Iteration Index|Index of the current iteration|
|Upstream Node External Id*|Id of start node of link (as specified in the network supply JSON input file)|
|Upstream Node Id|Id of start node of link|
|Upstream Node Location|Coordinates of the start node of the link|
|Downstream Node External Id*|Id of end node of link (as specified in the network supply JSON input file)|
|Downstream Node Id|Id of start node of link|
|Downstream Node Location|Coordinates of the start node of the link|
|Link Segment External Id|External Id of the link|
|Link Segment Id|Id of the link|
|Flow*|Flow of vehicles of the specified mode through this link (vehicles/hr)|
|Length*|Length of the current link (m)|
|Maximum Speed|Maximum speed of the current link (km/h)|
|Number of Lanes*|Number of lanes on the link|
|Calculated Speed*|Calculated speed along the current link (km/h)|
|Capacity per Lane*|Capacity per lane along the current link (vehicles/hr)|
|Density|Flow density of the current link (vehicles/km)|
|Cost*|Travel time on link (h)|

Properties marked with an asterisk are included by default.  Developers can add or remove properties in the code.

External Ids of links and nodes correspond to values used in the input files, whereas (internal) Ids are generated internally by PlanIt.  This means that most users will find external Ids more useful.

The default units for Cost are hours.  However this is configurable within the code.  The developer can set this to minutes or seconds.

By default links with zero flow are not included.  The developer can change this default within the code.

The file results.csv contains the results generated from the above example call.

## Format of CSV Origin-Destination Skim Matrix Output File

The generated output CSV file for the origin-destination skim matrix can contain the following columns:-

|Column|Meaning|
|---|---|
|Run Id|Id of the assignment run|
|Time Period Id|Id of time period|
|Time Period External Id*|External Id of time period|
|Mode Id|Id of mode|
|Mode External Id*|External Id of mode|
|Iteration Index|Index of the current iteration|
|Destination Zone External Id*|External Id of the Destination Zone|
|Destination Zone Id|Id of the Destination Zone|
|Origin Zone External Id*|External Id of the Origin Zone|
|Origin Zone Id|Id of the Origin Zone|
|Cost*|Cost of travel from the Origin to the Destination (h)|

Properties marked with an asterisk are included by default.  Developers can add or remove properties in the code.

External Ids of links and nodes correspond to values used in the input files, whereas (internal) Ids are generated internally by PlanIt.  This means that most users will find external Ids more useful.

The default units for Cost are hours.  However this is configurable within the code.  The developer can set this to minutes or seconds.

## Format of the CSV Origin-Destination Path Output File

This file is similar to the origin-destination skim matrix file described above, but instead on the "Cost" column it has a comma-separated list of node external Ids.

So the full list of column headers is:-

|Column|Meaning|
|---|---|
|Run Id|Id of the assignment run|
|Time Period Id|Id of time period|
|Time Period External Id*|External Id of time period|
|Mode Id|Id of mode|
|Mode External Id*|External Id of mode|
|Iteration Index|Index of the current iteration|
|Destination Zone External Id*|External Id of the Destination Zone|
|Destination Zone Id|Id of the Destination Zone|
|Origin Zone External Id*|External Id of the Origin Zone|
|Origin Zone Id|Id of the Origin Zone|
|Path*|Comma-separated list of the external Id of nodes in the path|

# Test Cases

February 2020:  We use ChicagoSketch for the unit tests.  We compare our results against the ChicagoSketch_flow.tntp file.  This file was created on 13 March 2016 (https://github.com/bstabler/TransportationNetworks/blob/master/Chicago-Sketch/ChicagoSketch_flow.tntp)

We have been able to replicate the link cost modelling for Type 3 links in the Chicago Sketch data files.  These use a BPR cost function with a standard maximum speed of 25 miles per hour.  We have not been able to determine how the costs for links of types 1 and 2 are calculated.