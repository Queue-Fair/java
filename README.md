---
## Queue-Fair Java Adapter README & Installation Guide

Queue-Fair can be added to any web server easily in minutes.  You will need a Queue-Fair account - please visit https://queue-fair.com/free-trial if you don't already have one.  You should also have received our Technical Guide.  You can find out all about Queue-Fair at https://queue-fair.com

## Client-Side JavaScript Adapter

Most of our customers prefer to use the Client-Side JavaScript Adapter, which is suitable for all sites that wish solely to protect against overload.

To add the Queue-Fair Client-Side JavaScript Adapter to your web server, you don't need the files included in this extension.

Instead, add the following tag to the `<head>` section of your pages:
 
```
<script data-queue-fair-client="CLIENT_NAME" src="https://files.queue-fair.net/queue-fair-adapter.js"></script>`
```

Replace CLIENT_NAME with the account system name visibile on the Account -> Your Account page of the Queue-Fair Portal

You shoud now see the Adapter tag when you perform View Source after refreshing your pages.

And you're done!  Your queues and activation rules can now be configured in the Queue-Fair Portal.

## Server-Side Adapter

The Server-Side Adapter means that your web server communicates directly with the Queue-Fair servers, rather than your visitors' browsers.

This can introduce a dependency between our systems, which is why most customers prefer the Client-Side Adapter.  See Section 10 of the Technical Guide for help regarding which integration method is most suitable for you.

The Server-Side Adapter is a small library that will run when visitors access your site.  It periodically checks to see if you have changed your Queue-Fair settings in the Portal, but other than that if the visitor is requesting a page that does not match any queue's Activation Rules, it does nothing.

If a visitor requests a page that DOES match any queue's Activation Rules, the Adapter makes a determination whether that particular visitor should be queued.  If so, the visitor is sent to our Queue Servers and execution and generation of the page for that HTTP request for that visitor will cease.  If the Adapter determines that the visitor should not be queued, it sets a cookie to indicate that the visitor has been processed and your page executes and shows as normal.

Thus the Server-Side Adapter prevents visitors from skipping the queue by disabling the Client-Side JavaScript Adapter, and also reduces load on your web server when things get busy.

This guide assumes you already have a functional Java web server. Tomcat is used as a base example - see later on in this document for other Java web servers.  You don't have to use a servlet container to use this code.

**1.** **IMPORTANT:** Make sure the system clock on your webserver is accurately set to network time! On unix systems, this is usually done with the ntp package.  It doesn't matter which timezone you are using. On Windows 10, it's under Settings -> Date & Time - make sure Set the time automatically is On.  On Windows Server, this procedure may vary.

**2.** Copy lib/queuefairadapter.jar somewhere that your JVM can find it in the Classpath.  On Tomcat, you can put it in /path/to/tomcat/lib or /path/to/tomcat/webapps/your_webapp/WEB-INF/lib.

**3.** The library requires GSON to parse JSON from the Queue-Fair servers.  So, add https://queue-fair.com/adapters/gson-2.10.1.jar to the same folder as 2. above.

**4.** Copy queue-fair-adapter.jsp to somewhere in your webapp.  Edit it and (at a minimum) enter your Account System Name and Secret from the Queue-Fair Portal where specified in that file.  If you are using Spring, you can instead use the example Filter or Interceptor code in the src/main/spring folder of this distribution.  If you already have filters or interceptors in your chain, you should specify that Queue-Fair runs first - or you could just rename the classes with AAA at the front, as often frameworks run these classes in alphabetical order when no other ordering information is specified.

**5.** Note the `settingsFileCacheLifetimeMinutes` setting - this is how often your web server will check for updated settings from the Queue-Fair queue servers (which change when you hit Make Live).   The default value is 5 minutes.  You can set this to -1 to disable local caching but **DON'T DO THIS** on your production machine/live queue with real people, or your server may collapse under load.  On download, your settings are parsed and stored in a memory cache.  If you restart your webserver a fresh copy will be downloaded.

**6.** Note the `adapterMode` setting.  MODE_SAFE is recommended - we also support MODE_SIMPLE - see the Technical Guide for further details.

**7.** **IMPORTANT** Note the `debug` setting - this is set to true by default, BUT you MUST set debug to false on production machines/live queues as otherwise your web logs will rapidly become full.

On tomcat, the debug logging statements will appear in /path/to/tomcat/logs/catalina.out .  The debug loglevel is Level.INFO, so you need your logging to be this level or lower to see them.

**8.** When you have finished making changes to `queue-fair-adapter.jsp`, save it.  If you are using Spring, you will need to set up the two @Values.

**NOTE:** If your web server is sitting behind a proxy, CDN or load balancer, you may need to edit the adapter.remoteIPAddress, adapter.requestedURL and adapter.userAgent property sets that occur in queue-fair-adapter.jsp before the isContinue() method is called to use values from forwarded headers instead.  If you need help with this, contact Queue-Fair support.  You may also need to create your own QueueFairService implementation - see below.


**9.** To add the Adapter to your JSP pages, all you need do is add

<%@ include file="queue-fair-adapter.jsp" %>

It's normally placed at the end of the very first line in the JSP page, so that no characters are output before the adapter is run - so immediately after the opening <%@ page ... > tag.  For Spring implementations, consult your implementation documentation for instructions on how to add the Filter or Interceptor.

In the case where the Adapter sends the request elsewhere (for example to show the user a queue page), the `isContinue()` method will return false and the rest of the page will NOT be generated, which means it isn't sent to the visitor's browser, which makes it secure, as well as preventing your server from having to do the work of producing the rest of the page.  It is important that this code runs *before* any other framework you may have in place initialises so that your server can perform this under load, when your full site framework is too onerous to load.


That's it you're done!

### To test the Server-Side Adapter

Use a queue that is not in use on other pages, or create a new queue for testing.

#### Testing SafeGuard
Set up an Activtion Rule to match the page you wish to test.  Hit Make Live.  Go to the Settings page for the queue.  Put it in SafeGuard mode.  Hit Make Live again.

In a new Private Browsing window, visit the page on your site.  

 - Verify that you can see debug output from the Adapter in your Visual Studio console and/or Event Log.
 - Verify that a cookie has been created named `Queue-Fair-Pass-queuename`, where queuename is the System Name of your queue
 - If the Adapter is in Safe mode, also verify that a cookie has been created named QueueFair-Store-accountname, where accountname is the System Name of your account (on the Your Account page on the portal).
 - If the Adapter is in Simple mode, the Queue-Fair-Store cookie is not created.
 - Hit Refresh.  Verify that the cookie(s) have not changed their values.

#### Testing Queue
Go back to the Portal and put the queue in Demo mode on the Queue Settings page.  Hit Make Live.  Delete any Queue-Fair-Pass cookies from your browser.  In a new tab, visit https://accountname.queue-fair.net , and delete any Queue-Fair-Pass or Queue-Fair-Data cookies that appear there.  Refresh the page that you have visited on your site.

 - Verify that you are now sent to queue.
 - When you come back to the page from the queue, verify that a new QueueFair-Pass-queuename cookie has been created.
 - If the Adapter is in Safe mode, also verify that the QueueFair-Store cookie has not changed its value.
 - Hit Refresh.  Verify that you are not queued again.  Verify that the cookies have not changed their values.

**IMPORTANT:**  Once you are sure the Server-Side Adapter is working as expected, you may remove the Client-Side JavaScript Adapter tag from your pages, and don't forget to disable debug level logging by setting `QueueFairConfig.debug` to false, and also set `QueueFairConfig.settingsFileCacheLifetimeMinutes` to at least 5.

**IMPORTANT:**  Responses that contain a Location header or a Set-Cookie header from the Adapter must not be cached!  You can check which cache-control headers are present using your browser's Inspector Network Tab.

### For maximum security

The Server-Side Adapter contains multiple checks to prevent visitors bypassing the queue, either by tampering with set cookie values or query strings, or by sharing this information with each other.  When a tamper is detected, the visitor is treated as a new visitor, and will be sent to the back of the queue if people are queuing.

 - The Server-Side Adapter checks that Passed Cookies and Passed Strings presented by web browsers have been signed by our Queue-Server.  It uses the Secret visible on each queue's Settings page to do this.
 - If you change the queue Secret, this will invalidate everyone's cookies and also cause anyone in the queue to lose their place, so modify with care!
 - The Server-Side Adapter also checks that Passed Strings coming from our Queue Server to your web server were produced within the last 300 seconds, which is why your clock must be accurately set.
 -  The Server-Side Adapter also checks that passed cookies were produced within the time limit set by Passed Lifetime on the queue Settings page, to prevent visitors trying to cheat by tampering with cookie expiration times or sharing cookie values.  So, the Passed Lifetime should be set to long enough for your visitors to complete their transaction, plus an allowance for those visitors that are slow, but no longer.
 - The signature also includes the visitor's USER_AGENT, to further prevent visitors from sharing cookie values.

## Validating Cookies (Hybrid Security Model)
In many cases it is better to use the Client-Side Javascript Adapter to send and receive people to and from the queue - the reasons for this are covered in the Technical Guide.  If your aim with the Server-Side adapter is merely to prevent the very small percentage of people who attempt to bypass the queue from ordering, it is better to leave the Client-Side Javascript Adapter in place and use the validateCookie() method of the Adapter instead. Example code is also included in the `queue-fair-adapter.jsp` file included in this distribution.

## If you are NOT using Tomcat

The example code in queue-fair-adapter.jsp will work with any other servlet container too, such as JBoss, or you can use the Filter or Interceptor code in the spring folder.

To ensure compatibility with the widest possible range of Java environments, QueueFairAdapter encapsulates all interations with the HTTP response into an object that implements the interface QueueFairService.  The included QueueFairServletService class should be used so long as you have HttpServletRequest and HttpServletResponse objects available with which to instantiate it.

If your java web server does not support HttpServletRequest or HttpServletResponse, you will need to code up a new class that implements the QueueFairService interface and use that instead of the QueueFairServletService class provided.  Your new class will need to do the same things as the QueueFairServletService - so please do look at the source code for further guidance.


## AND FINALLY

Remember we are here to help you! The integration process shouldn't take you more than an hour - so if you are scratching your head, ask us.  Many answers are contained in the Technical Guide too.  We're always happy to help!
