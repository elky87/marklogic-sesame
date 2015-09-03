/*
 * Copyright 2015 MarkLogic Corporation
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

import com.marklogic.semantics.sesame.client.MarkLogicClient;
import com.marklogic.semantics.sesame.query.*;

import info.aduna.iteration.*;

import org.openrdf.IsolationLevel;
import org.openrdf.IsolationLevels;
import org.openrdf.model.*;
import org.openrdf.model.impl.StatementImpl;
import org.openrdf.query.*;
import org.openrdf.query.impl.DatasetImpl;
import org.openrdf.query.parser.QueryParserUtil;
import org.openrdf.query.parser.sparql.SPARQLUtil;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.UnknownTransactionStateException;
import org.openrdf.repository.base.RepositoryConnectionBase;
import org.openrdf.repository.sparql.query.SPARQLQueryBindingSet;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.Iterator;

import static org.openrdf.query.QueryLanguage.SPARQL;

/**
 * RepositoryConnection to MarkLogic triplestore
 *
 * @author James Fuller
 */
public class MarkLogicRepositoryConnection extends RepositoryConnectionBase implements RepositoryConnection,MarkLogicRepositoryConnectionDependent {

    protected final Logger logger = LoggerFactory.getLogger(MarkLogicRepositoryConnection.class);

    private static final String DEFAULT_GRAPH_URI = "http://marklogic.com/semantics#default-graph";

