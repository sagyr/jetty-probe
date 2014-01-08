package com.wix.jettyprobe;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hamcrest.Matcher;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author sagyr
 * @since 1/15/13
 *
 * This is a test utility class for validating interactions against an http server
 * It enables the test to query the server for its incoming headers and body of the last incoming message
 */
public class JEmbeddedJettyProbe {

    private void respond(ServerRequest request, HttpServletResponse response) throws IOException {
        Responder responder = findResponder(request);
        responder.handle(request, response);
    }


    public void start() {
        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public void stop() {
        try {
            server.stop();
            server.join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ServerRequest probeForRequest(long n, TimeUnit unit){
        try {
            return (ServerRequest) incoming.poll(n,unit);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addResponder(Matcher<ServerRequest> requestMatcher, Responder responder) {
        this.responders.add(new ResponseMatch(responder,requestMatcher));
    }

    public static class ServerRequest {

        public final Map<String, String> headers;

        public final String body;
        private final Cookie[] cookies;
        public ServerRequest(Map<String, String> headers, String body) {
            this(headers,body,new Cookie[0]);
        }

        public ServerRequest(Map<String, String> headers, String body, Cookie[] cookies) {
            this.headers = headers;
            this.body = body;
            this.cookies = cookies;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ServerRequest that = (ServerRequest) o;

            if (!body.equals(that.body)) return false;
            if (!headers.equals(that.headers)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = headers.hashCode();
            result = 31 * result + body.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "ServerRequest{" +
                    "headers=" + headers +
                    ", body='" + body + '\'' +
                    '}';
        }

        public String getCookieValue(String cookieName) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(cookieName))
                    return cookie.getValue();
            }
            return null;
        }

    }
    public interface Responder {

        public void handle(ServerRequest request, HttpServletResponse response);
    }
    private static class NoopResponder implements Responder {

        @Override
        public void handle(ServerRequest request, HttpServletResponse response) {
            //response.setStatus(200);
            try {
                response.getOutputStream().print("foo");
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }
    private LinkedBlockingQueue incoming = new LinkedBlockingQueue<ServerRequest>();
    private Server server = new Server(9080);
    private List<ResponseMatch> responders = Collections.synchronizedList(new ArrayList<ResponseMatch>());


    public JEmbeddedJettyProbe() {
        server.setHandler(handler);
    }

    private class ResponseMatch {
        public final Responder responder;
        public final Matcher<ServerRequest> matcher;

        private ResponseMatch(Responder responder, Matcher<ServerRequest> matcher) {
            this.responder = responder;
            this.matcher = matcher;
        }

        public boolean matches(ServerRequest request) {
            return matcher.matches(request);
        }
    }


    AbstractHandler handler = new AbstractHandler() {

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            ServerRequest fullyReadRequest = new ServerRequest(headers(request), body(request), cookies(request));
            incoming.add(fullyReadRequest);
            respond(fullyReadRequest, response);
            baseRequest.setHandled(true);
        }

        private Cookie[] cookies(HttpServletRequest request) {
            return request.getCookies() == null ? new Cookie[0] : request.getCookies();
        }

        private String body(HttpServletRequest request) {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String currentLine = "";
                while ((currentLine = reader.readLine()) != null)
                    sb.append(currentLine).append("\n");
                return sb.toString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private Map<String,String> headers(HttpServletRequest request) {
            Map<String,String> result = new HashMap<String, String>();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                result.put(headerName,request.getHeader(headerName));
            }
            return result;
        }

    };

    private Responder findResponder(ServerRequest request) {
        Responder result = new NoopResponder();
        for (ResponseMatch responder : responders) {
            if (responder.matches(request)) {
                result = responder.responder;
            }
        }
        return result;
    }
}
