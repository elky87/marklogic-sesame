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
package com.marklogic.semantics.sesame.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.Transaction;
import com.marklogic.client.query.QueryDefinition;
import com.marklogic.client.semantics.GraphPermissions;
import com.marklogic.client.semantics.SPARQLRuleset;
import com.marklogic.semantics.sesame.MarkLogicSesameException;
import com.marklogic.semantics.sesame.MarkLogicTransactionException;
import org.apache.commons.io.input.ReaderInputStream;
import org.openrdf.http.protocol.UnauthorizedException;
import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.*;
import org.openrdf.query.resultio.QueryResultIO;
import org.openrdf.query.resultio.TupleQueryResultFormat;
import org.openrdf.query.resultio.TupleQueryResultParser;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sparql.query.SPARQLQueryBindingSet;
import org.openrdf.rio.*;
import org.openrdf.rio.helpers.ParseErrorLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Timer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * An internal class that straddles Sesame and MarkLogic Java API client.
 *
 * @author James Fuller
 */
public class MarkLogicClient {

	private static final Logger logger = LoggerFactory.getLogger(MarkLogicClient.class);

	protected static final Charset UTF8 = Charset.forName("UTF-8");
	protected static final Charset charset = UTF8;

	protected static final TupleQueryResultFormat format = TupleQueryResultFormat.JSON;
	protected static final RDFFormat rdfFormat = RDFFormat.NTRIPLES;
	private MarkLogicClientImpl _client;

	private final Executor executor = Executors.newCachedThreadPool();

	private ValueFactory f;

	private ParserConfig parserConfig = new ParserConfig();

	private Transaction tx = null;

	private WriteCacheTimerTask timerCache;
	private Timer timer;

	private static boolean WRITE_CACHE_ENABLED = true;

	/**
	 * constructor init with connection params
	 *
	 * @param host
	 * @param port
	 * @param user
	 * @param password
	 * @param auth
	 */
	public MarkLogicClient(String host, int port, String user, String password,String auth) {
		this._client = new MarkLogicClientImpl(host,port,user,password,auth);
		this.initTimer();
	}

	/**
	 * constructor init with DatabaseClient
	 *
	 * @param databaseClient
	 */
	public MarkLogicClient(DatabaseClient databaseClient) {
		this._client = new MarkLogicClientImpl(databaseClient);
		this.initTimer();
	}

	/**
	 * start Timer task (write cache)
	 */
	public void initTimer(){
		if(this.WRITE_CACHE_ENABLED) {
			stopTimer();
			timerCache = new WriteCacheTimerTask(this);
			timer = new Timer();
			timer.scheduleAtFixedRate(timerCache, WriteCacheTimerTask.DEFAULT_INITIAL_DELAY, WriteCacheTimerTask.DEFAULT_CACHE_MILLIS);
		}
	}

	public void initTimer(long initDelay, long delayCache, long cacheSize ){
		if(this.WRITE_CACHE_ENABLED) {
			logger.debug("configuring write cache");
			stopTimer();
			timerCache = new WriteCacheTimerTask(this,cacheSize);
			timer = new Timer();
			timer.scheduleAtFixedRate(timerCache, initDelay, delayCache);
		}
	}
	/**
	 * stop Timer task (write cache)
	 */
	public void stopTimer() {
		if(this.WRITE_CACHE_ENABLED) {
			if(timerCache != null) {
				timerCache.cancel();
			}
			if(timer != null){
				timer.cancel();
			}
		}
	}

	/**
	 *  forces write cache to flush triples
	 *
	 * @throws MarkLogicSesameException
	 */
	public void sync() throws MarkLogicSesameException {
		if(WRITE_CACHE_ENABLED && timerCache != null)
			timerCache.forceRun();
	}

	/**
	 * get value factory
	 *
	 * @return ValueFactory
	 */
	public ValueFactory getValueFactory() {
		return this.f;
	}

	/**
	 * sets the value factory
	 *
	 * @param f
	 */
	public void setValueFactory(ValueFactory f) {
		this.f=f;
	}

	/**
	 * TupleQuery
	 *
	 * @param queryString
	 * @param bindings
	 * @param start
	 * @param pageLength
	 * @param includeInferred
	 * @param baseURI
	 * @return
	 * @throws RepositoryException
	 * @throws MalformedQueryException
	 * @throws UnauthorizedException
	 * @throws QueryInterruptedException
	 */
	public TupleQueryResult sendTupleQuery(String queryString,SPARQLQueryBindingSet bindings, long start, long pageLength, boolean includeInferred, String baseURI) throws RepositoryException, MalformedQueryException,
			QueryInterruptedException {
		sync();
		InputStream stream = null;
		try {
			stream = getClient().performSPARQLQuery(queryString, bindings, start, pageLength, this.tx, includeInferred, baseURI);
		} catch (JsonProcessingException e) {
			logger.error(e.getLocalizedMessage());
			throw new MarkLogicSesameException("Issue processing json.");
		}
		TupleQueryResultParser parser = QueryResultIO.createParser(format, getValueFactory());
		MarkLogicBackgroundTupleResult tRes = new MarkLogicBackgroundTupleResult(parser,stream);
		execute(tRes);
		return tRes;
	}

