/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
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
 * See the License for the specific
 *
 */

package com.khartec.waltz.web.endpoints.extracts;

import org.finos.waltz.data.application.ApplicationIdSelectorFactory;
import org.finos.waltz.model.EntityLifecycleStatus;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.IdSelectionOptions;
import org.jooq.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

import static org.finos.waltz.schema.Tables.LICENCE;
import static org.finos.waltz.schema.tables.Application.APPLICATION;
import static org.finos.waltz.schema.tables.SoftwareUsage.SOFTWARE_USAGE;
import static org.finos.waltz.schema.tables.SoftwareVersionLicence.SOFTWARE_VERSION_LICENCE;
import static com.khartec.waltz.web.WebUtilities.getEntityReference;
import static com.khartec.waltz.web.WebUtilities.mkPath;
import static java.lang.String.format;
import static org.finos.waltz.model.IdSelectionOptions.mkOpts;
import static spark.Spark.get;


@Service
public class LicencesExtractor extends DirectQueryBasedDataExtractor {

    private final ApplicationIdSelectorFactory applicationIdSelectorFactory = new ApplicationIdSelectorFactory();

    @Autowired
    public LicencesExtractor(DSLContext dsl) {
        super(dsl);
    }


    @Override
    public void register() {
        String path = mkPath("data-extract", "licences", ":kind", ":id");
        get(path, (request, response) -> {

            EntityReference entityRef = getEntityReference(request);

            IdSelectionOptions selectionOptions = mkOpts(entityRef);
            Select<Record1<Long>> appIdSelector = applicationIdSelectorFactory.apply(selectionOptions);

            SelectConditionStep<Record8<Long, String, String, String, String, Timestamp, String, String>> qry = dsl
                    .selectDistinct(LICENCE.ID.as("Licence Id"),
                            LICENCE.NAME.as("Licence Name"),
                            LICENCE.DESCRIPTION.as("Description"),
                            LICENCE.EXTERNAL_ID.as("External Id"),
                            LICENCE.APPROVAL_STATUS.as("Approval Status"),
                            LICENCE.LAST_UPDATED_AT.as("Last Updated At"),
                            LICENCE.LAST_UPDATED_BY.as("Last Updated By"),
                            LICENCE.PROVENANCE.as("Provenance"))
                    .from(LICENCE)
                    .innerJoin(SOFTWARE_VERSION_LICENCE)
                    .on(SOFTWARE_VERSION_LICENCE.LICENCE_ID.eq(LICENCE.ID))
                    .innerJoin(SOFTWARE_USAGE)
                    .on(SOFTWARE_VERSION_LICENCE.SOFTWARE_VERSION_ID.eq(SOFTWARE_USAGE.SOFTWARE_VERSION_ID))
                    .innerJoin(APPLICATION).on(SOFTWARE_USAGE.APPLICATION_ID.eq(APPLICATION.ID))
                    .where(dsl.renderInlined(SOFTWARE_USAGE.APPLICATION_ID.in(appIdSelector)
                            .and(APPLICATION.ENTITY_LIFECYCLE_STATUS.notEqual(EntityLifecycleStatus.REMOVED.name())
                                    .and(APPLICATION.IS_REMOVED.isFalse()))));

            String filename = format("licences-%s/%s", entityRef.kind(), entityRef.id());

            return writeExtract(
                    filename,
                    qry,
                    request,
                    response);
        });
    }
}
