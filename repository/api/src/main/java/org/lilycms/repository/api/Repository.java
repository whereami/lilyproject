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
package org.lilycms.repository.api;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import org.lilycms.repository.api.exception.BlobNotFoundException;
import org.lilycms.repository.api.exception.FieldTypeNotFoundException;
import org.lilycms.repository.api.exception.InvalidRecordException;
import org.lilycms.repository.api.exception.RecordExistsException;
import org.lilycms.repository.api.exception.RecordNotFoundException;
import org.lilycms.repository.api.exception.RecordTypeNotFoundException;
import org.lilycms.repository.api.exception.RepositoryException;

/**
 * Repository is the primary access point for accessing the functionality of the Lily repository.
 *
 * <p>Via Repository, you can perform all {@link Record}-related CRUD operations.
 */
public interface Repository {
    /**
     * Instantiates a new Record object.
     *
     * <p>This is only a factory method, nothing is created in the repository.
     */
    Record newRecord();

    /**
     * Instantiates a new Record object with the RecordId already filled in.
     *
     * <p>This is only a factory method, nothing is created in the repository.
     */
    Record newRecord(RecordId recordId);

    /**
     * Creates a new record in the repository.
     *
     * <p>A Record object can be instantiated via {@link #newRecord}.
     *
     * <p>If a recordId is given in {@link Record}, that id is used. If not, a new id is generated and available
     * from the returned Record object.
     *
     * @throws RecordExistsException
     *             if a record with the given recordId already exists
     * @throws RecordNotFoundException
     *             if the master record for a variant record does not exist
     * @throws InvalidRecordException
     *             if an empty record is being created
     * @throws FieldTypeNotFoundException
     * @throws RecordTypeNotFoundException
     */
    Record create(Record record) throws RecordExistsException, RecordNotFoundException, InvalidRecordException,
            RecordTypeNotFoundException, FieldTypeNotFoundException, RepositoryException;

    /**
     * Updates an existing record in the repository.
     *
     * <p>The provided Record object can either be obtained by reading a record via {@link #read} or
     * it can also be instantiated from scratch via {@link #newRecord}.
     *
     * <p>The Record object only needs to contain fields that actually need to be updated (though it might also
     * contain unchanged fields). Fields that are not present in the record will not be deleted, deleting fields
     * needs to be done explicitly by adding them to the list of fields to delete, see {@link Record#getFieldsToDelete}.
     *
     * <p>If the record contains any changed versioned fields, a new version will be created. The number of this
     * version will be available on the returned Record object.
     *
     * @throws RecordNotFoundException
     *             if the record does not exist
     * @throws InvalidRecordException
     *             if no update information is provided
     * @throws RepositoryException
     *             TBD
     * @throws FieldTypeNotFoundException
     * @throws RecordTypeNotFoundException
     */
    Record update(Record record) throws RecordNotFoundException, InvalidRecordException, RecordTypeNotFoundException,
            FieldTypeNotFoundException, RepositoryException;

    /**
     * Updates the version-mutable fields of an existing version.
     *
     * <p><b>TODO</b>: this is being considered for redesign.
     */
    Record updateMutableFields(Record record) throws InvalidRecordException, RecordNotFoundException,
            RecordTypeNotFoundException, FieldTypeNotFoundException, RepositoryException;

    /**
     * Reads a record fully. All the fields of the record will be read.
     *
     * <p>If the record has versions, it is the latest version that will be read.
     */
    Record read(RecordId recordId) throws RecordNotFoundException, RecordTypeNotFoundException,
            FieldTypeNotFoundException, RepositoryException;

    /**
     * Reads a record limited to a subset of the fields. Only the fields specified in the fieldNames list will be
     * included.
     *
     * <p>Versioned and versioned-mutable fields will be taken from the latest version.
     *
     * <p>It is not an error if the record would not have a particular field, though it is an error to specify
     * a non-existing field name.
     */
    Record read(RecordId recordId, List<QName> fieldNames) throws RecordNotFoundException, RecordTypeNotFoundException,
            FieldTypeNotFoundException, RepositoryException;

    /**
     * Reads a specific version of a record.
     */
    Record read(RecordId recordId, Long version) throws RecordNotFoundException, RecordTypeNotFoundException,
            FieldTypeNotFoundException, RepositoryException;

    /**
     * Reads a specific version of a record limited to a subset of the fields.
     */
    Record read(RecordId recordId, Long version, List<QName> fieldNames) throws RecordNotFoundException,
            RecordTypeNotFoundException, FieldTypeNotFoundException, RepositoryException;

    /**
     * Reads a Record and also returns the mapping from QNames to IDs.
     *
     * <p>See {@link IdRecord} for more information.
     *
     * @param version version to load. Optional, can be null.
     * @param fieldIds load only the fields with these ids. optional, can be null.
     */
    IdRecord readWithIds(RecordId recordId, Long version, List<String> fieldIds) throws RecordNotFoundException,
            RecordTypeNotFoundException, FieldTypeNotFoundException, RepositoryException;

    /**
     * Delete a {@link Record} from the repository.
     *
     * @param recordId
     *            id of the record to delete
     */
    void delete(RecordId recordId) throws RepositoryException;

    /**
     * Returns the IdGenerator service.
     */
    IdGenerator getIdGenerator();

    /**
     * Returns the TypeManager.
     */
    TypeManager getTypeManager();

    /**
     * A {@link BlobStoreAccess} must be registered with the repository before
     * it can be used. Any BlobStoreAccess that has ever been used to store
     * binary data of a blob must be registered before that data can be
     * retrieved again.
     *
     */
    void registerBlobStoreAccess(BlobStoreAccess blobStoreAccess);

    /**
     * Returns an {@link OutputStream} for a blob. The binary data of a blob
     * must be written to this outputStream and the stream must be closed before
     * the blob may be stored in a {@link Record}. The method
     * {@link Blob#setValue(byte[])} will be called internally to update the
     * blob with information that will make it possible to retrieve that data
     * again through {@link #getInputStream(Blob)}.
     *
     * <p>
     * The {@link BlobStoreAccessFactory} will decide to which underlying
     * blobstore the data will be written.
     *
     * @param blob
     *            the blob for which to open an OutputStream
     * @return an OutputStream
     * @throws RepositoryException when an unexpected exception occurs
     */
    OutputStream getOutputStream(Blob blob) throws RepositoryException;

    /**
     * Returns an {@link InputStream} from which the binary data of a blob can
     * be read. The value of blob is used to identify the underlying blobstore
     * and actual data to return through this InputStream, see {@link #getOutputStream(Blob)}.
     *
     * @param blob the blob for which to open an InputStream
     * @return an InputStream
     * @throws BlobNotFoundException when the blob does not contain a valid key in its value
     * @throws RepositoryException when an unexpected exception occurs
     */
    InputStream getInputStream(Blob blob) throws BlobNotFoundException,
            RepositoryException;

    /**
     * Deletes the data identified by a blob from the underlying blobstore. See {@link #getOutputStream(Blob)} and {@link #getInputStream(Blob)}.
     * @param blob the blob to delete
     * @throws BlobNotFoundException when the blob does not contain a valid key in its value
     * @throws RepositoryException when an unexpected exception occurs
     */
    void delete(Blob blob) throws BlobNotFoundException, RepositoryException;

    /**
     * Get all the variants that exist for the given recordId.
     *
     * @param recordId typically a master record id, if you specify a variant record id, its master will automatically
     *                 be used
     * @return the set of variants, including the master record id.
     */
    Set<RecordId> getVariants(RecordId recordId) throws RepositoryException;    

}
