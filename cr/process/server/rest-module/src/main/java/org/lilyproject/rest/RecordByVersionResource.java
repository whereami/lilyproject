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
package org.lilyproject.rest;

import org.lilyproject.repository.api.*;
import org.lilyproject.tools.import_.core.ImportMode;
import org.lilyproject.tools.import_.core.ImportResult;
import org.lilyproject.tools.import_.core.ImportResultType;
import org.lilyproject.tools.import_.core.RecordImport;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

@Path("record/{id}/version/{version:\\d+}")
public class RecordByVersionResource extends RepositoryEnabled {
    @GET
    @Produces("application/json")
    public Record get(@PathParam("id") String id, @PathParam("version") Long version) {
        RecordId recordId = repository.getIdGenerator().fromString(id);
        try {
            return repository.read(recordId, version);
        } catch (RecordNotFoundException e) {
            throw new ResourceException(e, NOT_FOUND.getStatusCode());
        } catch (VersionNotFoundException e) {
            throw new ResourceException(e, NOT_FOUND.getStatusCode());
        } catch (Exception e) {
            throw new ResourceException("Error loading record.", e, INTERNAL_SERVER_ERROR.getStatusCode());
        }
    }

    @PUT
    @Produces("application/json")
    @Consumes("application/json")
    public Response put(@PathParam("id") String id, @PathParam("version") Long version, Record record) {
        RecordId recordId = repository.getIdGenerator().fromString(id);

        if (record.getId() != null && !record.getId().equals(recordId)) {
            throw new ResourceException("Record id in submitted record does not match record id in URI.",
                    BAD_REQUEST.getStatusCode());
        }

        if (record.getVersion() != null && !record.getVersion().equals(version)) {
            throw new ResourceException("Version in submitted record does not match version in URI.",
                    BAD_REQUEST.getStatusCode());
        }

        record.setId(recordId);
        record.setVersion(version);

        try {
            boolean useLatestRecordType = record.getRecordTypeName() == null || record.getRecordTypeVersion() == null;
            record = repository.update(record, true, useLatestRecordType);
        } catch (RecordNotFoundException e) {
            throw new ResourceException("Record not found: " + recordId, NOT_FOUND.getStatusCode());
        } catch (VersionNotFoundException e) {
            throw new ResourceException("Record '" + recordId + "', version " + record.getVersion() + " not found.",
                    NOT_FOUND.getStatusCode());
        } catch (Exception e) {
            throw new ResourceException("Error updating versioned-mutable scope of record with id " + id, e,
                    INTERNAL_SERVER_ERROR.getStatusCode());
        }

        // TODO record we respond with should be full record or be limited to user-specified field list
        Response response = Response.ok(record).build();

        return response;
    }
}
