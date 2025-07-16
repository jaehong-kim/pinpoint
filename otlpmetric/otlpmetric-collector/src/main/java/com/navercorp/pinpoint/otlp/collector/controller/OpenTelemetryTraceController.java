/*
 * Copyright 2022 NAVER Corp.
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

package com.navercorp.pinpoint.otlp.collector.controller;

import com.navercorp.pinpoint.otlp.collector.mapper.OtlpMetricMapper;
import com.navercorp.pinpoint.otlp.collector.mapper.OtlpTraceSpanMapper;
import com.navercorp.pinpoint.otlp.collector.model.OtlpTraceSpan;
import com.navercorp.pinpoint.otlp.collector.service.OtlpTraceCollectorService;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
public class OpenTelemetryTraceController {
    private final Logger logger = LogManager.getLogger(this.getClass());

    @NotNull
    private final OtlpTraceCollectorService otlpMetricCollectorService;
    @NotNull
    private final OtlpTraceSpanMapper otlpTraceSpanMapper;

    public OpenTelemetryTraceController(@Valid OtlpTraceCollectorService otlpTraceCollectorService,
                                        @Valid OtlpTraceSpanMapper otlpTraceSpanMapper) {
        this.otlpMetricCollectorService = Objects.requireNonNull(otlpTraceCollectorService, "otlpMetricService");
        this.otlpTraceSpanMapper = Objects.requireNonNull(otlpTraceSpanMapper, "otlpTraceSpanMapper");
    }

    @PostMapping(value = "/opentelemetry/trace", consumes = "application/x-protobuf")
    public ResponseEntity<Void> saveOtlpTrace(@RequestBody ExportTraceServiceRequest request) {
        List<ResourceSpans> resourceSpanList = request.getResourceSpansList();

        for (ResourceSpans resourceSpan : resourceSpanList) {
            List<KeyValue> attributesList = resourceSpan.getResource().getAttributesList();
            // TODO service.instance.id to agentId
            // TODO service.name to applicationName
            Map<String, String> tags = convertToMap(attributesList);

            List<ScopeSpans> scopeSpanList = resourceSpan.getScopeSpansList();
            for (ScopeSpans scopeSpan : scopeSpanList) {
                List<Span> spansList = scopeSpan.getSpansList();
                for (Span span : spansList) {
                    OtlpTraceSpan otlpTraceSpan = toSpan(span, tags);
                    otlpMetricCollectorService.save(otlpTraceSpan);
                }
            }
        }

        return ResponseEntity.ok().build();
    }

    private Map<String, String> convertToMap(List<KeyValue> tags) {
        Map<String, String> tagMap = new HashMap<>();
        for (KeyValue tag : tags) {
            tagMap.put(tag.getKey(), tag.getValue().getStringValue());
        }
        return tagMap;
    }

    private OtlpTraceSpan toSpan(Span span, Map<String, String> tags) {
        OtlpTraceSpan otlpMetricData = otlpTraceSpanMapper.map(span, tags);
        return otlpMetricData;
    }

}