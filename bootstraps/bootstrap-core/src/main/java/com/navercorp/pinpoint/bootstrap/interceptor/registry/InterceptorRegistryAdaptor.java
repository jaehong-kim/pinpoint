package com.navercorp.pinpoint.bootstrap.interceptor.registry;

import com.navercorp.pinpoint.bootstrap.interceptor.Interceptor;


/**
 * @author emeroad
 */
public interface InterceptorRegistryAdaptor {

    int addInterceptor();

    @Deprecated
    int addInterceptor(Interceptor interceptor);

    @Deprecated
    Interceptor getInterceptor(int key);

    void clear();
}
