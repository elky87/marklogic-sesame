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

package com.marklogic.semantics.sesame.client;

import com.marklogic.semantics.sesame.MarkLogicSesameException;
import org.openrdf.model.*;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.UpdateExecutionException;
import org.openrdf.query.parser.sparql.SPARQLUtil;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sparql.query.SPARQLQueryBindingSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by jfuller on 11/2/16.
 */
public class TripleDeleteCache extends TripleCache {

    private static final Logger log = LoggerFactory.getLogger(TripleDeleteCache.class);

    public TripleDeleteCache(MarkLogicClient client) {
        super(client);
    }

    public TripleDeleteCache(MarkLogicClient client, long cacheSize) {
        super(client, cacheSize);
    }
    /**
     * flushes the cache, writing triples as graph
     *
     * @throws MarkLogicSesameException
     */
    
    protected synchronized void flush() throws RepositoryException, MalformedQueryException, UpdateExecutionException, IOException {
        if (cache.isEmpty()) { return; }
        StringBuffer entireQuery = new StringBuffer();
        SPARQLQueryBindingSet bindingSet = new SPARQLQueryBindingSet();

        for (Namespace ns :cache.getNamespaces()){
            entireQuery.append("PREFIX "+ns.getPrefix()+": <"+ns.getName()+">. ");
        }
        entireQuery.append("DELETE DATA { ");

        Set<Resource> distinctCtx = new HashSet<Resource>();
        for (Resource context :cache.contexts()) {
            distinctCtx.add(context);
        }

        for (Resource ctx : distinctCtx) {
               if (ctx != null) {
                   entireQuery.append(" GRAPH <" + ctx + "> { ");
               }
                for (Statement stmt : cache.filter(null, null, null, ctx)) {
                    entireQuery.append("<" + stmt.getSubject().stringValue() + "> ");
                    entireQuery.append("<" + stmt.getPredicate().stringValue() + "> ");
                    Value object=stmt.getObject();
                    if (object instanceof Literal) {
                        Literal lit = (Literal) object;
                        entireQuery.append("\"");
                        entireQuery.append(SPARQLUtil.encodeString(lit.getLabel()));
                        entireQuery.append("\"");
                        if(null == lit.getLanguage()) {
                            entireQuery.append("^^<" + lit.getDatatype().stringValue() + ">");
                        }else{
                            entireQuery.append("@" + lit.getLanguage().toString());
                        }
                    } else {
                        entireQuery.append("<" + object.stringValue() + "> ");
                    }
                    entireQuery.append(".");
                }
                if (ctx != null) {
                    entireQuery.append(" }");
                }
        }

        entireQuery.append("} ");
        log.info(entireQuery.toString());
        client.sendUpdateQuery(entireQuery.toString(),bindingSet,false,null);
        lastCacheAccess = new Date();
        //log.info("success writing cache: {}",String.valueOf(cache.size()));
        cache.clear();

    }


}
