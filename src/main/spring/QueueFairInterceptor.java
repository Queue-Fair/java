//You may want to put this class in a different package, so this source code is packageless.
//package spring;

import com.qf.adapter.QueueFairAdapter;
import com.qf.adapter.QueueFairConfig;
import com.qf.adapter.QueueFairServletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueueFairInterceptor extends HandlerInterceptorAdapter {
      @Value("${queuefair.accountSecret}")
      @Value("${queuefair.accountSystemName}")
      
      private String queuefairAccountSecret;
      private String queuefairAccountSystemName;

      private final Logger logger = LoggerFactory.getLogger(QueueFairInterceptor.class);
      public static final Pattern SKIP_PATTERN = Pattern.compile("REGEX_HERE");

      @Override
      public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object handler)
                  throws Exception {
            String urlToBeChecked = httpServletRequest.getRequestURL().toString() + (httpServletRequest.getQueryString() != null ? "?" + httpServletRequest.getQueryString() : "");
            
            //Comment out or remove the line below to disable logging.
            QueueFairConfig.debug = true;
            
            if(QueueFairConfig.debug) logger.info("starting queue fair filter");
            //do not check against certain files
            final Matcher matcher = SKIP_PATTERN.matcher(urlToBeChecked);
            if (matcher.find() || httpServletRequest.getHeader("USER-AGENT").contains("REGEX_HERE")) {
                  if(QueueFairConfig.debug) logger.debug("skipping queuing of url " + urlToBeChecked);
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

                  //Do not comment out the below.
                  QueueFairAdapter adapter = QueueFairAdapter.getAdapter(new QueueFairServletService(httpServletRequest, httpServletResponse));

                  //You may need to amend these if your web server is behind a Proxy or CDN.
                  adapter.remoteIPAddress = httpServletRequest.getRemoteAddr();
                  if(QueueFairConfig.debug) logger.info(urlToBeChecked);
                  adapter.requestedURL = urlToBeChecked;
                  adapter.userAgent = httpServletRequest.getHeader("user-agent");

                  //Calling isContinue() on the adapter validates the user based on PassedString, PassedCookie and your queue settings from the Portal.
                  //If the user needs to go to a Queue-Fair server, the adapter will send the necessary redirect.
                  if (adapter.isContinue()) {
                        return true;
                  } else {
                        if(QueueFairConfig.debug) logger.info("user gets redirected by queue fair, processing of request stopped");
                        //Stop producing the page.
                        return false;
                  }
            }
            return true;
      }
}
