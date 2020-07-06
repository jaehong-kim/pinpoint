/*
 * Copyright 2017 NAVER Corp.
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

package com.navercorp.pinpoint.web.view;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.navercorp.pinpoint.web.applicationmap.histogram.Histogram;
import com.navercorp.pinpoint.web.applicationmap.histogram.LoadHistogramFormat;
import com.navercorp.pinpoint.web.applicationmap.histogram.NodeHistogram;
import com.navercorp.pinpoint.web.applicationmap.nodes.NodeHistogramSummary;
import com.navercorp.pinpoint.web.applicationmap.nodes.ServerInstanceList;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author HyunGil Jeong
 */
public class NodeHistogramSummarySerializer extends JsonSerializer<NodeHistogramSummary> {

    @Override
    public void serialize(NodeHistogramSummary nodeHistogramSummary, JsonGenerator jgen, SerializerProvider serializers) throws IOException, JsonProcessingException {
        jgen.writeStartObject();
        jgen.writeNumberField("currentServerTime", new ServerTime().getCurrentServerTime());

        ServerInstanceList serverInstanceList = nodeHistogramSummary.getServerInstanceList();
        jgen.writeObjectField("serverList", serverInstanceList);


        writeHistogram(jgen, nodeHistogramSummary);

        jgen.writeEndObject();
    }

    private void writeHistogram(JsonGenerator jgen, NodeHistogramSummary nodeHistogramSummary) throws IOException {
        NodeHistogram nodeHistogram = nodeHistogramSummary.getNodeHistogram();
        Histogram applicationHistogram = nodeHistogram.getApplicationHistogram();
        if (applicationHistogram == null) {
            writeEmptyObject(jgen, "histogram");
        } else {
            jgen.writeObjectField("histogram", applicationHistogram);
        }
        Map<String, Histogram> agentHistogramMap = nodeHistogram.getAgentHistogramMap();
        if (agentHistogramMap == null) {
            writeEmptyObject(jgen, "agentHistogram");
        } else {
            jgen.writeObjectField("agentHistogram", agentHistogramMap);
        }

        if (nodeHistogramSummary.getLoadHistogramFormat() == LoadHistogramFormat.V2) {
            List<LoadTimeViewModel> loadTimeViewModelList = nodeHistogram.getLoadHistogram();
            if (loadTimeViewModelList == null) {
                writeEmptyArray(jgen, "loadHistogram");
            } else {
                jgen.writeObjectField("loadHistogram", loadTimeViewModelList);
            }
        } else {
            List<ResponseTimeViewModel> applicationTimeSeriesHistogram = nodeHistogram.getApplicationTimeHistogram();
            if (applicationTimeSeriesHistogram == null) {
                writeEmptyArray(jgen, "timeSeriesHistogram");
            } else {
                jgen.writeObjectField("timeSeriesHistogram", applicationTimeSeriesHistogram);
            }
        }

        AgentResponseTimeViewModelList agentTimeSeriesHistogram = nodeHistogram.getAgentTimeHistogram();
        jgen.writeObject(agentTimeSeriesHistogram);
    }

    private void writeEmptyArray(JsonGenerator jgen, String fieldName) throws IOException {
        jgen.writeFieldName(fieldName);
        jgen.writeStartArray();
        jgen.writeEndArray();
    }

    private void writeEmptyObject(JsonGenerator jgen, String fieldName) throws IOException {
        jgen.writeFieldName(fieldName);
        jgen.writeStartObject();
        jgen.writeEndObject();
    }
}
