/*
 * Copyright 2015-2016 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * A library that enables access to a MarkLogic-backed triple-store via the
 * Sesame API.
 */
package com.marklogic.semantics.sesame;

import org.junit.*;
import org.junit.rules.ExpectedException;
import org.openrdf.model.ValueFactory;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.sparql.SPARQLConnection;
import org.openrdf.repository.sparql.SPARQLRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * test connectivity to SPARQLRepository with v1/graphs/sparql
 *
 * @author James Fuller
 */
public class SPARQLRepositoryTest extends SesameTestBase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected SPARQLRepository sr;
    protected SPARQLConnection conn;
    protected ValueFactory f;

    @Before
    public void setUp()
            throws Exception {
        logger.debug("setting up test");
        SPARQLRepository sr = new SPARQLRepository("http://"+host+":"+port+"/v1/graphs/sparql");
        sr.setUsernameAndPassword("admin","admin");
        sr.initialize();
        f = sr.getValueFactory();
        conn = (SPARQLConnection) sr.getConnection();
        logger.info("test setup complete.");
    }

    @After
    public void tearDown()
            throws Exception {
        logger.debug("tearing down...");
        if(conn.isActive() && conn.isOpen()){conn.rollback();}
        if(conn.isOpen()){conn.clear();}
        conn.close();
        conn = null;
        rep.shutDown();
        rep = null;
        logger.info("tearDown complete.");
    }

    // https://github.com/marklogic/marklogic-sesame/issues/237
    // assert ML SPARQL endpoint can be used by sesame SPARQLRepository
    @Test
    public void testSPARQLRepositoryWithMarkLogic()
            throws Exception
    {
        SPARQLRepository sparqlRepo = new SPARQLRepository("http://"+host+":"+port+"/v1/graphs/sparql");
        sparqlRepo.initialize();
        sparqlRepo.setUsernameAndPassword("s-rest-writer","x");
        RepositoryConnection sparqlConn = sparqlRepo.getConnection();
        Assert.assertEquals(0, sparqlConn.size());
    }
}
