package ch.digiges.visualregression;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.MatchLevel;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.TestResultsSummary;
import com.applitools.eyes.selenium.ClassicRunner;
import com.applitools.eyes.selenium.Configuration;
import com.applitools.eyes.selenium.Eyes;
import com.applitools.eyes.selenium.fluent.Target;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.w3c.dom.NodeList;

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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class DigigesWebsiteTest {
  static Eyes eyes;
  static WebDriver driver;
  static ClassicRunner runner;
  static RectangleSize viewportSize = new RectangleSize(1024, 1024);

  @Test
  void layoutTest() {
    eyes.setMatchLevel(MatchLevel.LAYOUT);
    startTest("Layout");

    driver.get(getWebRoot());
    eyes.check(Target.window()
            .fully()
            .withName("homepage"));
  }

  @Test
  void pagesTest() throws IOException, InterruptedException {
    var siteMapUrl = getWebRoot() + "sitemaps/page-sitemap1.xml";
    var response = fetchSitemapXml(siteMapUrl);
    var urls = extractUrls(response);

    eyes.setHideScrollbars(true);
    eyes.setMatchLevel(MatchLevel.STRICT);
    startTest("Pages");

    driver.get(getWebRoot());
    eyes.check(Target.window()
            .fully()
            .ignore(By.cssSelector("body > footer"))
            .ignore(By.cssSelector("main.main"))
            .withName("homepage"));

    for (var url : urls) {
      System.out.println(String.format("checking %s", url));
      driver.get(url.toString());
      eyes.check(Target.window()
              .fully()
              .ignore(By.cssSelector("body > footer"))
              .ignore(By.cssSelector(".random-capital-quiz"))
              .withName(url.getPath()));
    }
  }

  @BeforeAll
  static void setupEyes() throws MalformedURLException {
    WebDriverManager.chromedriver().browserVersion(System.getenv("CHROME_VERSION")).setup();
    runner = new ClassicRunner();
    eyes = new Eyes(runner);

    var config = new Configuration()
            .setApiKey(System.getenv("API_KEY"))
            .setBatch(new BatchInfo(new URL(getWebRoot()).getAuthority()));

    eyes.setConfiguration(config);
  }

  @AfterEach
  void closeTest() {
    eyes.closeAsync();
  }

  @AfterAll
  static void resultSummary() {
    driver.quit();

    // false : suppress exception thrown if visual differences
    TestResultsSummary allTestResults = runner.getAllTestResults(false);
    System.out.println(allTestResults);
  }

  private void startTest(String testname) {
    driver = eyes.open(createChromeDriver(), System.getenv("APP_NAME"), testname, viewportSize);
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
