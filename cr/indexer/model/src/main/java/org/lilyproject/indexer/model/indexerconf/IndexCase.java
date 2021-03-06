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
package org.lilyproject.indexer.model.indexerconf;

import org.lilyproject.repository.api.QName;
import org.lilyproject.repository.api.SchemaId;

import java.util.Map;
import java.util.Set;

public class IndexCase {
    private final WildcardPattern recordTypeNamespace;
    private final WildcardPattern recordTypeName;

    /**
     * The variant properties the record should have. Evaluation rules: a key named
     * "*" (star symbol) is a wildcard meaning that any variant dimensions not specified
     * are accepted. Otherwise the variant dimension count should match exactly. The other
     * keys in the map are required variant dimensions. If their value is not null, the
     * values should match.
     */
    private final Map<String, String> variantPropsPattern;
    private final Set<SchemaId> vtags;

    public IndexCase(WildcardPattern recordTypeNamespace, WildcardPattern recordTypeName,
            Map<String, String> variantPropsPattern, Set<SchemaId> vtags) {
        this.recordTypeNamespace = recordTypeNamespace;
        this.recordTypeName = recordTypeName;
        this.variantPropsPattern = variantPropsPattern;
        this.vtags = vtags;
    }

    public boolean match(QName recordTypeName, Map<String, String> varProps) {
        if (this.recordTypeNamespace != null && !this.recordTypeNamespace.lightMatch(recordTypeName.getNamespace())) {
            return false;
        }

        if (this.recordTypeName != null && !this.recordTypeName.lightMatch(recordTypeName.getName())) {
            return false;
        }

        if (variantPropsPattern.size() != varProps.size() && !variantPropsPattern.containsKey("*")) {
            return false;
        }

        for (Map.Entry<String, String> entry : variantPropsPattern.entrySet()) {
            if (entry.getKey().equals("*"))
                continue;

            String dimVal = varProps.get(entry.getKey());
            if (dimVal == null) {
                // this record does not have a required variant property
                return false;
            }

            if (entry.getValue() != null && !entry.getValue().equals(dimVal)) {
                // the variant property does not have the required value
                return false;
            }
        }

        return true;
    }

    /**
     * Version tags identified by ID.
     */
    public Set<SchemaId> getVersionTags() {
        return vtags;
    }
}
