/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual
 *  contributors.
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.archive.modules.fetcher;

import it.unimi.dsi.mg4j.util.MutableString;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieIdentityComparator;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.archive.checkpointing.Checkpointable;
import org.archive.modules.CrawlURI;
import org.archive.spring.ConfigFile;
import org.archive.spring.ConfigPath;
import org.springframework.context.Lifecycle;

import com.google.common.net.InternetDomainName;

abstract public class AbstractCookieStore implements Lifecycle, Checkpointable,
        CookieStore, FetchHTTPCookieStore {

    protected final Logger logger =
            Logger.getLogger(AbstractCookieStore.class.getName());

    protected static final Comparator<Cookie> cookieComparator = new CookieIdentityComparator();

    protected ConfigFile cookiesLoadFile = null;
    public ConfigFile getCookiesLoadFile() {
        return cookiesLoadFile;
    }
    public void setCookiesLoadFile(ConfigFile cookiesLoadFile) {
        this.cookiesLoadFile = cookiesLoadFile;
    }

    protected ConfigPath cookiesSaveFile = null;
    public ConfigPath getCookiesSaveFile() {
        return cookiesSaveFile;
    }
    public void setCookiesSaveFile(ConfigPath cookiesSaveFile) {
        this.cookiesSaveFile = cookiesSaveFile;
    }

    protected boolean isRunning = false;

    @Override
    public void start() {
        if (isRunning()) {
            return;
        }
        prepare();
        if (getCookiesLoadFile()!=null) {
            loadCookies(getCookiesLoadFile());
        }
        isRunning = true;
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    public void saveCookies() {
        if (getCookiesSaveFile() != null) {
            saveCookies(getCookiesSaveFile().getFile().getAbsolutePath());
        }
    }

    protected void loadCookies(ConfigFile file) {
        Reader reader = null;
        try {
            reader = file.obtainReader();
            loadCookies(reader);
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }

    protected void loadCookies(Reader reader) {
        Collection<Cookie> loadedCookies = readCookies(reader);
        for (Cookie cookie: loadedCookies) {
            addCookie(cookie);
        }
    }

    public void saveCookies(String saveCookiesFile) {
        // Do nothing if cookiesFile is not specified.
        if (saveCookiesFile == null || saveCookiesFile.length() <= 0) {
            return;
        }

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(new File(saveCookiesFile));
            String tab ="\t";
            out.write("# Heritrix Cookie File\n".getBytes());
            out.write("# This file is the Netscape cookies.txt format\n\n".getBytes());
            for (Cookie cookie: getCookies()) {
                // Guess an initial size
                MutableString line = new MutableString(1024 * 2);
                line.append(cookie.getDomain());
                line.append(tab);
                // XXX line.append(cookie.isDomainAttributeSpecified() ? "TRUE" : "FALSE");
                line.append("TRUE");
                line.append(tab);
                line.append(cookie.getPath() != null ? cookie.getPath() : "/");
                line.append(tab);
                line.append(cookie.isSecure() ? "TRUE" : "FALSE");
                line.append(tab);
                line.append(cookie.getExpiryDate() != null ? cookie.getExpiryDate().getTime() / 1000 : -1);
                line.append(tab);
                line.append(cookie.getName());
                line.append(tab);
                line.append(cookie.getValue() != null ? cookie.getValue() : "");
                line.append("\n");
                out.write(line.toString().getBytes());
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to write " + saveCookiesFile, e);
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    /**
     * Load cookies. The input is text in the Netscape's 'cookies.txt' file
     * format. Example entry of cookies.txt file:
     * <p>
     * www.archive.org FALSE / FALSE 1311699995 details-visit texts-cralond
     * </p>
     * <p>
     * Each line has 7 tab-separated fields:
     * </p>
     * <ol>
     * <li>DOMAIN: The domain that created and have access to the cookie value.</li>
     * <li>FLAG: A TRUE or FALSE value indicating if hosts within the given
     * domain can access the cookie value.</li>
     * <li>PATH: The path within the domain that the cookie value is valid for.</li>
     * <li>SECURE: A TRUE or FALSE value indicating if to use a secure
     * connection to access the cookie value.</li>
     * <li>EXPIRATION: The expiration time of the cookie value, or -1 for no
     * expiration</li>
     * <li>NAME: The name of the cookie value</li>
     * <li>VALUE: The cookie value</li>
     * </ol>
     *
     * @param reader
     *            input in the Netscape's 'cookies.txt' format.
     */
    protected Collection<Cookie> readCookies(Reader reader) {
        LinkedList<Cookie> cookies = new LinkedList<Cookie>();
        BufferedReader br = new BufferedReader(reader);
        try {
            String line;
            int lineNo = 1;
            while ((line = br.readLine()) != null) {
                if (!line.matches("\\s*(?:#.*)?")) { // skip blank links and comments
                    String[] tokens = line.split("\\t");
                    if (tokens.length == 7) {
                        long epochSeconds = Long.parseLong(tokens[4]);
                        Date expirationDate = (epochSeconds >= 0 ? new Date(epochSeconds * 1000) : null);
                        BasicClientCookie cookie = new BasicClientCookie(tokens[5], tokens[6]);
                        cookie.setDomain(tokens[0]);
                        cookie.setExpiryDate(expirationDate);
                        cookie.setSecure(Boolean.valueOf(tokens[3]).booleanValue());
                        cookie.setPath(tokens[2]);
                        // XXX httpclient cookie doesn't have this thing?
                        // cookie.setDomainAttributeSpecified(Boolean.valueOf(tokens[1]).booleanValue());
                        logger.fine("Adding cookie: domain " + cookie.getDomain() + " cookie " + cookie);
                        cookies.add(cookie);
                    } else {
                        logger.warning("cookies input line " + lineNo + " invalid, expected 7 tab-delimited tokens");
                    }
                }

                lineNo++;
            }
        } catch (IOException e) {
            logger.log(Level.WARNING,e.getMessage(), e);
        }
        return cookies;
    }

    protected class LimitedCookieStoreFacade implements CookieStore {
        private final List<Cookie> cookies;

        protected LimitedCookieStoreFacade(List<Cookie> cookies) {
            this.cookies = cookies;
        }

        @Override
        public List<Cookie> getCookies() {
            return cookies;
        }

        @Override
        public boolean clearExpired(Date date) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public void clear() {
            throw new RuntimeException("not implemented");
        }

        @Override
        public void addCookie(Cookie cookie) {
            AbstractCookieStore.this.addCookie(cookie);
        }
    }

    /**
     * Returns a string that uniquely identifies the cookie, and is prepended
     * with the top private domain (one level below the TLD) associated with the
     * cookie. This way such cookies can be grouped together in a sorted list,
     * for example. The format The format of the key is
     * {@code "topPrivateDomain;normalizedDomain;name;path"}. Adapted from
     * {@link CookieIdentityComparator#compare(Cookie, Cookie)}.
     */
    protected String sortableKey(Cookie cookie) {
        String normalizedDomain = normalizeDomain(cookie.getDomain());
        String topPrivateDomain = topPrivateDomain(normalizedDomain);

        // use ";" as delimiter since it is the delimiter in the cookie header,
        // so presumably can't appear in any of these values
        StringBuilder buf = new StringBuilder(topPrivateDomain);
        buf.append(";").append(normalizedDomain);
        buf.append(";").append(cookie.getName());
        buf.append(";").append(cookie.getPath() != null ? cookie.getPath() : "/");

        return buf.toString();
    }

    /**
     * Returns the top private domain, i.e. the topmost assigned domain, one
     * level below the TLD, for the supplied {@code host}. Returns
     * {@code host} unaltered if a top private domain can't be identified (for
     * example, if {@code host} is an IP address).
     */
    protected String topPrivateDomain(String host) {
        if (InternetDomainName.isValid(host)) {
            InternetDomainName d = InternetDomainName.from(host);
            if (d.hasPublicSuffix()) {
                return d.topPrivateDomain().toString();
            }
        }

        return host;
    }

    protected String normalizeDomain(String domain) {
        if (domain == null) {
            domain = "";
        }
        if (domain.startsWith(".")) {
            domain = domain.substring(1);
        }
        domain = domain.toLowerCase(Locale.ENGLISH);
        return domain;
    }

    public CookieStore cookieStoreFor(CrawlURI curi) throws URIException {
        String normalizedDomain = normalizeDomain(curi.getUURI().getHost());
        String topPrivateDomain = topPrivateDomain(normalizedDomain);
        return cookieStoreFor(topPrivateDomain);
    }

    abstract public CookieStore cookieStoreFor(String topPrivateDomain);
    abstract public void addCookie(Cookie cookie);
    abstract public void clear();
    abstract protected void prepare();
}
