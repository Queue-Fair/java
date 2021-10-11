package com.qf.adapter;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class QueueFairAdapter extends BaseAdapter {

    static final String synch = "synch";

    static ThreadLocal<QueueFairAdapter> adapters = new ThreadLocal<QueueFairAdapter>() {
        @Override
        public QueueFairAdapter initialValue() {
            return new QueueFairAdapter();
        }
    };

    private static volatile QueueFairSettings memSettings;

    private static volatile long lastMemSettings;

    public static final String COOKIE_NAME_BASE = "QueueFair-Pass-";

    private QueueFairService service;

    private boolean addedCacheControlHeader = false;

    // State
    public QueueFairSettings settings = null;
    private Map<String, Object> adapterResult = null;
    public QueueFairSettings.Queue adapterQueue = null;
    private HashSet<String> passedQueues = null;
    private String uid = null;
    private boolean continuePage = true;
    public long now = -1;

    // Input
    public String requestedURL = null;
    public String userAgent = null;
    public String remoteIPAddress = null;
    public String extra = null;

    private QueueFairAdapter reset() {
        settings = null;
        adapterResult = null;
        adapterQueue = null;
        passedQueues = null;
        uid = null;
        continuePage = true;
        now = -1;
        requestedURL = null;
        remoteIPAddress = null;
        userAgent = null;
        extra = null;
        return this;
    }

    /**
     * Call this method to get an Adapter object from a pool. Use this when you are
     * using a thread pool, such as Tomcat. Reusing objects improves performance.
     *
     * @param service An object embodying the functions required from the HTTP
     *                request and response in order to validate and process the
     *                visitor.
     * @return a QueueFairAdapter ready to call go().
     */
    public static QueueFairAdapter getAdapter(QueueFairService service) {
        if (QueueFairConfig.useThreadLocal) {
            QueueFairAdapter adapter = adapters.get();
            if (adapter == null) {
                return new QueueFairAdapter(service);
            }

            return adapter.reset().init(service);
        }
        return new QueueFairAdapter(service);
    }

    /**
     * Convenience constructor. Make sure you call setService() and setConfig()
     * before running an adapter created this way. getAdapter() is the preferred
     * method of obtaining an instance.
     */
    public QueueFairAdapter() {

    }

    private QueueFairAdapter init(QueueFairService service) {
        if (QueueFairConfig.debug) {
            log.setLevel(Level.INFO);
        } else {
            log.setLevel(Level.WARNING);
        }

        this.service = service;

        return this;
    }

    /**
     * Another convenience constructor. getAdapter() is the preferred method of
     * obtaining an instance.
     *
     * @param service a QueueFairService instance.
     */
    public QueueFairAdapter(QueueFairService service) {
        init(service);
    }

    public static String getStr(Map<String, Object> m, String key) {
        Object obj = m.get(key);
        if (obj == null)
            return null;
        if (obj instanceof String) {
            return (String) obj;
        }
        return "" + obj;
    }

    public static int getInt(Map<String, Object> m, String key) {
        Object obj = m.get(key);
        if (obj == null)
            return 0;
        if (obj instanceof Integer) {
            return (int) obj;
        } else if (obj instanceof Double) {
            return (int) (double) obj;
        }
        return Integer.parseInt(obj.toString());
    }

    public static boolean getBool(Map<String, Object> m, String key) {
        Object obj = m.get(key);
        if (obj == null)
            return false;
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        return Boolean.parseBoolean(obj.toString());
    }

    private boolean isMatch(QueueFairSettings.Queue queue) {

        if (queue == null || queue.rules == null)
            return false;

        return isMatchArray(queue.rules);

    }

    private boolean isMatchArray(QueueFairSettings.Rule[] arr) {
        if (arr == null)
            return false;
        boolean firstOp = true;
        boolean state = false;

        for (QueueFairSettings.Rule rule : arr) {
            String operator = rule.operator;

            if (operator != null) {
                if (operator.equals("And") && !state) {
                    return false;
                } else if (operator.equals("Or") && state) {
                    return true;
                }
            }

            boolean ruleMatch = isRuleMatch(rule);

            if (firstOp) {
                state = ruleMatch;
                firstOp = false;
            } else {
                if ("And".equals(operator)) {
                    state = (state && ruleMatch);
                    if (!state) {
                        break;
                    }
                } else if ("Or".equals(operator)) {
                    state = (state || ruleMatch);
                    if (state) {
                        break;
                    }
                }
            }
        }

        if (QueueFairConfig.debug)
            log.info("QF Rule result is " + state);

        return state;
    }

    private void markPassed(String name) {
        if (passedQueues == null) {
            passedQueues = new HashSet<String>();
        }

        passedQueues.add(name);
    }

    private void setCookie(String queueName, String value, int lifetimeSeconds, String cookieDomain) {
        if (QueueFairConfig.debug)
            log.info("QF Setting cookie for " + queueName + " to " + value + " domain " + cookieDomain);

        String tStr = COOKIE_NAME_BASE + queueName;

        service.setCookie(tStr, value, lifetimeSeconds, cookieDomain);

        if (lifetimeSeconds > 0) {
            markPassed(queueName);
            if (QueueFairConfig.stripPassedString) {
                String loc = requestedURL;
                int i = loc.lastIndexOf("qfqid=");
                if (i != -1) {
                    if (QueueFairConfig.debug)
                        log.info("QF Stripping passedString from URL");
                    loc = loc.substring(0, i - 1);
                    redirect(loc, 0);
                }

            }
        }
    }

    public boolean isRuleMatch(QueueFairSettings.Rule rule) {
        String comp = requestedURL;
        if (QueueFairConfig.debug)
            log.info("QF Checking rule against " + comp);
        String component = rule.component;
        switch (component) {
            case "Domain":
                comp = comp.replaceAll("http://", "");
                comp = comp.replaceAll("https://", "");
                comp = comp.split("[/?#:]")[0];
                break;
            case "Path": {
                String domain = comp.replaceAll("http://", "");
                domain = domain.replaceAll("https://", "");
                domain = domain.split("[/?#:]")[0];

                int i = comp.indexOf(domain);
                comp = comp.substring(i + domain.length());

                if (comp.startsWith(":")) {
                    // We have a port
                    i = comp.indexOf("/");
                    if (i != -1) {
                        comp = comp.substring(i);
                    } else {
                        comp = "";
                    }
                }

                i = comp.indexOf("#");
                if (i != -1) {
                    comp = comp.substring(0, i);
                }

                i = comp.indexOf("?");
                if (i != -1) {
                    comp = comp.substring(0, i);
                }

                if (comp.equals("")) {
                    comp = "/";
                }
                break;
            }
            case "Query": {
                int i = comp.indexOf('?');
                if (i == -1) {
                    comp = "";
                } else if (comp.equals("?")) {
                    comp = "";
                } else {
                    comp = comp.substring(i + 1);
                }
                break;
            }
            case "Cookie":
                comp = service.getCookie(rule.name);
                if (comp == null) {
                    comp = "";
                }
                break;
        }

        String test = (String) rule.value;

        if (!rule.caseSensitive) {
            comp = comp.toLowerCase();
            test = test.toLowerCase();
        }

        String match = rule.match;
        boolean negate = rule.negate;

        if (QueueFairConfig.debug)
            log.info("QF Testing " + component + " " + test + " against " + comp + " " + match + " " + negate);

        boolean ret = false;

        if (match.equals("Equal") && comp.equals(test)) {
            ret = true;
        } else if (match.equals("Contain") && comp != null && !comp.equals("") && comp.contains(test)) {
            ret = true;
        } else if (match.equals("Exist")) {
            ret = comp != null && !"".equals(comp);
        }

        if (negate) {
            ret = !ret;
        }

        return ret;
    }

    private boolean onMatch(QueueFairSettings.Queue queue) {
        if (isPassed(queue)) {
            if (QueueFairConfig.debug)
                log.info("QF Already passed " + queue.name + ".");
            return true;
        } else if (!continuePage) {
            return false;
        }

        if (QueueFairConfig.debug)
            log.info("QF Checking at server " + queue.displayName);
        consultAdapter(queue);
        return false;
    }

    private boolean isPassed(QueueFairSettings.Queue queue) {
        String qName = queue.name;
        if (passedQueues != null && passedQueues.contains(qName)) {
            if (QueueFairConfig.debug)
                log.info("QF Queue " + qName + " marked as passed already.");

            return true;
        }

        String queueCookie = service.getCookie(COOKIE_NAME_BASE + queue.name);

        if (queueCookie == null || "".equals(queueCookie)) {
            if (QueueFairConfig.debug)
                log.info("QF No cookie found for queue " + qName);
            return false;
        }

        if (QueueFairConfig.debug)
            log.info("Queue cookie found " + queueCookie);

        if (!queueCookie.contains(qName)) {
            if (QueueFairConfig.debug)
                log.info("QF Cookie value is invalid for " + qName);
            return false;
        }

        if (!validateCookie(queue, queueCookie)) {
            if (QueueFairConfig.debug)
                log.info("QF Cookie failed validation " + queueCookie);
            checkAndAddCacheControl();
            setCookie(qName, "", 0, queue.cookieDomain);
            return false;
        }

        if (QueueFairConfig.debug)
            log.info("QF Found valid cookie for " + qName);

        return true;
    }

    public void setUIDFromCookie() {
        String cookieBase = "QueueFair-Store-" + QueueFairConfig.account;

        String uidCookie = service.getCookie(cookieBase);
        if (uidCookie == null || "".equals(uidCookie))
            return;

        int i = uidCookie.indexOf(":");
        if (i == -1) {
            i = uidCookie.indexOf("=");
        }

        if (i == -1) {
            return;
        }

        uid = uidCookie.substring(i + 1);
    }

    private void gotSettings() {
        if (QueueFairConfig.debug)
            log.info("QF Got client settings.");
        checkQueryString();
        if (!continuePage) {
            return;
        }

        parseSettings();
    }

    private void parseSettings() {
        if (settings == null) {
            log.warning("QF ERROR: Settings not set.");
            return;
        }

        QueueFairSettings.Queue[] queues = settings.queues;

        if (queues == null || queues.length == 0) {
            if (QueueFairConfig.debug)
                log.info("QF No queues found.");
            return;
        }

        if (QueueFairConfig.debug)
            log.info("QF Running through queue rules");

        for (QueueFairSettings.Queue queue : queues) {

            if (passedQueues != null && passedQueues.contains(queue.name)) {
                if (QueueFairConfig.debug)
                    log.info("QF Passed from array " + queue.name);
                continue;
            }

            if (QueueFairConfig.debug)
                log.info("QF Checking " + queue.name);
            if (isMatch(queue)) {
                if (QueueFairConfig.debug)
                    log.info("QF Got a match " + queue.displayName);

                if (!onMatch(queue)) {
                    if (!continuePage) {
                        return;
                    }

                    if (QueueFairConfig.debug)
                        log.info("QF Found matching unpassed queue " + queue.displayName);
                    if (QueueFairConfig.adapterMode == QueueFairConfig.MODE_SIMPLE) {
                        return;
                    } else {
                        continue;
                    }
                }
                if (!continuePage)
                    return;
                // Passed.
                markPassed(queue.name);

            } else {
                if (QueueFairConfig.debug)
                    log.info("QF Rules did not match " + queue.name);
            }
        }
        if (QueueFairConfig.debug)
            log.info("QF All queues checked.");
    }

    private String processIdentifier(String param) {
        if (param == null)
            return null;
        int i = param.indexOf("[");
        if (i == -1) {
            return param;
        }
        if (i < 20)
            return param;
        return param.substring(0, i);
    }

    public void consultAdapter(QueueFairSettings.Queue queue) {

        if (QueueFairConfig.debug)
            log.info("QF Consulting Adapter Server for " + queue.name);

        adapterQueue = queue;
        int adapterMode = QueueFairConfig.adapterMode;

        if (queue.adapterMode != null && !"".equals(queue.adapterMode)) {
            if ("safe".equals(queue.adapterMode)) {
                adapterMode = QueueFairConfig.MODE_SAFE;
            } else if ("simple".equals(queue.adapterMode)) {
                adapterMode = QueueFairConfig.MODE_SIMPLE;
            }
        }

        if (QueueFairConfig.debug)
            log.info("QF Adapter mode is " + adapterMode);
        if (QueueFairConfig.MODE_SAFE == adapterMode) {
            String url = QueueFairConfig.protocol + "://" + queue.adapterServer + "/adapter/" + queue.name;

            char sep = '?';
            if (sendIPAddressToAdapter) {
                url += "?ipaddress=" + urlencode(remoteIPAddress);
                sep = '&';
            }
            if (uid != null) {
                url += sep + "uid=" + uid;
                sep = '&';
            }

            url += sep + "identifier=" + urlencode(processIdentifier(userAgent));
            if (QueueFairConfig.debug)
                log.info("QF Consulting adapter at " + url);

            adapterResult = urlToMap.urlToMap(url);

            if (adapterResult == null) {
                log.warning("QF No Adapter JSON!");
                return;
            }

            if (QueueFairConfig.debug)
                log.info("QF Downloaded JSON Settings " + adapterResult);

            gotAdapter();

        } else {
            String url = QueueFairConfig.protocol + "://" + queue.queueServer + "/" + queue.name + "?target="
                    + urlencode(requestedURL);

            url = appendVariantToRedirectLocation(queue, url);
            url = appendExtraToRedirectLocation(queue, url);
            if (QueueFairConfig.debug)
                log.info("QF Redirecting to queue server " + url);

            redirect(url, 0);
        }
    }

    public String getVariant(QueueFairSettings.Queue queue) {
        if (queue == null)
            return null;

        if (QueueFairConfig.debug)
            log.info("QF Getting variants for " + queue.name);

        QueueFairSettings.Variant[] variantRules = queue.variantRules;

        if (variantRules == null) {
            return null;
        }

        if (QueueFairConfig.debug)
            log.info("QF Got variant rules " + Arrays.toString(variantRules) + " for " + queue.name);

        String variantName;
        boolean ret;
        for (QueueFairSettings.Variant variant : variantRules) {
            variantName = variant.variant;
            QueueFairSettings.Rule[] rules = variant.rules;
            ret = isMatchArray(rules);
            if (QueueFairConfig.debug)
                log.info("QF Variant match " + variantName + " " + ret);
            if (ret) {
                return variantName;
            }
        }

        return null;
    }

    public String appendVariantToRedirectLocation(QueueFairSettings.Queue queue, String url) {
        if (QueueFairConfig.debug)
            log.info("appendVariant looking for variant");
        String variant = getVariant(queue);
        if (variant == null) {
            if (QueueFairConfig.debug)
                log.info("appendVariant no variant found.");
            return url;
        }
        if (QueueFairConfig.debug)
            log.info("appendVariant found " + variant);
        if (!url.contains("?")) {
            url += "?";
        } else {
            url += "&";
        }
        url += "qfv=" + urlencode(variant);
        return url;
    }

    public String appendExtraToRedirectLocation(QueueFairSettings.Queue queue, String url) {
        if (extra == null) {
            return url;
        }
        if (QueueFairConfig.debug)
            log.info("appendExtra found extra " + extra);
        if (!url.contains("?")) {
            url += "?";
        } else {
            url += "&";
        }
        url += "qfx=" + urlencode(extra);
        return url;
    }

    private void gotAdapter() {
        if (QueueFairConfig.debug)
            log.info("QF Got adapter " + adapterResult);
        if (adapterResult == null) {
            log.warning("QF ERROR: onAdapter() called without result");
            return;
        }

        if (getStr(adapterResult, "uid") != null) {
            if (uid != null && !uid.equals(getStr(adapterResult, "uid"))) {
                log.warning("QF UID Cookie Mismatch - Contact Queue-Fair Support! expected " + uid + " but received "
                        + getStr(adapterResult, "uid"));
            } else {
                if(uid==null) {
                    uid = getStr(adapterResult, "uid");
                    checkAndAddCacheControl();
                    service.setCookie("QueueFair-Store-" + QueueFairConfig.account, "u:" + uid,
                            getInt(adapterResult, "cookieSeconds"), adapterQueue.cookieDomain);
                }
            }
        }

        String action = getStr(adapterResult, "action");

        if (action == null) {
            if (QueueFairConfig.debug)
                log.info("QF ERROR: onAdapter() called without result action");
            return;
        }

        if ("SendToQueue".equals(action)) {
            if (QueueFairConfig.debug)
                log.info("GotAdapter Sending to queue server.");
            String dt = adapterQueue.dynamicTarget;
            String queryParams = "";
            String target = requestedURL;
            if (!"disabled".equals(dt)) {
                if ("path".equals(dt)) {
                    int i = target.indexOf("?");
                    if (i != -1) {
                        target = target.substring(0, i);
                    }
                }
                queryParams += "target=";
                queryParams += urlencode(target);
            }

            if (uid != null) {
                if (!"".equals(queryParams)) {
                    queryParams += "&";
                }
                queryParams += "qfuid=" + uid;
            }
            String redirectLoc = getStr(adapterResult, "location");
            if (!"".equals(queryParams)) {
                redirectLoc = redirectLoc + "?" + queryParams;
            }

            redirectLoc = appendVariantToRedirectLocation(adapterQueue, redirectLoc);
            redirectLoc = appendExtraToRedirectLocation(adapterQueue, redirectLoc);

            if (QueueFairConfig.debug)
                log.info("GotAdapter Redirecting to " + redirectLoc);
            redirect(redirectLoc, 0);
            return;
        }

        // SafeGuard etc
        checkAndAddCacheControl();
        setCookie(getStr(adapterResult, "queue"), urldecode(getStr(adapterResult, "validation")),
                adapterQueue.passedLifetimeMinutes * 60, adapterQueue.cookieDomain);

        if (!continuePage) {
            return;
        }
        if (QueueFairConfig.debug)
            log.info("QF Marking " + getStr(adapterResult, "queue") + " as passed by adapter.");
        markPassed(getStr(adapterResult, "queue"));

    }

    public void redirect(String location, int sleep) {
        if (sleep > 0) {
            try {
                Thread.sleep(sleep * 1000L);
            } catch (Exception e) {
                log.log(Level.WARNING, "Exception sleeping", e);
            }
        }

        if (QueueFairConfig.debug)
            log.info("QF redirecting to " + location);

        checkAndAddCacheControl();
        service.redirect(location);

        continuePage = false;
    }


    private void checkAndAddCacheControl() {
        if(addedCacheControlHeader) {
            return;
        }
        addedCacheControlHeader = true;
        service.addHeader("Cache-Control", "no-store,max-age=0");

    }

    public QueueFairSettings loadSettings() {
        if (memSettings != null && (QueueFairConfig.settingsCacheLifetimeMinutes == -1
                || now - lastMemSettings < QueueFairConfig.settingsCacheLifetimeMinutes * 60000L)) {
            return memSettings;
        }

        synchronized (synch) {
            if (memSettings != null && (QueueFairConfig.settingsCacheLifetimeMinutes == -1
                    || now - lastMemSettings < QueueFairConfig.settingsCacheLifetimeMinutes * 60000L)) {
                return memSettings;
            }

            try {
                String url = getSettingsURL();

                if (QueueFairConfig.debug)
                    log.info("Downloading settings for memory from " + url);

                Map<String, Object> temp = urlToMap.urlToMap(url);
                if (temp == null) {
                    log.info("Could not download Queue-Fair settings!");
                    return memSettings;
                }
                memSettings = new QueueFairSettings(temp);
                lastMemSettings = System.currentTimeMillis();
                return memSettings;
            } catch (Exception e) {
                log.log(Level.WARNING, "QF Exception downloading settings to memory", e);
            }
            // Could be old settings on error.
            return memSettings;
        }
    }

    /**
     * Forces a reload of settings from the Queue-Fair servers.
     */
    public void reloadSettings() {
        int originalSetting = QueueFairConfig.settingsCacheLifetimeMinutes;
        QueueFairConfig.settingsCacheLifetimeMinutes = 0;
        loadSettings();
        QueueFairConfig.settingsCacheLifetimeMinutes = originalSetting;
    }

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public final static String createHash(final String secret, final String message) {
        try {
            final Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            final SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256");
            sha256HMAC.init(secret_key);

            final byte[] digest = sha256HMAC.doFinal(message.getBytes("UTF-8"));
            return bytesToHex(digest);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public boolean validateCookie(QueueFairSettings.Queue queue, String cookie) {
        return validateCookie(queue.secret, queue.passedLifetimeMinutes, cookie);
    }

    /**
     * Convenience method to check whether a cookie is a valid PassedCookie.
     *
     * @param queueSecret the queue's Secret from the Portal.
     * @param cookie      the value of the cookie
     * @return true if the cookie is valid for the given secret, false otherwise.
     */
    public boolean validateCookie(String queueSecret, int passedLifetimeMinutes, String cookie) {
        if (QueueFairConfig.debug)
            log.info("QF Validating cookie " + cookie);

        try {
            if(cookie==null)
                return false;

            int hpos = cookie.lastIndexOf("qfh=");
            if(hpos==-1)
                return false;

            String check = cookie.substring(0, hpos);
            String hash = cookie.substring(hpos + "qfh=".length());

            if (!QueueFairConfig.account.equals(getValueQuick(cookie, "qfa="))) {
                if (QueueFairConfig.debug)
                    log.info("QF Cookie Account Does Not Match");
                return false;
            }

            if (usesSecrets) {

                String checkHash = createHash(queueSecret, processIdentifier(userAgent) + check);
                if (checkHash != null && !checkHash.equals(hash)) {
                    if (QueueFairConfig.debug)
                        log.info("QF Cookie Hash Mismatch given " + hash + " should be " + checkHash);
                    return false;
                }
            }

            String tsStr = getValueQuick(cookie, "qfts=");

            if (tsStr == null) {
                if (QueueFairConfig.debug)
                    log.info("QF Cookie contains no timestamp.");
                return false;
            }

            if (!isNumeric(tsStr)) {
                if (QueueFairConfig.debug)
                    log.info("QF Cookie bad timestamp " + tsStr);
                return false;
            }

            if(usesSecrets) {
                //Only check this for server side adapters - client may not have clock properly set.
                long ts;

                try {
                    ts = Long.parseLong(tsStr);
                } catch (Exception e) {
                    log.info("QF bad timestamp " + tsStr);
                    return false;
                }

                // Don't use 'now' as may be calling this method without rest of adapter
                // process.
                if (ts * 1000L < System.currentTimeMillis() - (passedLifetimeMinutes * 60000L)) {
                    if (QueueFairConfig.debug)
                        log.info("ValidateCookie Too Old" + ts + " " + System.currentTimeMillis() / 1000);
                    return false;
                }
            }

            if (QueueFairConfig.debug)
                log.info("QF Cookie Validated");
            return true;

        } catch (Exception e) {
            log.log(Level.WARNING, "Could not validate cookie " + cookie, e);
            return false;
        }

    }

    // Make sure to have the "=" sign in the name.
    private String getValueQuick(String query, String name) {
        int i = query.lastIndexOf(name);
        if (i == -1)
            return null;

        i = i + name.length();

        int j = query.indexOf('&', i);
        if (j == -1) {
            return query.substring(i);
        }
        return query.substring(i, j);
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.equals("")) {
            return false;
        }
        return java.util.regex.Pattern.matches("\\d+", s);
    }

    private boolean validateQuery(QueueFairSettings.Queue queue) {
        return validateQuery(queue.secret);
    }

    /**
     * Convenience method to validate a PassedString, present in the query string of
     * the page URL.
     *
     * @param secret the queue secret from the Portal
     * @return true if the query string is valid. Note that this is time sensitive.
     */
    public boolean validateQuery(String secret) {
        String str = requestedURL;

        if (str == null)
            return false;

        try {

            int i = str.indexOf('?');

            if (i == -1) {
                if (QueueFairConfig.debug)
                    log.info("QF No query string found.");
                return false;
            }

            if (i == str.length() - 1) {
                if (QueueFairConfig.debug)
                    log.info("QF Query string empty.");
                return false;
            }

            str = str.substring(i + 1);

            if (QueueFairConfig.debug)
                log.info("QF Validating Passed Query " + str);

            int hPos = str.lastIndexOf("qfh=");
            int qPos = str.lastIndexOf("qfqid=");

            if (hPos == -1) {
                if (QueueFairConfig.debug)
                    log.info("QF No hash found! " + str);
                return false;
            }

            if (qPos == -1) {
                if (QueueFairConfig.debug)
                    log.info("QF No qID found! " + str);
                return false;
            }

            String queryHash = getValueQuick(str, "qfh=");

            if (queryHash == null) {
                if (QueueFairConfig.debug)
                    log.info("QF Malformed hash");
                return false;
            }

            String queryTS = getValueQuick(str, "qfts=");

            if (queryTS == null) {
                if (QueueFairConfig.debug)
                    log.info("QF No Timestamp");
                return false;
            }

            if (!isNumeric(queryTS)) {
                if (QueueFairConfig.debug)
                    log.info("QF Timestamp Not Numeric");
                return false;
            }

            long qTS = Long.parseLong(queryTS);

            if (qTS > (now / 1000) + QueueFairConfig.queryTimeLimitSeconds) {
                if (QueueFairConfig.debug)
                    log.info("QF Too Late " + queryTS + " " + (now / 1000));
                return false;
            }

            if (qTS < (now / 1000) - QueueFairConfig.queryTimeLimitSeconds) {
                if (QueueFairConfig.debug)
                    log.info("QF Too Early " + queryTS + " " + (now / 1000));
                return false;
            }

            if (!usesSecrets) {
                return true;
            }

            String check = str.substring(qPos, hPos);

            if (QueueFairConfig.debug)
                log.info("QF Check is " + check);

            String checkHash = createHash(secret, processIdentifier(userAgent) + check);

            if (checkHash == null || !checkHash.equals(queryHash)) {
                if (QueueFairConfig.debug)
                    log.info("QF Failed Hash " + checkHash + " " + queryHash);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not validate query " + requestedURL, e);
            return false;
        }
    }

    private void checkQueryString() {
        String urlParams = requestedURL;
        if (QueueFairConfig.debug)
            log.info("QF Checking URL for Passed String " + urlParams);
        int q = urlParams.lastIndexOf("qfqid=");
        if (q == -1) {
            return;
        }
        if (QueueFairConfig.debug)
            log.info("QF Passed string found");

        int i = urlParams.lastIndexOf("qfq=");
        if (i == -1)
            return;

        if (!QueueFairConfig.account.equals(getValueQuick(urlParams, "qfa="))) {
            if (QueueFairConfig.debug)
                log.info("QF Passed string does not match account.");
            return;
        }

        if (QueueFairConfig.debug)
            log.info("QF Passed String with Queue Name found");

        int j = urlParams.indexOf('&', i);

        int subStart = i + "qfq=".length();

        String queueName = urlParams.substring(subStart, j);

        if (QueueFairConfig.debug)
            log.info("QF Queue name is " + queueName);
        // var_dump(settings,"queues);
        QueueFairSettings.Queue[] queues = settings.queues;

        boolean foundQueue = false;
        for (QueueFairSettings.Queue queue : queues) {
            if (!queueName.equals(queue.name)) {
                continue;
            }
            foundQueue = true;
            if (QueueFairConfig.debug)
                log.info("QF Found queue for querystring " + queueName);

            String value = urlParams;
            value = value.substring(value.lastIndexOf("qfqid"));

            if (!validateQuery(queue)) {
                // This can happen if it's a stale query string too - check for valid cookie.
                String queueCookie = service.getCookie(COOKIE_NAME_BASE + queueName);
                if (queueCookie != null && !"".equals(queueCookie)) {
                    if (QueueFairConfig.debug)
                        log.info("QF Query validation failed but we have cookie " + queueCookie);
                    if (validateCookie(queue, queueCookie)) {
                        if (QueueFairConfig.debug)
                            log.info("QF ...and the cookie is valid. That's fine.");
                        return;
                    }
                    if (QueueFairConfig.debug)
                        log.info("QF Query AND Cookie validation failed!!!");
                } else {
                    if (QueueFairConfig.debug)
                        log.info("QF Bad queueCookie for " + queueName + " " + queueCookie);
                }

                if (QueueFairConfig.debug)
                    log.info("QF Query validation failed - redirecting to error page.");

                String loc = QueueFairConfig.protocol + "://" + queue.queueServer + "/" + queue.name
                        + "?qfError=InvalidQuery";

                redirect(loc, 1);
                return;
            }

            if (QueueFairConfig.debug)
                log.info("QF Query validation succeeded for " + value);

            checkAndAddCacheControl();
            setCookie(queueName, value, queue.passedLifetimeMinutes * 60, queue.cookieDomain);

            if (QueueFairConfig.debug)
                log.info("QF Marking " + queueName + " as passed by queryString");
            if (!continuePage) {
                return;
            }
            markPassed(queueName);

        }

        if (!foundQueue) {
            if (QueueFairConfig.debug)
                log.info("QF no matching queue found for query string");
        }

    }

    /**
     * This is the main method. It downloads settings if necessary, validates any
     * PassedString if present in the query string and converts it to a
     * PassedCookie. It checks to see if the request matches any Activation Rules
     * for any queue, and checks with the Queue-Fair servers to find out if the user
     * should be queued. If the user should be queued, a redirect is sent to the
     * user's browser.
     *
     * @return
     */
    public boolean isContinue() {
        try {
            if (QueueFairConfig.debug)
                log.info("QF --------------- Adapter Starting");
            now = System.currentTimeMillis();

            setUIDFromCookie();

            settings = loadSettings();

            if (settings == null) {
                return true;
            }

            gotSettings();

            if (QueueFairConfig.debug)
                log.info("QF --------------- Adapter Finished");
            return continuePage;
        } catch (Exception e) {
            String message = "QF Exception in Adapter";
            if (service != null) {
                message += " for URL " + requestedURL;
            }
            log.log(Level.WARNING, message, e);
            return true;
        }
    }

    private String urlencode(String input) {
        if (input == null) {
            return null;
        }
        try {

            return URLEncoder.encode(input, "UTF-8");
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not encode " + input, e);
        }
        return null;
    }

    private String urldecode(String input) {
        if (input == null)
            return null;

        try {
            return URLDecoder.decode(input, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * If using QueueFairConfig.useThreadLocal = true, Be sure to call this in your ServletContextListener's contextDestroyed()
     * method to prevent memory leak warnings due to use of ThreadLocals.
     */
    public static void onContextDestroy() {
        adapters = null;
        System.gc();
    }

    public QueueFairService getService() {
        return service;
    }

    public void setService(QueueFairService service) {
        this.service = service;
    }

}