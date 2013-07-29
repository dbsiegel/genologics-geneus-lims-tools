////////////////////////////////////////////
// Flowcell
/////////////////////////////////////////
//
// A class for aggregating data regarding the samples sequenced on a flowcell

import java.sql.RowId
import java.math.*
import java.util.regex.Pattern
import java.util.regex.Matcher


class Flowcell extends LimsScriptClass {

	// Private member vars
	
	private String flowcellid = null
	private boolean seqIsPreHyb = false
	private boolean amIvalid = true
	private String validationString = ""
	private String flowcelluri = null
	
	// A map of the Libraries corresponding to a given sample - 
	// if multiple preps of a single sample are present, we have
	// to make sure to update all of them appropriately
	private Map sampleLibs = [:]
	
	// A map of all the lanes in the flowcell
	private def laneMap = [:]
	
	// Information for each individual output's constituents
	private def analyteMap = [:]
		
	// A list of the outputs of the process producing this Flowcell class.
	// This list is unique()'d once made, and is effectively a Set.
	private def inputSet = [] 
	
	// Some queries are very expensive and should only be run if the values
	// weren't found in a previous query. This Map breaks out only input/output
	// luids and their Pick Plate Luids, and can be deep copied to make
	// a unique set of found/unfound search terms
	private Map subFilter = [:]
	
	/**
	 * Used when only a single flowcell is the output of a process
	 * 
	 * @param username
	 * @param password
	 * @param processUrl
	 * @param flowcellid_
	 * @param seqIsPreHyb_
	 * 
	 */
	public Flowcell(username, password, processUrl,flowcellid_,seqIsPreHyb_) {
		super(username, password, processUrl)
		flowcellid = flowcellid_.toUpperCase()
		seqIsPreHyb = seqIsPreHyb_
	}

	/**
	 * Used when more than one flowcell is produced by a sequencing process
	 * 
	 * @param username
	 * @param password
	 * @param processUrl
	 * @param flowcellid_
	 * @param flowcelluri_
	 * @param seqIsPreHyb_
	 * 
	 */
	public Flowcell(username, password, processUrl,flowcellid_,flowcelluri_,seqIsPreHyb_) {
		super(username, password, processUrl)
		flowcellid = flowcellid_
		seqIsPreHyb = seqIsPreHyb_
		flowcelluri = flowcelluri_
	}
	
	// Getters
	
	/**
	 * 
	 * @return
	 * 
	 */
	public String getFlowcellId() {
		return flowcellid
	}
	
	/**
	 * 
	 * @param laneId
	 * @return
	 * 
	 */
	public getThisLane(laneId) {
		return laneMap.get(laneId)
	}

	/**
	 * 
	 * 
	 * @param laneId
	 * @param thisLane
	 * @return
	 * 
	 */
	public putLane(laneId,thisLane) {
		laneMap.put(laneId,thisLane)
	}
	
	/**
	 * 
	 * @param analyte
	 * @return
	 * 
	 */
	public getThisAnalyte(analyte) {
		return analyteMap.get(analyte)
	}
	
	/**
	 * 
	 * 
	 * 
	 * @param analyte
	 * @param thisAnalyte
	 * @return
	 * 
	 */
	public putAnalyte(analyte,thisAnalyte) {
		analyteMap.put(analyte,thisAnalyte)
	}
	
	/**
	 * Returns the keys of the analyteMap as an
	 * ArrayList.
	 * 
	 * 
	 * @return
	 * 
	 */
	public ArrayList getAnalyteList() {
		def analyteSet = analyteMap.keySet()
		ArrayList analyteList = new ArrayList()
		analyteList.addAll(analyteSet)
		return analyteList
	}
	
	/**
	 * 
	 * 
	 * @return
	 * 
	 */
	public ArrayList getInputList() {
		ArrayList inputs = new ArrayList()
		inputs.addAll(inputSet)
		return inputs
	}

	/**
	 * 
	 * @param sample
	 * @return
	 * 
	 */
	public getSampleLib(sample) {
		return sampleLibs.get(sample)
	}
	
	/**
	 * 
	 * @return
	 * 
	 */
	public boolean isSeqPreHyb() {
		return seqIsPreHyb
	}
		
	/**
	*
	* @return
	*
	*/
   public getSortedLanes() {
	   
	   ArrayList laneSortList = new ArrayList()
	   laneSortList.addAll(laneMap.keySet())	
	   
	   return GeneusLibraryRoutines.sortLaneNames(laneSortList)
   }

