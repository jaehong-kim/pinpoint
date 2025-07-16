package com.navercorp.pinpoint.otlp.collector.service;

import com.navercorp.pinpoint.otlp.collector.dao.OtlpTraceSpanDao;
import com.navercorp.pinpoint.otlp.collector.model.OtlpTraceSpan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
public class HbaseOtlpTraceCollectorService implements OtlpTraceCollectorService {
    private final Logger logger = LogManager.getLogger(getClass());

    @NotNull
    private final OtlpTraceSpanDao otlpTraceSpanDao;

    public HbaseOtlpTraceCollectorService(@Valid OtlpTraceSpanDao otlpTraceSpanDao) {
        this.otlpTraceSpanDao = Objects.requireNonNull(otlpTraceSpanDao, "otlpTraceSpanDao");
    }


    @Override
    public void save(OtlpTraceSpan otlpTraceSpan) {
        CompletableFuture<Void> future = otlpTraceSpanDao.asyncInsert(otlpTraceSpan);
        future.whenCompleteAsync((unused, throwable) -> {
            final boolean result = throwable == null;
            if (logger.isTraceEnabled()) {
                logger.trace("success {}", result);
            }
        });
    }
}
