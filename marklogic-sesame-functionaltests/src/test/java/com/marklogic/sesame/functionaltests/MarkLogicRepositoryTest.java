package com.marklogic.sesame.functionaltests;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.URI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.ValueFactoryImpl;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.RepositoryFactory;

import com.marklogic.semantics.sesame.MarkLogicRepository;
import com.marklogic.semantics.sesame.MarkLogicRepositoryConnection;
import com.marklogic.semantics.sesame.config.MarkLogicRepositoryConfig;
import com.marklogic.semantics.sesame.config.MarkLogicRepositoryFactory;
import com.marklogic.sesame.functionaltests.util.ConnectedRESTQA;

/**
 * Integration test suite for implementations of Repository.
 * 
 * @author Srinath S
 */
public class MarkLogicRepositoryTest extends  ConnectedRESTQA{
	private static MarkLogicRepository testRepository;
	private static ValueFactory vf ;
	private static MarkLogicRepositoryConnection testConn;
	private static int restPort = 8024;
	private static String host = "localhost";
	private static String dbName = "MLSesameRep";
	private static String [] fNames = {"MLSesameRep-1"};
	private static String restServer = "REST-MLSesame-Rep-API-Server";
	
	@BeforeClass
	public static void initialSetup() throws Exception {			
		setupJavaRESTServer(dbName, fNames[0], restServer, restPort);
		setupAppServicesConstraint(dbName);
		enableCollectionLexicon(dbName);
		enableTripleIndex(dbName);		
	}
	
	@AfterClass
	public static void tearDownSetup() throws Exception  {
		tearDownJavaRESTServer(dbName, fNames, restServer);
		
	}
	
	@Before
    public void testGetRepository() throws Exception {
        MarkLogicRepositoryConfig config = new MarkLogicRepositoryConfig();

        config.setHost(host);
        config.setPort(restPort);
        config.setUser("admin");
        config.setPassword("admin");
        config.setAuth("DIGEST");

        RepositoryFactory factory = new MarkLogicRepositoryFactory();
        Assert.assertEquals("marklogic:MarkLogicRepository", factory.getRepositoryType());
        testRepository = (MarkLogicRepository) factory.getRepository(config);
        testRepository.initialize();
        vf = testRepository.getValueFactory();
        testConn = testRepository.getConnection();
        Assert.assertTrue(testRepository.getConnection() instanceof MarkLogicRepositoryConnection);

    	try{
    		 Repository otherrepo = factory.getRepository(config);
    		 RepositoryConnection conn = otherrepo.getConnection();
			Assert.assertTrue(2>1);
		}
		catch(Exception e){
			Assert.assertTrue(e instanceof RepositoryException);
		}
     
    }
	@After
	public void tearDown()
		throws Exception
	{
		clearDB(restPort);
		testConn.close();
		testRepository.shutDown();
		testRepository = null;
		testConn = null;
	}
	@Test
	public void testShutdownFollowedByInit()
		throws Exception
	{
		testRepository.shutDown();
		testRepository.initialize();

		testConn = testRepository.getConnection();
		try {
			Resource s = vf.createURI("http://a");
			URI p = vf.createURI("http://b");
			Value o =vf.createLiteral("c");
			testConn.add(s,p,o);
			assertTrue(testConn.hasStatement(s,p,o, true));
		}
		finally {
			testConn.close();
		}
	}
	
    @Test
    public void testRepo1()
            throws Exception {

    	testRepository.initialize();
        Assert.assertTrue(testRepository.getConnection() instanceof MarkLogicRepositoryConnection);

    	testRepository.shutDown();
    	testRepository.initialize();
    	testRepository.initialize();
    	testRepository.shutDown();
    	testRepository.shutDown();
    	try {
    		testConn = testRepository.getConnection();
			fail("Getting connection object should fail if repository is not initialized");
		}
		catch (Exception e) {
			Assert.assertTrue(e instanceof RepositoryException);
		}
    	testRepository.initialize();
    	testConn = testRepository.getConnection();
        Assert.assertTrue(testRepository.getDataDir() == null);
        Assert.assertTrue(testRepository.isWritable());
        Assert.assertTrue(testRepository.getValueFactory() instanceof ValueFactoryImpl);
    }
	
	
}