    private static final String EVERYTHING = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }";

    private static final String SOMETHING = "ASK { ?s ?p ?o }";

    private static final String COUNT_EVERYTHING = "SELECT (count(?s) as ?ct) where { GRAPH ?g { ?s ?p ?o } }";

    private final boolean quadMode;

    private MarkLogicClient client;

    /**
     * constructor
     *
     * @param repository
     * @param client
     * @param quadMode
     */
    public MarkLogicRepositoryConnection(MarkLogicRepository repository, MarkLogicClient client, boolean quadMode) {
        super(repository);
        this.client = client;
        this.quadMode = true;
        client.setValueFactory(repository.getValueFactory());
    }

    /**
     * gets the current value factory
     *
     * @return ValueFactory
     */
    @Override
    public ValueFactory getValueFactory() {
        return client.getValueFactory();
    }

    /**
     * sets the current value factory
     *
     * @param f
     */
    public void setValueFactory(ValueFactory f) {
        client.setValueFactory(f);
    }

    @Override
    /**
     * Releases the connection to the database.  Ensures that open transactions
     * are complete.
     */
    public void close()
        throws RepositoryException
    {
        super.close();
        try {
            if (this.isActive()) {
                client.rollbackTransaction();
            }
        } catch (Exception e) {
            
        }
    }
    
    /**
     * overload for prepareQuery
     *
     * @param queryString
     * @return MarkLogicQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public Query prepareQuery(String queryString) throws RepositoryException, MalformedQueryException {
        return prepareQuery(QueryLanguage.SPARQL, queryString, null);
    }

    /**
     * overload for prepareQuery
     *
     * @param queryString
     * @param baseURI
     * @return MarkLogicQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public Query prepareQuery(String queryString, String baseURI) throws RepositoryException, MalformedQueryException {
        return prepareQuery(QueryLanguage.SPARQL, queryString, baseURI);
    }

    /**
     * overload for prepareQuery
     *
     * @param queryLanguage
     * @param queryString
     * @return MarkLogicQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public Query prepareQuery(QueryLanguage queryLanguage, String queryString) throws RepositoryException, MalformedQueryException {
        return prepareQuery(queryLanguage, queryString, null);
    }

    /**
     * base method for prepareQuery
     *
     * routes to all other query forms (prepareTupleQuery,prepareBooleanQuery,prepareGraphQuery)
     *
     * @param queryLanguage
     * @param queryString
     * @param baseURI
     * @return MarkLogicQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicQuery prepareQuery(QueryLanguage queryLanguage, String queryString, String baseURI)
            throws RepositoryException, MalformedQueryException
    {
        // function routing based on query form
        if (SPARQL.equals(queryLanguage)) {
            String queryStringWithoutProlog = QueryParserUtil.removeSPARQLQueryProlog(queryString).toUpperCase();
            if (queryStringWithoutProlog.startsWith("SELECT")) {
                return prepareTupleQuery(queryLanguage, queryString, baseURI);   //must be a TupleQuery
            }
            else if (queryStringWithoutProlog.startsWith("ASK")) {
                return prepareBooleanQuery(queryLanguage, queryString, baseURI); //must be a BooleanQuery
            }
            else {
                return prepareGraphQuery(queryLanguage, queryString, baseURI);   //all the rest use GraphQuery
            }
        }
        throw new UnsupportedQueryLanguageException("Unsupported query language " + queryLanguage.getName());
    }

    /**
     * overload for prepareTupleQuery
     *
     * @param queryString
     * @return MarkLogicTupleQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicTupleQuery prepareTupleQuery(String queryString) throws RepositoryException, MalformedQueryException {
        return prepareTupleQuery(QueryLanguage.SPARQL, queryString);
    }
    /**
     * overload for prepareTupleQuery
     *
     * @param queryString
     * @param baseURI
     * @return MarkLogicTupleQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicTupleQuery prepareTupleQuery(String queryString,String baseURI) throws RepositoryException, MalformedQueryException {
        return prepareTupleQuery(QueryLanguage.SPARQL, queryString, baseURI);
    }

    /**
     * overload for prepareTupleQuery
     *
     * @param queryLanguage
     * @param queryString
     * @return MarkLogicTupleQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicTupleQuery prepareTupleQuery(QueryLanguage queryLanguage, String queryString) throws RepositoryException, MalformedQueryException {
        return prepareTupleQuery(queryLanguage, queryString, null);
    }

    /**
     * base method for prepareTupleQuery
     *
     * @param queryLanguage
     * @param queryString
     * @param baseURI
     * @return MarkLogicTupleQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicTupleQuery prepareTupleQuery(QueryLanguage queryLanguage, String queryString, String baseURI) throws RepositoryException, MalformedQueryException {
        if (QueryLanguage.SPARQL.equals(queryLanguage)) {
            return new MarkLogicTupleQuery(this.client, new SPARQLQueryBindingSet(), baseURI, queryString);
        }
        throw new UnsupportedQueryLanguageException("Unsupported query language " + queryLanguage.getName());
    }

    /**
     * overload for prepareGraphQuery
     *
     * @param queryString
     * @return MarkLogicGraphQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicGraphQuery prepareGraphQuery(String queryString) throws RepositoryException, MalformedQueryException {
        return prepareGraphQuery(QueryLanguage.SPARQL, queryString, null);
    }

    /**
     * overload for prepareGraphQuery
     *
     * @param queryString
     * @param baseURI
     * @return MarkLogicGraphQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicGraphQuery prepareGraphQuery(String queryString,String baseURI) throws RepositoryException, MalformedQueryException {
        return prepareGraphQuery(QueryLanguage.SPARQL, queryString, baseURI);
    }

    /**
     * overload for prepareGraphQuery
     *
     * @param queryLanguage
     * @param queryString
     * @return MarkLogicGraphQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicGraphQuery prepareGraphQuery(QueryLanguage queryLanguage, String queryString) throws RepositoryException, MalformedQueryException {
        return prepareGraphQuery(queryLanguage, queryString, null);
    }

    /**
     * base method for prepareGraphQuery
     *
     * @param queryLanguage
     * @param queryString
     * @param baseURI
     * @return MarkLogicGraphQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicGraphQuery prepareGraphQuery(QueryLanguage queryLanguage, String queryString, String baseURI)
            throws RepositoryException, MalformedQueryException
    {
        if (QueryLanguage.SPARQL.equals(queryLanguage)) {
            return new MarkLogicGraphQuery(this.client, new SPARQLQueryBindingSet(), baseURI, queryString);
        }
        throw new UnsupportedQueryLanguageException("Unsupported query language " + queryLanguage.getName());
    }

    /**
     * overload for prepareBooleanQuery
     *
     * @param queryString
     * @return MarkLogicBooleanQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicBooleanQuery prepareBooleanQuery(String queryString) throws RepositoryException, MalformedQueryException {
        return prepareBooleanQuery(QueryLanguage.SPARQL, queryString, null);
    }

    /**
     * overload for prepareBooleanQuery
     *
     * @param queryString
     * @param baseURI
     * @return MarkLogicBooleanQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicBooleanQuery prepareBooleanQuery(String queryString,String baseURI) throws RepositoryException, MalformedQueryException {
        return prepareBooleanQuery(QueryLanguage.SPARQL, queryString, baseURI);
    }

    /**
     * overload for prepareBooleanQuery
     *
     * @param queryLanguage
     * @param queryString
     * @return MarkLogicBooleanQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicBooleanQuery prepareBooleanQuery(QueryLanguage queryLanguage, String queryString) throws RepositoryException, MalformedQueryException {
        return prepareBooleanQuery(queryLanguage, queryString, null);
    }

    /**
     * base method for prepareBooleanQuery
     *
     * @param queryLanguage
     * @param queryString
     * @param baseURI
     * @return MarkLogicBooleanQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicBooleanQuery prepareBooleanQuery(QueryLanguage queryLanguage, String queryString, String baseURI) throws RepositoryException, MalformedQueryException {
        if (QueryLanguage.SPARQL.equals(queryLanguage)) {
            return new MarkLogicBooleanQuery(this.client, new SPARQLQueryBindingSet(), baseURI, queryString);
        }
        throw new UnsupportedQueryLanguageException("Unsupported query language " + queryLanguage.getName());
    }

    /**
     * overload for prepareUpdate
     *
     * @param queryString
     * @return MarkLogicUpdateQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicUpdateQuery prepareUpdate(String queryString) throws RepositoryException, MalformedQueryException {
        return prepareUpdate(QueryLanguage.SPARQL, queryString, null);
    }

    /**
     * overload for prepareUpdate
     *
     * @param queryString
     * @param baseURI
     * @return MarkLogicUpdateQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicUpdateQuery prepareUpdate(String queryString,String baseURI) throws RepositoryException, MalformedQueryException {
        return prepareUpdate(QueryLanguage.SPARQL, queryString, baseURI);
    }

    /**
     * overload for prepareUpdate
     *
     * @param queryLanguage
     * @param queryString
     * @return MarkLogicUpdateQuery
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicUpdateQuery prepareUpdate(QueryLanguage queryLanguage, String queryString) throws RepositoryException, MalformedQueryException {
       return prepareUpdate(queryLanguage, queryString, null);
    }

    /**
     * base method for prepareUpdate
     *
     * @param queryLanguage
     * @param queryString
     * @param baseURI
     * @return
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public MarkLogicUpdateQuery prepareUpdate(QueryLanguage queryLanguage, String queryString, String baseURI) throws RepositoryException, MalformedQueryException {
        if (QueryLanguage.SPARQL.equals(queryLanguage)) {
            return new MarkLogicUpdateQuery(this.client, new SPARQLQueryBindingSet(), baseURI, queryString);
        }
        throw new UnsupportedQueryLanguageException("Unsupported query language " + queryLanguage.getName());
    }

    /**
     * returns list of graph names as Resource
     *
     * @return RepositoryResult<Resource>
     * @throws RepositoryException
     */
    @Override
    public RepositoryResult<Resource> getContextIDs() throws RepositoryException {

        try{
            String queryString = "SELECT DISTINCT ?_ WHERE { GRAPH ?ctx { ?s ?p ?o } }";
            TupleQuery tupleQuery = prepareTupleQuery(QueryLanguage.SPARQL, queryString);
            TupleQueryResult result = tupleQuery.evaluate();
            return
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

        } catch (MalformedQueryException e) {
            throw new RepositoryException(e);
        } catch (QueryEvaluationException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * returns all statements
     *
     * @param subj
     * @param pred
     * @param obj
     * @param includeInferred
     * @return RepositoryResult<Statement>
     * @throws RepositoryException
     */
    public RepositoryResult<Statement> getStatements(Resource subj, URI pred, Value obj, boolean includeInferred) throws RepositoryException {
        try {
            if (isQuadMode()) {
                StringBuilder sb= new StringBuilder();
                sb.append("SELECT * {GRAPH ?ctx { ?s ?p ?o . }}");
                TupleQuery tupleQuery = prepareTupleQuery(sb.toString());
                setBindings(tupleQuery, subj, pred, obj);
                tupleQuery.setIncludeInferred(includeInferred);
                TupleQueryResult qRes = tupleQuery.evaluate();
                return new RepositoryResult<Statement>(
                        new ExceptionConvertingIteration<Statement, RepositoryException>(
                                toStatementIteration(qRes, subj, pred, obj)) {
                            @Override
                            protected RepositoryException convert(Exception e) {
                                return new RepositoryException(e);
                            }
                        });
            } else if (subj != null && pred != null && obj != null) {
                if (hasStatement(subj, pred, obj, includeInferred)) {
                    Statement st = new StatementImpl(subj, pred, obj);
                    CloseableIteration<Statement, RepositoryException> cursor;
                    cursor = new SingletonIteration<Statement, RepositoryException>(st);
                    return new RepositoryResult<Statement>(cursor);
                } else {
                    return new RepositoryResult<Statement>(new EmptyIteration<Statement, RepositoryException>());
                }
            }

            GraphQuery query = prepareGraphQuery(EVERYTHING);
            setBindings(query, subj, pred, obj);
            GraphQueryResult result = query.evaluate();
            return new RepositoryResult<Statement>(
                    new ExceptionConvertingIteration<Statement, RepositoryException>(result) {

                        @Override
                        protected RepositoryException convert(Exception e) {
                            return new RepositoryException(e);
                        }
                    });
        } catch (MalformedQueryException e) {
            throw new RepositoryException(e);
        } catch (QueryEvaluationException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * returns statements from supplied context
     *
     * TBD - should share code path with above getStatements
     *
     * @param subj
     * @param pred
     * @param obj
     * @param includeInferred
     * @param contexts
     * @return RepositoryResult<Statement>
     * @throws RepositoryException
     */
    @Override
    public RepositoryResult<Statement> getStatements(Resource subj, URI pred, Value obj, boolean includeInferred, Resource... contexts) throws RepositoryException {
        if (contexts == null) {
            contexts = new Resource[] { null };
        }
        try {
            if (isQuadMode()) {
                StringBuffer sb = new StringBuffer();
                sb.append("SELECT * WHERE { GRAPH ?ctx { ?s ?p ?o } filter (?ctx = (");
                boolean first = true;
                for (Resource context : contexts) {
                    if (first) {
                        first = !first;
                    }
                    else {
                        sb.append(",");
                    }
                    if (context == null) {
                        sb.append("IRI(\"http://marklogic.com/semantics#default-graph\")");
                    } else {
                        sb.append("IRI(\"" + context.toString() + "\")");
                    }
                }
                sb.append(") ) }");
                TupleQuery tupleQuery = prepareTupleQuery(sb.toString());
                tupleQuery.setIncludeInferred(includeInferred);
                setBindings(tupleQuery, subj, pred, obj, (Resource) null);
                TupleQueryResult qRes = tupleQuery.evaluate();
                return new RepositoryResult<Statement>(
                        new ExceptionConvertingIteration<Statement, RepositoryException>(
                                toStatementIteration(qRes, subj, pred, obj)) {
                            @Override
                            protected RepositoryException convert(Exception e) {
                                return new RepositoryException(e);
                            }
                        });
            } else if (subj != null && pred != null && obj != null) {
                if (hasStatement(subj, pred, obj, includeInferred, contexts)) {
                    Statement st = new StatementImpl(subj, pred, obj);
                    CloseableIteration<Statement, RepositoryException> cursor;
                    cursor = new SingletonIteration<Statement, RepositoryException>(st);
                    return new RepositoryResult<Statement>(cursor);
                } else {
                    return new RepositoryResult<Statement>(new EmptyIteration<Statement, RepositoryException>());
                }
            }
            else {
                GraphQuery query = prepareGraphQuery(EVERYTHING);
                setBindings(query, subj, pred, obj, contexts);
                GraphQueryResult result = query.evaluate();
                return new RepositoryResult<Statement>(
                        new ExceptionConvertingIteration<Statement, RepositoryException>(result) {
    
                            @Override
                            protected RepositoryException convert(Exception e) {
                                return new RepositoryException(e);
                            }
                        });
            }
        } catch (MalformedQueryException e) {
            throw new RepositoryException(e);
        } catch (QueryEvaluationException e) {
            throw new RepositoryException(e);
        }
    }

    // all statements

    /**
     * returns true or false if a statement exists in repository / context
     *
     * @param st
     * @param includeInferred
     * @param contexts
     * @return boolean
     * @throws RepositoryException
     */
    @Override
    public boolean hasStatement(Statement st, boolean includeInferred, Resource... contexts) throws RepositoryException {
        return hasStatement(st.getSubject(),st.getPredicate(),st.getObject(),includeInferred,contexts);
    }

    /**
     * returns true or false if a statement exists in repository / context
     *
     * TBD- should refactor
     *
     * @param subject
     * @param predicate
     * @param object
     * @param includeInferred
     * @param contexts
     * @return boolean
     * @throws RepositoryException
     */
    @Override
    public boolean hasStatement(Resource subject, URI predicate, Value object, boolean includeInferred, Resource... contexts) throws RepositoryException {
        String queryString = null;
        if (contexts == null || contexts.length == 0) {
            queryString = SOMETHING;
        }
        else {
            
            StringBuilder sb= new StringBuilder();
            //if (baseURI != null) sb.append("BASE <" + baseURI + ">\n");
            sb.append("ASK { GRAPH ?ctx { ?s ?p ?o } filter (?ctx = (");
                boolean first = true;
                for (Resource context : contexts) {
                    if (first) {
                        first = !first;
                    }
                    else {
                        sb.append(",");
                    }
                    if (context == null) {
                        sb.append("IRI(\"http://marklogic.com/semantics#default-graph\")");
                    } else {
                        sb.append("IRI(\"" + context.toString() + "\")");
                    }
                }
                sb.append(") ) }");
            queryString = sb.toString();
        }
        try {
            BooleanQuery query = prepareBooleanQuery(queryString); // baseuri ?

            setBindings(query, subject, predicate, object, contexts);
            return query.evaluate();
        }
        catch (MalformedQueryException e) {
            throw new RepositoryException(e);
        }
        catch (QueryEvaluationException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * exports statements via RDFHandler
     *
     * TBD- should refactor
     *
     * @param subject
     * @param predicate
     * @param object
     * @param includeInferred
     * @param handler
     * @param contexts
     * @throws RepositoryException
     * @throws RDFHandlerException
     */
    @Override
    public void exportStatements(Resource subject, URI predicate, Value object, boolean includeInferred, RDFHandler handler, Resource... contexts) throws RepositoryException, RDFHandlerException {
        try {
            StringBuilder ob = new StringBuilder();
            StringBuilder sb = new StringBuilder();

            if(object != null && object instanceof Literal) {
                if (object instanceof Literal) {
                    Literal lit = (Literal) object;
                    ob.append("\"");
                    ob.append(SPARQLUtil.encodeString(lit.getLabel()));
                    ob.append("\"");
                    ob.append("^^<" + lit.getDatatype().stringValue() + ">");
                    ob.append(" ");
                } else {
                    ob.append("<" + object.stringValue() + "> ");
                }
                    //if (baseURI != null) sb.append("BASE <" + baseURI + ">\n");
                sb.append("CONSTRUCT {?s ?p "+ob.toString()+"} WHERE {");
                if(notNull(contexts) && contexts.length>0) {
                    for (int i = 0; i < contexts.length; i++) {
                        if(notNull(contexts[i])) {
                            sb.append("GRAPH <" + contexts[i].stringValue() + "> {?s ?p "+ob.toString()+" .} ");
                        }else{
                            sb.append("GRAPH <"+DEFAULT_GRAPH_URI+"> {?s ?p "+ob.toString()+" .}");
                        }
                    }
                    sb.append("}");
                }else{
                    sb.append("?s ?p "+ob.toString()+" }");
                }
            }else{
                sb.append("CONSTRUCT {?s ?p ?o} WHERE {");
                if(notNull(contexts) && contexts.length>0) {
                    for (int i = 0; i < contexts.length; i++) {
                        if(contexts[i] != null) {
                            sb.append("GRAPH <" + contexts[i].stringValue() + "> {?s ?p "+ob.toString()+" .} ");
                        }else{
                            sb.append("GRAPH <"+DEFAULT_GRAPH_URI+"> {?s ?p "+ob.toString()+" .}");
                        }                    }
                    sb.append("}");
                }else{
                    sb.append("?s ?p "+ob.toString()+" }");
                }
            }

            GraphQuery query = prepareGraphQuery(sb.toString());
            setBindings(query, subject, predicate, object, contexts);
            query.evaluate(handler);
        }
        catch (MalformedQueryException e) {
            throw new RepositoryException(e);
        }
        catch (QueryEvaluationException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * exports statements via RDFHandler
     *
     * TBD- should refactor
     *
     * @param handler
     * @param contexts
     * @throws RepositoryException
     * @throws RDFHandlerException
     */
    @Override
    public void export(RDFHandler handler, Resource... contexts) throws RepositoryException, RDFHandlerException {
        exportStatements(null, null, null, true, handler);
    }


    /**
     * returns number of triples in the entire triple store
     *
     *
     * @return long
     */
    @Override
    public long size(){
        try {
            TupleQuery tupleQuery = prepareTupleQuery(COUNT_EVERYTHING);
            tupleQuery.setIncludeInferred(false);
            TupleQueryResult qRes = tupleQuery.evaluate();
            // just one answer
            BindingSet result = qRes.next();
            long resultValue = ((Literal) result.getBinding("ct").getValue()).longValue();
            return resultValue;
        } catch (RepositoryException | MalformedQueryException e) {
            e.printStackTrace();
        } catch (QueryEvaluationException e) {
            e.printStackTrace();
        }
        return 0;
    }
    
    /**
     * returns number of triples in supplied context
     *
     *
     * @param contexts
     * @return long
     * @throws RepositoryException
     * @throws MalformedQueryException
     */
    @Override
    public long size(Resource... contexts)  {
        try {
            StringBuffer sb = new StringBuffer();
            sb.append("SELECT (count(?s) as ?ct) where { GRAPH ?g { ?s ?p ?o }");
            boolean first = true;
            // with no args, measure the whole triple store.
            if (contexts != null && contexts.length > 0) {
                sb.append("filter (?g = (");
                for (Resource context : contexts) {
                    if (first) {
                        first = !first;
                    }
                    else {
                        sb.append(",");
                    }
                    if (context == null) {
                        sb.append("IRI(\""+DEFAULT_GRAPH_URI+"\")");
                    } else {
                        sb.append("IRI(\"" + context.toString() + "\")");
                    }
                }
                sb.append(") )");
            }
            sb.append("}");
            TupleQuery tupleQuery = prepareTupleQuery(sb.toString());
            tupleQuery.setIncludeInferred(false);
            TupleQueryResult qRes = tupleQuery.evaluate();
            // just one answer
            BindingSet result = qRes.next();
            long resultValue = ((Literal) result.getBinding("ct").getValue()).longValue();
            // if 'null' was one or more of the arguments, then totalSize will be non-zero.
            return resultValue;
        } catch (RepositoryException | MalformedQueryException e) {
            e.printStackTrace();
        } catch (QueryEvaluationException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * clears all triples from repository
     *
     * @throws RepositoryException
     */
    @Override
    public void clear() throws RepositoryException{
        this.client.sendClearAll();
    }

    /**
     * clears triples in repository context
     *
     * @param contexts
     * @throws RepositoryException
     */
    @Override
    public void clear(Resource... contexts) throws RepositoryException {
            this.client.sendClear(contexts);
    }

    /**
     * returns true or false if the repository is empty or not
     *
     * @return boolean
     * @throws RepositoryException
     */
    @Override
    public boolean isEmpty() throws RepositoryException {
        return size() == 0;
    }

    /**
     * returns true if a transaction is open and active
     *
     * @return boolean
     * @throws UnknownTransactionStateException
     * @throws RepositoryException
     */
    @Override
    public boolean isActive() throws UnknownTransactionStateException, RepositoryException {
        return this.client.isActiveTransaction();
    }

    /**
     * sets transaction isolationlevel
     *
     * @return level
     */
    @Override
    public IsolationLevel getIsolationLevel() {
        return IsolationLevels.SNAPSHOT;
    }

    /**
     * sets transaction isolationlevel
     *
     * @param level
     * @throws IllegalStateException
     */
    @Override
    public void setIsolationLevel(IsolationLevel level) throws IllegalStateException {
        if(level != IsolationLevels.SNAPSHOT){
         throw new IllegalStateException();
        }
    }

    /**
     * opens a new transaction
     *
     * @throws RepositoryException
     */
    @Override
    public void begin() throws RepositoryException {
        this.client.openTransaction();
    }

    /**
     * opens a new transaction
     *
     * @param level
     * @throws RepositoryException
     */
    @Override
    public void begin(IsolationLevel level) throws RepositoryException {
        setIsolationLevel(level);
        begin();
    }

    /**
     * commits a transaction
     *
     * @throws RepositoryException
     */
    @Override
    public void commit() throws RepositoryException {
        this.client.commitTransaction();
    }

    /**
     * rollbacks an open transaction
     *
     * @throws RepositoryException
     */
    @Override
    public void rollback() throws RepositoryException {
        this.client.rollbackTransaction();
    }

    /**
     * add triples
     *
     * @param in
     * @param baseURI
     * @param dataFormat
     * @param contexts
     * @throws IOException
     * @throws RDFParseException
     * @throws RepositoryException
     */
    @Override
    public void add(InputStream in, String baseURI, RDFFormat dataFormat, Resource... contexts) throws IOException, RDFParseException, RepositoryException {
        this.client.sendAdd(in, baseURI, dataFormat, contexts);
    }

    /**
     * add triples
     *
     * will use file uri as base URI if none supplied
     *
     * @param file
     * @param baseURI
     * @param dataFormat
     * @param contexts
     * @throws IOException
     * @throws RDFParseException
     * @throws RepositoryException
     */
    @Override
    public void add(File file, String baseURI, RDFFormat dataFormat, Resource... contexts) throws IOException, RDFParseException, RepositoryException {
        if(notNull(baseURI)) {
            this.client.sendAdd(file, baseURI, dataFormat, contexts);
        }else{
            this.client.sendAdd(file, file.toURI().toString(), dataFormat, contexts);
        }
    }

    /**
     * add triples
     *
     * @param reader
     * @param baseURI
     * @param dataFormat
     * @param contexts
     * @throws IOException
     * @throws RDFParseException
     * @throws RepositoryException
     */
    @Override
    public void add(Reader reader, String baseURI, RDFFormat dataFormat, Resource... contexts) throws IOException, RDFParseException, RepositoryException {
        this.client.sendAdd(reader,baseURI,dataFormat,contexts);
    }

    /**
     * add triples
     *
     * sets base URI to url if none is supplied
     *
     * @param url
     * @param baseURI
     * @param dataFormat
     * @param contexts
     * @throws IOException
     * @throws RDFParseException
     * @throws RepositoryException
     */
    @Override
    public void add(URL url, String baseURI, RDFFormat dataFormat, Resource... contexts) throws IOException, RDFParseException, RepositoryException {
        InputStream in = new URL(url.toString()).openStream(); //TBD- naive impl, will need refactoring
        if(notNull(baseURI)) {
            this.client.sendAdd(in, baseURI, dataFormat, contexts);
        }else{
            this.client.sendAdd(in, url.toString(), dataFormat, contexts);
        }
    }

    /**
     * add single statement of triples
     *
     * @param subject
     * @param predicate
     * @param object
     * @param contexts
     * @throws RepositoryException
     */
    @Override
    public void add(Resource subject, URI predicate, Value object, Resource... contexts) throws RepositoryException {
        this.client.sendAdd(null, subject, predicate, object, contexts);
    }

    /**
     * add triples
     *
     * @param st
     * @param contexts
     * @throws RepositoryException
     */
    @Override
    public void add(Statement st, Resource... contexts) throws RepositoryException {
        add(st.getSubject(), st.getPredicate(), st.getObject(), mergeResource(st.getContext(), contexts));
    }

    /**
     * add triples
     *
     * TBD - will use write cache
     *
     * @param statements
     * @param contexts
     * @throws RepositoryException
     */
    @Override
    public void add(Iterable<? extends Statement> statements, Resource... contexts) throws RepositoryException {
        Iterator <? extends Statement> iter = statements.iterator();
        while(iter.hasNext()){
            Statement st = iter.next();
            add(st.getSubject(), st.getPredicate(), st.getObject(), mergeResource(st.getContext(), contexts));
        }
    }

    /**
     * add triples
     *
     * TBD- will use write cache
     *
     * @param statements
     * @param contexts
     * @param <E>
     * @throws RepositoryException
     * @throws E
     */
    @Override
    public <E extends Exception> void add(Iteration<? extends Statement, E> statements, Resource... contexts) throws RepositoryException, E {
        while(statements.hasNext()){
            Statement st = statements.next();
            add(st.getSubject(), st.getPredicate(), st.getObject(), mergeResource(st.getContext(), contexts));
        }
    }


    /**
     * remove triples
     *
     * @param subject
     * @param predicate
     * @param object
     * @param contexts
     * @throws RepositoryException
     */
    @Override
    public void remove(Resource subject, URI predicate, Value object, Resource... contexts) throws RepositoryException {
        this.client.sendRemove(null, subject, predicate, object, contexts);
    }

    /**
     * remove triples
     *
     * @param st
     * @param contexts
     * @throws RepositoryException
     */
    @Override
    public void remove(Statement st, Resource... contexts) throws RepositoryException {
        this.client.sendRemove(null,st.getSubject(),st.getPredicate(),st.getObject(),mergeResource(st.getContext(),contexts));
    }

    /**
     * remove triples
     *
     * TBD- will use write cache
     *
     * @param statements
     * @throws RepositoryException
     */
    @Override
    public void remove(Iterable<? extends Statement> statements) throws RepositoryException {
        Iterator <? extends Statement> iter = statements.iterator();
        while(iter.hasNext()){
            Statement st = iter.next();
            this.client.sendRemove(null, st.getSubject(), st.getPredicate(), st.getObject());
        }
    }

    /**
     * remove triples
     *
     * TBD -will use write cache
     *
     * @param statements
     * @param contexts
     * @throws RepositoryException
     */
    @Override
    public void remove(Iterable<? extends Statement> statements, Resource... contexts) throws RepositoryException {
        Iterator <? extends Statement> iter = statements.iterator();
        while(iter.hasNext()){
            Statement st = iter.next();
            this.client.sendRemove(null, st.getSubject(), st.getPredicate(), st.getObject(), mergeResource(st.getContext(),contexts));
        }
    }

    /**
     * remove triples
     *
     * TBD - will use write cache
     *
     * @param statements
     * @param <E>
     * @throws RepositoryException
     * @throws E
     */
    @Override
    public <E extends Exception> void remove(Iteration<? extends Statement, E> statements) throws RepositoryException, E {
        while(statements.hasNext()){
            Statement st = statements.next();
            this.client.sendRemove(null, st.getSubject(), st.getPredicate(), st.getObject());
        }
    }

    /**
     * remove triples
     *
     * TBD -will use write cache
     *
     * @param statements
     * @param contexts
     * @param <E>
     * @throws RepositoryException
     * @throws E
     */
    @Override
    public <E extends Exception> void remove(Iteration<? extends Statement, E> statements, Resource... contexts) throws RepositoryException, E {
        while(statements.hasNext()){
            Statement st = statements.next();
            this.client.sendRemove(null, st.getSubject(), st.getPredicate(), st.getObject(), mergeResource(st.getContext(),contexts));
        }
    }

    /**
     * add without commit
     *
     * supplied to honor interface
     *
     * @param subject
     * @param predicate
     * @param object
     * @param contexts
     * @throws RepositoryException
     */
    @Override
    protected void addWithoutCommit(Resource subject, URI predicate, Value object, Resource... contexts) throws RepositoryException {
        add(subject, predicate, object, contexts);
    }

    /**
     * remove without commit
     *
     * supplied to honor interface
     *
     * @param subject
     * @param predicate
     * @param object
     * @param contexts
     * @throws RepositoryException
     */
    @Override
    protected void removeWithoutCommit(Resource subject, URI predicate, Value object, Resource... contexts) throws RepositoryException {
        remove(subject, predicate, object, contexts);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // not in scope for 1.0.0 /////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     *
     * supplied to honor interface
     *
     * @return
     * @throws RepositoryException
     */
    @Override
    public RepositoryResult<Namespace> getNamespaces() throws RepositoryException {
        return null;
    }

    /**
     *
     * supplied to honor interface
     *
     * @param prefix
     * @return
     * @throws RepositoryException
     */
    @Override
    public String getNamespace(String prefix) throws RepositoryException {
        return null;
    }

    /**
     *
     * supplied to honor interface
     *
     * @param prefix
     * @param name
     * @throws RepositoryException
     */
    @Override
    public void setNamespace(String prefix, String name) throws RepositoryException {
    }

    /**
     *
     * supplied to honor interface
     *
     * @param prefix
     * @throws RepositoryException
     */
    @Override
    public void removeNamespace(String prefix) throws RepositoryException {
    }

    /**
     *
     * supplied to honor interface
     *
     * @throws RepositoryException
     */
    @Override
    public void clearNamespaces() throws RepositoryException {
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////



    ///////////////////////////////////////////////////////////////////////////////////////////////
    // private ////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * set bindings ?s, ?p and special handling of Value ?o (and ?ctx)
     *
     * @param query
     * @param subj
     * @param pred
     * @param obj
     * @param contexts
     * @throws RepositoryException
     */
    private void setBindings(Query query, Resource subj, URI pred, Value obj, Resource... contexts)
            throws RepositoryException {
        if (subj != null) {
            query.setBinding("s", subj);
        }
        if (pred != null && pred instanceof URI) {
            query.setBinding("p", pred);
        }
        if (obj != null) {
            query.setBinding("o", obj);
        }
        if (contexts != null && contexts.length > 0) {
            DatasetImpl dataset = new DatasetImpl();
            if(notNull(contexts)){
            for (int i = 0; i < contexts.length; i++) {
                if (notNull(contexts[i]) || contexts[i] instanceof URI) {
                    dataset.addDefaultGraph((URI) contexts[i]);
                } else {
                    dataset.addDefaultGraph(getValueFactory().createURI(DEFAULT_GRAPH_URI));
                }
            }
            }else{
                dataset.addDefaultGraph(getValueFactory().createURI(DEFAULT_GRAPH_URI));
            }
            query.setDataset(dataset);
        }
    }

    /**
     * return if quadMode is enabled or not (should be)
     *
     * @return
     */
    private boolean isQuadMode() {
        return quadMode;
    }

    /**
     * private utility for merging Resource varargs
     *
     * @param o
     * @param arr
     * @return
     */
    private static Resource[] mergeResource(Resource o, Resource... arr) {
        if(o != null) {
            Resource[] newArray = new Resource[arr.length + 1];
            newArray[0] = o;
            System.arraycopy(arr, 0, newArray, 1, arr.length);
            return newArray;
        }else{
            return arr;
        }

    }

    /**
     * convert bindings
     *
     * @param iter
     * @param subj
     * @param pred
     * @param obj
     * @return iterator
     */
    private Iteration<Statement, QueryEvaluationException> toStatementIteration(TupleQueryResult iter, final Resource subj, final URI pred, final Value obj) {
        return new ConvertingIteration<BindingSet, Statement, QueryEvaluationException>(iter) {
            @Override
            protected Statement convert(BindingSet b) throws QueryEvaluationException {
                Resource s = subj==null ? (Resource)b.getValue("s") : subj;
                URI p = pred==null ? (URI)b.getValue("p") : pred;
                Value o = obj==null ? b.getValue("o") : obj;
                URI ctx = (URI)b.getValue("ctx");
                if (ctx.stringValue().equals(DEFAULT_GRAPH_URI)) {
                    ctx = null;
                }
                return getValueFactory().createStatement(s, p, o, ctx);
            }
        };
    }

    /**
     * private utility method that tests if an object is null
     *
     * TBD -
     * @param item
     * @return boolean
     */
    private Boolean notNull(Object item) {
        if (item!=null)
            return true;
        else
            return false;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

}
