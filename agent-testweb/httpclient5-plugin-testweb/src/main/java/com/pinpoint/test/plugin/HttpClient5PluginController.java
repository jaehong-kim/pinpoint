package com.pinpoint.test.plugin;

import org.apache.hc.client5.http.async.methods.*;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.CharBuffer;
import java.util.concurrent.Future;

@RestController
public class HttpClient5PluginController {

    @GetMapping("/")
    public Mono<String> weclome() {
        return Mono.just("Welcome");
    }

    @GetMapping("/client/get")
    public String httpGet() throws Exception {
        final CloseableHttpClient httpClient = HttpClients.createDefault();
        final HttpGet httpGet = new HttpGet("http://httpbin.org/get");

        System.out.println("Executing request " + httpGet.getMethod() + " " + httpGet.getUri());

        final HttpClientResponseHandler<String> responseHandler = new HttpClientResponseHandler<String>() {
            @Override
            public String handleResponse(ClassicHttpResponse classicHttpResponse) throws HttpException, IOException {
                final int status = classicHttpResponse.getCode();
                if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
                    final HttpEntity entity = classicHttpResponse.getEntity();
                    return entity != null ? EntityUtils.toString(entity) : null;
                }

                return null;
            }
        };

        final String responseBody = httpClient.execute(httpGet, responseHandler);
        System.out.println("------------------------------------------------------");
        System.out.println(responseBody);

