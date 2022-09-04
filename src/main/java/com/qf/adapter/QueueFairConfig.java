package com.qf.adapter;

public class QueueFairConfig {

	public static final int MODE_SIMPLE = 0;
	public static final int MODE_SAFE = 1;

	//Your Account Secret is shown on the Your Account page of
	//the Queue-Fair Portal.  If you change it there, you must
	//change it here too.
	public static String accountSecret = "REPLACE_WITH_YOUR_ACCOUNT_SECRET";

	//The System Name of your account
	public static String account = "REPLACE_WITH_YOUR_ACCOUNT_NAME";

	//Leave this set as is
	public static String filesServer = "files.queue-fair.net";

	//Time limit for Passed Strings to be considered valid,
	//before and after the current time.  Make sure your system clock is accurately set!
	public static int queryTimeLimitSeconds = 30;

	//Valid values are true, false, or an "IP_address".
	public static boolean debug = false;
	
	//How long to wait for network reads of config, in seconds
	//or Adapter Server (safe mode only)
	public static int readTimeoutSeconds = 5;

	//How long a cached copy of your Queue-Fair settings will be kept before downloading
	//a fresh copy.  Set this to 0 if you are updating your settings in the
	//Queue-Fair Portal and want to test your changes quickly, but remember
	//to set it back again when you are finished to reduce load on your server.
	public static int settingsCacheLifetimeMinutes = 5;

	//Whether or not to strip the Passed String from the URL
	//that the Visitor sees on return from the Queue or Adapter servers
	//(simple mode) - when set to true causes one additinal HTTP request
	//to your site but only on the first matching visit from a particular
	//visitor. The recommended value is true.
	public static boolean stripPassedString = true;

	//Whether to send the visitor to the Adapter server for counting (MODE_SIMPLE),
	//or consult the Adapter server (MODE_SAFE).  The recommended value is MODE_SAFE.
	//If you change this to MODE_SIMPLE, consider setting stripPassedString above to
	//false to make it easier for Google to crawl your pages.
	public static int adapterMode = MODE_SAFE;

	//Whether or not to cache QueueFairAdapter and StringBuilder instances for performance.
	//If you set this to True, make sure you call 
	public static boolean useThreadLocal = false;

	public static String protocol = "https";
	

}