   public boolean isValid() {
	   return amIvalid
   }
   
   public String getValidationString() {
	   return validationString
   }
	

   
   /**
    * Close sql connections
    * 
    * @return
    * 
    */
   public close() {
	   sql?.close()
   }
   
   
   
   	/**
	 * Determines if this is a valid flowcell based on this Sequencing Type. 
	 * Writes the results of its findings into the member var validationString.
	 * 
	 * @param seqType
	 * @return
	 * 
	 */
	public validate(SequencingType seqType) {
		def count = 0
		def flowcellContainerName = null

		// Validate the container type (has to be a flowcell), the parent process 
		// (Cluster for HiSEQ and MiSEQ Load for MiSEQ, and Rapid Run Loading for RRSEQ), and the flowcell ID
		// (Cluster/MiSEQ Load/RR Load and Seq proc detail have to match) 
		
		def inputArUris = []
		
		sql.eachRow(GeneusDatabaseQueries.getSqlGetSrcContainerNoSample(),[processLuid]) { row ->
			if(!row.srcconttype.contains("Flow Cell")
				&& !row.srcconttype.contains("Flowcell")
				&& !row.srcconttype.contains("flowcell")
				&& !row.srcconttype.contains("flow cell")) {
				validationString += "ERROR: ${row.srccontname} does not appear to be a flowcell!\n"
				amIvalid = false
			} else {
				if(flowcellContainerName == null) {
					flowcellContainerName = row.srccontname
					if(!flowcellContainerName.contains(flowcellid)) {
						validationString += "ERROR: Conflicting flowcell IDs provided! Process level " +
							"flowcell ID: ${flowcellid}, container flowcell ID: ${row.srccontname.split("\\s")[1]}\n"
						amIvalid = false
					}
				}
			}
			
			inputArUris << "${GetApiUrl.getApiPath()}artifacts/${row.inputanalyteluid}"
		}
		
		inputArUris.unique()
		
		def inputArs = GeneusLibraryRoutines.artifactBatchRetrieve(inputArUris,username,password)
			
		inputArs.each { ar ->

			def inputParentAbbrv = ar.'parent-process'.@limsid[0].split("-")[0]
			
			// Is this a HiSEQ run?
			if (seqType.isHiSeq()) {
				// If so, we need to have the right kind of flowcell ID and the right
				// kind of parent process (Cluster)
				if (inputParentAbbrv != "CLS") {
					validationString += "ERROR: The input flowcell is not the output of a Cluster cBot process." +
						" (Input parent process abbreviation is " + inputParentAbbrv + " and not CLS.)\n"
					amIvalid = false
				}

				if (seqType == SequencingType.HISEQPE) {
					if( !flowcellid.contains(GeneusConstants.V2_FLOWCELL) && !flowcellid.contains(GeneusConstants.V3_FLOWCELL) ) {
						validationString += "ERROR: This is not a V2 or V3 flowcell (" + flowcellid + ") " +
							"but this sequencing type (" + seqType.toString() + ") requires V2 or V3 flowcells." +
							"\n(Be sure to include the flowcell version in the Flowcell ID, eg. ACXX.)\n"
						amIvalid = false
					}
				} 

				if (seqType == SequencingType.HISEQSR) {
					if(!flowcellid.contains(GeneusConstants.V2_FLOWCELL)) {
						validationString += "ERROR: This is not a V2 flowcell (" + flowcellid + ") " +
							"but this sequencing type (" + seqType.toString() + ") requires V2 flowcells."  +
							"\n(Be sure to include the flowcell version in the Flowcell ID, eg. ABXX.)\n"
						amIvalid = false
					}
				}
			} 
			else if (seqType.isRRSeq()) {
				// If so, we need to have the right kind of flowcell ID and the right
				// kind of parent process (Rapid Read Loading)
				if (inputParentAbbrv != "RRL") {
					validationString += "ERROR: The input flowcell is not the output of a Rapid Read Loading process." +
						" (Input parent process abbreviation is " + inputParentAbbrv + " and not RRL.)\n"
					amIvalid = false
				}

				if ((seqType == SequencingType.RRSEQPE) ||  (seqType == SequencingType.RRSEQSR) )  {
					if( !flowcellid.contains(GeneusConstants.V4_FLOWCELL)  ) {
						validationString += "ERROR: This is not a V4 flowcell (" + flowcellid + ") " +
							"but this sequencing type (" + seqType.toString() + ") requires V4 flowcells." +
							"\n(Be sure to include the flowcell version in the Flowcell ID, eg. ADXX.)\n"
						amIvalid = false
					}
				} 

			}
			else {
				// If this a MiSEQ run, it must be on a V4 flowcell only
				if(!flowcellid.contains(GeneusConstants.VM_FLOWCELL)) {
					validationString = "ERROR: This is not a miseq flowcell (" + flowcellid + ") " +
						"but this sequencing type (" + seqType.toString() + ") requires miseq flowcells."  +
							"\n(Be sure to include the flowcell version in the Flowcell ID, eg. AZXX.)"
					amIvalid = false
				}
				// If this is a MiSEQ run (i.e. post-Hyb), it has to have been
				// run through MiSEQ Loading process
				if (inputParentAbbrv != "MSQ") {
					validationString += "ERROR: The input was not run on a MiSEQ Loading Process." +
						" (Input parent process abbreviation is " + inputParentAbbrv + " and not MSQ.)\n"
					amIvalid = false
				}
			}
			count++
		}
		


		if ( (seqType.isMiSeq()) || (seqType.isHiSeq()) ) {
			def splitNode = processNode.'udf:field'.find{it.@name.equalsIgnoreCase("Split Flowcell")}
			boolean isASplitFlowcell = (splitNode != null && splitNode.text().equalsIgnoreCase("true"))
		
			if ( (seqType.isMiSeq() && count != 1) || (seqType.isHiSeq() && !isASplitFlowcell &&  ((count != 8) ) ) ){
				amIvalid = false
				validationString += "ERROR: This is the wrong number of lanes for this sequencing type! " +
					"Lanes found: ${count}, expected: " + (seqType.isMiSeq() ? "1" : "8") + "\n"
			} else {
				if(amIvalid) {
					validationString = "Validated ${flowcellContainerName}:" +
						"\n\n\t* Correct number of lanes in use.\n" +
						"\t* Appropriate precursor process was run.\n" +
						"\t* Input container was a flowcell.\n" +
						"\t* Correct flowcell version was indicated.\n"
				}
			}
		
		}
		else if (seqType.isRRSeq()) {	
			if ( (count != 2) ) {
				amIvalid = false
				validationString += "ERROR: This is the wrong number of lanes for this sequencing type! " +
					"Lanes found: ${count}, expected: " + "2" + "\n"
			} else {
				if(amIvalid) {
					validationString = "Validated ${flowcellContainerName}:" +
						"\n\n\t* Correct number of lanes in use.\n" +
						"\t* Appropriate precursor process was run.\n" +
						"\t* Input container was a flowcell.\n" +
						"\t* Correct flowcell version was indicated.\n"
				}
			}

		}
}