	/**
	 * GraphQuery
	 *
	 * @param queryString
	 * @param bindings
	 * @param includeInferred
	 * @param baseURI
	 * @return
	 * @throws IOException
	 */
	public GraphQueryResult sendGraphQuery(String queryString, SPARQLQueryBindingSet bindings, boolean includeInferred, String baseURI) throws IOException, MarkLogicSesameException {
		sync();
		InputStream stream = getClient().performGraphQuery(queryString, bindings, this.tx, includeInferred, baseURI);

		RDFParser parser = Rio.createParser(rdfFormat, getValueFactory());
		parser.setParserConfig(getParserConfig());
		parser.setParseErrorListener(new ParseErrorLogger());
		parser.setPreserveBNodeIDs(true);

		MarkLogicBackgroundGraphResult gRes;

		// fixup - baseURI cannot be null
		if(baseURI != null){
			gRes= new MarkLogicBackgroundGraphResult(parser,stream,charset,baseURI);
		}else{
			gRes= new MarkLogicBackgroundGraphResult(parser,stream,charset,"");
		}

		execute(gRes);
		return gRes;
	}

	/**
	 * BooleanQuery
	 *
	 * @param queryString
	 * @param bindings
	 * @param includeInferred
	 * @param baseURI
	 * @return
	 * @throws IOException
	 * @throws RepositoryException
	 * @throws MalformedQueryException
	 * @throws UnauthorizedException
	 * @throws QueryInterruptedException
	 */
	public boolean sendBooleanQuery(String queryString, SPARQLQueryBindingSet bindings, boolean includeInferred, String baseURI) throws IOException, RepositoryException, MalformedQueryException,
			QueryInterruptedException {
		sync();
		return getClient().performBooleanQuery(queryString, bindings, this.tx, includeInferred, baseURI);
	}

	/**
	 * UpdateQuery
	 *
	 * @param queryString
	 * @param bindings
	 * @param includeInferred
	 * @param baseURI
	 * @throws IOException
	 * @throws RepositoryException
	 * @throws MalformedQueryException
	 * @throws UnauthorizedException
	 * @throws UpdateExecutionException
	 */
	public void sendUpdateQuery(String queryString, SPARQLQueryBindingSet bindings, boolean includeInferred, String baseURI) throws IOException, RepositoryException, MalformedQueryException,UpdateExecutionException {
		sync();
		getClient().performUpdateQuery(queryString, bindings, this.tx, includeInferred, baseURI);
	}

	/**
	 * add triples from file
	 *
	 * @param file
	 * @param baseURI
	 * @param dataFormat
	 * @param contexts
	 * @throws RDFParseException
	 */
	public void sendAdd(File file, String baseURI, RDFFormat dataFormat, Resource... contexts) throws RDFParseException {
		getClient().performAdd(file, baseURI, dataFormat, this.tx, contexts);
	}

	/**
	 * add triples from InputStream
	 *
	 * @param in
	 * @param baseURI
	 * @param dataFormat
	 * @param contexts
	 */
	public void sendAdd(InputStream in, String baseURI, RDFFormat dataFormat, Resource... contexts) throws RDFParseException, MarkLogicSesameException {
		getClient().performAdd(in, baseURI, dataFormat, this.tx, contexts);
	}

	/**
	 * add triples from Reader
	 *
	 * @param in
	 * @param baseURI
	 * @param dataFormat
	 * @param contexts
	 */
	public void sendAdd(Reader in, String baseURI, RDFFormat dataFormat, Resource... contexts) throws RDFParseException, MarkLogicSesameException {
		//TBD- must deal with char encoding
		getClient().performAdd(new ReaderInputStream(in), baseURI, dataFormat, this.tx, contexts);
	}

	/**
	 * add single triple, if cache is enabled will add triple to cache model
	 *
	 * @param baseURI
	 * @param subject
	 * @param predicate
	 * @param object
	 * @param contexts
	 */
	public void sendAdd(String baseURI, Resource subject, URI predicate, Value object, Resource... contexts) throws MarkLogicSesameException {
		if (WRITE_CACHE_ENABLED) {
			timerCache.add(subject, predicate, object, contexts);
		} else {
			getClient().performAdd(baseURI, (Resource) skolemize(subject), (URI) skolemize(predicate), skolemize(object), this.tx, contexts);
		}
	}

	/**
	 * remove single triple
	 *
	 * @param baseURI
	 * @param subject
	 * @param predicate
	 * @param object
	 * @param contexts
	 */
	public void sendRemove(String baseURI, Resource subject,URI predicate, Value object, Resource... contexts) throws MarkLogicSesameException {
		sync();
		getClient().performRemove(baseURI, (Resource) skolemize(subject), (URI) skolemize(predicate), skolemize(object), this.tx, contexts);
	}

