/*
 * Copyright 2020 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.web.service;

import com.navercorp.pinpoint.loader.service.ServiceTypeRegistryService;
import com.navercorp.pinpoint.web.applicationmap.ApplicationMapBuilderFactory;
import com.navercorp.pinpoint.web.applicationmap.appender.server.datasource.NodeHistogramServerInstanceListDataSource;
import com.navercorp.pinpoint.web.dao.ApplicationTraceIndexDao;
import com.navercorp.pinpoint.web.dao.TraceDao;
import com.navercorp.pinpoint.web.security.ServerMapDataFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * @author netspider
 * @author emeroad
 * @author minwoo.jung
 * @author jaehong.kim
 */
@Service
public class CompactFilteredMapServiceImpl extends BaseFilteredMapServiceImpl {
    public CompactFilteredMapServiceImpl(AgentInfoService agentInfoService,
                                         @Qualifier("hbaseTraceDaoFactory") TraceDao traceDao,
                                         ApplicationTraceIndexDao applicationTraceIndexDao,
                                         ServiceTypeRegistryService registry,
                                         ApplicationFactory applicationFactory,
                                         Optional<ServerMapDataFilter> serverMapDataFilter,
                                         ApplicationMapBuilderFactory applicationMapBuilderFactory) {
        super(new NodeHistogramServerInstanceListDataSource(), traceDao, applicationTraceIndexDao, registry, applicationFactory, serverMapDataFilter, applicationMapBuilderFactory);
    }
}