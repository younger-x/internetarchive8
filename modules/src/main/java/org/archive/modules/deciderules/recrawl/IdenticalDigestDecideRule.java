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

package org.archive.modules.deciderules.recrawl;

import org.archive.format.warc.WARCConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.deciderules.DecideResult;
import org.archive.modules.deciderules.PredicatedDecideRule;
import org.archive.modules.revisit.RevisitProfile;

/**
 * Rule applies configured decision to any CrawlURIs whose prior-history
 * content-digest matches the latest fetch. 
 *
 * @author gojomo
 */
public class IdenticalDigestDecideRule extends PredicatedDecideRule {
    private static final long serialVersionUID = 4275993790856626949L;

    /** default for this class is to REJECT */
    {
        setDecision(DecideResult.REJECT);
    }
    
    /**
     * Usual constructor. 
     */
    public IdenticalDigestDecideRule() {
    }

    /**
     * Evaluate whether given CrawlURI's content-digest exactly 
     * matches that of preceding fetch. 
     *
     * @param object should be CrawlURI
     * @return true if current-fetch content-digest matches previous
     */
    protected boolean evaluate(CrawlURI curi) {
        return hasIdenticalDigest(curi);
    }


    /**
     * Utility method for testing if a CrawlURI's last two history 
     * entries (one being the most recent fetch) have identical 
     * content-digest information. 
     * 
     * @param curi CrawlURI to test
     * @return true if last two history entries have identical digests, 
     * otherwise false
     */
    public static boolean hasIdenticalDigest(CrawlURI curi) {
        RevisitProfile revisit = curi.getRevisitProfile();
        if (revisit==null) {
        	return false;
        }
        return revisit.getProfileName().equals(WARCConstants.PROFILE_REVISIT_IDENTICAL_DIGEST);
    }

}
