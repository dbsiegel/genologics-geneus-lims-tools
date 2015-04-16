/*this is a command line tool which takes in a file of sample names (one per line), finds out which pick plate its on,  and writes out a pick plate fingerprint report for each pickplate, just like the one made by running the pick plate process assuming that the fingerprint file was attached and processed. It copies that file to the relevant directory for parsing.

Deborah Siegel
*/


def cli = new CliBuilder(usage: 'groovy getPickPlateFingerprintFileForSamples.groovy -u username -p password -i inputfile')
cli.u(argName:'username', longOpt:'username',   required:true, args:1, 'LIMS username (Required)')
cli.p(argName:'password', longOpt:'password',   required:true, args:1, 'LIMS password (Required)')
cli.i(argName:'inputfile', longOpt:'infile', required:true, args:1, 'input file')

def opt = cli.parse(args)

if (!opt) {
  System.exit(-1)
}

String inputFileName = opt.i
String username = opt.u
String password = opt.p

sampleList = []
mySampleInfo = [:]
SamplenameToSampleluidMap = [:]
SampleluidToSampleArtifactluidMap = [:]
sampleSheetFilename = "script.csv"
myProcessList = []
SampleArtifactLuidToPickPlateProcessMap = [:]
sql = GeneusDatabaseApi.getSqlInstance("readonly")
processToFind = "%Make Pick Plate%"




//parse the input file
	def lines = new File(inputFileName).readLines()
	lines.each
	{
		if(it!=null){sampleList.add(it)}
	}
	sampleList.unique()



//get sample process luid from the sample name using database
	String filterLine = ''
	sampleList.each {filterLine +=  it + "|"}
	filterLine = filterLine.substring(0,filterLine.length()-1)
	sql.eachRow(GeneusDatabaseQueries.getSqlSampleLimsId() ,[filterLine]) { row ->
	        def mySampleInfo = SamplenameToSampleluidMap.get(row.sampleid)
		if(mySampleInfo == null) {
			mySampleInfo = [:]
			mySampleInfo.put("sampleprocessluid",row.sampleluid)
			SamplenameToSampleluidMap.put(row.sampleid,mySampleInfo)
			println row.sampleid + "," + row.sampleluid			
			}		
	}



//get sample artifact luid from the sample process luid using the API. theres no batch operation for samples
	SamplenameToSampleluidMap.each { samplename,myInfo ->
			myprocessluid = myInfo.get("sampleprocessluid")
			def sampleNode = GeneusLibraryRoutines.httpRequest(
				"${GetApiUrl.getApiPath()}samples/" + myprocessluid,
				username,
				password
				)

			artifactlimsid = sampleNode.artifact.@limsid[0]
			println artifactlimsid
			SampleluidToSampleArtifactluidMap.put(myprocessluid, artifactlimsid)
			}


//get sample pick plate process from the sample artifact luid using database. need separate queries for pick plates run directly on samples vs pick plates run on norm plates
	String filterLine2 = ''
	SampleluidToSampleArtifactluidMap.each{myprocessluid, artifactluid -> 
		filterLine2 +=  artifactluid + "|"
	}
	filterLine2 = filterLine2.substring(0,filterLine2.length()-1)


	//first, query for pick plates run directly on samples
	//there could be more than one pick plate process per input luid, so key the map by the output luids
	sql.eachRow(GeneusDatabaseQueries.getSqlProcessFromInputSampleLuid() ,[filterLine2,processToFind]) { row ->
    		def mySampleInfo =  SampleArtifactLuidToPickPlateProcessMap.get(row.outputartifactluid)
			if(mySampleInfo == null) {
				mySampleInfo = [:]
				mySampleInfo.put("inputartifactluid",row.searchanalyteluid)
				mySampleInfo.put("processluid",row.processluid)
				SampleArtifactLuidToPickPlateProcessMap.put(row.outputartifactluid,mySampleInfo)
				println row.outputartifactluid + "," + row.searchanalyteluid + "," + row.processluid		
				}		
			}

	//separate query for ppl run off normalized descendents
	sql.eachRow(GeneusDatabaseQueries.getSqlProcessFromAncestorInputSampleLuid(),[filterLine2,processToFind]) { row ->
   	 	def mySampleInfo =  SampleArtifactLuidToPickPlateProcessMap.get(row.outputartifactluid)
			if(mySampleInfo == null) {
				mySampleInfo = [:]
				mySampleInfo.put("inputartifactluid",row.searchanalyteluid)
				mySampleInfo.put("processluid",row.processluid)
				SampleArtifactLuidToPickPlateProcessMap.put(row.outputartifactluid,mySampleInfo)
				println row.outputartifactluid + "," + row.searchanalyteluid + "," + row.processluid		
				}		
			}
	//unique the processes
	SampleArtifactLuidToPickPlateProcessMap.each{outputluid, myMap ->
	myProcessList.add(myMap.get("processluid"))
	}
	myProcessList.unique()
	/*myProcessList.each{println it}*/



//write out the ppl process fingerprint sheet for those processes (by creating instance of  PickPlateFingerprint and passing in the uri of the ppl process
	myProcessList.each{
		mypplProcessUri = "${GetApiUrl.getApiPath()}processes/" + it
		PickPlateFingerprint myPickPlate = null
		myPickPlate = new PickPlateFingerprint(username,password,mypplProcessUri)	
		myPickPlate.make(sampleSheetFilename)
		myPickPlate?.close()
	}

