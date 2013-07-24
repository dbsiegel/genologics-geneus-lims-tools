////////////////////////////////
// GeneusDatabaseQueries
//////////////////////////////

/**
 * Stores SQL queries to facilitate their use in scripts for the 
 * Genologics Geneus LIMS, because we are not permitted to make
 * database-located Functions or Views. 
 * 
 * This class is fully static and not instantiable.
 * 
 * 
 *
 */


class GeneusDatabaseQueries {
	
	/**
	 * Private Constructor.
	 * This class is meant for static use only.
	 * 
	 */
	private GeneusDatabaseQueries() {

	}
		
		
	/**
	 * Returns a parameterized query that will check the artifact_ancestor map for instances of a  
	 * process in the history of an analyte. 
	 * 
	 * Change to include the name of a specific process (pt.displayname)
	 */
	public static String getSqlWasArtifactProcessed() {
		return "SELECT DISTINCT ar.luid AS searchanalyteluid, " + 
			"    anar.luid AS ancestorluid,p.luid AS processluid " + 
			"FROM artifact_ancestor_map AS aam " + 
			"    INNER JOIN artifact AS anar " + 
			"        INNER JOIN outputmapping AS om " + 
			"            INNER JOIN processiotracker AS pio " + 
			"                INNER JOIN artifact AS inar " + 
			"                ON inar.artifactid = pio.inputartifactid " + 
			"                INNER JOIN process AS p  " + 
			"                    INNER JOIN processtype AS pt " + 
			"                    ON pt.typeid = p.typeid " + 
			"                ON p.processid = pio.processid " + 
			"            ON pio.trackerid = om.trackerid " + 
			"        ON om.outputartifactid = anar.artifactid " + 
			"        INNER JOIN artifacttype AS at " + 
			"        ON at.typeid = anar.artifacttypeid " + 
			"    ON anar.artifactid = aam.ancestorartifactid " + 
			"    INNER JOIN artifact AS ar " + 
			"    ON ar.artifactid = aam.artifactid " + 
			"WHERE (ar.luid SIMILAR TO ?) " + 
			"    AND (at.displayname NOT LIKE 'ResultFile') " + 
			"    AND (pt.displayname SIMILAR TO '%')"
	}
	
	
	
