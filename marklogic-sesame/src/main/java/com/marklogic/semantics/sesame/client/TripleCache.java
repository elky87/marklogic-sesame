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
 * A timer that flushes a cache of triple add statements
 * periodically. The cache is represented as a Model.
 */
package com.marklogic.semantics.sesame.client;

import com.marklogic.semantics.sesame.MarkLogicSesameException;
import org.openrdf.model.Model;
import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.LinkedHashModel;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.UpdateExecutionException;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.io.IOException;
import java.util.Date;
import java.util.TimerTask;

/**
 * Timer implements write cache for efficient adding of triples
 *
 * @author James Fuller
 */
public abstract class TripleCache extends TimerTask {

    private static final Logger log = LoggerFactory.getLogger(TripleCache.class);

    protected Model cache;
    protected MarkLogicClient client;

    public static final long DEFAULT_CACHE_SIZE = 750;

    public static final long DEFAULT_CACHE_MILLIS = 800;
    public static final long DEFAULT_INITIAL_DELAY = 50;

    protected RDFFormat format = RDFFormat.NQUADS;

    protected long cacheSize;

    protected long cacheMillis;

    protected Date lastCacheAccess = new Date();

    /**
     * constructor
     *
     * @param client
     */
    public TripleCache(MarkLogicClient client) {
        super();
        this.client = client;
        this.cache = new LinkedHashModel();
        this.cacheSize = DEFAULT_CACHE_SIZE;
        this.cacheMillis = DEFAULT_CACHE_MILLIS;
    }

    public TripleCache(MarkLogicClient client, long cacheSize) {
        super();
        this.client = client;
        this.cache = new LinkedHashModel();
        setCacheSize(cacheSize);
    }

    /**
     * return cacheSize
     *
     * @return
     */
    public long getCacheSize() {
        return this.cacheSize;
    }

    /**
     *  set cacheSize
     *
     * @param cacheSize
     */
    public void setCacheSize(long cacheSize) {
        this.cacheSize = cacheSize;
    }

    /**
     * getter cacheMillis
     *
     * @return
     */
    public long getCacheMillis() {
        return cacheMillis;
    }

    /**
     * setter cacheMillis
     *
     * @param cacheMillis
     */
    public void setCacheMillis(long cacheMillis) {
        this.cacheMillis = cacheMillis;
    }

    /**
     * tests to see if we should flush cache
     *
     */
    @Override
    public synchronized void run(){
        Date now = new Date();
        if ( !cache.isEmpty() &&
                ((cache.size() > cacheSize - 1) || (now.getTime() - lastCacheAccess.getTime() > cacheMillis))) {
            try {
                flush();
            } catch (RepositoryException e) {
                e.printStackTrace();
            } catch (MalformedQueryException e) {
                e.printStackTrace();
            } catch (UpdateExecutionException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

//    /**
//     * flushes the cache, writing triples as graph
//     *
//     * @throws MarkLogicSesameException
//     */
//    private void flush1() throws MarkLogicSesameException, InterruptedException {
//        log.debug("flushing write cache:" + this.cache.size());
//        try {
//            ByteArrayOutputStream out = new ByteArrayOutputStream();
//            Rio.write(this.cache, out, this.format);
//            this.client.sendAdd(new ByteArrayInputStream(out.toByteArray()), null, this.format);
//            this.lastCacheAccess = new Date();
//            this.cache.clear();
//        } catch (RDFHandlerException | RDFParseException e) {
//            log.info(e.getLocalizedMessage());
//            throw new MarkLogicSesameException(e);
//        }
//    }

    protected abstract void flush() throws RepositoryException, MalformedQueryException, UpdateExecutionException, IOException;

    /**min
     * forces the cache to flush if there is anything in it
     *
     * @throws MarkLogicSesameException
     */
    public void forceRun() throws MarkLogicSesameException {
        log.debug(String.valueOf(cache.size()));
        if( !cache.isEmpty()) {
            try {
                flush();
            } catch (RepositoryException e) {
                e.printStackTrace();
            } catch (MalformedQueryException e) {
                e.printStackTrace();
            } catch (UpdateExecutionException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * add triple to cache Model
     *
     * @param subject
     * @param predicate
     * @param object
     * @param contexts
     */
    public synchronized void add(Resource subject, URI predicate, Value object, Resource... contexts) throws MarkLogicSesameException {
        cache.add(subject,predicate,object,contexts);
        if( cache.size() > cacheSize - 1){
            forceRun();
        }
    }

}