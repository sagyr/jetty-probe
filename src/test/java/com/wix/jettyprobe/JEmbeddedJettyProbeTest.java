package com.wix.jettyprobe;

import org.hamcrest.Description;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.wix.jettyprobe.JEmbeddedJettyProbe.Responder;
import static com.wix.jettyprobe.JEmbeddedJettyProbe.ServerRequest;
import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author sagyr
 * @since 4/11/13
 */
public class JEmbeddedJettyProbeTest {

    private JEmbeddedJettyProbe probe;
    private ProbeTester probeTester;

    private static Matcher<? super ServerRequest> hasHeader(final String header, final String value) {
        return new TypeSafeMatcher<ServerRequest>() {
            @Override
            protected boolean matchesSafely(ServerRequest item) {
                return item.headers.containsKey(header) && value.equals(item.headers.get(header));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("server request with header %s and value %s",header,value));
            }
        };
    }

    private static Matcher<? super ServerRequest> hasEmptyBody() {
        return new TypeSafeMatcher<ServerRequest>() {
            @Override
            protected boolean matchesSafely(ServerRequest item) {
                return "".equals(item.body);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("has empty body");
            }
        };
    }

    private static Matcher<ServerRequest> bodyMatches(final Matcher m) {
        return new FeatureMatcher<ServerRequest,String>(m,"body matches","body") {
            @Override
            protected String featureValueOf(ServerRequest actual) {
                return actual.body;
            }
        };
    }

    private static Matcher<ServerRequest> anyRequest() {
        return any(ServerRequest.class);
    }

    private Responder respondWith(final String responseString) {
        return new Responder() {
            @Override
            public void handle(ServerRequest request, HttpServletResponse response) {
                try {
                    response.getOutputStream().print(responseString);
                } catch (IOException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        };
    }

    private static Matcher<ServerRequest> hasBody(final String body) {
        return new TypeSafeMatcher<ServerRequest>() {
            @Override
            protected boolean matchesSafely(ServerRequest item) {
                return body.equals(item.body);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("has body: %s",body));
            }
        };
    }


    @Before
    public void setup() throws IOException {
        probe = new JEmbeddedJettyProbe();
        probe.start();
        probeTester = new ProbeTester();
    }

    @After
    public void tearDown() {
        probe.stop();
    }

    @Test
    public void returnsLastSentRequest() {
        probeTester.executeHttpGet();
        ServerRequest lastRequest = probe.probeForRequest(2, TimeUnit.SECONDS);
        assertThat(lastRequest, hasEmptyBody());
        assertThat(lastRequest, hasHeader("Host", "localhost:9080"));
    }

    @Test
    public void returnsBody() {
        probeTester.executeHttpPost("TEST_MESSAGE");
        ServerRequest lastRequest = probe.probeForRequest(2, TimeUnit.SECONDS);
        assertThat(lastRequest, hasBody("TEST_MESSAGE\n"));
        assertThat(lastRequest,hasHeader("Host", "localhost:9080"));
    }

    @Test
    public void returnsExpectedValue() {
        probe.addResponder(anyRequest(), respondWith("HELLO"));
        assertThat(probeTester.executeHttpGet(),is("HELLO"));
    }

    @Test
    public void returnsDifferentValuesAccordingToRequestCriteria() {
        probe.addResponder(bodyMatches(startsWith("A")), respondWith("HELLO A"));
        probe.addResponder(bodyMatches(startsWith("B")), respondWith("Bye B"));
        assertThat(probeTester.executeHttpPost("A"),is("HELLO A"));
        assertThat(probeTester.executeHttpPost("B"),is("Bye B"));
    }

    @Test
    public void whenCriteriaOverlapsTheLastOneIsSelected() {
        probe.addResponder(bodyMatches(startsWith("A")), respondWith("response 1"));
        probe.addResponder(bodyMatches(startsWith("A")), respondWith("response 2"));
        assertThat(probeTester.executeHttpPost("A"),is("response 2"));
    }

    @Test
    public void returnsLastSentRequestWithCookies() {
        probeTester.addCookie("cookie_name","cookie_value");
        probeTester.executeHttpGet();
        ServerRequest lastRequest = probe.probeForRequest(2, TimeUnit.SECONDS);
        assertThat(lastRequest.getCookieValue("cookie_name"), is("cookie_value"));
    }
}
