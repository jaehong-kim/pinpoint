package com.navercorp.pinpoint.plugin.cassandra4.interceptor;

import com.navercorp.pinpoint.bootstrap.context.MethodDescriptor;
import com.navercorp.pinpoint.bootstrap.context.TraceContext;
import com.navercorp.pinpoint.bootstrap.interceptor.AroundInterceptor;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.plugin.jdbc.BindValueAccessor;
import com.navercorp.pinpoint.common.util.ArrayArgumentUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultSimpleStatementConstructorInterceptor implements AroundInterceptor {
    private final PLogger logger = PLoggerFactory.getLogger(getClass());

    private TraceContext traceContext;

    public DefaultSimpleStatementConstructorInterceptor(TraceContext traceContext, MethodDescriptor methodDescriptor) {
        this.traceContext = traceContext;
    }

    @Override
    public void before(Object target, Object[] args) {
    }

    @Override
    public void after(Object target, Object[] args, Object result, Throwable throwable) {
        if (throwable != null) {
            return;
        }

        if (Boolean.FALSE == (target instanceof BindValueAccessor)) {
            return;
        }

        final Map<Integer, String> bindList = new HashMap<>();
        int bindIndex = 1;

        try {
            final List<Object> positionalValues = ArrayArgumentUtils.getArgument(args, 1, List.class);
            if (positionalValues != null) {
                for (Object value : positionalValues) {
                    final String bind = traceContext.getJdbcContext().getBindVariableService().formatBindVariable(value);
                    if (bind != null) {
                        bindList.put(bindIndex++, bind);
                    }
                }
            }
            final Map namedValues = ArrayArgumentUtils.getArgument(args, 2, Map.class);
            if (namedValues != null) {
                for (Object value : namedValues.values()) {
                    final String bind = traceContext.getJdbcContext().getBindVariableService().formatBindVariable(value);
                    if (bind != null) {
                        bindList.put(bindIndex++, bind);
                    }
                }
            }
            ((BindValueAccessor) target)._$PINPOINT$_setBindValue(bindList);
        } catch (Throwable th) {
            if (logger.isWarnEnabled()) {
                logger.warn("AFTER. Caused:{}", th.getMessage(), th);
            }
        }
    }
}
