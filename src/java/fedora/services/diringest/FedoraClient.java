package fedora.services.diringest;

import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.trippi.*;

import fedora.client.*;
import fedora.server.access.*;
import fedora.server.management.*;
import fedora.server.types.gen.*;

public class FedoraClient {

    public static final String FEDORA_URI_PREFIX = "info:fedora/";

    public int TIMEOUT_SECONDS = 20;
    public boolean FOLLOW_REDIRECTS = true;

    private String m_baseURL;
    private String m_host;
    private UsernamePasswordCredentials m_creds;

    private MultiThreadedHttpConnectionManager m_cManager;

    private String m_serverVersion;

    public FedoraClient(String baseURL, String user, String pass) throws MalformedURLException {
        m_baseURL = baseURL;
        if (!baseURL.endsWith("/")) m_baseURL += "/";
        URL url = new URL(m_baseURL);
        m_host = url.getHost();
        m_creds = new UsernamePasswordCredentials(user, pass);
        m_cManager = new MultiThreadedHttpConnectionManager();
    }

    /**
     * Get a an http resource's input stream given a
     * locator that either begins with 'info:fedora/' , 'http://', or '/'.
     *
     * Note that if the HTTP response has no body, the InputStream will
     * be empty.  The success of a request can be checked with
     * getResponseCode().  Usually you'll want to see a 200.
     * See http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html for other codes.
     */
    public HttpInputStream get(String locator, boolean failIfNotOK) throws IOException {
        String url = getURL(locator);
        HttpClient client = getHttpClient();
        GetMethod getMethod = new GetMethod(url);
        getMethod.setDoAuthentication(true);
        getMethod.setFollowRedirects(true);
        HttpInputStream in = new HttpInputStream(client, getMethod, url);
        if (failIfNotOK) {
            if (in.getStatusCode() != 200) {
                try { 
                    throw new IOException("Request failed [" + in.getStatusCode() + " " + in.getStatusText() + "]");
                } finally {
                    try { in.close(); } catch (Exception e) { }
                }
            }
        }
        return in;
    }

    public String getString(String locator, boolean failIfNotOK) throws IOException {
        InputStream in = get(locator, failIfNotOK);
        try {
            BufferedReader reader = new BufferedReader(
                                        new InputStreamReader(in));
            StringBuffer buffer = new StringBuffer();
            String line = reader.readLine();
            while (line != null) {
                buffer.append(line + "\n");
                line = reader.readLine();
            }
            return buffer.toString();
        } finally {
            try { in.close(); } catch (Exception e) { }
        }
    }

    private String getURL(String locator) throws IOException {
        String url;
        if (locator.startsWith(FEDORA_URI_PREFIX)) {
            url = m_baseURL + "get/" + locator.substring(FEDORA_URI_PREFIX.length());
        } else if (locator.startsWith("http://")) {
            url = locator;
        } else if (locator.startsWith("/")) {
            // assume it's for something within this Fedora server
            while (locator.startsWith("/")) {
                locator = locator.substring(1);
            }
            url = m_baseURL + locator;
        } else {
            throw new IOException("Bad locator (must start with '" + FEDORA_URI_PREFIX + "', 'http://', or '/'");
        }
        return url;
    }

    public FedoraAPIM getAPIM() throws Exception {
        if (getServerVersion().equals("2.0")) {
            // use the old path for soap calls

        } else {
            // use the most recent known path for soap calls
        }
        return null;
    }

    public String getServerVersion() throws IOException {
        if (m_serverVersion == null) {
            String desc = getString("/describe?xml=true", true);
            String[] parts = desc.split("<repositoryVersion>");
            if (parts.length < 2) {
                throw new IOException("Could not find repositoryVersion element in content of /describe?xml=true");
            }
            int i = parts[1].indexOf("<");
            if (i == -1) {
                throw new IOException("Could not find end of repositoryVersion element in content of /describe?xml=true");
            }
            m_serverVersion = parts[1].substring(0, i).trim();
        }
        return m_serverVersion;
    }

    public Date getLastModifiedDate(String locator) throws IOException {
        HttpInputStream in = get(locator, true);
        return null;
//        String dateString = in.getHeader("Last-Modified");
    }

    public HttpClient getHttpClient() {
        HttpClient client = new HttpClient(m_cManager);
        client.setConnectionTimeout(TIMEOUT_SECONDS * 1000);
        client.getState().setCredentials(null, m_host, m_creds);
        client.getState().setAuthenticationPreemptive(true);
        return client;
    }


    /**
     * Get tuples from the remote resource index.
     *
     * The map contains <em>String</em> values for parameters that should be 
     * passed to the service. Two parameters are required:
     *
     * 1) lang
     * 2) query
     *
     * Two parameters to the risearch service are implied: 
     * 
     * 1) type = tuples
     * 2) format = sparql
     *
     * See http://www.fedora.info/download/2.0/userdocs/server/webservices/risearch/#app.tuples
     */
    public TupleIterator getTuples(Map params) throws IOException {
        params.put("type", "tuples");
        params.put("format", RDFFormat.SPARQL.getName());
        try {
            String url = getRIQueryURL(params);
            return TupleIterator.fromStream(get(url, true), RDFFormat.SPARQL);
        } catch (TrippiException e) {
            throw new IOException("Error getting tuple iterator: " + e.getMessage());
        }
    }

    private String getRIQueryURL(Map params) throws IOException {
        if (params.get("type") == null) throw new IOException("'type' parameter is required");
        if (params.get("lang") == null) throw new IOException("'lang' parameter is required");
        if (params.get("query") == null) throw new IOException("'query' parameter is required");
        if (params.get("format") == null) throw new IOException("'format' parameter is required");
        return m_baseURL + "risearch?" + encodeParameters(params);
    }

    private String encodeParameters(Map params) {
        StringBuffer encoded = new StringBuffer();
        Iterator iter = params.keySet().iterator();
        int n = 0;
        while (iter.hasNext()) {
            String name = (String) iter.next();
            if (n > 0) {
                encoded.append("&");
            }
            n++;
            encoded.append(name);
            encoded.append('=');
            try {
                encoded.append(URLEncoder.encode((String) params.get(name), "UTF-8"));
            } catch (UnsupportedEncodingException e) { // UTF-8 won't fail
            }
        }
        return encoded.toString();
    }

}