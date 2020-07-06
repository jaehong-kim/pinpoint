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

package com.navercorp.pinpoint.web.applicationmap.appender.server.datasource;

import com.navercorp.pinpoint.web.applicationmap.histogram.Histogram;
import com.navercorp.pinpoint.web.applicationmap.histogram.NodeHistogram;
import com.navercorp.pinpoint.web.applicationmap.nodes.Node;
import com.navercorp.pinpoint.web.applicationmap.nodes.ServerBuilder;
import com.navercorp.pinpoint.web.applicationmap.nodes.ServerInstanceList;
import com.navercorp.pinpoint.web.vo.AgentInfo;
import com.navercorp.pinpoint.web.vo.Application;
import com.navercorp.pinpoint.web.vo.Range;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author jaehong.kim
 */
public class NodeHistogramServerInstanceListDataSource implements ServerInstanceListDataSource {
    public NodeHistogramServerInstanceListDataSource() {
    }

    public ServerInstanceList createServerInstanceList(Node node, Range range) {
        Objects.requireNonNull(node, "node");

        final Application application = node.getApplication();
        final ServerBuilder builder = new ServerBuilder();
        final Set<AgentInfo> agentInfoSet = new HashSet<>();
        final NodeHistogram nodeHistogram = node.getNodeHistogram();
        if (nodeHistogram == null) {
            return new ServerInstanceList();
        }

        // Find ping slot
        for (Map.Entry<String, Histogram> entry : nodeHistogram.getAgentHistogramMap().entrySet()) {
            final String agentId = entry.getKey();
            final Histogram histogram = entry.getValue();
            if (histogram.getPingCount() > 0) {
                AgentInfo agentInfo = new AgentInfo();
                agentInfo.setAgentId(agentId);
                agentInfo.setHostName(agentId);
                agentInfo.setIp("");
                agentInfo.setServiceTypeCode(application.getServiceTypeCode());
                agentInfoSet.add(agentInfo);
            }
        }
        builder.addAgentInfo(agentInfoSet);

        return builder.build();
    }
}