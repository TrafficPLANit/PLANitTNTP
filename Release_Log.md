# Release Log

PLANitTNTP releases.  PLANit reader for TNTP input files.  First released as part of Release 0.0.3

## 0.4.0

**Enhancements**
* #25 Add logging for network settings in reader
* #24 update to junit5
* #22 Support CI though github actions and maven build
* #20 Add smaller test than Chicago Sketch. Add SiouxFalls
* #18 Refactor setup of tests so it is no longer so cluttered and more modular
* #16 Make time period configuration configurable via settings, currently hard coded
* #14 Zoning reader should havenow has factory and settings are constructed in line with rest of architecture
* #8 Add networkreaderfactory to created network readers in line with other repos

**Bug fixes**

* #23 Update mode uses to predefined modes properly
* #21 Capacity column did not support double values when parsing
* #19 Bug in parsing of standard test results as zero flows are skipped but they need to be included as well
* #17 User class and traveller type not created in TNTP demands reader
* #15 Centroid has no location on generated zones
* #13 We should not create new link/linksegment for each directional link. Add support for bi-directional links with segments in either direction
* #11 Travel time column has no configurable time unit and default (hour) does not suffice
* #10 Mode access and link segment types are not correctly created. We cannot rely on type id of TNTP
* #9 Support for source coordinate reference system missing

## 0.3.0

* parser is not compliant with reader/writer/converter paradigm. This has been fixed #4
* updated artifact id to conform with how this generally is setup, i.e. <application>-<subrepo> #5
* updated packages to conform to new domain org.goplanit.* #6

## 0.2.0

**enhancements**
* add LICENSE.TXT to each repository so it is clearly licensed (planit/#33)
* changes to be compatible, i.e., compilable, with PLANit changes for version 0.2.0

** bug fixes**
* jam density on link segment not properly set (max speed is used), this is fixed (Planittntp/#1)
* setting of mode specific maximum speed was not properly mutliplied with multiplier on link segment types (planittntp/#2)

## 0.1.0

* Move repository to new location (www.github.com/trafficplanit/PLANitTNTP)

## 0.0.4

* Fixed Logging (PLANitTNTP JIRA Task #1)
