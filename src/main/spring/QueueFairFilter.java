//You may want to put this class in a different package, so this source code is packageless.
//package spring;

import com.qf.adapter.QueueFairAdapter;
import com.qf.adapter.QueueFairConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/* These imports for Spring Boot 3 / Tomcat 10 */
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.qf.adapter.QueueFairJakartaServletService;

/* These imports for Spring Boot 2 / Tomcat 9 */
/* 
import org.jetbrains.annotations.NotNull;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.qf.adapter.QueueFairServletService;
*/

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QueueFairFilter extends OncePerRequestFilter {

    @Value("${queuefair.accountSecret}")
    @Value("${queuefair.accountSystemName}")

    private String queuefairAccountSecret;
    private String queuefairAccountSystemName;
    private final Logger logger = LoggerFactory.getLogger(QueueFairFilter.class);
    public static final Pattern SKIP_PATTERN = Pattern.compile("REGEX_HERE");

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws ServletException, IOException {
        String urlToBeChecked = request.getRequestURL().toString() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        
        //Comment out or remove the line below to disable logging.
        QueueFairConfig.debug = false;
  
        if(QueueFairConfig.debug) logger.debug("starting queue fair filter");      
        //do not check against certain files
        final Matcher matcher = SKIP_PATTERN.matcher(urlToBeChecked);
        if (matcher.find() || request.getHeader("USER-AGENT").contains("REGEX_HERE")) {
            if(QueueFairConfig.debug) logger.debug("skipping queuing of url " + urlToBeChecked);
            filterChain.doFilter(request, response);
        } else {
            //You must set these to the values shown on the Account -> Your Account page of the Portal.
            QueueFairConfig.account = queuefairAccountSystemName;
            QueueFairConfig.accountSecret = queuefairAccountSecret;

            //The following are default values and can be commented out.
            QueueFairConfig.queryTimeLimitSeconds = 30;        //How long a PassedString may be turned into a PassedCookie.  Make sure your system clock is set to network time!
            QueueFairConfig.readTimeoutSeconds = 1;    //How long to wait for network reads of config, in seconds, or Adapter Server (safe mode only)
            QueueFairConfig.settingsCacheLifetimeMinutes = 5;  //How long a downloaded copy of your Settings from the portal remains fresh.
            QueueFairConfig.stripPassedString = true; //Whether to strip the Passed String from the URL that the Visitor sees on return from the Queue or Adapter servers (simple mode) - when set to true causes one additional HTTP request to your site but only on the first matching visit from a particular visitor. The recommended value is true.
            QueueFairConfig.adapterMode = QueueFairConfig.MODE_SAFE;    //Whether to send the visitor to the Adapter server for counting (MODE_SIMPLE), or consult the Adapter server (MODE_SAFE).  The recommended value is MODE_SAFE.
            QueueFairConfig.useThreadLocal = false;    //For maximum performance, set this to true BUT ONLY IF your web server uses a fixed thread pool and does NOT create new worker threads once its size is reached.

            //For Spring Boot 3 / Tomcat 10+
            QueueFairAdapter adapter = QueueFairAdapter.getAdapter(new QueueFairJakartaServletService(request, response));

            //For Spring Boot 2 / Tomcat 9
            // QueueFairAdapter adapter = QueueFairAdapter.getAdapter(new QueueFairServletService(request, response));

            //You may need to amend these if your web server is behind a Proxy or CDN.
            adapter.remoteIPAddress = request.getRemoteAddr();
            if(QueueFairConfig.debug) logger.info(urlToBeChecked);
            adapter.requestedURL = urlToBeChecked;
            adapter.userAgent = request.getHeader("user-agent");

            //Calling isContinue() on the adapter validates the user based on PassedString, PassedCookie and your queue settings from the Portal.
            //If the user needs to go to a Queue-Fair server, the adapter will send the necessary redirect.
            if (adapter.isContinue()) {
                filterChain.doFilter(request, response);
            } else {
                if(QueueFairConfig.debug) logger.info("user gets redirected by queue fair, processing of request stopped. requested url was: " + urlToBeChecked);
                //Stop producing the page.
            }
        }
    }
}
