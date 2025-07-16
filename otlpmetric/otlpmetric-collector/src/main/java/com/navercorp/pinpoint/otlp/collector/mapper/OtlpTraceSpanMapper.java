package com.navercorp.pinpoint.otlp.collector.mapper;

import com.navercorp.pinpoint.otlp.collector.model.OtlpTraceSpan;
import com.navercorp.pinpoint.otlp.collector.model.OtlpTraceSpanStatus;
import io.opentelemetry.proto.trace.v1.Span;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OtlpTraceSpanMapper {

    private final OtlpTraceSpanEventMapper otlpTraceSpanEventMapper;
    private final OtlpTraceSpanLinkMapper otlpTraceSpanLinkMapper;
    private final OtlpTraceSpanStatusMapper otlpTraceSpanStatusMapper;

    public OtlpTraceSpanMapper(OtlpTraceSpanEventMapper otlpTraceSpanEventMapper, OtlpTraceSpanLinkMapper otlpTraceSpanLinkMapper, OtlpTraceSpanStatusMapper otlpTraceSpanStatusMapper) {
        this.otlpTraceSpanEventMapper = otlpTraceSpanEventMapper;
        this.otlpTraceSpanLinkMapper = otlpTraceSpanLinkMapper;
        this.otlpTraceSpanStatusMapper = otlpTraceSpanStatusMapper;
    }

    public OtlpTraceSpan map(Span span, Map<String, String> commonTags) {
        if (span == null) {
            return null;
        }

        OtlpTraceSpan.Builder builder = new OtlpTraceSpan.Builder();

        // Set common attributes
        builder.setTraceId(span.getTraceId().toByteArray());
        builder.setSpanId(span.getSpanId().toByteArray());
        builder.setTraceState(span.getTraceState());
        builder.setParentSpanId(span.getParentSpanId().toByteArray());
        builder.setFlags(span.getFlags());
        builder.setName(span.getName());
        builder.setSpanKind(span.getKind().getNumber());
        builder.setStartTimeUnixNano(span.getStartTimeUnixNano());
        builder.setEndTimeUnixNano(span.getEndTimeUnixNano());
        // Map span attributes
        span.getAttributesList().forEach(attribute -> {
            builder.addAttribute(attribute.getKey(), attribute.getValue().getStringValue());
        });
        builder.setDroppedAttributesCount(span.getDroppedAttributesCount());
        span.getEventsList().stream()
                .map(otlpTraceSpanEventMapper::map)
                .forEach(builder::addEvent);
        builder.setDroppedEventsCount(span.getDroppedEventsCount());
        span.getLinksList().forEach(link -> {
            builder.addLink(otlpTraceSpanLinkMapper.map(link));
        });
        builder.setDropedLinksCount(span.getDroppedLinksCount());
        OtlpTraceSpanStatus status = otlpTraceSpanStatusMapper.mapper(span.getStatus());
        builder.setStatus(status);

        final String agentId = commonTags.get("service.instance.id");
        if(agentId == null) {
            // TODO generate agentId ?
        }
        builder.setAgentId(agentId);
        final String applicationName = commonTags.get("service.name");
        if(applicationName == null) {
            // TODO generate applicationName ?
        }
        builder.setApplicationName(applicationName);
        builder.setCollectorAcceptTime(System.currentTimeMillis());

        return builder.build();
    }

}
