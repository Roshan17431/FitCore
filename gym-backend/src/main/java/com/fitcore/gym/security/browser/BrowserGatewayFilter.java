package com.fitcore.gym.security.browser;

import io.jsonwebtoken.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import com.fitcore.gym.security.SharedAbuseLimiter;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.http.MediaType;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;

/** Internal proof is method/target/body/cookie-bound, short-lived, and single-use across instances. */
final class BrowserGatewayFilter extends OncePerRequestFilter {
    private final BrowserSecurityMaintenance maintenance;
    private final SharedAbuseLimiter limiter;
    private final PublicKey publicKey;
    private final String origin;
    BrowserGatewayFilter(BrowserSecurityMaintenance maintenance,SharedAbuseLimiter limiter,String origin,String pem){
        this.maintenance=maintenance;this.limiter=limiter;this.origin=origin;
        try{
            var uri=java.net.URI.create(origin);
            if(!"https".equals(uri.getScheme()) || !Set.of("127.0.0.1","localhost","[::1]").contains(uri.getHost()) || uri.getPort()<1 || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !uri.getRawPath().isEmpty())throw new IllegalArgumentException();
            publicKey=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getMimeDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", ""))));
            if(!(publicKey instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength()<2048)throw new IllegalArgumentException();
        }catch(Exception invalid){throw new IllegalStateException("Browser spike requires exact loopback HTTPS origin and synthetic RSA public key",invalid);}
    }
    static void error(HttpServletResponse response,int status,String code)throws IOException{
        response.setStatus(status);response.setContentType("application/json");response.setHeader("Cache-Control","no-store");response.getWriter().write("{\"message\":\""+code+"\"}");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        response.setHeader("Cache-Control","no-store");
        if("GET".equals(request.getMethod()) && "/api/health".equals(request.getRequestURI())){chain.doFilter(request,response);return;}
        try{
            String proof=request.getHeader("X-FitCore-Gateway");
            if(proof==null || proof.length()>8192)throw new BrowserSessionService.Failure(401,"GATEWAY_PROOF_REQUIRED");
            for(String header:Set.of("Origin","Sec-Fetch-Site","X-FitCore-Gateway","Cookie","Content-Type","X-CSRF-TOKEN","X-FitCore-Bootstrap")) {
                if(Collections.list(request.getHeaders(header)).size()>1)throw new BrowserSessionService.Failure(403,"AMBIGUOUS_HEADER");
            }
            if(!request.isSecure())throw new BrowserSessionService.Failure(401,"GATEWAY_TLS_REQUIRED");
            if(!"same-origin".equals(request.getHeader("Sec-Fetch-Site")))throw new BrowserSessionService.Failure(403,"SAME_ORIGIN_REQUIRED");
            boolean unsafe=!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod());
            if((unsafe || request.getHeader("Origin")!=null) && !origin.equals(request.getHeader("Origin")))throw new BrowserSessionService.Failure(403,"EXACT_ORIGIN_REQUIRED");
            if(request.getHeader("Authorization")!=null || request.getHeader("X-Gym-ID")!=null || request.getHeader("Forwarded")!=null || request.getHeader("X-Forwarded-For")!=null)throw new BrowserSessionService.Failure(400,"UNTRUSTED_HEADER");
            if(request.getParameterMap().keySet().stream().map(k->k.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT)).anyMatch(k->Set.of("gymid","tenantid").contains(k)))throw new BrowserSessionService.Failure(400,"TENANT_SELECTOR_REJECTED");
            if(request.getContentLengthLong()>1048576)throw new BrowserSessionService.Failure(413,"BODY_TOO_LARGE");
            byte[] body=request.getInputStream().readNBytes(1048577);
            if(body.length>1048576)throw new BrowserSessionService.Failure(413,"BODY_TOO_LARGE");
            Jws<Claims> signed=Jwts.parserBuilder().setSigningKey(publicKey).requireIssuer("fitcore-local-bff").requireAudience("fitcore-browser-spike").setAllowedClockSkewSeconds(5).build().parseClaimsJws(proof);
            Claims c=signed.getBody();Date issue=c.getIssuedAt(),expiry=c.getExpiration();Instant now=Instant.now();
            String target=request.getRequestURI()+(request.getQueryString()==null?"":"?"+request.getQueryString());
            String cookies=Objects.toString(request.getHeader("Cookie"),"");
            Object clientIp=c.get("clientIp");
            boolean loopbackClient=clientIp instanceof String && Set.of("127.0.0.1","::1","::ffff:127.0.0.1").contains(clientIp);
            if(!"RS256".equals(signed.getHeader().getAlgorithm()) || issue==null || expiry==null || issue.toInstant().isAfter(now.plusSeconds(5)) || issue.toInstant().isBefore(now.minusSeconds(35)) || !expiry.after(issue) || expiry.getTime()-issue.getTime()>30000 || !request.getMethod().equals(c.get("method",String.class)) || !target.equals(c.get("target",String.class)) || !BrowserSessionService.digest(body).equals(c.get("bodySha256",String.class)) || !BrowserSessionService.digest(cookies).equals(c.get("cookieSha256",String.class)) || !loopbackClient || c.getId()==null || !c.getId().matches("[a-fA-F0-9-]{36}"))throw new BrowserSessionService.Failure(401,"INVALID_GATEWAY_PROOF");
            // Shared, bounded and independently committed before business transactions.
            try {
                if(!maintenance.consumeNonce(BrowserSessionService.digest(c.getId()),now.plusSeconds(120)))
                    throw new BrowserSessionService.Failure(401,"GATEWAY_PROOF_REPLAYED");
                if(Set.of("/api/browser/bootstrap","/api/browser/login").contains(request.getRequestURI())
                    && !limiter.allow(request.getRequestURI(),(String)clientIp))
                    throw new BrowserSessionService.Failure(429,"RATE_LIMITED");
            }catch(DataAccessException | TransactionException unavailable){
                throw new BrowserSessionService.Failure(503,"SECURITY_STORE_UNAVAILABLE");
            }
            if("/api/browser/bootstrap".equals(request.getRequestURI()) && (!"POST".equals(request.getMethod()) || !"1".equals(request.getHeader("X-FitCore-Bootstrap")) || !bootstrapJson(request.getContentType())))throw new BrowserSessionService.Failure(403,"BOOTSTRAP_ADMISSION_REQUIRED");
            if(request.getRequestURI().startsWith("/api/browser/")) {
                String path=request.getRequestURI();
                String required=Set.of("/api/browser/csrf","/api/browser/state").contains(path)?"GET":Set.of("/api/browser/bootstrap","/api/browser/login","/api/browser/logout","/api/browser/ack").contains(path)?"POST":null;
                if(required==null)throw new BrowserSessionService.Failure(404,"BROWSER_ROUTE_NOT_FOUND");
                if(!required.equals(request.getMethod()))throw new BrowserSessionService.Failure(405,"BROWSER_METHOD_REJECTED");
            }
            chain.doFilter(new BodyRequest(request,body),response);
        }catch(BrowserSessionService.Failure failure){error(response,failure.status,failure.getMessage());}
        catch(JwtException | IllegalArgumentException failure){error(response,401,"INVALID_GATEWAY_PROOF");}
    }
    private static boolean bootstrapJson(String contentType){
        if(contentType==null)return false;
        try{
            MediaType mediaType=MediaType.parseMediaType(contentType);
            return "application".equalsIgnoreCase(mediaType.getType()) && "json".equalsIgnoreCase(mediaType.getSubtype());
        }catch(InvalidMediaTypeException invalid){return false;}
    }
    private static final class BodyRequest extends HttpServletRequestWrapper{
        private final byte[] body;BodyRequest(HttpServletRequest request,byte[] body){super(request);this.body=body;}
        @Override public ServletInputStream getInputStream(){var in=new ByteArrayInputStream(body);return new ServletInputStream(){public int read(){return in.read();}public boolean isFinished(){return in.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous bounded gateway only");}};}
        @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
    }
}
