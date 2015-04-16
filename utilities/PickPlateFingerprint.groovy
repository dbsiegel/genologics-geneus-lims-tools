/** Writes a Pick Plate Sample Sheet with the most recent Fingerprint results filenames for each analyte
*
* @author Deborah Siegel
*/

public class PickPlateFingerprint extends LimsScriptClass {
	
		
	public PickPlateFingerprint() {
		super()
	}
	
	public PickPlateFingerprint(username_,password_,processUri_) {
		super(username_,password_,processUri_)
	}
	
	public void make(String sampleSheetFilename) {	
		try {
				println "[" + processLuid + "] Writing Pick Plate Fingerprint Sample Sheet..."
				def myMap = makeFNGSrcDst()
				def filterLine = ""
		
				//using the process names, go and query database for fingerprint file name (query one process per pick plate analyte)
				myMap.each { ppldstwell, info ->
					def myProc = ""
					def fngProcURI = info.get("fngprocess") ? info.get("fngprocess") : "null"
				 		if (fngProcURI == null) {
							info.put("fngfile","none")	
				 			 }					
						else { 
							myProc = fngProcURI.split("/")[-1]
							sql.eachRow(GeneusDatabaseQueries.getFingerprintFilename(),[myProc]) { row ->
							info.put("fngfile",row.fngfileloc)
						  	}
						}				
				}
		
				//sort by wells and write the file
				def sortBySet = myMap.keySet()
				def ArrayList sortByList = new ArrayList()
				sortByList.addAll(sortBySet)
				GeneusLibraryRoutines.sortByWellCol(sortByList)

				def sampleFile = new File(sampleSheetFilename)
		
				if(sampleFile.exists()) {
				sampleFile.renameTo("${sampleFile.getName()}.bak")
				sampleFile = new File(sampleSheetFilename)
				}
				
				sampleFile << "Sample ID, Well, Fingerprint File\n"
			
				sortByList.each
				{
					def myWellMap = myMap.get(it)	
					def samp = myWellMap.get("samplename")
					def fingerprintfile = myWellMap.get("fngfile")
					println samp + "," + it + "," + fingerprintfile + "\n"
					sampleFile << samp + "," + it + "," + fingerprintfile + "\n"
				}		 
					
				def ssfileDirectory = (GeneusLibraryRoutines.amIDev()  ? GeneusConstants.TEST_OUTPUT_DIR : GeneusConstants.PICKPLATE_INFO_DIR)
			
				def pickPlateNumber = ""
				pickPlateNumber = (processNode.'udf:field'.find{it.@name=="Pick Plate Number"}.text())
				
				GeneusLibraryRoutines.copyFile(
					new File(sampleSheetFilename), 
					ssfileDirectory + pickPlateNumber + "_" + sampleSheetFilename.replaceAll(/.csv/, ".") + processLuid + "." +
					processNode.type.text().replaceAll("[\\s+\\(\\)]", "-") + "-" +
					processNode.'date-run'.text() + ".samples.txt"
				)
	
				println "[" + processLuid + "] ...done."		
			
		} finally {
		
		}
	}
	
		private Map makeFNGSrcDst() {
		AssayData myfngData = null
		Map fngSrcDst = [:]
		def inputToDstWells = [:]
		
		try {
			//database query to get the wells and samples
			sql.eachRow(GeneusDatabaseQueries.getSqlSrcDstContainers(),[processLuid]) { row ->
				def thisAnalyte = fngSrcDst.get(row.dstwell)
				if(thisAnalyte == null) {
					thisAnalyte = [:]
					fngSrcDst.put(row.dstwell,thisAnalyte)
					thisAnalyte.put("samplename",row.samplename)
	
					def dstWells = inputToDstWells.get(row.outputanalyteluid)
					if(dstWells == null) {
						dstWells = []
						inputToDstWells.put(row.outputanalyteluid,dstWells)
					}
					dstWells << row.dstwell	
				}
			}

			
			//need to find the most recent fingerprint process for each input analyte on the pick plate			
			def fngToInput = [:]
			def fngUris = []
			myfngData = new AssayData(username,password,processUrl,"%Fingerprint%")
		    def mostRecentfng = myfngData.getMostRecentAssayProcFromDescendentsDependingOnParentProcessIterative()
			mostRecentfng.each { limsid,myMostRecentfng ->
				def myOut = myMostRecentfng.get("wantedoutanalyteluid")		
				def well = inputToDstWells.get(limsid)[0]					
				def myProcess = myMostRecentfng.get("procluid")
				fngSrcDst.get(well).put("fngoutputluid",myOut)
				fngSrcDst.get(well).put("fngprocess",myProcess)
				}
		
		return fngSrcDst
		} finally {
			myfngData?.close()
		}
	}