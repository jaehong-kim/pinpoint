package com.navercorp.pinpoint.otlp.collector.mapper;

import com.navercorp.pinpoint.otlp.collector.model.OtlpTraceSpanEvent;
import io.opentelemetry.proto.trace.v1.Span;
import org.springframework.stereotype.Component;

@Component
public class OtlpTraceSpanEventMapper {

    public OtlpTraceSpanEvent map(Span.Event event) {
        if (event == null) {
            return null;
        }

        OtlpTraceSpanEvent.Builder builder = new OtlpTraceSpanEvent.Builder();
        builder.setTimeUnixNano(event.getTimeUnixNano());
        builder.setName(event.getName());


        event.getAttributesList().forEach(attribute -> {
            builder.addAttribute(attribute.getKey(), attribute.getValue().getStringValue());
        });
        builder.setDroppedAttributesCount(event.getDroppedAttributesCount());

        return builder.build();
    }
}
