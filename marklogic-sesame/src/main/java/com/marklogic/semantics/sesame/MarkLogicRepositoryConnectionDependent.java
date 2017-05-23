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

import com.marklogic.client.semantics.GraphPermissions;
import com.marklogic.client.query.QueryDefinition;
import com.marklogic.client.semantics.SPARQLRuleset;
import org.eclipse.rdf4j.common.iteration.Iteration;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.repository.RepositoryException;

/**
 * interface defining MarkLogic semantic features
 *
 * @author James Fuller
 */
interface MarkLogicRepositoryConnectionDependent {

    Query prepareQuery(String queryString) throws RepositoryException, MalformedQueryException;
    Query prepareQuery(String queryString, String baseURI) throws RepositoryException, MalformedQueryException;

    TupleQuery prepareTupleQuery(String queryString) throws RepositoryException, MalformedQueryException;
    TupleQuery prepareTupleQuery(String queryString,String baseURI) throws RepositoryException, MalformedQueryException;

    Update prepareUpdate(String queryString) throws RepositoryException, MalformedQueryException;
    Update prepareUpdate(String queryString, String baseURI) throws RepositoryException, MalformedQueryException;

    BooleanQuery prepareBooleanQuery(String queryString) throws RepositoryException, MalformedQueryException;
    BooleanQuery prepareBooleanQuery(String queryString, String baseURI) throws RepositoryException, MalformedQueryException;

    GraphQuery prepareGraphQuery(String queryString) throws RepositoryException, MalformedQueryException;
    GraphQuery prepareGraphQuery(String queryString, String baseURI) throws RepositoryException, MalformedQueryException;

    void clear() throws RepositoryException;
    long size() throws RepositoryException;

    void remove(Iterable<? extends Statement> statements) throws RepositoryException;
    <E extends Exception> void remove(Iteration<? extends Statement, E> statements) throws RepositoryException, E;

    void setDefaultGraphPerms(GraphPermissions graphPerms);
    GraphPermissions getDefaultGraphPerms();

    void setDefaultConstrainingQueryDefinition(QueryDefinition defaultQueryDef);
    QueryDefinition getDefaultConstrainingQueryDefinition();

    void setDefaultRulesets(SPARQLRuleset... ruleset);
    SPARQLRuleset[] getDefaultRulesets();

    void sync() throws MarkLogicSesameException;

    void configureWriteCache(long initDelay, long delayCache, long cacheSize);

}
