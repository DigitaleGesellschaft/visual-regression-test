package ch.digiges.visualregression;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.FileLogger;
import com.applitools.eyes.MatchLevel;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.TestResultsSummary;
import com.applitools.eyes.selenium.ClassicRunner;
import com.applitools.eyes.selenium.Configuration;
import com.applitools.eyes.selenium.Eyes;
import com.applitools.eyes.selenium.fluent.SeleniumCheckSettings;
import com.applitools.eyes.selenium.fluent.Target;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.impl.SimpleLoggerFactory;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class DigigesWebsiteTest {
  static Eyes eyes;
  static WebDriver driver;
  static ClassicRunner runner;
  static RectangleSize viewportSize = new RectangleSize(1024, 1024);
  static Logger logger = new SimpleLoggerFactory().getLogger(DigigesWebsiteTest.class.getCanonicalName());

  @Test
  void layoutTest() {
    // TODO
    //  Verschiedene Sidebars?
    List<URL> urls = mapToUrls(List.of(
            ""
            , "category/demokratie"
            , "tag/datenschutz"
            , "dossier"
            , "dossier/netzpodcast"
            , "dossier/swiss-lawful-interception-report"
            , "2022/01/02/np001-netzpodcast-auftakt-erklaerstueck"
            , "event/mitgliederversammlung"
    ));

    var sizes = List.of(
            viewportSize
            , new RectangleSize(640, 768)
            , new RectangleSize(375, 768)
            , new RectangleSize(810, 768)
    );
//    eyes.setSaveDebugScreenshots(true);
//    eyes.setDebugScreenshotsPath("./screenshots");

    eyes.setMatchLevel(MatchLevel.LAYOUT);

    for (var size : sizes) {
      startTest("Layout - " + size, size);

      for (var url : urls) {
        logger.info("checking {}", url.getPath());
        driver.get(url.toString());
        eyes.check(chain(fullWindow(), this::ignorePostSlider, this::ignoreIframe, this::ignoreCaptcha).withName(url.getPath() + " - " + size));
      }
      eyes.closeAsync();
    }
  }

  // TODO einmal Sidebar prüfen, einmal Footer prüfen, neue Pages prüfen.

  @Test
  void forms() throws MalformedURLException {
    startTest("Forms");

    var contactPageUrl = new URL(getWebRoot() + "uber-uns/kontakt/");
    var contactPageSettings = chain(fullWindow(), this::ignoreFooter, this::ignoreCaptcha, this::ignoreSidebar);

    logger.info("checking {}", contactPageUrl.getPath());
    driver.get(contactPageUrl.toString());
    eyes.check(contactPageSettings.withName("Kontakt - Initial"));

    $(".wpcf7-form .wpcf7-submit").click();

    eyes.check(contactPageSettings.withName("Kontakt - Submit Invalid"));

    $(".wpcf7-form [name=contact-name]").sendKeys("testname");
    $(".wpcf7-form [name=contact-email]").sendKeys("test@000.com");
    $(".wpcf7-form [name=contact-message]").sendKeys("test message");
    $(".wpcf7-form .wpcf7-submit").click();

    eyes.check(contactPageSettings.withName("Kontakt - Submit Semi Valid"));

    eyes.closeAsync();
  }
  @Test
  void pagesTest() throws IOException, InterruptedException {
    eyes.setHideScrollbars(true);
    eyes.setMatchLevel(MatchLevel.STRICT);
    startTest("Pages");

    logger.info("checking homepage {}", getWebRoot());
    driver.get(getWebRoot());
    eyes.check(chain(fullWindow(), this::ignoreFooter)
            .ignore(By.cssSelector("main.main"))
            .withName("homepage"));

    var checkAlways = mapToUrls(List.of(
            "category/demokratie",
            "tag/datenschutz",
            "a-propos-de-nous",
            "anonip",
            "ueberwachung",
            "publicwlan",
            "2022/01/27/sbb-ticketdaten-im-internet-sicherheitsluecke-verantwortungsvoll-melden-und-schliessen", // video
            "2022/01/13/digitale-gesellschaft-weist-verharmlosende-darstellung-zur-massenueberwachung-des-geheimdienstes-zurueck-kabelaufklaerung-am-bundesverwaltungsgericht", // spenden block
            "2021/12/15/newsletter-zu-digitale-grundrechte-gesichtserkennung-leistungsschutzrecht-winterkongress-stammtisch-update-dezember-2021", // embedded form
            "2021/12/05/ich-wuerde-mir-nie-ein-selbstfahrendes-auto-kaufen-deep-technology-podcast", // images, video, block
            "2021/08/01/transparenzbericht-2021-unserer-dns-server-oeffentliche-dns-resolver", // lists
            "vorratsdatenspeicherung", // nested lists
            "uber-uns/bitwaescherei", // OSM
            "event/mitgliederversammlung" // event
    ));

    logger.info("start always checks");
    for (var url : checkAlways) {
      logger.info("checking {}", url);
      driver.get(url.toString());
      eyes.check(chain(fullWindow(), this::ignoreFooter, this::ignoreCaptcha, this::ignoreIframe, this::ignoreSidebar)
              .withName(url.getPath()));
    }

    logger.info("start form checks");
    var siteMapUrl = getWebRoot() + "sitemaps/page-sitemap1.xml";
    var response = fetchSitemapXml(siteMapUrl);
    var urls = extractUrls(response);
    for (var url : urls) {
      driver.get(url.toString());
      var found = driver.findElements(By.cssSelector("main.main form")).size() > 0;
      if (found) {
        logger.info("checking {}", url);
        driver.get(url.toString());
        eyes.check(chain(fullWindow(), this::ignoreFooter, this::ignoreCaptcha, this::ignoreIframe, this::ignoreSidebar)
                .withName(url.getPath()));
      }
    }

    eyes.closeAsync();
  }

  @BeforeAll
  static void setupEyes() throws MalformedURLException {
    WebDriverManager.chromedriver().browserVersion(System.getenv("CHROME_VERSION")).setup();
    runner = new ClassicRunner();
    eyes = new Eyes(runner);
    eyes.setLogHandler(new FileLogger("logs/applitools.log", true, false));

    var config = new Configuration()
            .setApiKey(System.getenv("API_KEY"))
            .setBatch(new BatchInfo(new URL(getWebRoot()).getAuthority()));

    eyes.setConfiguration(config);
  }

  @AfterAll
  static void resultSummary() {
    driver.quit();

    // false : suppress exception thrown if visual differences
    TestResultsSummary allTestResults = runner.getAllTestResults(false);
    logger.info(allTestResults.toString());
  }

  private void startTest(String testname) {
    this.startTest(testname, viewportSize);
  }

  private void startTest(String testname, RectangleSize viewportSize) {
    logger.info("startTest {} at {}", testname, viewportSize);
    driver = eyes.open(createChromeDriver(), System.getenv("APP_NAME"), testname, viewportSize);
  }

  private List<URL> mapToUrls(List<String> urlPaths) {
    return urlPaths.stream().map(path -> {
      try {
        return new URL(getWebRoot() + path);
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }).collect(Collectors.toList());
  }

  private WebElement $(String css) {
    return driver.findElement(By.cssSelector(css));
  }

  @SafeVarargs
  @Nonnull
  private SeleniumCheckSettings chain(SeleniumCheckSettings existing, Function<SeleniumCheckSettings, SeleniumCheckSettings>... successors) {
    var end = existing;
    for (var next : successors) {
      end = next.apply(end);
    }
    return end;
  }

  private SeleniumCheckSettings ignoreSidebar(SeleniumCheckSettings existing) {
    return existing.ignore(By.cssSelector("aside.sidebar"));
  }

  private SeleniumCheckSettings ignorePostSlider(SeleniumCheckSettings existing) {
    return existing.ignore(By.cssSelector(".featured.animated"));
  }

  private SeleniumCheckSettings ignoreCaptcha(SeleniumCheckSettings existing) {
    return existing.ignore(By.cssSelector(".random-capital-quiz"));
  }

  private SeleniumCheckSettings ignoreFooter(SeleniumCheckSettings existing) {
    return existing.ignore(By.cssSelector("body > footer"));
  }

  private SeleniumCheckSettings ignoreIframe(SeleniumCheckSettings existing) {
    return existing.ignore(By.cssSelector("iframe"));
  }

  private SeleniumCheckSettings fullWindow() {
    return Target.window()
            .fully();
  }

  private static WebDriver createChromeDriver() {
    var options = new ChromeOptions().setHeadless(true);
    return new ChromeDriver(options);
  }

  private static List<URL> extractUrls(HttpResponse<byte[]> response) {
    List<URL> urls;
    var factory = createDocumentBuilderFactory();

    try {
      var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(response.body()));
      var expr = XPathFactory.newInstance().newXPath().compile("//url/loc/text()");
      var nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

      urls = IntStream.range(0, nodes.getLength())
              .mapToObj(i -> {
                try {
                  return new URL(nodes.item(i).getNodeValue());
                } catch (MalformedURLException e) {
                  throw new RuntimeException(e);
                }
              })
              //.limit(3)
              .collect(Collectors.toList());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return urls;
  }

  private static HttpResponse<byte[]> fetchSitemapXml(String siteMapUrl) throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(siteMapUrl))
            .GET()
            .build();

    return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
  }

  private static DocumentBuilderFactory createDocumentBuilderFactory() {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    String FEATURE;
    try {
      // This is the PRIMARY defense. If DTDs (doctypes) are disallowed, almost all
      // XML entity attacks are prevented
      // Xerces 2 only - http://xerces.apache.org/xerces2-j/features.html#disallow-doctype-decl
      FEATURE = "http://apache.org/xml/features/disallow-doctype-decl";
      factory.setFeature(FEATURE, true);

      // If you can't completely disable DTDs, then at least do the following:
      // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-general-entities
      // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-general-entities
      // JDK7+ - http://xml.org/sax/features/external-general-entities
      //This feature has to be used together with the following one, otherwise it will not protect you from XXE for sure
      FEATURE = "http://xml.org/sax/features/external-general-entities";
      factory.setFeature(FEATURE, false);

      // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-parameter-entities
      // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities
      // JDK7+ - http://xml.org/sax/features/external-parameter-entities
      //This feature has to be used together with the previous one, otherwise it will not protect you from XXE for sure
      FEATURE = "http://xml.org/sax/features/external-parameter-entities";
      factory.setFeature(FEATURE, false);

      // Disable external DTDs as well
      FEATURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
      factory.setFeature(FEATURE, false);

      // and these as well, per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks"
      factory.setXIncludeAware(false);

      factory.setExpandEntityReferences(false);

    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    }
    return factory;
  }

  private static String getWebRoot() {
    return System.getenv("WEB_ROOT");
  }
}
