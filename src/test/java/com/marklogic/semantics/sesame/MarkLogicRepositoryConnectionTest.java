package com.marklogic.semantics.sesame;

import com.marklogic.semantics.sesame.query.MarkLogicTupleQuery;
import info.aduna.iteration.ConvertingIteration;
import info.aduna.iteration.ExceptionConvertingIteration;
import org.junit.*;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.query.*;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.rio.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

public class MarkLogicRepositoryConnectionTest {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    private Repository rep;

    protected RepositoryConnection conn;

    protected ValueFactory f;

    @Before
    public void setUp()
            throws Exception {
        logger.debug("setting up test");

        // extrude to semantics.utils
        Properties props = new Properties();
        try {
            props.load(new FileInputStream("gradle.properties"));
        } catch (IOException e) {
            System.err.println("problem loading properties file.");
            System.exit(1);
        }
        String host = props.getProperty("mlHost");
        int port = Integer.parseInt(props.getProperty("mlRestPort"));
        String user = props.getProperty("mlUsername");
        String pass = props.getProperty("mlPassword");
        // extrude to semantics.utils

        this.rep = new MarkLogicRepository(host, port, user, pass, "DIGEST");


        rep.initialize();

        f = rep.getValueFactory();
        conn = rep.getConnection();

        logger.info("test setup complete.");
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown()
            throws Exception {
        logger.debug("tearing down...");
        conn.close();
        conn = null;

        rep.shutDown();
        rep = null;

        logger.info("tearDown complete.");
    }


    @Test
    public void testMarkLogicRepositoryConnection()
            throws Exception {

        Assert.assertNotNull("Expected repository to exist.", rep);
        Assert.assertTrue("Expected repository to be initialized.", rep.isInitialized());
        rep.shutDown();
        Properties props = new Properties();
        try {
            props.load(new FileInputStream("gradle.properties"));
        } catch (IOException e) {
            System.err.println("problem loading properties file.");
            System.exit(1);
        }
        String host = props.getProperty("mlHost");
        int port = Integer.parseInt(props.getProperty("mlRestPort"));
        String user = props.getProperty("mlUsername");
        String pass = props.getProperty("mlPassword");

        rep = new MarkLogicRepository(host, port, user, pass, "DIGEST");

        Assert.assertNotNull("Expected repository to exist.", rep);
        Assert.assertFalse("Expected repository to not be initialized.", rep.isInitialized());
        rep.initialize();
        Assert.assertTrue("Expected repository to be initialized.", rep.isInitialized());
        rep.shutDown();
        Assert.assertFalse("Expected repository to not be initialized.", rep.isInitialized());
        rep.initialize();
        Assert.assertNotNull("Expected repository to exist.", rep);
    }

    @Test
    public void testSPARQLQueryWithPrepareQuery()
            throws Exception {

        String queryString = "select ?s ?p ?o { ?s ?p ?o } limit 1 ";
        Query q = conn.prepareQuery(QueryLanguage.SPARQL, queryString);

        if (q instanceof TupleQuery) {
            TupleQueryResult result = ((TupleQuery)q).evaluate();
            while (result.hasNext()) {
                BindingSet tuple = result.next();
                Assert.assertEquals("s",tuple.getBinding("s").getName());
                Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#AlexandriaGeodata",tuple.getBinding("s").getValue().stringValue());
            }
        }
    }

    @Test
    public void testSPARQLQuery()
            throws Exception {

        String queryString = "select ?s ?p ?o { ?s ?p ?o } limit 2 ";
        TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
        TupleQueryResult results = tupleQuery.evaluate();

        Assert.assertEquals(results.getBindingNames().get(0), "s");
        Assert.assertEquals(results.getBindingNames().get(1), "p");
        Assert.assertEquals(results.getBindingNames().get(2), "o");

        BindingSet bindingSet = results.next();

        Value sV = bindingSet.getValue("s");
        Value pV = bindingSet.getValue("p");
        Value oV = bindingSet.getValue("o");

        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#AlexandriaGeodata", sV.stringValue());
        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#altitude", pV.stringValue());
        Assert.assertEquals("0", oV.stringValue());

        BindingSet bindingSet1 = results.next();

        Value sV1 = bindingSet1.getValue("s");
        Value pV1 = bindingSet1.getValue("p");
        Value oV1 = bindingSet1.getValue("o");

        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#AmphipolisGeodata", sV1.stringValue());
        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#altitude", pV1.stringValue());
        Assert.assertEquals("0", oV1.stringValue());
    }

    @Test
    public void testSPARQLQueryDistinct()
            throws Exception {

        try{
        String queryString = "SELECT DISTINCT ?_ WHERE { GRAPH ?_ { ?s ?p ?o } }";
        TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
        TupleQueryResult result = tupleQuery.evaluate();
        RepositoryResult<Resource> rr =
         new RepositoryResult<Resource>(
                new ExceptionConvertingIteration<Resource, RepositoryException>(
                        new ConvertingIteration<BindingSet, Resource, QueryEvaluationException>(result) {

                            @Override
                            protected Resource convert(BindingSet bindings)
                                    throws QueryEvaluationException {
                                return (Resource) bindings.getValue("_");
                            }
                        }) {

                    @Override
                    protected RepositoryException convert(Exception e) {
                        return new RepositoryException(e);
                    }
                });

        Resource resource = rr.next();

        logger.debug(resource.stringValue());

    } catch (MalformedQueryException e) {
        throw new RepositoryException(e);
    } catch (QueryEvaluationException e) {
        throw new RepositoryException(e);
    }
}

    @Test
    public void testSPARQLQueryWithPagination()
            throws Exception {
        String queryString = "select ?s ?p ?o { ?s ?p ?o } limit 100 ";
        MarkLogicTupleQuery tupleQuery = (MarkLogicTupleQuery) conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
        TupleQueryResult results = tupleQuery.evaluate(3, 1);

        Assert.assertEquals(results.getBindingNames().get(0), "s");
        Assert.assertEquals(results.getBindingNames().get(1), "p");
        Assert.assertEquals(results.getBindingNames().get(2), "o");

        BindingSet bindingSet = results.next();

        Value sV = bindingSet.getValue("s");
        Value pV = bindingSet.getValue("p");
        Value oV = bindingSet.getValue("o");

        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#AntiochGeodata", sV.stringValue());
        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#altitude", pV.stringValue());
        Assert.assertEquals("0", oV.stringValue());

        Assert.assertFalse(results.hasNext());
    }

    @Test
    public void testSPARQLQueryWithResultsHandler()
            throws Exception {
        String queryString = "select ?s ?p ?o { ?s ?p ?o } limit 10";
        TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);

        tupleQuery.evaluate(new TupleQueryResultHandler() {
            @Override
            public void startQueryResult(List<String> bindingNames) {
                Assert.assertEquals(bindingNames.get(0), "s");
                Assert.assertEquals(bindingNames.get(1), "p");
                Assert.assertEquals(bindingNames.get(2), "o");
            }

            @Override
            public void handleSolution(BindingSet bindingSet) {
                Assert.assertEquals(bindingSet.getBinding("o").getValue().stringValue(), "0");
            }

            @Override
            public void endQueryResult() {
            }

            @Override
            public void handleBoolean(boolean arg0)
                    throws QueryResultHandlerException {
            }

            @Override
            public void handleLinks(List<String> arg0)
                    throws QueryResultHandlerException {
            }
        });
        tupleQuery.evaluate();
    }

