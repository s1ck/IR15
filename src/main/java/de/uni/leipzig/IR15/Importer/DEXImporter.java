package de.uni.leipzig.IR15.Importer;

import java.io.FileNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;

import com.sparsity.dex.gdb.AttributeKind;
import com.sparsity.dex.gdb.DataType;
import com.sparsity.dex.gdb.Database;
import com.sparsity.dex.gdb.Dex;
import com.sparsity.dex.gdb.DexConfig;
import com.sparsity.dex.gdb.Graph;
import com.sparsity.dex.gdb.Session;
import com.sparsity.dex.gdb.Value;

public class DEXImporter extends Importer {
	protected static Logger log = Logger.getLogger(DEXImporter.class);
	
	public static enum RelTypes
	{
		CO_S,
		CO_N
	}

	private Dex dexConnector;
	private Database dex;

	@Override
	public void setUp() {
		super.setUp("dex");
		
		dexConnector = new Dex(new DexConfig());
		
		try {
			dex = dexConnector.create(graphConfiguration.getPropertyAsString("location"), "dex");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		registerShutdownHook();
	}

	@Override
	public void tearDown() {
		// shutdown the connections
		dex.close();
	    dexConnector.close();
		super.tearDown();
	}
	
	/**
	 * @param args
	 */
	public void importData() {													
		// transfer the data from mysql to neo4j		
		transferData();											
	}
	
	public Object getDatabaseInstance() {
		return dex;
	}
	
	private void transferData()
	{		
		importWords();
		importCooccurrences(RelTypes.CO_N);
		importCooccurrences(RelTypes.CO_S);
	}
	
	private void registerShutdownHook() {
	    // Registers a shutdown hook for the Neo4j instance so that it
	    // shuts down nicely when the VM exits (even if you "Ctrl-C" the
	    // running example before it's completed)
	    Runtime.getRuntime().addShutdownHook( new Thread()
	    {
	        @Override
	        public void run()
	        {
	        	dex.close();
	            dexConnector.close();
	        }
	    } );
	}
	
	private void importCooccurrences(RelTypes relType) {
		String table = relType.toString().toLowerCase();
		Integer count = getMysqlRowCount(table);
		log.info(String.format("Importing %d cooccurences from mysql table %s", count, table));
		
	    String query = "SELECT * FROM " + table;
	    Session session = dex.newSession();
	    session.begin();

	    try {
	    	Statement st = mySQLConnection.createStatement();
	    	st.setFetchSize(Integer.MIN_VALUE);
	    	ResultSet rs = st.executeQuery(query);
	    	
	    	Graph graph = session.getGraph();
	    	int wordNodeType = graph.findType("word");
	    	int wordIdAttribute = graph.findAttribute(wordNodeType, "w_id");

	    	int edgeType = graph.newEdgeType(relType.toString(), true, true);
	    	int edgeSigAttribute = graph.newAttribute(edgeType, "sig", DataType.Integer, AttributeKind.Indexed);
	    	int edgeFreqAttribute = graph.newAttribute(edgeType, "freq", DataType.Integer, AttributeKind.Indexed);
	    	
	    	while (rs.next()) {
		        Integer w1_id 	= rs.getInt("w1_id");
		        Integer w2_id 	= rs.getInt("w2_id");
		        Integer sig 	= rs.getInt("sig");
		        Integer freq 	= rs.getInt("freq");
		        
		        long sourceNode = graph.findObject(wordIdAttribute, new Value().setInteger(w1_id));
		        long targetNode = graph.findObject(wordIdAttribute, new Value().setInteger(w2_id));
		        
		        long edge = graph.newEdge(edgeType, sourceNode, targetNode);
		        graph.setAttribute(edge, edgeSigAttribute, new Value().setInteger(sig));
		        graph.setAttribute(edge, edgeFreqAttribute, new Value().setInteger(freq));
	    	}
	    	session.commit();	    	
	    } catch (SQLException ex) {
		      System.err.println(ex.getMessage());
		} finally {
	    	session.close();
	    }
	    
	    /*
	    // start transaction
	    Transaction tx = neo4j.beginTx();	    
	    try {	    	
	      Statement st = mySQL.createStatement();
	      st.setFetchSize(Integer.MIN_VALUE);
	      ResultSet rs = st.executeQuery(query);
	      
	      int i = 0;
	      
	      while (rs.next()) {
	        Integer w1_id 	= rs.getInt("w1_id");
	        Integer w2_id 	= rs.getInt("w2_id");
	        Integer sig 	= rs.getInt("sig");
	        Integer freq 	= rs.getInt("freq");
	        
	        
	        Node source = nodeIndex.get("w_id", w1_id).getSingle();
	        Node target = nodeIndex.get("w_id", w2_id).getSingle();
	        Relationship edge = source.createRelationshipTo(target, relType);
	        	      	        
	        edge.setProperty("freq", freq);
	        edge.setProperty("sig", sig);
	        
	        if(++i % operationsPerTx == 0)
	        {
	        	// commit
	        	tx.success();
	        	tx.finish();
	        	tx = neo4j.beginTx();
	        	System.out.println(".");
	        }
	        
	        step++;	       
	      }
	      tx.success();
	    } catch (SQLException ex) {
	      System.err.println(ex.getMessage());
	    } finally {
	    	tx.finish();
	    }
	    */
	}
	
	private void importWords() {		
		String table = "words";
		Integer count = getMysqlRowCount(table);
		log.info(String.format("Importing %d words from mysql table %s", count, table));
	    
	    String query = "SELECT * FROM words";
	    Session session = dex.newSession();
	    session.begin();

	    try {
	    	Statement st = mySQLConnection.createStatement();
	    	st.setFetchSize(Integer.MIN_VALUE);
	    	ResultSet rs = st.executeQuery(query);
	    	
	    	Graph graph = session.getGraph();
	    	int wordNodeType = graph.newNodeType("word");
	    	int wordIdAttribute = graph.newAttribute(wordNodeType, "w_id", DataType.Integer, AttributeKind.Indexed);
	    	int wordAttribute = graph.newAttribute(wordNodeType, "word", DataType.String, AttributeKind.Basic);
	    	
	    	while (rs.next()) {
	    		String word 	= rs.getString("word");
	    		Integer word_id = rs.getInt("w_id");
	    		
	    		long node = graph.newNode(wordNodeType);
	    		
	    		graph.setAttribute(node, wordIdAttribute, new Value().setInteger(word_id));
	    		graph.setAttribute(node, wordAttribute, new Value().setString(word));
	    	}
	    	session.commit();	    	
	    	
	    } catch (SQLException ex) {
		      System.err.println(ex.getMessage());
		} finally {
	    	session.close();
	    }
	}

	@Override
	public String getName() {		
		return "dex";
	}
}
