package org.archive.crawler.selftest;

import org.archive.crawler.reporting.StatisticsTracker;
import org.archive.crawler.util.CrawledBytesHistotable;

public class StatisticsSelfTest extends SelfTestBase {

    @Override
    protected String changeGlobalConfig(String config) {
        String warcWriterConfig = " <bean id='warcWriter' class='org.archive.modules.writer.WARCWriterProcessor'/>\n";
        config = config.replace("<!--@@MORE_EXTRACTORS@@-->", warcWriterConfig);
        return super.changeGlobalConfig(config);
    }

    @Override
    protected String getSeedsString() {
        // we have these coming from different hosts, so that robots.txt is
        // fetched for both; otherwise what is crawled would be
        // non-deterministic
        return "http://127.0.0.1:7777/a.html\\nhttp://localhost:7777/b.html";
    }
    
    @Override
    protected void verify() throws Exception {
        verifySourceStats();
        verifyWarcStats();
    }
    
    protected void verifyWarcStats() {
        StatisticsTracker stats = heritrix.getEngine().getJob("selftest-job").getCrawlController().getStatisticsTracker();
        assertNotNull(stats);
        assertEquals(14, (long) stats.getCrawledBytes().get(CrawledBytesHistotable.WARC_NOVEL_URLS));
        assertEquals(12718, (long) stats.getCrawledBytes().get(CrawledBytesHistotable.WARC_NOVEL_CONTENT_BYTES));

        assertEquals(3, (long) stats.getServerCache().getHostFor("127.0.0.1").getSubstats().get(CrawledBytesHistotable.WARC_NOVEL_URLS));
        assertEquals(2942, (long) stats.getServerCache().getHostFor("127.0.0.1").getSubstats().get(CrawledBytesHistotable.WARC_NOVEL_CONTENT_BYTES));
        assertEquals(10, (long) stats.getServerCache().getHostFor("localhost").getSubstats().get(CrawledBytesHistotable.WARC_NOVEL_URLS));
        assertEquals(9727, (long) stats.getServerCache().getHostFor("localhost").getSubstats().get(CrawledBytesHistotable.WARC_NOVEL_CONTENT_BYTES));
        assertEquals(1, (long) stats.getServerCache().getHostFor("dns:").getSubstats().get(CrawledBytesHistotable.WARC_NOVEL_URLS));
        assertEquals(49, (long) stats.getServerCache().getHostFor("dns:").getSubstats().get(CrawledBytesHistotable.WARC_NOVEL_CONTENT_BYTES));
    }

    protected void verifySourceStats() throws Exception {
        StatisticsTracker stats = heritrix.getEngine().getJob("selftest-job").getCrawlController().getStatisticsTracker();
        assertNotNull(stats);
        assertTrue(stats.getTrackSources());
        CrawledBytesHistotable sourceStats = stats.getSourceStats("http://127.0.0.1:7777/index.html");
        assertNull(sourceStats);
        sourceStats = stats.getSourceStats("http://127.0.0.1:7777/a.html");
        assertNotNull(sourceStats);
        assertEquals(4, sourceStats.keySet().size());
        assertEquals(2942l, (long) sourceStats.get("novel"));
        assertEquals(3l, (long) sourceStats.get("novelCount"));
        assertEquals(2942l, (long) sourceStats.get("warcNovelContentBytes"));
        assertEquals(3l, (long) sourceStats.get("warcNovelUrls"));
        
        sourceStats = stats.getSourceStats("http://localhost:7777/b.html");
        assertNotNull(sourceStats);
        assertEquals(4, sourceStats.keySet().size());
        assertEquals(9776l, (long) sourceStats.get("novel"));
        assertEquals(11l, (long) sourceStats.get("novelCount"));
        assertEquals(9776l, (long) sourceStats.get("warcNovelContentBytes"));
        assertEquals(11l, (long) sourceStats.get("warcNovelUrls"));
    }

}