	/**
	 * Returns a parameterized query that will retrieve relevant sample UDFs based
	 * on an artifact luid. Pools will automatically expand into the samples they 
	 * contain.
	 *  
	 * Change to your relevant sample udfs
	 */
	public static String getSqlSampleUdfsByArtifact() {
		return "SELECT DISTINCT s.name AS samplename " + 
			"	,p.name AS project " + 
			"	,invn.invsamplename " + 
			"	,invln.invlastname " + 
			"	,sp.species " + 
			"	,coh.cohort " + 
			"	,dbg.dbgapid " + 
			"	,sex.gender " + 
			"	,tv.totalvol " + 
			"	,con.concentration " + 
			" 	,ar.luid AS searchanalyteluid " + 
			"FROM artifact AS ar " + 
			"    INNER JOIN artifact_sample_map AS asm  " + 
			"        INNER JOIN sample AS s " + 
			"            LEFT OUTER JOIN ( " + 
			"                SELECT s.sampleid,suv.udfvalue AS concentration " + 
			"                FROM sample AS s  " + 
			"                    LEFT OUTER JOIN sample_udf_view AS suv  " + 
			"                    ON suv.sampleid = s.sampleid  " + 
			"                WHERE suv.udfname LIKE 'Concentration %ng/uL%' " + 
			"            ) AS con  " + 
			"            ON con.sampleid = s.sampleid  " + 
			"            LEFT OUTER JOIN ( " + 
			"                SELECT s.sampleid,suv.udfvalue AS totalvol " + 
			"                FROM sample AS s  " + 
			"                    LEFT OUTER JOIN sample_udf_view AS suv  " + 
			"                    ON suv.sampleid = s.sampleid  " + 
			"                WHERE suv.udfname LIKE 'Total Volume %uL%' " + 
			"            ) AS tv  " + 
			"            ON tv.sampleid = s.sampleid  " + 
			"            LEFT OUTER JOIN ( " + 
			"                SELECT s.sampleid,suv.udfvalue AS gender  " + 
			"                FROM sample AS s  " + 
			"                    LEFT OUTER JOIN sample_udf_view AS suv  " + 
			"                    ON suv.sampleid = s.sampleid  " + 
			"                WHERE suv.udfname LIKE 'Gender' " + 
			"            ) AS sex  " + 
			"            ON sex.sampleid = s.sampleid  " + 
			"            LEFT OUTER JOIN ( " + 
			"                SELECT s.sampleid,invname.udfvalue AS invsamplename  " + 
			"                FROM sample AS s  " + 
			"                    LEFT OUTER JOIN sample_udf_view AS invname  " + 
			"                    ON invname.sampleid = s.sampleid  " + 
			"                WHERE invname.udfname LIKE 'Investigator Sample Name' " + 
			"            ) AS invn  " + 
			"            ON invn.sampleid = s.sampleid  " + 
			"            LEFT OUTER JOIN ( " + 
			"                SELECT s.sampleid,invlastname.udfvalue AS invlastname  " + 
			"                FROM sample AS s  " + 
			"                    LEFT OUTER JOIN sample_udf_view AS invlastname  " + 
			"                    ON invlastname.sampleid = s.sampleid  " + 
			"                WHERE invlastname.udfname LIKE 'Investigator Last Name' " + 
			"            ) AS invln  " + 
			"            ON invln.sampleid = s.sampleid  " + 
			"            LEFT OUTER JOIN ( " + 
			"                SELECT s.sampleid,species.udfvalue AS species " + 
			"                FROM sample AS s  " + 
			"                    LEFT OUTER JOIN sample_udf_view AS species " + 
			"                    ON species.sampleid = s.sampleid  " + 
			"                WHERE species.udfname LIKE 'Organism' " + 
			"            ) AS sp  " + 
			"            ON sp.sampleid = s.sampleid  " + 
			"            LEFT OUTER JOIN ( " + 
			"                SELECT s.sampleid,coh.udfvalue AS cohort " + 
			"                FROM sample AS s  " + 
			"                    LEFT OUTER JOIN sample_udf_view AS coh " + 
			"                    ON coh.sampleid = s.sampleid  " + 
			"                    WHERE coh.udfname LIKE 'Cohort' " + 
			"            ) AS coh  " + 
			"            ON coh.sampleid = s.sampleid  " + 
			"            LEFT OUTER JOIN ( " + 
			"                SELECT s.sampleid,dbg.udfvalue AS dbgapid " + 
			"                FROM sample AS s  " + 
			"                    LEFT OUTER JOIN sample_udf_view AS dbg  " + 
			"                    ON dbg.sampleid = s.sampleid  " + 
			"                WHERE dbg.udfname LIKE 'dbGaP ID' " + 
			"            ) AS dbg  " + 
			"            ON dbg.sampleid = s.sampleid          " + 
			"            INNER JOIN project AS p  " + 
			"            ON p.projectid = s.projectid  " + 
			"        ON s.processid = asm.processid " + 
			"    ON asm.artifactid = ar.artifactid " + 
			"WHERE (ar.luid SIMILAR TO ?) " + 
			";"
	}
	
	
	/**
	 * Defines the SQL query for producing an all samples report
	 * 
	 * @return Query as a string.
	 *
	 * Change to your relevant sample UDFs
	 */
	public static String getSampleNameToInvReport() {
		return "SELECT s.name, p.name As project, invname.invsamplename, " +
			"ethn.ethnicity,coh.cohort,dbg.dbgapid,sex.sex,progress.progress " +
			"FROM sample AS s " +
			"    INNER JOIN project AS p " +
			"    ON p.projectid = s.projectid " +
			"    LEFT OUTER JOIN (SELECT s.sampleid,invName.udfvalue AS invsamplename " +
			"        FROM sample AS s " +
			"            LEFT OUTER JOIN sample_udf_view AS invName " +
			"            ON invName.sampleid = s.sampleid " +
			"            WHERE invName.udfname LIKE 'Investigator Sample Name') AS invName " +
			"    ON invName.sampleid = s.sampleid " +
			"    LEFT OUTER JOIN (SELECT s.sampleid,ethn.udfvalue AS ethnicity " +
			"        FROM sample AS s " +
			"            LEFT OUTER JOIN sample_udf_view AS ethn " +
			"            ON ethn.sampleid = s.sampleid " +
			"            WHERE ethn.udfname LIKE 'Race') AS ethn " +
			"    ON ethn.sampleid = s.sampleid " +
			"    LEFT OUTER JOIN (SELECT s.sampleid,coh.udfvalue AS cohort " +
			"        FROM sample AS s " +
			"            LEFT OUTER JOIN sample_udf_view AS coh " +
			"            ON coh.sampleid = s.sampleid " +
			"            WHERE coh.udfname LIKE 'Cohort') AS coh " +
			"    ON coh.sampleid = s.sampleid " +
			"    LEFT OUTER JOIN (SELECT s.sampleid,dbg.udfvalue AS dbgapid " +
			"        FROM sample AS s " +
			"            LEFT OUTER JOIN sample_udf_view AS dbg " +
			"            ON dbg.sampleid = s.sampleid " +
			"            WHERE dbg.udfname LIKE 'dbGaP ID') AS dbg " +
			"    ON dbg.sampleid = s.sampleid " +
			"    LEFT OUTER JOIN (SELECT s.sampleid,sex.udfvalue AS sex " +
			"        FROM sample AS s " +
			"            LEFT OUTER JOIN sample_udf_view AS sex " +
			"            ON sex.sampleid = s.sampleid " +
			"            WHERE sex.udfname LIKE 'Gender') AS sex " +
			"    ON sex.sampleid = s.sampleid " +
			"    LEFT OUTER JOIN (SELECT s.sampleid,progress.udfvalue AS progress " +
			"        FROM sample AS s " +
			"            LEFT OUTER JOIN sample_udf_view AS progress " +
			"            ON progress.sampleid = s.sampleid " +
			"            WHERE progress.udfname LIKE 'Progress') AS progress " +
			"    ON progress.sampleid = s.sampleid;"

	}
	

		
	
