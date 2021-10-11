<%@ page language="java" import="com.qf.adapter.*"%><%

//Comment out or remove the line below to disable debug level logging.
QueueFairConfig.debug=true;

//You must set these
QueueFairConfig.account="REPLACE_WITH_YOUR_ACCOUNT_SYSTEM_NAME";
QueueFairConfig.accountSecret="REPLACE_WITH_YOUR_ACCOUNT_SECRET";

//The following are default values and can be commented out.
QueueFairConfig.queryTimeLimitSeconds = 30;		//How long a PassedString may be turned into a PassedCookie.  Make sure your system clock is set to network time!	
QueueFairConfig.readTimeoutSeconds = 5;    //How long to wait for network reads of config, in seconds, or Adapter Server (safe mode only)
QueueFairConfig.settingsCacheLifetimeMinutes = 5;  //How long a downloaded copy of your Settings from the portal remains fresh.
QueueFairConfig.stripPassedString = true; ////Whether or not to strip the Passed String from the URL that the Visitor sees on return from the Queue or Adapter servers (simple mode) - when set to true causes one additinal HTTP request to your site but only on the first matching visit from a particular visitor. The recommended value is true.
QueueFairConfig.adapterMode=QueueFairConfig.MODE_SAFE;	//Whether to send the visitor to the Adapter server for counting (MODE_SIMPLE), or consult the Adapter server (MODE_SAFE).  The recommended value is MODE_SAFE.
QueueFairConfig.useThreadLocal=false;	//For maximum performance, set this to true BUT ONLY IF your web server uses a fixed thread pool and does NOT create new worker threads once its size is reached.

//Do not comment out the below.
QueueFairAdapter adapter=QueueFairAdapter.getAdapter(new QueueFairServletService(request,response)); 

//You may need to amend these if your web server is behind a Proxy or CDN.		
adapter.remoteIPAddress = request.getRemoteAddr();
adapter.requestedURL = request.getRequestURL().toString() + (request.getQueryString()!=null ? "?"+request.getQueryString() : "");
adapter.userAgent = request.getHeader("user-agent"); 

/* If you JUST want to validate a PassedCookie, use this on a path that does NOT match any queue's Activation Rules: */
/*
if(adapter.requestedURL.indexOf("/path/to/page")!=-1) {
    try {
		int passedLifetimeMinutes = 60;		//One hour passed lifetime
		String queueName="QUEUE_NAME_FROM_PORTAL";
		String queueSecret="QUEUE_SECRET_FROM_PORTAL";
		if(!adapter.validateCookie(queueSecret,
    			passedLifetimeMinutes, 
    			adapter.getService().getCookie(QueueFairAdapter.COOKIE_NAME_BASE+queueName))) {
		            adapter.redirect("https://"+QueueFairConfig.account+".queue-fair.net/"+queueName+"?qfError=InvalidCookie",0);		
		}
    } catch (Exception exception) {
    	
    }
}
*/

/* To run the full Adapter, use this instead */
//Calling isContinue() on the adapter validates the user based on PassedString, PassedCookie and your queue settings from the Portal.
//If the user needs to go to a Queue-Fair server, the adapter will send the necessary redirect.
if(!adapter.isContinue()) {
	//Stop producing the page.
	return;
}

%>

