/*
 * Copyright 2010 Outerthought bvba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lilyproject.repository.api;

import java.util.HashMap;
import java.util.Map;

public class BlobNotFoundException extends BlobException {
    private String blob = null;
    private String info = null;
    
    public BlobNotFoundException(String message, Map<String, String> state) {
        this.blob = state.get("blob");
        this.info = state.get("info");
    }
    
    @Override
    public Map<String, String> getState() {
        Map<String, String> state = new HashMap<String, String>();
        state.put("blob", blob);
        state.put("info", info);
        return state;
    }
    
    public BlobNotFoundException(Blob blob, String info, Throwable cause) {
        super(cause);
        this.blob = (blob != null) ? blob.toString() : null;
        this.info = info;
    }
    
    public BlobNotFoundException(Blob blob, String info) {
        this.blob = (blob != null) ? blob.toString() : null;
        this.info = info;
    }
    
    public BlobNotFoundException(String info) {
        this.info = info;
    }
    
    public BlobNotFoundException(String info, Throwable cause) {
        super(cause);
        this.info = info;
    }
    
    @Override
    public String getMessage() {
        if (blob != null) {
            return "Blob '" + blob + "' could not be found: " + info;
        } else {
            return "Blob could not be found: " + info;
        }
    }
}
