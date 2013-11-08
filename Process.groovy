import groovy.xml.StreamingMarkupBuilder

public class Process {
	
	// Process settings variables
	private ProcessType thisProcessType = null
	private ArrayList<String> inputUris = null
	private String defaultRsrchrUri = null
	private Map outputPlacementMap = null
	private String eppParameter = null
	private String protocol = null
	private Map udfValues = null
	private int sharedResFileCount = 0
	private int perInputResFileCount = 0
	
	// Process DOM and Node variables
	private def processDom = null
	private Node processXmlNode = null

	
	// SQL instance for retrieving process settings
	// from the LIMS database
	def sql = null
	
	
	/**
	 * 
	 * 
	 * 
	 * 
	 * @param thisType_ The enum ProcessType of this process; used to determine the name and make SQL queries
	 * @param inputUris_ Artifacts input to the process
	 * @param defaultRsrchrUri_ The researcher to default to in the process execution
	 * @param protocol_ The protocol settings to run during process execution (optional), overrides eppParameter_
	 * @param eppParameter_ The EPP parameter string to run during process execution (optional), overriden by protocol_
	 * 
	 */
	public Process(
		ProcessType thisType_, 
		ArrayList<String> inputUris_, 
		String defaultRsrchrUri_,
		String eppParameter_,
		String protocol_
	) {
		thisProcessType = thisType_
		inputUris = inputUris_
		defaultRsrchrUri = defaultRsrchrUri_
		if(protocol_ != null) {
			eppParameter = eppParameter_
		} else {
			eppParameter = eppParameter_
		} 
		

		sql = GeneusDatabaseApi.getSqlInstance("readonly")
	}
	
	/**
	 * Set Process Instance-Specific Variables
	 * 
	 * These methods set the values which may change from process
	 * execution to execution.
	 * 
	 * 
	 * 
	 */
	public void setResearcher(String rsrchrUri) {
		defaultRsrchrUri = rsrchrUri
	}
	
	public void setUdfs(Map udfValues_) {
		udfValues = udfValues_
	}
	
	public void setOutputPlacementMap(Map outputPlacementMap_) {
		outputPlacementMap = outputPlacementMap_
	}

	/**
	 * If the outputs are to be placed in a stamp, we can
	 * generate the map internally, since it will be a 
	 * duplicate of the inputs.
	 * 
	 * 
	 * 
	 */
	public void setOutputPlacementMapStamp() {
		def credsMap = GetApiCredentials.getApiCreds()
		def artifacts = GeneusLibraryRoutines.artifactBatchRetrieve(
			inputUris, credsMap.get("user"), credsMap.get("password")
		)
		
		outputPlacementMap = [:]
		
		artifacts.each {
			outputPlacementMap.put(
				it.@limsid, 
				[["cont":"${it.location.container[0].@limsid}","well":"${it.location.value.text()}"]]
			)
		}
		
		
	}
		
	public void setEppParam(String eppParameter_) {
		eppParameter = eppParameter_
	}
	
	/**
	 * 
	 * 
	 * @return
	 * 
	 */
	public String getProcessDomAsString() {
		if(processDom == null) {
			buildProcessDom()
		}
		return processDom.toString()
	}
	
	/**
	 * 
	 * 
	 * 
	 * @return
	 * 
	 */
	public Node getProcessNode() {
		if(processXmlNode == null) {
			processXmlNode = new XmlParser().parseText(getProcessDomAsString())
		}
		return processXmlNode	
	}
	