        return responseBody;
    }

    @GetMapping("/client/local")
    public String clientLocal() throws Exception {
        final CloseableHttpClient httpClient = HttpClients.createDefault();
        final HttpGet httpGet = new HttpGet("http://localhost:18080/");

        System.out.println("Executing request " + httpGet.getMethod() + " " + httpGet.getUri());

        final HttpClientResponseHandler<String> responseHandler = new HttpClientResponseHandler<String>() {
            @Override
            public String handleResponse(ClassicHttpResponse classicHttpResponse) throws HttpException, IOException {
                final int status = classicHttpResponse.getCode();
                if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
                    final HttpEntity entity = classicHttpResponse.getEntity();
                    return entity != null ? EntityUtils.toString(entity) : null;
                }

                return null;
            }
        };

        final String responseBody = httpClient.execute(httpGet, responseHandler);
        System.out.println("------------------------------------------------------");
        System.out.println(responseBody);

        return responseBody;
    }

    @GetMapping("/client/thread")
    public String threadExecution() throws Exception {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(10);

        CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(cm).build();
        final String[] urisToGet = {"http://hc.apache.org", "http://hc.apache.org/httpcomponents-core-ga/", "http://hc.apache.org/httpcomponents-client-ga/"};
        final GetThread[] threads = new GetThread[urisToGet.length];

        for (int i = 0; i < threads.length; i++) {
            final HttpGet httpGet = new HttpGet(urisToGet[i]);
            threads[i] = new GetThread(httpClient, httpGet, i + 1);
        }

        for (final GetThread thread : threads) {
            thread.start();
        }

        for (final GetThread thread : threads) {
            thread.join();
        }

        return "OK";
    }

    @GetMapping("/client/async")
    public String asyncFuture() throws Exception {
        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom().setSoTimeout(Timeout.ofSeconds(5)).build();
        final CloseableHttpAsyncClient client = HttpAsyncClients.custom().setIOReactorConfig(ioReactorConfig).build();
        client.start();

        final HttpHost target = new HttpHost("httpbin.org");
        final String[] requestUris = new String[]{"/", "/ip", "/user-agent", "/headers"};

        final SimpleHttpRequest request = SimpleRequestBuilder.get().setHttpHost(target).setPath("/").build();
        System.out.println("Execution request " + request);
        final Future<SimpleHttpResponse> future = client.execute(SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(SimpleHttpResponse response) {
                System.out.println(request + "->" + new StatusLine(response));
                System.out.println(response.getBody());
            }

            @Override
            public void failed(Exception e) {
                System.out.println(request + "->" + e);
            }

            @Override
            public void cancelled() {
                System.out.println(request + " cancelled");
            }
        });
        future.get();

        System.out.println("Shutting down");
        client.close(CloseMode.GRACEFUL);
        return "OK";
    }

    @GetMapping("/client/stream")
    public String clientStream() throws Exception {
        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom().setSoTimeout(Timeout.ofSeconds(5)).build();
        final CloseableHttpAsyncClient client = HttpAsyncClients.custom().setIOReactorConfig(ioReactorConfig).build();
        client.start();

        final HttpHost target = new HttpHost("httpbin.org");
        final String[] requestUris = new String[]{"/", "/ip", "/user-agent", "/headers"};

        final BasicHttpRequest request = BasicRequestBuilder.get().setHttpHost(target).setPath(requestUris[0]).build();
        System.out.println("Execution request " + request);
        final Future<Void> future = client.execute(new BasicRequestProducer(request, null), new AbstractCharResponseConsumer<Void>() {
            @Override
            protected void start(HttpResponse response, ContentType contentType) throws HttpException, IOException {
                System.out.println(request + "->" + new StatusLine(response));
            }

            @Override
            protected Void buildResult() throws IOException {
                return null;
            }

            @Override
            protected int capacityIncrement() {
                return Integer.MAX_VALUE;
            }

            @Override
            protected void data(CharBuffer src, boolean endOfStream) throws IOException {
                while(src.hasRemaining()) {
                    System.out.println(src.get());
                }
                if(endOfStream) {
                    System.out.println();
                }
            }

            @Override
            public void releaseResources() {

            }
        }, null);
        future.get();

        System.out.println("Shutting down");
        client.close(CloseMode.GRACEFUL);
        return "OK";
    }

    @GetMapping("/client/minimal")
    public String asyncMinimal() throws Exception {
        final CloseableHttpAsyncClient client = HttpAsyncClients.createMinimal();
        client.start();

        final HttpHost target = new HttpHost("httpbin.org");

        final SimpleHttpRequest request = SimpleRequestBuilder.get().setHttpHost(target).setPath("/").build();
        System.out.println("Execution request " + request);
        final Future<SimpleHttpResponse> future = client.execute(SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(SimpleHttpResponse response) {
                System.out.println(request + "->" + new StatusLine(response));
                System.out.println(response.getBody());
            }

            @Override
            public void failed(Exception e) {
                System.out.println(request + "->" + e);
            }

            @Override
            public void cancelled() {
                System.out.println(request + " cancelled");
            }
        });
        future.get();

        System.out.println("Shutting down");
        client.close(CloseMode.GRACEFUL);
        return "OK";
    }

    @GetMapping("/client/cookie")
    public String httpCookie() throws Exception {
        final CloseableHttpClient httpClient = HttpClients.createDefault();
        final HttpGet httpGet = new HttpGet("http://localhost");
        httpGet.addHeader("Cookie", "COOKIE");

        final HttpClientResponseHandler<String> responseHandler = new HttpClientResponseHandler<String>() {
            @Override
            public String handleResponse(ClassicHttpResponse classicHttpResponse) throws HttpException, IOException {
                final int status = classicHttpResponse.getCode();
                if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
                    final HttpEntity entity = classicHttpResponse.getEntity();
                    return entity != null ? EntityUtils.toString(entity) : null;
                }
                return null;
            }
        };

        final String responseBody = httpClient.execute(httpGet, responseHandler);
        System.out.println("------------------------------------------------------");
        System.out.println(responseBody);

        return responseBody;
    }

    @GetMapping("/client/entity")
    public String httpEntity() throws Exception {
        final CloseableHttpClient httpClient = HttpClients.createDefault();
        final HttpPost httpPost = new HttpPost("http://localhost");
        httpPost.setEntity(new StringEntity("foo-bar"));

        final HttpClientResponseHandler<String> responseHandler = new HttpClientResponseHandler<String>() {
            @Override
            public String handleResponse(ClassicHttpResponse classicHttpResponse) throws HttpException, IOException {
                final int status = classicHttpResponse.getCode();
                if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
                    final HttpEntity entity = classicHttpResponse.getEntity();
                    return entity != null ? EntityUtils.toString(entity) : null;
                }
                return null;
            }
        };

        final String responseBody = httpClient.execute(httpPost, responseHandler);
        System.out.println("------------------------------------------------------");
        System.out.println(responseBody);

        return responseBody;
    }


    static class GetThread extends Thread {

        private final CloseableHttpClient httpClient;
        private final HttpContext context;
        private final HttpGet httpget;
        private final int id;

        public GetThread(final CloseableHttpClient httpClient, final HttpGet httpget, final int id) {
            this.httpClient = httpClient;
            this.context = new BasicHttpContext();
            this.httpget = httpget;
            this.id = id;
        }

        /**
         * Executes the GetMethod and prints some status information.
         */
        @Override
        public void run() {
            try {
                System.out.println(id + " - about to get something from " + httpget.getUri());
                try (CloseableHttpResponse response = httpClient.execute(httpget, context)) {
                    System.out.println(id + " - get executed");
                    // get the response body as an array of bytes
                    final HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        final byte[] bytes = EntityUtils.toByteArray(entity);
                        System.out.println(id + " - " + bytes.length + " bytes read");
                    }
                }
            } catch (final Exception e) {
                System.out.println(id + " - error: " + e);
            }
        }
    }
}
