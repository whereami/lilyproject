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
package org.lilyproject.hbaseindex;

import com.gotometrics.orderly.FloatRowKey;
import com.gotometrics.orderly.RowKey;
import com.gotometrics.orderly.Termination;
import org.codehaus.jackson.node.ObjectNode;

/**
 * An IndexFieldDefinition for floats.
 *
 * <p>Note that since float representation is always approximative, it is
 * better suited for range queries than equals queries.
 */
public class FloatIndexFieldDefinition extends IndexFieldDefinition {
    public FloatIndexFieldDefinition(String name) {
        super(name);
    }

    public FloatIndexFieldDefinition(String name, ObjectNode jsonObject) {
        super(name, jsonObject);
    }

    @Override
    RowKey asRowKey() {
        final FloatRowKey rowKey = new FloatRowKey();
        rowKey.setOrder(this.getOrder());
        return rowKey;
    }

    @Override
    RowKey asRowKeyWithoutTermination() {
        final RowKey rowKey = asRowKey();
        rowKey.setTermination(Termination.SHOULD_NOT);
        return rowKey;
    }

}

