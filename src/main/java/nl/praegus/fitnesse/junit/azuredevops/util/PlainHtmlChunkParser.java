package nl.praegus.fitnesse.junit.azuredevops.util;

import com.google.common.io.ByteStreams;
import nl.hsac.fitnesse.fixture.Environment;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlainHtmlChunkParser {
    private final org.slf4j.Logger logger = LoggerFactory.getLogger(PlainHtmlChunkParser.class);

    private final String css = new String(getBytesForResource("/plaincss.min.css"));
    private final String js = new String(getBytesForResource("/javascript.min.js"));

    public StringBuilder initializeStandalonePage(String fullPath) {
        return new StringBuilder().append("<html>\r\n")
                .append("<head>\r\n")
                .append("<style>\r\n")
                .append(css)
                .append("\r\n</style>\r\n")
                .append("</head>\r\n")
                .append("<body onload=\"enableClickHandlers()\">\r\n")
                .append("<script>\r\n")
                .append(js)
                .append("\r\n</script>\r\n")
                .append("<h1>").append(fullPath).append("</h1>\r\n");
    }

    public void finalizeStandalonePage(StringBuilder output) {
        output.append("</body></html>");
    }

    public String embedImages(String html) {
        Pattern imgPattern = Pattern.compile("<img(\\s+.*?)?\\s+src=\"(.*?)\".*?/>", Pattern.CASE_INSENSITIVE);
        html = html.replaceAll("<a.+?>(.+?)</a>", "$1");
        Matcher imgMatcher = imgPattern.matcher(html);
        while (imgMatcher.find()) {
            String src = imgMatcher.group(2);
            String root = Environment.getInstance().getFitNesseRootDir();
            String img = root + "/" + src;
            File imageFile = new File(img);
            html = imgMatcher.replaceAll("<img src=\"data:image/png;base64," + encodeFile(imageFile) + "\" width=\"200\" onClick=\"openImage(this)\">");
        }
        return html;
    }


    private byte[] getBytesForResource(String resource) {
        byte[] result;
        try {
            result = ByteStreams.toByteArray(getClass().getResourceAsStream(resource));
        } catch (Exception e) {
            logger.error("An exception occured while reading resource {}: {}", resource, e);
            result = "".getBytes();
        }
        return result;
    }

    private String encodeFile(File file) {
        String base64Image = "";
        try (FileInputStream imageInFile = new FileInputStream(file)) {
            // Reading a Image file from file system
            byte[] imageData = new byte[(int) file.length()];
            int i = 0;
            while (i != -1) {
                i = imageInFile.read(imageData);
            }
            base64Image = Base64.getEncoder().encodeToString(imageData);
        } catch (FileNotFoundException e) {
            logger.error("Image not found {}", e);
        } catch (IOException ioe) {
            logger.error("Exception while reading the Image {}", ioe);
        }
        return base64Image;
    }
}
