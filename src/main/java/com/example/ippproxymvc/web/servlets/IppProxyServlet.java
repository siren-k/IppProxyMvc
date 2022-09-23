package com.example.ippproxymvc.web.servlets;

import com.hp.jipp.encoding.AttributeGroup;
import com.hp.jipp.encoding.AttributeType;
import com.hp.jipp.encoding.IppInputStream;
import com.hp.jipp.encoding.IppOutputStream;
import com.hp.jipp.encoding.IppPacket;
import com.hp.jipp.encoding.MutableAttributeGroup;
import com.hp.jipp.encoding.Tag;
import com.hp.jipp.model.Operation;
import com.hp.jipp.model.Types;
import com.hp.jipp.trans.IppClientTransport;
import com.hp.jipp.trans.IppPacketData;
import com.parchment.commons.log.LogBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

public class IppProxyServlet extends HttpServlet {

    private static final long serialVersionUID = 6659759891804913472L;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static IppClientTransport transport;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        logger.info("Received get request on IPP Proxy. {}", new LogBuilder().add("printer", req.getPathInfo()));
        resp.setContentType("text/plain");
        resp.getWriter().write("Success!");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        logger.info("Received post request on IPP Proxy. {}", new LogBuilder().add("printer", req.getRequestURI())
                .add("mappings", req.getHttpServletMapping().toString()));

        //String printerUrl = "http://localhost:631/printers/IPPTest1";
        //String printerUrl = "http://localhost:631/printers/localcups";
        String printerUrl = "http://localhost:631/printers/test_ldp";
        URI uri = URI.create(printerUrl);
        URL url = new URL(printerUrl);

        logger.info("Headers from original request. {}", getHeadersFromRequest(req));

        //Extract IPP Packets
        IppInputStream requestStream = new IppInputStream(req.getInputStream());
        IppPacket requestPacket = requestStream.readPacket();

        //Loop over operation attributes to modify printer URI
        List<AttributeGroup> attributeGroups = requestPacket.getAttributeGroups();
        AttributeGroup operationGroup = null;
        for (AttributeGroup attributeGroup : attributeGroups) {
            if (attributeGroup.getTag().equals(Tag.operationAttributes)) {
                MutableAttributeGroup mutableAttributeGroup = attributeGroup.toMutable();
                mutableAttributeGroup.set(Types.printerUri, uri);
                operationGroup = mutableAttributeGroup.toGroup();
            }
        }
        //Create a new Packet for the forwarding request
        IppPacket generatedPacket = new IppPacket(requestPacket.getOperation(), requestPacket.getRequestId(),
                operationGroup);
        String requestString = generatedPacket.prettyPrint(60, "    ");
        logger.info("Request from packets. {}", requestString);
        IppPacketData packetData = new IppPacketData(generatedPacket, req.getInputStream());

        //Create HTTP Request to Post data to forwarding endpoint
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(6 * 1000);
        connection.setRequestMethod("POST");
        connection.addRequestProperty("Content-type", "application/ipp");
        connection.setRequestProperty("Accept", "application/ipp");
        connection.setChunkedStreamingMode(0);
        connection.setDoOutput(true);
        try (IppOutputStream output = new IppOutputStream(connection.getOutputStream())) {
            output.write(packetData.getPacket());
            InputStream extraData = packetData.getData();
            if (extraData != null) {
                copy(extraData, output);
                extraData.close();
            }
        }
        // Read the response from the input stream
        Map<String, List<String>> responseHeaders = connection.getHeaderFields();
        logger.info("headers {} ", responseHeaders);
        ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
        try (InputStream response = connection.getInputStream()) {
            copy(response, responseBytes);
        }

        //Generate response for caller
        IppInputStream responseInput = new IppInputStream(new ByteArrayInputStream(responseBytes.toByteArray()));
        IppPacketData responsePacketData = new IppPacketData(responseInput.readPacket(), responseInput);
        String responseString = responsePacketData.getPacket().prettyPrint(60, "   ");
        logger.info("Response from server. {}", responseString);
        resp.setStatus(200);
        resp.setContentType("application/ipp");
        resp.setCharacterEncoding("utf-8");
        for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            if (StringUtils.isNotBlank(entry.getKey())) {
                if (!(entry.getKey().equalsIgnoreCase("Content-Type") ||
                        entry.getKey().equalsIgnoreCase("Date") ||
                        entry.getKey().equalsIgnoreCase("Server"))) {
                    logger.info("setting header {}", new LogBuilder().add("key", entry.getKey())
                            .add("value", StringUtils.join(entry.getValue(), ";")));
                    resp.setHeader(entry.getKey(), StringUtils.join(entry.getValue(), ";"));
                }
            }
        }

        resp.setHeader("Connection", "keep-alive");
        resp.getWriter().write(responseBytes.toString());
        resp.getWriter().flush();
    }

    private void copy(InputStream data, OutputStream output) throws IOException {
        byte[] buffer = new byte[8 * 1024];
        int readAmount = data.read(buffer);
        while (readAmount != -1) {
            output.write(buffer, 0, readAmount);
            readAmount = data.read(buffer);
        }
    }

    private List<String> getRequestedAttributes() {
        List<String> keywords = new ArrayList<>();
        keywords.add(Types.compressionSupported.getName());
        keywords.add(Types.copiesSupported.getName());
        keywords.add(Types.documentFormatSupported.getName());
        keywords.add(Types.jobPasswordEncryptionSupported.getName());
        keywords.add(Types.mediaColSupported.getName());
        keywords.add(Types.multipleDocumentHandlingSupported.getName());
        keywords.add(Types.operationsSupported.getName());
        keywords.add(Types.printColorModeSupported.getName());
        keywords.add(Types.printerAlert.getName());
        keywords.add(Types.printerAlertDescription.getName());
        keywords.add(Types.printerIsAcceptingJobs.getName());
        keywords.add(Types.printerMandatoryJobAttributes.getName());
        keywords.add(Types.printerState.getName());
        keywords.add(Types.printerStateMessage.getName());
        keywords.add(Types.printerStateReasons.getName());
        keywords.add(Types.colorSupported.getName());
        return keywords;
    }

    private HttpHeaders getHeadersFromRequest(HttpServletRequest request) {

        HttpHeaders headers = new HttpHeaders();
        final Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.add(headerName, headerValue);
        }
        return headers;
    }

}