    @Test
    public void testSPARQLQueryBindings()
            throws Exception {

        String queryString = "select ?s ?p ?o { ?s ?p ?o . filter (?s = ?b) filter (?p = ?c) }";
        TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);

        tupleQuery.setBinding("b", ValueFactoryImpl.getInstance().createURI("http://semanticbible.org/ns/2006/NTNames#Jim"));
        tupleQuery.setBinding("c", ValueFactoryImpl.getInstance().createURI("http://semanticbible.org/ns/2006/NTNames#parentOf"));

        tupleQuery.removeBinding("c");

        // TBD -  Assert. for confirmation of removal

        Assert.assertEquals(null,tupleQuery.getBindings().getBinding("c"));

        tupleQuery.clearBindings();

        Assert.assertEquals(null, tupleQuery.getBindings().getBinding("b"));

        tupleQuery.setBinding("b", ValueFactoryImpl.getInstance().createURI("http://semanticbible.org/ns/2006/NTNames#Jotham"));
        tupleQuery.setBinding("c", ValueFactoryImpl.getInstance().createURI("http://semanticbible.org/ns/2006/NTNames#parentOf"));

        TupleQueryResult results = tupleQuery.evaluate();

        Assert.assertEquals(results.getBindingNames().get(0), "s");
        Assert.assertEquals(results.getBindingNames().get(1), "p");
        Assert.assertEquals(results.getBindingNames().get(2), "o");

