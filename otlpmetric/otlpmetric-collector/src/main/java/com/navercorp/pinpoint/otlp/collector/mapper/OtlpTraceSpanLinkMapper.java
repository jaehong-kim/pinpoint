package com.navercorp.pinpoint.otlp.collector.mapper;

import com.navercorp.pinpoint.otlp.collector.model.OtlpTraceSpanLink;
import org.springframework.stereotype.Component;

@Component
public class OtlpTraceSpanLinkMapper {

    public OtlpTraceSpanLink map(io.opentelemetry.proto.trace.v1.Span.Link link) {
        if (link == null) {
            return null;
        }

        OtlpTraceSpanLink.Builder builder = new OtlpTraceSpanLink.Builder();
        builder.setTraceId(link.getTraceId().toByteArray());
        builder.setSpanId(link.getSpanId().toByteArray());
        link.getAttributesList().forEach(attribute -> {
            builder.addAttribute(attribute.getKey(), attribute.getValue().getStringValue());
        });
        builder.setDroppedAttributesCount(link.getDroppedAttributesCount());

        return builder.build();
    }
}