	/**
	 * 
	 * 
	 * 
	 * @return
	 * 
	 */
	private buildProcessDom() {
		def builder = new StreamingMarkupBuilder()
		builder.encoding = "UTF-8"
						
		def outputContLuidToUri = null
		
		// if there are analyte outputs, there will be an output placement map
		// pre-generate our containers for output
		if(outputPlacementMap == null && getPerInputAnalyteCount() != 0) {
			throw new Exception(
				"This process (${thisProcessType.toString()}) expects analyte outputs, " +
					"but no output placement map was provided!"
			)
		} else {
			outputContLuidToUri = [:]
			def credsMap = GetApiCredentials.getApiCreds()
			outputPlacementMap.each { ar ->
				def locs = ar.value
				locs.each { loc ->
					// The container identifier stored in the 'cont' key can be any identifier,
					// from an old LUID to a shorthand or a container name. It's only purpose
					// is to differentiate output containers from one another.
					def newCont = outputContLuidToUri.get(loc.get("cont"))
					if(newCont == null) {
					  def outputContainer = GeneusLibraryRoutines.newContainer(
						  ContainerType.PLATE_96_WELL,
						  null
					  )
					  def outputContainerMap = GeneusLibraryRoutines.httpPost(
						  outputContainer,"${GetApiUrl.getApiPath()}containers",
						  credsMap.get("user"),
						  credsMap.get("password")
					  )
					  outputContLuidToUri.put(loc.get("cont"),outputContainerMap.get("node").@uri)
					}
				}
			}		
		}
		
		processDom = builder.bind {
			mkp.xmlDeclaration()
			mkp.declareNamespace(prx: 'http://genologics.com/ri/processexecution')
			mkp.declareNamespace(udf: 'http://genologics.com/ri/userdefined')
			'prx:process' {
				'type'(GeneusConstants.ProcessNames.get(thisProcessType))
				'technician'(uri:defaultRsrchrUri)
				
				sharedResFileCount = sharedResFileCount ?: getSharedResFileCount()
				if(sharedResFileCount < 0) {
					throw new Exception(
						"A shared Results Files count wasn't supplied, but is required for " +
						"this process (${GeneusConstants.ProcessNames.get(thisProcessType)})!"
					)
				}
				perInputResFileCount = perInputResFileCount ?: getPerInputResFileCount()
				if(perInputResFileCount < 0) {
					throw new Exception(
						"A Results File per Input count wasn't supplied, but is required for " +
						"this process (${GeneusConstants.ProcessNames.get(thisProcessType)})!"
					)
				}

				// Analytes in shared ResultFile outputs
				for(int i = 0; i < sharedResFileCount; i++) {
					'input-output-map' (shared:'true') {
						inputUris.each { ar ->
							def inputUri = ar
							'input'(uri:"${inputUri}")
						}
						'output'(type:'ResultFile')
					}
				}
				
				//Analytes in per-input ResultFile outputs
				inputUris.each { ar ->
					for(int i = 0; i < perInputResFileCount; i++) {
						def inputUri = ar
						'input-output-map' {
							'input'(uri:"${inputUri}")
							'output'(type:'ResultFile')
						}
					}
				}
				
				// Analytes in per-output Analytes
				inputUris.each { ar ->
					if(outputPlacementMap != null) {
						def outputLocs = outputPlacementMap.get(ar.split("\\/")[-1])
						outputLocs.each { loc ->
							def well = loc.get("well")
							def inputUri = ar
							def containerUri = outputContLuidToUri.get(loc.get("cont"))
							'input-output-map' {
								'input'(uri: "${inputUri}")
								'output'(type:'Analyte') {
									'location' {
										'container'(uri: "${containerUri}")
										'value'("${well}")
									}
								}
							}
						}
					}
				}
				
				// EPP string
				if(eppParameter != null) {
					'process-parameter'(name:"${eppParameter}")
				}
				
				// Process UDFs
				getProcUdfs().each { udf ->
					if((udf.value.get("required") == true) && (udfValues.get(udf.key) == null)) {
						throw new Exception("UDF ${udf.key} was not provided, but is required!")
					}
					if(udfValues.get(udf.key) != null) {
						def udfval = udfValues.get(udf.key)
						def udfname = udf.key
						'udf:field' (name:"${udfname}",type:"String","${udfval}") 
					}
				}
			}
		}
	}
	
	public Map postProcess() {
		def credsMap = GetApiCredentials.getApiCreds()
		return GeneusLibraryRoutines.httpPost(
			getProcessNode(), 
			"${GetApiUrl.getApiPath()}processes", 
			credsMap.get("user"), 
			credsMap.get("password")
		)
	}
	
	public close() {
		sql?.close()
	}
		
	/**
	 * These methods retrieve process type settings
	 * from the database via a SQL query.
	 * 
	 * 
	 */
	private int getSharedResFileCount() {
		return sql.firstRow(
			GeneusDatabaseQueries.getProcTypeInfo(),
			[GeneusConstants.ProcessNames.get(thisProcessType).replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"),
				"ResultFile","COMPOUND","%"]
		)?.numoutputstogenerate ?: 0
		
	}	

	private int getPerInputResFileCount() {
		return sql.firstRow(
			GeneusDatabaseQueries.getProcTypeInfo(),
			[GeneusConstants.ProcessNames.get(thisProcessType).replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"),
				"ResultFile","PER_INPUT","%"]
		)?.numoutputstogenerate ?: 0
	}
	
	private int getPerInputAnalyteCount() {
		return sql.firstRow(
			GeneusDatabaseQueries.getProcTypeInfo(),
			[GeneusConstants.ProcessNames.get(thisProcessType).replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)"),
				"Analyte","PER_INPUT","%"]
		)?.numoutputstogenerate ?: 0
	}
	
	private Map getProcUdfs() {
		Map procTypeUdfs = [:]
		sql.eachRow(
			GeneusDatabaseQueries.getProcTypeUdfs(),
			[GeneusConstants.ProcessNames.get(thisProcessType).replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)")]
		) { row ->
			procTypeUdfs.put(row.udfname, ["udt":row.udtname,"required":row.udfisrequired])
		}
		return procTypeUdfs
	}
	
	
	/**
	 * For test cases
	 * 
	 * 
	 * @param args
	 * 
	 */
	public static void main(String[] args) {
		Process testProc = null
		try {
			//write test

			} catch(Exception e) {
			println "[ERROR] Exception in ${this.getClass().getName()}.groovy: ${e}"
			e.printStackTrace()
		} finally {
			testProc?.close()
		}
	}
}