        logger.info(results.getBindingNames().toString());

        results.hasNext();
        BindingSet bindingSet = results.next();

        Value sV = bindingSet.getValue("s");
        Value pV = bindingSet.getValue("p");
        Value oV = bindingSet.getValue("o");

        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#Jotham", sV.stringValue());
        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#parentOf", pV.stringValue());
        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#Ahaz", oV.stringValue());
    }

    @Ignore
    public void incrementallyBuildQueryTest() throws MalformedQueryException, RepositoryException {

        TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, "");

//        StringBuffer sb = new StringBuffer();
//        sb.append("SELECT ?g ?s ?p ?o where {GRAPH ?g { ?s ?p ?o }");
//        if (s != Node) {
//            tupleQuery.setBinding("a", s.getURI());
//            sb.append("FILTER (?s = ?a) ");
//        }
//        if (p != Node.ANY) {
//            tupleQuery.setBinding("b", p.getURI());
//            sb.append("FILTER (?p = ?b) ");
//        }
//        if (o != Node.ANY) {
//            tupleQuery.setBinding("c",o);
//            sb.append("FILTER (?o = ?c) ");
//        }
//        sb.append("}");
//
//        qdef.setSparql(sb.toString());

    }

    //negative test by supplying CONSTRUCT query to TupleQuery
    @Ignore
    public void negativeTestSPARQLQuery()
            throws Exception {
        String queryString = "PREFIX nn: <http://semanticbible.org/ns/2006/NTNames#>\n" +
                "PREFIX test: <http://marklogic.com#test>\n" +
                "\n" +
                "construct { ?s  test:test \"0\"} where  {?s nn:childOf nn:Eve . }";
        TupleQuery tupleQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString);
        TupleQueryResult results = tupleQuery.evaluate();

        Assert.assertEquals(results.getBindingNames().get(0), "s");
        Assert.assertEquals(results.getBindingNames().get(1), "p");
        Assert.assertEquals(results.getBindingNames().get(2), "o");
    }

    @Test
    public void testConstructQuery()
            throws Exception {
        String queryString = "PREFIX nn: <http://semanticbible.org/ns/2006/NTNames#>\n" +
                "PREFIX test: <http://marklogic.com#test>\n" +
                "\n" +
                "construct { ?s  test:test \"0\"} where  {?s nn:childOf nn:Eve . }";
        GraphQuery graphQuery = conn.prepareGraphQuery(QueryLanguage.SPARQL, queryString);
        GraphQueryResult results = graphQuery.evaluate();
        Statement st1 = results.next();
        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#Abel",st1.getSubject().stringValue());
        Statement st2 = results.next();
        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#Cain", st2.getSubject().stringValue());
    }

    @Test
    public void testDescribeQuery()
            throws Exception {
        String queryString = "DESCRIBE <http://semanticbible.org/ns/2006/NTNames#Shelah>";
        GraphQuery graphQuery = conn.prepareGraphQuery(QueryLanguage.SPARQL, queryString);
        GraphQueryResult results = graphQuery.evaluate();
        Statement st1 = results.next();
        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#Shelah",st1.getSubject().stringValue());
        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#childOf",st1.getPredicate().stringValue());
        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#CainanSonOfArphaxad",st1.getObject().stringValue());
    }

    @Ignore
    public void negativeTestDescribeQuery1()
            throws Exception {
        String queryString = "ASK { <http://semanticbible.org/ns/2006/NTNames#Shelah1> ?p ?o}";
        GraphQuery graphQuery = conn.prepareGraphQuery(QueryLanguage.SPARQL, queryString);
        GraphQueryResult results = graphQuery.evaluate();
        Statement st1 = results.next();
        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#Shelah",st1.getSubject().stringValue());
        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#childOf",st1.getPredicate().stringValue());
        Assert.assertEquals("http://semanticbible.org/ns/2006/NTNames#CainanSonOfArphaxad",st1.getObject().stringValue());
    }

    @Test
    public void testBooleanQuery()
            throws Exception {
        String queryString = "ASK { <http://semanticbible.org/ns/2006/NTNames#Shelah1> ?p ?o}";
        BooleanQuery booleanQuery = conn.prepareBooleanQuery(QueryLanguage.SPARQL, queryString);
        boolean results = booleanQuery.evaluate();
        Assert.assertEquals(false,results);
        queryString = "ASK { <http://semanticbible.org/ns/2006/NTNames#Shelah> ?p ?o}";
        booleanQuery = conn.prepareBooleanQuery(QueryLanguage.SPARQL, queryString);
        results = booleanQuery.evaluate();
        Assert.assertEquals(true,results);
    }

    @Test
    public void testUpdateQuery()
            throws Exception {
        String defGraphQuery = "INSERT DATA { GRAPH <http://marklogic.com/test/g27> { <http://marklogic.com/test> <pp1> <oo1> } }";
        String checkQuery = "ASK WHERE { <http://marklogic.com/test> <pp1> <oo1> }";
        Update updateQuery = conn.prepareUpdate(QueryLanguage.SPARQL, defGraphQuery);
        updateQuery.execute();
        BooleanQuery booleanQuery = conn.prepareBooleanQuery(QueryLanguage.SPARQL, checkQuery);
        boolean results = booleanQuery.evaluate();
        Assert.assertEquals(true, results);
    }

    @Ignore
    public void testTransactions() throws Exception{
        File inputFile1 = new File("src/test/resources/testdata/default-graph-1.ttl");
        String baseURI1 = "http://example.org/example1/";

        File inputFile2 = new File("src/test/resources/testdata/default-graph-2.ttl");
        String baseURI2 = "http://example.org/example2/";

        try {
            conn.begin();

            // Add the first file
            conn.add(inputFile1, baseURI1, RDFFormat.TURTLE);


            // Add the second file
            conn.add(inputFile2, baseURI2, RDFFormat.TURTLE);

            // If everything went as planned, we can commit the result
            conn.commit();
        }
        catch (RepositoryException e) {
            // Something went wrong during the transaction, so we roll it back
            conn.rollback();
        }
        finally {
            // Whatever happens, we want to close the connection when we are done.
            conn.close();
        }

    }
    @Test
    public void testContextIDs()
            throws Exception {
        RepositoryResult<Resource> result = conn.getContextIDs();
        try {
            Assert.assertTrue("result should not be empty", result.hasNext());
            logger.debug("ContextIDs");
            Assert.assertEquals("http://marklogic.com/test/my-graph", result.next().stringValue());
            Assert.assertEquals("http://marklogic.com/test/g27",result.next().stringValue());
        } finally {
            result.close();
        }
    }

    @Test
    public void testHasStatement(){

    }
}