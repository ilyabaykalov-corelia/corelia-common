package ru.corelia.http;

import static ru.corelia.support.Json.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import ru.corelia.auth.JwtVerifier;
import ru.corelia.auth.AuthContext;
import ru.corelia.config.CoreliaConfig;
import ru.corelia.support.LogJson;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.*;

import javax.naming.ldap.LdapName;

import org.slf4j.MDC;

/** Проверяет идентичность сервиса по mTLS и независимо проверяет пользовательский JWT. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSecurity extends OncePerRequestFilter {
    public static final String AUTH = RequestSecurity.class.getName() + ".auth";
    private final JwtVerifier verifier;
    private final String service;
    private final CoreliaConfig config;

    public RequestSecurity(JwtVerifier verifier, CoreliaConfig config) {
        this.verifier = verifier;
        this.config = config;
        this.service = config.value("corelia.service");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        boolean gateway = service.equals("corelia-gateway");
        String incomingRequestId = request.getHeader("X-Request-Id");
        String requestId =
                !gateway
                                && incomingRequestId != null
                                && incomingRequestId.matches("[A-Za-z0-9._-]{1,100}")
                        ? incomingRequestId
                        : UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", requestId);
        long startedAt = System.nanoTime();
        int failureStatus = 0;
        String failureMessage = "";
        MDC.put("requestId", requestId);
        try {
            if (gateway) {
                String corsOrigin = config.value("CORS_ORIGIN");
                if (!corsOrigin.isBlank()) {
                    response.setHeader("Access-Control-Allow-Origin", corsOrigin);
                    response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
                    response.setHeader("Access-Control-Allow-Headers", "Content-Type,Authorization");
                }
                if (request.getMethod().equals("OPTIONS")) {
                    response.setStatus(204);
                    return;
                }
            }
            String path = request.getRequestURI();
            String peer = gateway ? "" : authorizeService(request, path);
            if (gateway && path.startsWith("/internal/"))
                throw new ApiException(404, "Маршрут не найден");
            if (!gateway && !path.startsWith("/internal/v1/"))
                throw new ApiException(404, "Маршрут не найден");
            boolean health =
                    request.getMethod().equals("GET")
                            && Set.of(
                                            "/api/core/v1/health",
                                            "/internal/v1/health")
                                    .contains(path);
            if (!health) {
                String authorization = request.getHeader("Authorization");
                if (!gateway && (authorization == null || authorization.isBlank())
                        && peer.equals("corelia-workflow-service") && workflowDocumentCommand(path, request.getMethod()))
                    request.setAttribute(AUTH, serviceContext(peer));
                else request.setAttribute(AUTH, verifier.authenticate(authorization));
            }
            if (request.getMethod().equals("HEAD"))
                throw new ApiException(404, "Маршрут не найден");
            chain.doFilter(request, response);
        } catch (ApiException error) {
            failureStatus = error.status();
            failureMessage = error.getMessage() == null ? "" : error.getMessage();
            if (!response.isCommitted()) {
                response.setStatus(error.status());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(write(object("message", error.getMessage())));
            }
        } catch (RuntimeException error) {
            failureStatus = 500;
            failureMessage = "Внутренняя ошибка сервера";
            throw error;
        } catch (IOException | ServletException error) {
            failureStatus = 500;
            failureMessage = "Ошибка обработки HTTP-запроса";
            throw error;
        } finally {
            int status = failureStatus > 0 ? failureStatus : response.getStatus();
            var details =
                    object(
                            "requestId", requestId,
                            "method", request.getMethod(),
                            "path", request.getRequestURI(),
                            "status", status,
                            "durationMs", (System.nanoTime() - startedAt) / 1_000_000);
            if (!failureMessage.isEmpty()) details.put("message", failureMessage);
            LogJson.info(
                    status >= 400 ? "HTTP request failed" : "HTTP request completed", details);
            MDC.remove("requestId");
        }
    }

    private String authorizeService(HttpServletRequest request, String path) {
        X509Certificate[] certificates =
                (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (certificates == null || certificates.length == 0)
            throw new ApiException(401, "Требуется клиентский сертификат сервиса");
        String peer = "";
        try {
            for (var rdn :
                    new LdapName(certificates[0].getSubjectX500Principal().getName()).getRdns())
                if (rdn.getType().equalsIgnoreCase("CN")) peer = rdn.getValue().toString();
        } catch (Exception error) {
            throw new ApiException(401, "Некорректный сертификат сервиса");
        }
        Set<String> allowed = new HashSet<>(List.of("corelia-gateway"));
        if (service.equals("corelia-data-service")) {
            allowed.add("corelia-document-service");
            allowed.add("corelia-workflow-service");
            allowed.add("corelia-attachment-service");
        }
        if (service.equals("corelia-workflow-service") || service.equals("corelia-attachment-service")) allowed.add("corelia-document-service");
        if (service.equals("corelia-document-service")) {
            allowed.add("corelia-attachment-service");
            allowed.add("corelia-workflow-service");
        }
        if (path.equals("/internal/v1/health")) allowed.add(service);
        if (!allowed.contains(peer))
            throw new ApiException(403, "Сервису запрещён доступ к этому API");
        if (service.equals("corelia-data-service")
                && !path.equals("/internal/v1/health")
                && !peer.equals("corelia-document-service")
                && !(peer.equals("corelia-workflow-service")
                        && (request.getMethod().equals("GET")
                                || request.getMethod().equals("POST")
                                && path.equals("/internal/v1/data/documents/search")))
                && !(peer.equals("corelia-attachment-service")
                        && request.getMethod().equals("GET")
                        && path.startsWith("/internal/v1/data/documents/attachments/")))
            throw new ApiException(403, "Сервису запрещён доступ к этому API");
        if (peer.equals("corelia-attachment-service")
                && !path.equals("/internal/v1/health")
                && !request.getMethod().equals("GET")
                && !(request.getMethod().equals("POST") && path.matches("/internal/v1/documents/[^/]+/[^/]+/(attachment-commands|workflow-readiness)")))
            throw new ApiException(403, "Сервису вложений разрешены чтение и команды состава документа");
        if (peer.equals("corelia-workflow-service")
                && !(service.equals("corelia-data-service")
                        && ((request.getMethod().equals("GET")
                                && path.startsWith("/internal/v1/data/"))
                                || (request.getMethod().equals("POST")
                                && path.equals("/internal/v1/data/documents/search"))))
                && !(request.getMethod().equals("GET") && path.matches("/internal/v1/documents/[^/]+/[^/]+"))
                && !(request.getMethod().equals("POST") && path.matches("/internal/v1/documents/[^/]+/[^/]+/workflow-commands/[^/]+")))
            throw new ApiException(403, "Сервису workflow разрешены чтение документа и workflow-команды");
        if (path.endsWith("/attachment-commands") && !peer.equals("corelia-attachment-service"))
            throw new ApiException(403, "Команды метаданных принимаются только от сервиса вложений");
        if ((path.startsWith("/internal/v1/initial-attachments/")
                        || path.startsWith("/internal/v1/staged-attachments/"))
                && !peer.equals("corelia-document-service"))
            throw new ApiException(403, "Подготовка первого файла доступна только сервису документов");
        if (peer.equals("corelia-document-service")
                && !path.startsWith("/internal/v1/process")
                && !path.equals("/internal/v1/health")
                && !(service.equals("corelia-data-service")
                        && path.startsWith("/internal/v1/data/"))
                && !(service.equals("corelia-attachment-service")
                        && request.getMethod().equals("POST")
                        && (path.matches("/internal/v1/(initial-attachments|staged-attachments)/[^/]+")
                        || path.matches("/internal/v1/documents/[^/]+/[^/]+/attachments")))
                && !(request.getMethod().equals("GET") && path.matches("/internal/v1/documents/[^/]+/[^/]+/workflow")))
            throw new ApiException(403, "Сервис документов может запускать процессы и читать их состояние");
        return peer;
    }

    private static boolean workflowDocumentCommand(String path, String method) {
        return (method.equals("GET") && path.matches("/internal/v1/documents/[^/]+/[^/]+"))
                || (method.equals("POST") && path.matches("/internal/v1/documents/[^/]+/[^/]+/workflow-commands/[^/]+"));
    }

    private static AuthContext serviceContext(String service) {
        return new AuthContext("", service, service, service, "", List.of(service), service);
    }
}
