//You may want to put this class in a different package, so this source code is packageless.
//package spring;

import com.qf.adapter.QueueFairAdapter;
import com.qf.adapter.QueueFairConfig;
import com.qf.adapter.QueueFairJakartaServletService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class QueueFairJakartaInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(QueueFairInterceptor.class);
    public static final Pattern SKIP_PATTERN = Pattern.compile("REGEX_HERE");

    private final String queuefairAccountSecret;
    private final String queuefairAccountSystemName;

    public QueueFairJakartaInterceptor(@Value("${queuefair.accountSecret}") String queuefairAccountSecret, @Value("${queuefair.accountSystemName}") String queuefairAccountSystemName) {
        this.queuefairAccountSecret = queuefairAccountSecret;
        this.queuefairAccountSystemName = queuefairAccountSystemName;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {

        final String urlToBeChecked = request.getRequestURL().toString() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");

        // enable/disable your adapter’s debug
        QueueFairConfig.debug = true;

        if (QueueFairConfig.debug) {
            logger.info("starting queue fair interceptor");
        }

        // skip some URLs or user agents
        final Matcher matcher = SKIP_PATTERN.matcher(urlToBeChecked);
        final String ua = String.valueOf(request.getHeader("User-Agent")); // header name is case-insensitive

        if (matcher.find() || ua.contains("REGEX_HERE")) {
            if (QueueFairConfig.debug) {
                logger.debug("skipping queuing of url {}", urlToBeChecked);
            }
            return true; // allow request through
        }

        // required config from your portal
        QueueFairConfig.account = queuefairAccountSystemName;
        QueueFairConfig.accountSecret = queuefairAccountSecret;

        // defaults (leave as-is or externalize)
        QueueFairConfig.queryTimeLimitSeconds = 30;
        QueueFairConfig.readTimeoutSeconds = 1;
        QueueFairConfig.settingsCacheLifetimeMinutes = 5;
        QueueFairConfig.stripPassedString = true;
        QueueFairConfig.adapterMode = QueueFairConfig.MODE_SAFE;
        QueueFairConfig.useThreadLocal = false;

        // create adapter bound to current request/response
        QueueFairAdapter adapter = QueueFairAdapter.getAdapter(new QueueFairJakartaServletService(request, response));
        adapter.remoteIPAddress = request.getRemoteAddr();
        adapter.requestedURL = urlToBeChecked;
        adapter.userAgent = ua;

        if (QueueFairConfig.debug) {
            logger.info(urlToBeChecked);
        }

        // let adapter decide whether to continue or redirect
        boolean continueProcessing = adapter.isContinue();
        if (!continueProcessing) {
            if (QueueFairConfig.debug) {
                logger.info("user gets redirected by queue fair, processing of request stopped");
            }
            // adapter already sent redirect — stop the chain
            return false;
        }

        return true;
    }
}
