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
package org.archive.modules.extractor;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import javax.script.Bindings;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang.StringEscapeUtils;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.modules.fetcher.FetchStatusCodes;
import org.archive.util.TextUtils;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;

public class ExtractorMultipleRegex extends Extractor {

    private static Logger LOGGER =
        Logger.getLogger(ExtractorMultipleRegex.class.getName());
    
    {
        setContentRegexes(new HashMap<String,String>());
    }
    public void setContentRegexes(Map<String, String> regexes) {
        kp.put("contentRegexes", regexes);
    }
    @SuppressWarnings("unchecked")
    public Map<String, String> getContentRegexes() {
        return (Map<String, String>) kp.get("contentRegexes");
    }
    
    {
        setTemplate("");
    }
    public String getTemplate() {
        return (String) kp.get("template");
    }
    public void setTemplate(String templ) {
        kp.put("template", templ);
    }
    
    
    {
        setUriRegex("");
    }
    public void setUriRegex(String reg) {
        kp.put("uriRegex", reg);
    }
    public String getUriRegex() {
        return (String) kp.get("uriRegex");
    }

    
    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        if (uri.getContentLength() <= 0) {
            return false;
        }
        if (!getExtractorParameters().getExtract404s() 
                && uri.getFetchStatus()==FetchStatusCodes.S_NOT_FOUND) {
            return false; 
        }
        return true;
    }

    @Override
    public void extract(CrawlURI curi) {
        
        Matcher m = TextUtils.getMatcher(getUriRegex(), curi.getURI());
        if (!m.matches()) {
            return;
        }
        String[] uriRegexGroups = new String[m.groupCount() +1];
        for(int i = 0; i < uriRegexGroups.length; i++) {
            uriRegexGroups[i] = m.group(i);
        }
        // our data structure to prepopulate with matches for nested iteration
        SortedMap<String,List<String[]>> allMatches = new TreeMap<String, List<String[]>>();
        LinkedList<String[]> uriRegexMatchList = new LinkedList<String[]>();
        uriRegexMatchList.add(uriRegexGroups);
        allMatches.put("uriRegex", uriRegexMatchList);

        ReplayCharSequence cs;
        try {
            cs = curi.getRecorder().getContentReplayCharSequence();
        } catch (IOException e) {
            curi.getNonFatalFailures().add(e);
            LOGGER.log(Level.WARNING,"Failed get of replay char sequence in " +
                Thread.currentThread().getName(), e);
            return;
        }
        // the names for regexes given in the config
        Set<String> names = getContentRegexes().keySet();
        for (String patternName : names) {
            // the matcher for this patternName against the content
            Matcher namedMatcher = TextUtils.getMatcher(getContentRegexes().get(patternName), cs);
            // populate the list of finds for this patternName
            List<String[]> foundList = new LinkedList<String[]>();
            while(namedMatcher.find()) {
                // +1 to include the full match in addition to the groups
                String[] groups = new String[namedMatcher.groupCount() +1];
                for(int i = 0; i < groups.length; i++) {
                    groups[i] = namedMatcher.group(i);
                }
                foundList.add(groups);
            }
            allMatches.put(patternName, foundList);
        }
        
        long i = 0;
        boolean done = false;
        while (!done) {
            long tmp = i;
            SimpleBindings matches = new SimpleBindings();
            matches.put("index", i);
            String[] patternNames = allMatches.keySet().toArray(new String[0]);
            for (int j = 0; j < patternNames.length; j++) {
                List<String[]> matchList = allMatches.get(patternNames[j]);
                if (j == patternNames.length - 1 && tmp >= matchList.size()) {
                    done = true;
                    break;
                }
                matches.put(patternNames[j], matchList.get((int) (tmp % matchList.size())));
                tmp = tmp / matchList.size();
            }
            
            if (!done) {
                addOutlink(curi, matches);
            }
            
            i++;
        }
    }
    
    protected void addOutlink(CrawlURI curi, Bindings matches) {
        GroovyScriptEngineImpl gse = new GroovyScriptEngineImpl();
        String stringUri = null;
        try {
            stringUri = (String) gse.eval("\""+ StringEscapeUtils.escapeJava(getTemplate()) +"\"", matches);
        } catch (ScriptException e) {
            logUriError(new URIException(e.toString()), curi.getUURI(), stringUri);
            return;
        }
        try {
            int max = getExtractorParameters().getMaxOutlinks();
            Link.addRelativeToBase(curi, max, stringUri, 
                    HTMLLinkContext.INFERRED_MISC, Hop.INFERRED);
        } catch (URIException e) {
            logUriError(e, curi.getUURI(), stringUri);
        }
    }
    
}
