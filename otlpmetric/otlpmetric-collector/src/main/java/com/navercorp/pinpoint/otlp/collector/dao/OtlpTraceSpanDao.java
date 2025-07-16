package com.navercorp.pinpoint.otlp.collector.dao;

import com.navercorp.pinpoint.otlp.collector.model.OtlpTraceSpan;

import java.util.concurrent.CompletableFuture;

public interface OtlpTraceSpanDao {
    CompletableFuture<Void> asyncInsert(final OtlpTraceSpan spanBo);
}
