//////////////////////////////////
// GoogleCalendar
//////////////////////////////////
//
// A class for writing out Google Calendar entries based on data from Geneus API. 
//
// You need to add in your calendar feed string into googleCalendarPostUrl, fill in 
// googleuser and googlepassword with a google user who has access to the calendar (eg geneususer)
// Your User Defined Fields will vary


import com.google.gdata.client.*
import com.google.gdata.client.calendar.*
import com.google.gdata.data.*
import com.google.gdata.data.acl.*
import com.google.gdata.data.calendar.*
import com.google.gdata.data.extensions.*
import com.google.gdata.util.*
import java.net.URL
import groovy.util.XmlParser
import java.text.SimpleDateFormat

class GoogleCalendar extends LimsScriptClass {
		
	private SimpleDateFormat dateFormatShort = new SimpleDateFormat("yyyy-MM-dd")
	private SimpleDateFormat dateFormatLong = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S")
	private String googleCalendarPostUrl = "http://www.google.com/calendar/feeds/[...]/private/full"
	private String calendarServiceStr = ""
	private String googleuser = ""
	private String googlepassword = ""
	
	public GoogleCalendar(username,password,processUrl) {
		super(username,password,processUrl)
	}

	
	public writeSeqEvent(
		flowcellId,
		calFilename,
		seqType,
		runAsLive,
		fileDirectory
	) {
		
		def csvRow = ""
		def flowcellPos = ""
		def flowcellid = processNode.'udf:field'.find{ it.@name=="Flow Cell ID" }.text()
		def processtype = processNode.type.text()
		def processTech = processLuid.split("-")[1]
		def seqEventType = GeneusConstants.SeqEvents.get(seqType)
		def processDateRun = processNode.'date-run'.text()
		def numCycles = processNode.'udf:type'[0].'udf:field'.find{ it.@name=="Number of Cycles for Run" }.text()
		boolean barcodeRead = 
			!(processNode.'udf:field'.find{ it.@name=="Index Read Length" }.text().equalsIgnoreCase(GeneusConstants.NO_BARCODE_READ_UDF))
		if(seqType == SequencingType.HISEQSR 
			|| seqType == SequencingType.HISEQPE) {
			flowcellPos = 
			processNode.'udf:type'[0].'udf:field'.find{ it.@name=="Flowcell Position" }.text().split(" ")[-1]
		}
		
		def instrumentId = processNode.'udf:type'[0].'udf:field'.find{ it.@name=="Instrument" }.text()
		
		def geneusCalendarEntryTitle = instrumentId + flowcellPos + " - " + seqEventType + 
			"[" + numCycles + "] - " + processTech + " - " + flowcellid
		
		// If the sequencing start date wasn't entered, we use today.
		def sequencerStartDateEntry = 
			processNode.'udf:field'.find{ it.@name=="Sequencer Start Date For Google Calendar" }?.text() ?:
			dateFormatShort.format(new Date())
			
		def sequencerEndDateEntry = 
			processNode.'udf:field'.find{ it.@name=="Sequencer End Date For Google Calendar" }?.text() ?: "NA"
		
		def containerMap = [:]
		//go through the process outputs to get the sample and project information for the flowcell
		
		processNode.'input-output-map'.each {
			def output = it.output[0]
			if (it.output.@'output-type'[0] == 'Analyte')
			{
				def outputSample =  GeneusLibraryRoutines.httpRequest(it.output[0].@uri, username, password)
				def outputSampleWell = outputSample.location.value.text()
				def outputSampleName = outputSample.name.text()
				def outputSampleSampleNode = GeneusLibraryRoutines.httpRequest(outputSample.sample.@uri[0], username, password)
				def outputSampleProjectNode = GeneusLibraryRoutines.httpRequest(outputSampleSampleNode.project.@uri[0], username, password)
				def outputSampleProjectName = outputSampleProjectNode.name.text()
				csvRow = outputSampleName + ",\t" + outputSampleProjectName + "\n"
				containerMap.put(outputSampleWell,csvRow)
			}
		}
		
		def ListForSorting = []
		
		containerMap.each {
			ListForSorting.add(it.key)
		}
		
		def sortedList = ListForSorting.sort{it}
		
		def csvFile = new File(calFilename)
		csvRow = geneusCalendarEntryTitle + "\n"
		csvFile << csvRow
		
		def geneusCalendarEntryDescription = "Sample ID,\tProject" + "\n"
		
		sortedList.each {
			def outputLine = containerMap.get(it)
			geneusCalendarEntryDescription = geneusCalendarEntryDescription + outputLine
			csvFile << outputLine
		}
		
		def copyFileCommand = "cp " + calFilename + " " +  fileDirectory + calFilename.replaceAll(/csv/,"") +
			processLuid + "." + processNode.type.text().replaceAll(/ /, "-") + "-" +
			processNode.'date-run'.text() + ".google-calendar.txt"
			
		// Call *execute* on the string
		copyFileCommand.execute()                 
		
		
		//Calculate and Format the Start and End times for the run
		Date convertedStartDate = dateFormatShort.parse(sequencerStartDateEntry);  // convert the start date text to a date
		
		// set the end date if not set in the GUI. If set in the GUI, convert the text to a date
		def endDate = null
		
		if (sequencerEndDateEntry.equals("NA")) {
			endDate = convertedStartDate + getSeqEventTime(seqType,numCycles,barcodeRead)
		} else {
			endDate = dateFormatShort.parse(sequencerEndDateEntry) + 1;
		}
		
		// format the dates to that required by google, including the time, and pass to strings		
		String transactionStartDate = dateFormatLong.format(convertedStartDate)
		String transactionEndDate = dateFormatLong.format(endDate)
		
		if(runAsLive) {
			
			URL postUrl = new URL(googleCalendarPostUrl);
			
			CalendarService myService = new CalendarService(calendarServiceStr);
			myService.setUserCredentials(googleuser, googlepassword);
			
			CalendarEventEntry myEntry = new CalendarEventEntry();
			
			myEntry.setTitle(new PlainTextConstruct(geneusCalendarEntryTitle));
			myEntry.setContent(new PlainTextConstruct(geneusCalendarEntryDescription));
				
			DateTime startTime = DateTime.parseDateTime(transactionStartDate);
			startTime.setDateOnly(true);
			DateTime endTime = DateTime.parseDateTime(transactionEndDate);
			endTime.setDateOnly(true);
			
			When eventTimes = new When();
			eventTimes.setStartTime(startTime);
			eventTimes.setEndTime(endTime);
			myEntry.addTime(eventTimes);
			
			// Send the request and receive the response:
			CalendarEventEntry insertedEntry = myService.insert(postUrl, myEntry);
		} else {
			println "[STATUS] Google Calendar events are not being created (runAsLive is false)." + 
				"\nStart date is " + convertedStartDate.toString() + "; end date is " + endDate
		}
	}
	
	private int getSeqEventTime(seqType,numCycles,barcodeRead) {
		def runType = GeneusConstants.RunTypes.get(seqType)
		int numHours = 0
		int numDays = 0
		
		// Google calendar date calculations:
			
		switch(seqType) {
			case SequencingType.MISEQPE:
			case SequencingType.MISEQSR:
				numHours = Math.ceil((numCycles.toInteger() * 0.083 * (runType == "P" ? 2 : 1)) + 6)
				numDays = Math.ceil(numHours/24)
				break
			case SequencingType.HISEQPE:
			case SequencingType.HISEQSR:
				numHours = (numCycles.toInteger() * 1 * (runType == "P" ? 2 : 1))
				numDays = Math.ceil(numHours/24) + (barcodeRead ? 1 : 0)
				break
		}
		// Make sure to round *up* the result
		return numDays
	}
	
	/**
	 * For testing
	 * 
	 * 
	 * @param args
	 * 

	public static void main(String[] args) {
		GoogleCalendar myCal = new GoogleCalendar(
			"",
			"",
			""
		)
		myCal.writeSeqEvent("TESTACXX", "SOMEFILE.txt", SequencingType.HISEQPE, false, "testoutput/")
	}
	  */
}