	/**
	 * clears all triples from context
	 *
	 * @param contexts
	 */
	public void sendClear(Resource... contexts) throws MarkLogicSesameException {
		sync();
		getClient().performClear(this.tx, contexts);
	}

	/**
	 * clear all triples
	 *
	 */
	public void sendClearAll() throws MarkLogicSesameException {
		sync();
		getClient().performClearAll(this.tx);
	}

	/**
	 * opens a transaction
	 *
	 * @throws MarkLogicTransactionException
	 */
	public void openTransaction() throws MarkLogicTransactionException {
		if (!isActiveTransaction()) {
			this.tx = getClient().getDatabaseClient().openTransaction();
		}else{
			throw new MarkLogicTransactionException("Only one active transaction allowed.");
		}
	}

	/**
	 * commits a transaction
	 *
	 * @throws MarkLogicTransactionException
	 */
	public void commitTransaction() throws MarkLogicTransactionException {
		if (isActiveTransaction()) {
			try {
				sync();
				this.tx.commit();
				this.tx=null;
			} catch (MarkLogicSesameException e) {
				logger.warn(e.getLocalizedMessage());
				throw new MarkLogicTransactionException(e);
			}
		}else{
			throw new MarkLogicTransactionException("No active transaction to commit.");
		}
	}

	/**
	 * rollback transaction
	 *
	 * @throws MarkLogicTransactionException
	 */
	public void rollbackTransaction() throws MarkLogicTransactionException {
		if(isActiveTransaction()) {
			try {
				sync();
			} catch (MarkLogicSesameException e) {
				throw new MarkLogicTransactionException(e);
			}
			this.tx.rollback();
			this.tx = null;
		}else{
			throw new MarkLogicTransactionException("No active transaction to rollback.");
		}
	}

	/**
	 * checks if a transaction currently exists
	 *
	 * @return
	 */
	public boolean isActiveTransaction(){
		return this.tx != null;
	}

	/**
	 * sets tx to null
	 *
	 * @throws MarkLogicTransactionException
	 */
	public void setAutoCommit() throws MarkLogicTransactionException {
		if (isActiveTransaction()) {
			throw new MarkLogicTransactionException("Active transaction.");
		}else{
			this.tx=null;
		}
	}

	/**
	 * getter for ParserConfig
	 *
	 * @return
	 */
	public ParserConfig getParserConfig() {
		return this.parserConfig;
	}

	/**
	 * setter for ParserConfig
	 *
	 * @param parserConfig
	 */
	public void setParserConfig(ParserConfig parserConfig) {
		this.parserConfig=parserConfig;
	}

	/**
	 * setter for Rulesets
	 *
	 * @param rulesets
	 */
	public void setRulesets(SPARQLRuleset... rulesets){
		getClient().setRulesets(rulesets);
	}

	/**
	 * getter for Rulesets
	 *
	 * @return
	 */
	public SPARQLRuleset[] getRulesets(){
		return getClient().getRulesets();
	}

	/**
	 * setter for QueryDefinition
	 *
	 * @param constrainingQueryDefinition
	 */
	public void setConstrainingQueryDefinition(QueryDefinition constrainingQueryDefinition){
		getClient().setConstrainingQueryDefinition(constrainingQueryDefinition);
	}

	/**
	 * getter for QueryDefinition
	 *
	 * @return
	 */
	public QueryDefinition getConstrainingQueryDefinition(){
		return getClient().getConstrainingQueryDefinition();
	}

	/**
	 * setter for GraphPermissions
	 *
	 * @param graphPerms
	 */
	public void setGraphPerms(GraphPermissions graphPerms){

		if (graphPerms != null) {
			getClient().setGraphPerms(graphPerms);
		}else {
			getClient().setGraphPerms(getClient().getDatabaseClient().newGraphManager().newGraphPermissions());
		}
	}

	/**
	 * getter for GraphPermissions
	 *
	 * @return
	 */
	public GraphPermissions getGraphPerms(){
		return getClient().getGraphPerms();
	}

	public GraphPermissions emptyGraphPerms(){
		return _client.getDatabaseClient().newGraphManager().newGraphPermissions();
	}

	/**
	 *exec
	 * @param command
	 */
	protected void execute(Runnable command) {
		executor.execute(command);
	}


	///////////////////////////////////////////////////////////////////////////////////////////////
	// private ////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 *
	 * @return
	 */
	private MarkLogicClientImpl getClient(){
		return this._client;
	}

	/**
	 *
	 * @param s
	 * @return
	 */
	private Value skolemize(Value s) {
		if (s instanceof org.openrdf.model.BNode) {
			return getValueFactory().createURI("http://marklogic.com/semantics/blank/" + s.toString());
		} else {
			return s;
		}
	}

	/**
	 *
	 */
	public void close() {
		_client.close();
	}

}