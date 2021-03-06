/*
 * Copyright 2012 NGDATA nv
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
package org.lilyproject.repository.spi;

import org.lilyproject.repository.api.FieldTypes;
import org.lilyproject.repository.api.Record;
import org.lilyproject.repository.api.Repository;
import org.lilyproject.repository.api.RepositoryException;

public interface RecordUpdateHook {
    /**
     * This hook is called before a record is updated but after the record has been locked for updating
     * and the original record state has been read.
     *
     * <p>Compared to a {@link RepositoryDecorator}, you would use it when you would decorate the update
     * method and would find that you need to read the original record. Since the repository implementation
     * reads the previous record state anyway, we can avoid this double HBase-involving work. In addition,
     * since the record is locked, you can be sure the record state won't change anymore between the read
     * and the update.</p>
     *
     * <p>The hook is called before the conditional update checks are checked.</p>
     *
     * <p>The hook should not modify the ID of the record, this will lead to unpredictable behavior.</p>
     *
     * @param record the record supplied by the user (not validated)
     * @param originalRecord the record as it is stored in the repository, containing all record
     *                       fields. Unmodifiable.
     * @param fieldTypes snapshot of the state of the field types when the update operation started (to
     *                   be insensitive to changes such as field type name changes)
     */
    void beforeUpdate(Record record, Record originalRecord, Repository repository, FieldTypes fieldTypes)
            throws RepositoryException, InterruptedException;
}
