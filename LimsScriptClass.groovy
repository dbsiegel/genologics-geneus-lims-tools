///////////////////////////////////////
// LimsScriptClass
/////////////////////////////////////
//
// A base class for developing back-end modules in Genologics Geneus LIMS


class LimsScriptClass {
	
	protected def username = ""
	protected def password = ""
	protected def processUrl = ""
	protected def processNode = null
	protected def processLuid = null
	protected def processAbbr = null
	
	protected def filterAnalyteList = []

	// Database connection instance
	protected def sql = null
	
	/**
	 * 
	 * 
	 */
	public LimsScriptClass() {
		
	}
	

	/**
	 * Constructor
	 *
	 *
	 * @param username
	 * @param password
	 * @param processUrl
	 */
	public LimsScriptClass(username_, password_, processUrl_) {
		processUrl = processUrl_
		password = password_
		username = username_
		processNode = GeneusLibraryRoutines.httpRequest(processUrl,username,password)
		processLuid = processNode.@limsid
		processAbbr = processNode.@limsid.split("-")[0]
		sql = GeneusDatabaseApi.getSqlInstance("readonly")
	}

	public LimsScriptClass(username_, password_, processUrl_,processAbbrv_) {
		this(username_,password_,processUrl_)
		processAbbr = processAbbrv_
		sql = GeneusDatabaseApi.getSqlInstance("readonly")
	}
	
	/**
	 * General database filter creation; can be overridden if
	 * the artifacts or process node used aren't appropriate.
	 * 
	 * 
	 * @param thisType
	 * @return
	 * 
	 */
	protected String makeDatabaseFilter(AnalyteType thisType) {
		filterAnalyteList = []
		
		processNode.'input-output-map'.each {
			def addThis = null
			switch(thisType) {
				case AnalyteType.OUTPUT:
					if(it.output[0].'@output-type' != 'ResultFile') {
						addThis = it.output[0].@limsid
					}
					break
				case AnalyteType.INPUT:
					addThis = it.input[0].@limsid
					break
			}
			if(addThis != null) {
				filterAnalyteList.add(addThis)
			}
		}
		filterAnalyteList.unique()
		
		return filterAnalyteList.join("|")
	}
		
	protected close() {
		sql?.close()
	}
}
