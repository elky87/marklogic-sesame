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

import org.openrdf.http.client.BackgroundGraphResult;
import org.openrdf.http.client.QueueCursor;
import org.openrdf.model.Statement;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.rio.RDFParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * wrapper on Sesame BackgroundGraphResult
 *
 * @author James Fuller
 */

class MarkLogicBackgroundGraphResult extends BackgroundGraphResult
{
    private static final Logger logger = LoggerFactory.getLogger(MarkLogicBackgroundGraphResult.class);

    /**
     *  constructor
     *
     * @param parser
     * @param in
     * @param charset
     * @param baseURI
     */
    public MarkLogicBackgroundGraphResult(RDFParser parser, InputStream in, Charset charset, String baseURI) {
        super(parser, in, charset, baseURI);
    }

    /**
     * constructor
     *
     * @param queue
     * @param parser
     * @param in
     * @param charset
     * @param baseURI
     */
    public MarkLogicBackgroundGraphResult(QueueCursor<Statement> queue, RDFParser parser, InputStream in, Charset charset, String baseURI) {
        super(queue, parser, in, charset, baseURI);
    }

    /**
     * wrap exception to return false instead of throwing error, debug log
     *
     */
    @Override
    public boolean hasNext()
        throws QueryEvaluationException
    {
        try {
            return super.hasNext();

        }catch(QueryEvaluationException e){
            logger.info("MarkLogicBackgroundGraphResult hasNext() stream closed");
            return false;
        }
    }

    /**
     * wrap exception, debug log
     *
     */
    @Override
    protected void handleClose() throws QueryEvaluationException {
        try {
            super.handleClose();
        }catch(Exception e){
            logger.error("MarkLogicBackgroundGraphResult handleClose() stream closed exception",e);
            throw new QueryEvaluationException(e);
        }
    }

}