	/**
	 * Validates the inputs from a Denature and Dilute process for placing on a 
	 * flowcell.
	 * 
	 * @return
	 * 
	 */
	public validateClusterInputs() {
		AncestorAttributes myAA = null
		
		try {
			amIvalid = true
			

			if(flowcellid.contains("O")) {
				amIvalid = false
				validationString = "The letter O is not a valid character in a flowcell ID! " +
						"Please doublecheck your flowcell ID (${flowcellid})."
				return
			}
			
			if(!flowcellid.endsWith("XX")) {
				amIvalid = false
				validationString = "This flowcell ID doesn't end in a valid flowcell version. " +
						"Please doublecheck your flowcell ID (${flowcellid})."
				return
			}
			
			def dndInputMap = [:]
			
			// Trim off any container iteration if it was given by the tech,
			// because we compare the container ID, which has no iteration
			// value.
			Pattern ptrnTubeIter = Pattern.compile("^(.*)-[0-9]\$")
			
			def dndInputList = processNode.'udf:field'.findAll{ it.@name.contains("D&D Input #") }
			
			if(dndInputList == null || dndInputList?.size() <= 0) {
				validationString += "* No D&D input container IDs were provided! Enter the D&D input container IDs into the boxes provided.\n"
				amIvalid = false
			}
			
			processNode.'udf:field'.findAll{ it.@name.contains("D&D Input #") }.each {
				if(it.text().length() > 0 && !it.text().equalsIgnoreCase("null")) {
					def inputContainerSplit = it.text().split("[\\s+_]")		
					def inputContainerName = inputContainerSplit[0]
					def inputContainerWell = (inputContainerSplit.size() > 1 ? " ${inputContainerSplit[1]}" : "")
					Matcher mtchTubeIter = ptrnTubeIter.matcher(it.text())
					if(mtchTubeIter.find()) {
						inputContainerName = mtchTubeIter.group(1)
					}
					dndInputMap.put(it.@name,"${inputContainerName}${inputContainerWell}")
				}
			}
				
			myAA = new AncestorAttributes(username,password,processUrl)
			def dndLuids = myAA.getDndContainers(AnalyteType.OUTPUT)
			
			def outputContainerType = null
			def outputCount = 0
			
			dndLuids.each {
	
				def outputNode = GeneusLibraryRoutines.httpRequest(
					GetApiUrl.getApiPath() + "artifacts/" + it.key,
					username,
					password
				)
				
				if(outputContainerType == null) {
					def outputContainer = GeneusLibraryRoutines.httpRequest(
						outputNode.location.container.@uri[0],
						username,
						password
					)
					outputContainerType = GeneusLibraryRoutines.httpRequest(
						outputContainer.type.@uri[0],
						username,
						password
					)
				}
				
				def outputWell = outputNode.location.value.text()
				def listedOutput = "D&D Input #" + outputWell.split(":")[0]
				if(dndInputMap.get(listedOutput) != null) {
					if(it.value.get("dndincontainerid") == null) {
						validationString += "* A D&D Input container ID could not be found for output ${it.key}. " +
							"Double-check that this process is being run on the *outputs* of a D&D process!\n"
						amIvalid = false
					} else {
						def dndInput = it.value.get("dndincontainerid").replaceAll("_", " ")
												
						if(!dndInputMap.get(listedOutput).equalsIgnoreCase(dndInput)) {
							validationString += "* D&D Input ID mismatch. " + listedOutput + " was entered as " +
								dndInputMap.get(listedOutput) + ". The corresponding input to " +
								"Denature and Dilute was " + dndInput + ".\n\n"
							amIvalid = false
						}
					}
					outputCount++
				}
			}
			
			def splitNode = processNode.'udf:field'.find{it.@name.equalsIgnoreCase("Split Flowcell")}
			boolean isASplitFlowcell = (splitNode != null && splitNode.text().equalsIgnoreCase("true"))
			
			if(!isASplitFlowcell && (outputCount != 
				(outputContainerType.'x-dimension'[0].size.text().toInteger() * 
				 outputContainerType.'y-dimension'[0].size.text().toInteger()))
			) {
				validationString += "* All of the wells in your output flowcell must be used. Please " +
					" correct this error and rerun the process.\n\n"
				amIvalid = false
			}
		} finally {
			myAA?.close()
		}
	} 
		
	
	/**
	 * Loads all of the data about the flowcell into member vars.
	 * 
	 * 
	 * @return
	 * 
	 */
	public loadFlowcellData() {
		
		def thisLane = null		
		AncestorAttributes myAa = null
		AnalyteTargets myAt = null
		
		try {
			// Get pick plate luids, build the flowcell lane map and analyte map,
			// and build the filter for various queries to the database
			sql.eachRow(GeneusDatabaseQueries.getSqlGetSrcContainerNoSample(),[processLuid]) { row ->
				//get the inputs that are analytes or samples and not resultfiles,
				inputSet << row.inputanalyteluid
				def inputNode = GeneusLibraryRoutines.httpRequest(
					GetApiUrl.getApiPath() + "artifacts/${row.inputanalyteluid}",
					username,
					password
				)
				sql.eachRow(GeneusDatabaseQueries.getSqlPickPlateLuid(),[row.inputanalyteluid]) { pplrow ->
					analyteMap.put(pplrow.searchanalyteluid + "." + pplrow.pickplateluid,[:])
					def thisSub = subFilter.get(pplrow.searchanalyteluid)
					if(thisSub == null) {
						subFilter.put(pplrow.searchanalyteluid,[pplrow.pickplateluid])
					} else {
						thisSub.add(pplrow.pickplateluid)
					}
					thisLane = laneMap.get(inputNode.location.value.text())
					if(thisLane == null) {
						thisLane = []
						laneMap.put(inputNode.location.value.text(),thisLane)
					}
					thisLane.add(pplrow.searchanalyteluid + "." + pplrow.pickplateluid)
				}
			}
				
			inputSet.unique()
				
			// Get sample UDFs, sample name, and sample limsid
			sql.eachRow(GeneusDatabaseQueries.getSampleUdfsByArtifactDs(),[inputSet.join("|")]) { row ->
				def thisAnalyteInfo = analyteMap.get(row.searchanalyteluid + "." + row.pickplateluid)
				if(thisAnalyteInfo != null) {
					thisAnalyteInfo.put("samplename",row.samplename)
					thisAnalyteInfo.put("sampleluid",row.sampleluid)
					thisAnalyteInfo.put("org",row.speciesname)
					if(
						row.cohort == null 
						|| row.cohort.equalsIgnoreCase("Not Applicable") 
						|| row.cohort.equalsIgnoreCase("Not Supplied")
					) {
						thisAnalyteInfo.put("cohort","")
					} else {
						thisAnalyteInfo.put("cohort",row.cohort)
					}
					thisAnalyteInfo.put("dbgapid",row.dbgapid)
					thisAnalyteInfo.put("invln",row.investigatorlastname)
					thisAnalyteInfo.put("invsamp",row.invsamplename)
					thisAnalyteInfo.put("project",row.project)
				}
			}
			
			// Get an ID which represents the Library Prep process.
			//
			// SLP IDs must be retrieved separately, since SLP is run off a
			// common ancestor and is not part of the main ancestry of a sample.
			
			sql.eachRow(GeneusDatabaseQueries.getSqlSlpOutputContainer(),[inputSet.join("|")]) { row ->
				def thisAnalyteInfo = analyteMap.get(row.searchanalyteluid + "." + row.pickplateluid)
				if(thisAnalyteInfo != null && thisAnalyteInfo.get("libid") == null) {
					def thisContainer = row.containerid +
					(row.containerwell == "1:1" ? "" : "_" + row.containerwell)
					thisAnalyteInfo.put("libid",thisContainer)
				}
			}
			
			// If a sample wasn't prepped in-house, we need to check for the data
			// recorder process
			
			sql.eachRow(GeneusDatabaseQueries.getSqlRecordedData(),[inputSet.join("|")]) { row ->
				def thisAnalyteInfo = analyteMap.get(row.searchanalyteluid + "." + row.pickplateluid)
				if(thisAnalyteInfo != null && thisAnalyteInfo.get("libid") == null) {
					def thisContainer = row.containerid +
					(row.containerwell == "1:1" ? "" : "_" + row.containerwell)
					thisAnalyteInfo.put("libid",thisContainer)
				}
			}
			
					
			sql.eachRow(GeneusDatabaseQueries.getSqlAncsOutputContainerPpl(),
				[inputSet.join("|"),"%Normalize Sequencing Plate%|%Make Library Tubes%|%Record Preprepped Sample Data%"]) { row ->
				def thisAnalyteInfo = analyteMap.get(row.searchanalyteluid + "." + row.pickplateluid)
				if(thisAnalyteInfo != null && thisAnalyteInfo.get("idtoseq") == null) {
					def thisContainer = row.containerid +
					(row.containerwell == "1:1" ? "" : "_" + row.containerwell)
					thisAnalyteInfo.put("idtoseq",thisContainer)
				}
				def libs = sampleLibs.get(row.samplename)
				if(libs == null) {
					libs = []
					sampleLibs.put(row.samplename,libs)
				}
				libs.add(
					row.containerid +(row.containerwell == "1:1" ? "" : "_" + row.containerwell)
				)
			}
	
				
			// Get the Target Loading Container from the Hyb process. and the output container from the D&D process.
			//
			// Although this is pool-wide value, the query used to find this
			// information is generic and meant to break out on a per-constituent basis,
			// so we save the information in the analyteMap and not the outputMap.
			//
			// However, if this is a MiSEQ process, there may not be a D&D process to get
			// a sequencing ID from, in which case we use pool-related values later on
			
			sql.eachRow(GeneusDatabaseQueries.getSqlPrcUdfByAncs(),
				["%Denature and Dilute%","Target Loading Concentration pM","%Denature and Dilute%",inputSet.join("|")]) { row ->
				println row
				def thisAnalyteInfo = analyteMap.get(row.searchanalyteluid + "." + row.pickplateluid)
				if(thisAnalyteInfo != null) {
					thisAnalyteInfo.put("loadcon",row.processval)
					def thisContainer = row.containerid +
						(row.containerwell == "1:1" ? "" : "_" + row.containerwell)
					thisAnalyteInfo.put("dndid",thisContainer)
					thisAnalyteInfo.put("dndluid",row.ancsluid)
				}
			}
			
			// Get the DNA Fingerprint files under the old model, and knock them off
			// a special filter if they're found
				
			def dnaFngFilter = GeneusLibraryRoutines.deepcopy(subFilter)
			
			sql.eachRow(GeneusDatabaseQueries.getSqlDnaFingerprintByAncs(),
				[inputSet.join("|")]) { row ->
				def thisAnalyteInfo = analyteMap.get(row.searchanalyteluid + "." + row.pickplateluid)
				if(thisAnalyteInfo != null && row.fngfileluid != null) {
					Date currDate = thisAnalyteInfo.get("fngdate")
					Date newDate = row.processdate
					if(
						(!(thisAnalyteInfo.get("fngrf") != null && thisAnalyteInfo.get("fngss") != null))
						 || currDate.before(newDate)
					) {
						if(row.fngfilename?.contains("Sample Sheet")) {
							thisAnalyteInfo.put("fngss",row.fngfileluid)
							thisAnalyteInfo.put("fngssloc",row.fngfileloc)
						}
						if(row.fngfilename?.contains("Result File")) {
							thisAnalyteInfo.put("fngrf",row.fngfileluid)
							thisAnalyteInfo.put("fngrfloc",row.fngfileloc)
						}
						thisAnalyteInfo.put("fngdate",row.processdate)
					}
					def thisDff = dnaFngFilter.get(row.searchanalyteluid)
					if(thisDff != null) {
						thisDff.remove(row.pickplateluid)
						if(thisDff.size() == 0) {
							dnaFngFilter.remove(row.searchanalyteluid)
						}
					}
				}
			}
				
			if(dnaFngFilter.size() > 0) {
				def mnpDnaFilter = ""
				dnaFngFilter.each {
					mnpDnaFilter += it.key + "|"
				}
				mnpDnaFilter = mnpDnaFilter.substring(0,mnpDnaFilter.length()-1)
				
				// Get the DNA Fingerprint files where DNA Fingerprint Process
				// is run off the Project Init Normalized Plate Process. This is a much longer running
				// query than the previous one, so only run it against samples whose fingerprint
				// files are still outstanding.
				
				sql.eachRow(GeneusDatabaseQueries.getSqlDnaFingerprintByAncsFromMnp(),
					[mnpDnaFilter]) { row ->
					def thisAnalyteInfo = analyteMap.get(row.searchanalyteluid + "." + row.pickplateluid)
					if(thisAnalyteInfo != null && row.fngfileluid != null) {
						Date currDate = thisAnalyteInfo.get("fngdate")
						Date newDate = row.processdate
						if(
							(!(thisAnalyteInfo.get("fngrf") != null && thisAnalyteInfo.get("fngss") != null))
							 || currDate.before(newDate)
						) {
							if(row.fngfilename?.contains("Sample Sheet")) {
								thisAnalyteInfo.put("fngss",row.fngfileluid)
								thisAnalyteInfo.put("fngssloc",row.fngfileloc)
							}
							if(row.fngfilename?.contains("Result File")) {
								thisAnalyteInfo.put("fngrf",row.fngfileluid)
								thisAnalyteInfo.put("fngrfloc",row.fngfileloc)
							}
							thisAnalyteInfo.put("fngdate",row.processdate)
						}
					}
				}
			}
				
					
			// Get the first pooling ID available for a given pool constituent
			//
			// Because samples can be pooled more than once over the course of
			// prep, we have to compare all of the results of the query and
			// take the oldest.
				
			sql.eachRow(GeneusDatabaseQueries.getSqlAllPoolingByAncs(),[inputSet.join("|")]) { row ->
				def analyteKey = row.searchanalyteluid + row.pickplateluid
				def thisAnalyteInfo = analyteMap.get(row.searchanalyteluid + "." + row.pickplateluid)
				if(thisAnalyteInfo != null) {
					def thisPooling = thisAnalyteInfo.get("pooling")
					if(thisPooling == null) {
						thisPooling = [:]
						thisAnalyteInfo.put("pooling",thisPooling)
					}
					def poolType = ""
					if(row.processname.equalsIgnoreCase(GeneusConstants.PRECAP_POOLING_PROC_NAME)) {
						poolType = "precappool"
					} else if(row.processname.equalsIgnoreCase(GeneusConstants.PRESEQ_POOLING_PROC_NAME)) {
						 poolType = "seqpool"
					}
					def thisPool = thisPooling.get(poolType)
					if(thisPool == null) {
						thisPool = [:]
						thisPooling.put(poolType,thisPool)
					}
	
					Date currDate = thisPool.get("pooldate")
					Date newDate = row.processdate
					
					// If this pooling type isn't in the Pooling map yet, or,
					// if it is, if the date of the current record is *before*
					// the one already in the map, store it. (We use after because
					// we're checking if the date of the pool in the map is
					// after the one we just pulled up in the database; if it is,
					// we need the record from the database instead.)
					if(thisPool.size() <= 0 || currDate.after(newDate)) {
						thisPool.put("containerid",
							row.poolingcontainerluid + (row.poolingcontainerwell == "1:1" ? "" :
							"_" + row.poolingcontainerwell))
						thisPool.put("pooldate",row.processdate)
						thisPool.put("poolluid", row.poolingoutputluid)
					}
				}
			}
				
			// Get target, WGA, and MasterPlate using AnalyteTargets and
			// AncestorAttributes.
			myAt = new AnalyteTargets(username,password,processUrl)
			def targetMap = myAt.getTargetsFromHybAndPpl(AnalyteType.INPUT)
			
			myAa = new AncestorAttributes(username,password,processUrl)
			def wasWgaRun = myAa.getWgaHistory(AnalyteType.INPUT)
			def masterPlates = myAa.getMasterPlates(AnalyteType.INPUT)
			def indexMap = myAa.getAppliedIndicies(AnalyteType.INPUT)
			
			analyteMap.each {
				def thisAnalyteInfo = analyteMap.get(it.key)
				def thisTarget = targetMap.get(it.key).get("target")
				if(thisTarget.equalsIgnoreCase(AnalyteTargets.TARGET_NOT_FOUND_DEF)) {
					thisTarget = (seqIsPreHyb ? GeneusConstants.WHOLE_GENOME_TARGET : GeneusConstants.TARGET_NOT_SET_ERR)
				} else if(thisTarget.equalsIgnoreCase(AnalyteTargets.TARGET_MISMATCH_DEF)) {
					thisTarget = GeneusConstants.TARGET_MISMATCH_ERR
				}
				thisAnalyteInfo.put("target",thisTarget)
				thisAnalyteInfo.put("wga",wasWgaRun.get(it.key))
				thisAnalyteInfo.put("masterplate",masterPlates.get(it.key))
				thisAnalyteInfo.put("indexseq",indexMap.get(it.key)[0].split("~")[1])
				thisAnalyteInfo.put("indexname",indexMap.get(it.key)[0].split("~")[0])
				thisAnalyteInfo.put("indexlayout",indexMap.get(it.key)[0].split("~")[2])
			}
		} finally {
			myAt?.close()
			myAa?.close()
		}
	}	
	
	
	
	
	/**
	 * For testing
	 * 
	 * 
	 * @param args
	 * 

	public static void main(String[] args) {
		String username = ""
		String password = ""
		String abbr = ""
		String processUri = ""
		Flowcell myFlowcell = null

		try {		
			def cli = new CliBuilder(usage: "groovy Flowcell.groovy -u USERNAME -p PASSWORD -l PROCESSURI")
			cli.l(argName:'url',      longOpt:'processUri', required:true, args:1, 'process URL (Required)')
			cli.u(argName:'username', longOpt:'username',   required:true, args:1, 'LIMS username (Required)')
			cli.p(argName:'password', longOpt:'password',   required:true, args:1, 'LIMS password (Required)')
			
			def opt = cli.parse(args)
			
			if (!opt) {
			  System.exit(-1)
			}
			
			processUri = opt.l
			username = opt.u
			password = opt.p

			Node processNode = GeneusLibraryRoutines.httpRequest(processUri,username,password)
			
			myFlowcell = new Flowcell(username,password,processUri,processNode.'udf:field'.find{it.@name=="Flow Cell ID"}.text(),null)
			
			myFlowcell.validateInputs()
			
			println myFlowcell.getSortedLanes()
		} catch(e) {
			e.printStackTrace()
			myFlowcell?.close()
		}	
	}	 
	*/
}
