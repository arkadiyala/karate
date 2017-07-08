/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package mockhttp;

import com.intuit.karate.ScriptContext;
import com.intuit.karate.demo.mockhttp.HelloResource;
import static com.intuit.karate.http.Cookie.DOMAIN;
import static com.intuit.karate.http.Cookie.MAX_AGE;
import static com.intuit.karate.http.Cookie.PATH;
import static com.intuit.karate.http.Cookie.SECURE;
import static com.intuit.karate.http.Cookie.VERSION;
import com.intuit.karate.http.HttpClient;
import com.intuit.karate.http.HttpConfig;
import com.intuit.karate.http.HttpResponse;
import com.intuit.karate.http.MultiPartItem;
import com.intuit.karate.http.MultiValuedMap;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 *
 * @author pthomas3
 */
public class MockHttpClient extends HttpClient<MockHttpEntity> {

    private static final Logger logger = LoggerFactory.getLogger(MockHttpClient.class);

    private final ServletContext servletContext;
    private final ServletContainer servlet;

    private URI uri;
    private MockHttpServletRequestBuilder requestBuilder;

    public MockHttpClient() throws Exception {
        ServletConfig servletConfig = new MockServletConfig();
        ResourceConfig resourceConfig = new ResourceConfig(HelloResource.class);
        servlet = new ServletContainer(resourceConfig);
        servlet.init(servletConfig);
        servletContext = new MockServletContext();
        logger.info("init mock http client");
    }

    @Override
    public void configure(HttpConfig config, ScriptContext context) {
        // if you want more custom set-up driven by the test script
        // use this call-back instead of the constructor, and use config.getUserDefined()
        // refer to the documentation of the 'configure userDefined' keyword
    }

    @Override
    protected MockHttpEntity getEntity(List<MultiPartItem> multiPartItems, String mediaType) {
        throw new UnsupportedOperationException("multi part not implemented yet");
    }

    @Override
    protected MockHttpEntity getEntity(MultiValuedMap formFields, String mediaType) {
        throw new UnsupportedOperationException("url-encoded form-fields not implemented yet");
    }

    @Override
    protected MockHttpEntity getEntity(InputStream stream, String mediaType) {
        try {
            byte[] bytes = IOUtils.toByteArray(stream);
            return new MockHttpEntity(bytes, mediaType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected MockHttpEntity getEntity(String content, String mediaType) {
        return new MockHttpEntity(content.getBytes(), mediaType);
    }

    @Override
    protected void buildUrl(String url) {
        String method = request.getMethod();
        try {
            uri = new URI(url);
            requestBuilder = request(method, uri);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void buildPath(String path) {
        String url = uri.toString();
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        buildUrl(url + path);
    }

    @Override
    protected void buildParam(String name, Object... values) {
        List<String> list = new ArrayList<>(values.length);
        for (Object o : values) {
            list.add(o == null ? null : o.toString());
        }
        requestBuilder.param(name, list.toArray(new String[]{}));
    }

    @Override
    protected void buildHeader(String name, Object value, boolean replace) {
        requestBuilder.header(name, value);
    }

    @Override
    protected void buildCookie(com.intuit.karate.http.Cookie c) {
        Cookie cookie = new Cookie(c.getName(), c.getValue());
        requestBuilder.cookie(cookie);
        for (Map.Entry<String, String> entry : c.entrySet()) {
            switch (entry.getKey()) {
                case DOMAIN:
                    cookie.setDomain(entry.getValue());
                    break;
                case PATH:
                    cookie.setPath(entry.getValue());
                    break;
            }
        }
        if (cookie.getDomain() == null) {
            cookie.setDomain(uri.getHost());
        }
    }

    @Override
    protected HttpResponse makeHttpRequest(MockHttpEntity entity, long startTime) {
        logger.info("making mock http client request: {} - {}", request.getMethod(), getRequestUri());
        MockHttpServletRequest req = requestBuilder.buildRequest(servletContext);
        if (entity != null) {
            req.setContent(entity.bytes);
            req.setContentType(entity.contentType);
        }        
        MockHttpServletResponse res = new MockHttpServletResponse();
        try {
            servlet.service(req, res);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        long responseTime = getResponseTime(startTime);
        HttpResponse response = new HttpResponse();
        response.setTime(responseTime);
        response.setUri(getRequestUri());
        response.setBody(res.getContentAsByteArray());
        response.setStatus(res.getStatus());
        for (Cookie c : res.getCookies()) {
            com.intuit.karate.http.Cookie cookie = new com.intuit.karate.http.Cookie(c.getName(), c.getValue());
            cookie.put(DOMAIN, c.getDomain());
            cookie.put(PATH, c.getPath());
            cookie.put(SECURE, c.getSecure() + "");
            cookie.put(MAX_AGE, c.getMaxAge() + "");
            cookie.put(VERSION, c.getVersion() + "");
            response.addCookie(cookie);
        }
        for (String headerName : res.getHeaderNames()) {
            response.addHeader(headerName, res.getHeaders(headerName));
        }
        return response;
    }

    @Override
    protected String getRequestUri() {
        return uri.toString();
    }

}
