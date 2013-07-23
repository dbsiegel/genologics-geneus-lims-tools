import groovy.sql.Sql
import java.net.InetAddress

class GeneusDatabaseApi {
	
	private static sqlUsers = [readonly:[user:"", password:""]]
	
	public static Sql getSqlInstance(String usertype) {
		String computername = InetAddress.getLocalHost().getHostName()
		def host = "localhost"
		return Sql.newInstance("jdbc:postgresql://" + host + ":5432/systemDBGeneus", 
		sqlUsers.get(usertype).get("user"),
		sqlUsers.get(usertype).get("password"), 
		"org.postgresql.Driver")
	}

	public static Sql getSqlInstance(String usertype, String connectFor) {
		String computername = InetAddress.getLocalHost().getHostName()
		
		String databaseUrl = "jdbc:postgresql://localhost:5432/systemDBGeneus"
		if(connectFor.equalsIgnoreCase("glsftp")) {
			databaseUrl = "jdbc:postgresql://" + 
				computername + 
				":5432/systemDBGeneus"
		}
		return Sql.newInstance(databaseUrl,
			sqlUsers.get(usertype).get("user"),
			sqlUsers.get(usertype).get("password"),
			"org.postgresql.Driver")
	}

	public static Sql getSqlInstanceFor(String usertype, String connectTo) {
		String databaseUrl = "jdbc:postgresql://${connectTo}:5432/systemDBGeneus"
		return Sql.newInstance(databaseUrl,
			sqlUsers.get(usertype).get("user"),
			sqlUsers.get(usertype).get("password"),
			"org.postgresql.Driver")
	}
			
	public static Map loadSqlFileWithFilterWildcard(String filename) {
		def sqlText = ''
		def filterLine = ''
		def sqlFile = [:]
		new File(filename).eachLine { line ->
			if(!(line =~ "^//")) {
				sqlText += (line + " ")
			} else {
				if(line =~ /\(/) {
					sqlText += line.replaceAll("//","") + " "
				} else if(!(line =~ /[\(\)]/)) {
					filterLine += line.replaceAll("//","") + " "
				} else if(line =~ /\)/) {
					sqlText += ("<>" + line.replaceAll("//",""))
				}
			}
		}
		sqlFile.put("query",sqlText)
		sqlFile.put("filter",filterLine)
		return sqlFile
	}


		
}