     /**
    * Returns a parameterized query that will provide the sample LIMSIDs of input sample 'names'
    * (i.e. lab sequencing names)
    * 
    * @return String containing the query ("sqlQuery")
    */
   public static String getSqlSampleLimsId() {
	   return "SELECT s.name AS sampleid, prc.luid AS sampleluid " +
		   "FROM sample AS s " +
		   "    INNER JOIN process AS prc " +
		   "    ON prc.processid = s.processid " +
		   "WHERE s.name SIMILAR TO ?" 
   }

   
  
   
   /**
    * 
    * Returns a parameterized query that will provide the quantification ancestor artifacts
    * artifact luids
    * 
    * Change the name of your quantification process
    * 
    */
   public static String getSqlQuantAncs() {
	   return "SELECT ar.luid AS searchanalyteluid " + 
			"    ,descp.luid AS processluid " + 
			"    ,descp.createddate AS processrundate " + 
			"    ,descar.luid AS quantluid " + 
			"FROM artifact_ancestor_map AS aam " + 
			"   INNER JOIN artifact AS ar " + 
			"       INNER JOIN artifacttype AS at " + 
			"       ON at.typeid = ar.artifacttypeid " + 
			"   ON ar.artifactid = aam.artifactid " + 
			"   INNER JOIN artifact AS anar " + 
			"        INNER JOIN processiotracker AS pio " + 
			"            INNER JOIN process AS descp " + 
			"                INNER JOIN processtype AS descpt " + 
			"                ON descpt.typeid = descp.typeid " + 
			"            ON descp.processid = pio.processid " + 
			"            INNER JOIN outputmapping AS om " + 
			"                INNER JOIN artifact AS descar " + 
			"                    INNER JOIN artifacttype AS descat " + 
			"                    ON descat.typeid = descar.artifacttypeid " + 
			"                ON descar.artifactid = om.outputartifactid " + 
			"            ON om.trackerid = pio.trackerid " + 
			"        ON pio.inputartifactid = anar.artifactid " + 
			"        INNER JOIN artifacttype AS anarat " + 
			"        ON anarat.typeid = anar.artifacttypeid " + 
			"   ON anar.artifactid = aam.ancestorartifactid " + 
			"WHERE (ar.luid SIMILAR TO ?) " + 
			"   AND (descpt.displayname SIMILAR TO '%DNA Quantification%') " + 
			"   AND (at.displayname NOT LIKE 'ResultFile') " + 
			"   AND (anarat.displayname NOT LIKE 'ResultFile') " + 
			"   AND (descat.displayname NOT LIKE 'ResultFile') " + 
			"ORDER BY descp.createddate DESC " + 
			";"
  }
   	
		
}
