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

import org.openrdf.repository.RepositoryException;

/**
 * specific exception for throwing MarkLogic related errors
 *
 * @author James Fuller
 */
@SuppressWarnings("serial")
public class MarkLogicSesameException extends RepositoryException {

    public MarkLogicSesameException(String message) {
        super(message);
    }

    public MarkLogicSesameException(Exception e) {
        super(e);
    }

    public MarkLogicSesameException(String message, Exception e) {
        super(message,e);
    }
}