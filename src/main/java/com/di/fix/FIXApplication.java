package com.di.fix;

import com.di.connection.KdbConnectionRT;
import lombok.extern.slf4j.Slf4j;
import quickfix.*;
import quickfix.Message;
import quickfix.field.*;
import quickfix.fix44.*;
import quickfix.fix44.MessageCracker;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Slf4j
public class FIXApplication extends MessageCracker implements Application {
    private static final String CONFIG_FILE = "symbols.properties";
    private static final String SWAP_POINTS_FILE = "KX Swap Pts.csv";

    private final Set<String> sentSubscriptions = Collections.synchronizedSet(new HashSet<>());

    private final KdbConnectionRT kdbConnection;

    private Map<String, List<SwapPointData>> swapPointsMap;

    public FIXApplication() {
        this.kdbConnection = new KdbConnectionRT();
        this.swapPointsMap = new HashMap<>();
        loadSwapPoints();
    }

    private static class SwapPointData {
        String ccy1;
        String ccy2;
        String fromMaturity;
        String toMaturity;

        public SwapPointData(String ccy1, String ccy2, String fromMaturity, String toMaturity) {
            this.ccy1 = ccy1;
            this.ccy2 = ccy2;
            this.fromMaturity = fromMaturity;
            this.toMaturity = toMaturity;
        }
    }

    private String normalizeTenor(String tenor) {
        if (tenor == null || tenor.isEmpty()) return "";

        // Remove unwanted prefixes (e.g., "SP-", "SP:", or extra colons)
        String normalizedTenor = tenor.replaceAll("^(SP-|SP:)", "");

        // Recognize and return Central Bank Meeting Tenors (e.g., FED_Jan_25, ECB_Mar_25)
        if (normalizedTenor.matches("^(FED|RBA|ECB|BOE|BOC|SNB)_\\w{3}_\\d{2}$")) {
            return normalizedTenor;
        }

        // Recognize IMM (International Monetary Market) Futures (e.g., IMM1, IMM2, IMM3, IMM4)
        if (normalizedTenor.matches("^IMM\\d+$")) {
            return normalizedTenor;
        }

        // Recognize Quarter-Based Tenors (e.g., EOQ5, BOQ6)
        if (normalizedTenor.matches("^(EOQ|BOQ)\\d+$")) {
            return normalizedTenor;
        }

        // Recognize Futures-Style Tenors (e.g., F1, M2, T1)
        if (normalizedTenor.matches("^[FMT]\\d+$")) {
            return normalizedTenor;
        }

        // Recognize Special Short-Term Tenors (ON, TN, SN) and their relative versions (ON+1, TN+1)
        if (normalizedTenor.matches("^(ON|TN|SN)(\\+\\d+)?$")) {
            return normalizedTenor;
        }

        // Handle end/beginning of month tenors (e.g., BOM5 → 5M, EOM3 → 3M)
        if (normalizedTenor.matches(".*BOM\\d+.*") || normalizedTenor.matches(".*EOM\\d+.*")) {
            String number = normalizedTenor.replaceAll(".*?(\\d+).*", "$1");
            return number.isEmpty() ? "" : number + "M";
        }

        // Recognize valid time-based tenors (e.g., 1W, 2M, 3Y, 30D)
        if (normalizedTenor.matches("^\\d+[DWMY]$")) {
            return normalizedTenor;
        }

        // Recognize explicit dates (YYYYMMDD)
        if (normalizedTenor.matches("^\\d{8}$")) {
            return normalizedTenor;
        }

        // If it reaches here, the format is unrecognized – but instead of skipping, return as-is
        log.warn("Unrecognized tenor format: {} (original: {}) - returning as-is", normalizedTenor, tenor);
        return normalizedTenor;
    }


    private List<String> loadCurrencyPairs() {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                log.error("Unable to find {}", CONFIG_FILE);
                return List.of();
            }
            props.load(input);
            String pairs = props.getProperty("currency.pairs");
            if (pairs == null || pairs.isEmpty()) {
                log.error("No currency pairs found in configuration");
                return List.of();
            }
            return Arrays.asList(pairs.split(","));
        } catch (IOException e) {
            log.error("Error loading currency pairs configuration: {}", e.getMessage());
            return List.of();
        }
    }

    private void loadSwapPoints() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(SWAP_POINTS_FILE)) {
            if (is == null) {
                log.error("Swap points file {} not found in resources!", SWAP_POINTS_FILE);
                return;
            }
            Scanner scanner = new Scanner(is);
            List<String> lines = new ArrayList<>();
            while (scanner.hasNextLine()) {
                lines.add(scanner.nextLine());
            }

            int skippedTenors = 0;
            for (int i = 1; i < lines.size(); i++) { // Skip header
                String[] fields = lines.get(i).split(",");
                if (fields.length >= 4) {
                    String ccy1 = fields[0].trim();
                    String ccy2 = fields[1].trim();
                    String fromMaturity = normalizeTenor(fields[2].trim());
                    String toMaturity = normalizeTenor(fields[3].trim());

                    if (toMaturity.isEmpty()) {  // Ensure we always have a valid tenor
                        log.warn("Skipping invalid tenor: {} (original: {})", fields[3].trim(), fields[3].trim());
                        skippedTenors++;
                        continue;
                    }

                    String currencyPair = ccy1 + "/" + ccy2;
                    swapPointsMap.computeIfAbsent(currencyPair, k -> new ArrayList<>())
                            .add(new SwapPointData(ccy1, ccy2, fromMaturity, toMaturity));
                }
            }
            log.info("Loaded swap points for {} currency pairs with {} tenors skipped", swapPointsMap.size(), skippedTenors);
        } catch (Exception e) {
            log.error("Error loading swap points: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onCreate(SessionID sessionId) {
        log.info("onCreate method called! Session: {}", sessionId);
        log.info("Env KXI_CONFIG_URL = {}", System.getenv("KXI_CONFIG_URL"));
        try {
            kdbConnection.openConnection();
        } catch (IOException e) {
            log.error("Failed to open KDB connection", e);
        }
    }

    @Override
    public void onLogon(SessionID sessionId) {
        log.info("Logon: Start Time: {}", LocalDateTime.now());
        log.info("Logon Session: {}", sessionId);

        List<String> currencyPairs = loadCurrencyPairs();

        sentSubscriptions.clear();

        for (String pair : currencyPairs) {
            sendMarketDataRequest(sessionId, pair);

            if (swapPointsMap.containsKey(pair)) {
                Set<String> processedTenors = new HashSet<>();

                for (SwapPointData swapPoint : swapPointsMap.get(pair)) {
                    boolean isNDF = pair.contains("NDF") || pair.contains("NDS");
                    String tenor = swapPoint.toMaturity;

                    // Skip if we've already processed this tenor for this currency pair
                    if (!processedTenors.contains(tenor)) {
                        processedTenors.add(tenor);
                        sendForwardMarketDataRequest(sessionId, pair, swapPoint.fromMaturity, swapPoint.toMaturity, isNDF);
                    } else {
                        log.info("Skipping duplicate tenor {} for currency pair {}", tenor, pair);
                    }
                }
            }
        }
    }

    public void sendForwardMarketDataRequest(SessionID sessionID, String symbol,
                                             String fromMaturity, String toMaturity, boolean isNDF) {
        String rawTenor = toMaturity != null ? toMaturity : fromMaturity;
        String normalizedTenor = normalizeTenor(rawTenor);

        String subscriptionKey = symbol + "::" + normalizedTenor;

        if (sentSubscriptions.contains(subscriptionKey)) {
            log.info("Duplicate subscription for {} skipped", subscriptionKey);
            return;
        }

        sentSubscriptions.add(subscriptionKey);

        try {
            MarketDataRequest request = new MarketDataRequest();
            String mdReqId = String.format(
                    "MDReq-%s-%s-%d-%s",
                    symbol.replace("/", ""),
                    normalizedTenor,
                    System.currentTimeMillis(),
                    UUID.randomUUID().toString().substring(0, 8)
            );
            request.setField(new MDReqID(mdReqId));
            request.setField(new SubscriptionRequestType('1'));
            request.setField(new NoRelatedSym(1));

            MarketDataRequest.NoRelatedSym symbolGroup = new MarketDataRequest.NoRelatedSym();
            symbolGroup.setField(new Symbol(symbol));
            symbolGroup.setField(new SymbolSfx("KX"));

            boolean tenorValueSet = false;

            if (toMaturity != null && !toMaturity.isEmpty()) {
                if (!normalizedTenor.isEmpty()) {
                    symbolGroup.setField(new StringField(6215, normalizedTenor));
                    log.info("Using toMaturity in 6215: {} for {}", normalizedTenor, symbol);
                    tenorValueSet = true;
                }
            }

            if (!tenorValueSet && fromMaturity != null && !fromMaturity.isEmpty()) {
                if (!normalizedTenor.isEmpty() && normalizedTenor.matches("^\\d{8}$")) {
                    symbolGroup.setField(new SettlDate(normalizedTenor));
                    log.info("Using fromMaturity as SettlDate: {} for {}", normalizedTenor, symbol);
                }
            }

            if (isNDF) {
                symbolGroup.setField(new StringField(9001, "NDF"));
                symbolGroup.setField(new SettlType("6"));
                log.debug("NDF flag set for {}", symbol);
            }

            request.addGroup(symbolGroup);
            Session.sendToTarget(request, sessionID);

            log.info("Sent market data request for {}, MDReqID: {}, Tenor: {}, NDF: {}",
                    symbol, mdReqId, normalizedTenor, isNDF);
        } catch (Exception e) {
            sentSubscriptions.remove(subscriptionKey);
            log.error("Error sending market data request for {}: {}", symbol, e.getMessage(), e);
        }
    }

    public void sendMarketDataRequest(SessionID sessionID, String symbol) {
        String subscriptionKey = symbol + "::SPOT";

        if (sentSubscriptions.contains(subscriptionKey)) {
            log.info("Duplicate spot subscription for {} skipped", symbol);
            return;
        }

        sentSubscriptions.add(subscriptionKey);

        try {
            MarketDataRequest request = new MarketDataRequest();
            String mdReqId = String.format(
                    "MDReq-%s-SPOT-%d-%s",
                    symbol.replace("/", ""),
                    System.currentTimeMillis(),
                    UUID.randomUUID().toString().substring(0, 8)
            );
            request.setField(new MDReqID(mdReqId));
            request.setField(new SubscriptionRequestType('1'));
            request.setField(new NoRelatedSym(1));

            MarketDataRequest.NoRelatedSym symbolGroup = new MarketDataRequest.NoRelatedSym();
            symbolGroup.setField(new Symbol(symbol));
            symbolGroup.setField(new SymbolSfx("KX"));
            request.addGroup(symbolGroup);
            Session.sendToTarget(request, sessionID);
            log.info("Sent spot market data request for {}, MDReqID: {}", symbol, mdReqId);
        } catch (Exception e) {
            sentSubscriptions.remove(subscriptionKey);
            log.error("Error sending spot market data request for {}: {}", symbol, e.getMessage(), e);
        }
    }


    @Override
    public void onMessage(MarketDataSnapshotFullRefresh message, SessionID sessionID) {
        try {
            String symbol = message.getString(Symbol.FIELD);
            String reqID = message.isSetField(MDReqID.FIELD) ? message.getString(MDReqID.FIELD) : "";
            String symbolSfx = message.isSetField(SymbolSfx.FIELD) ? message.getString(SymbolSfx.FIELD) : "";
            String origin = message.isSetField(6313) ? message.getString(6313) : "FIX";

            List<Object[]> rows = new ArrayList<>();

            for (Group group : message.getGroups(NoMDEntries.FIELD)) {
                char entryType = group.getChar(MDEntryType.FIELD);
                String side = (entryType == MDEntryType.BID) ? "BID" : "OFFER";
                String tenorValue = group.isSetField(6215) ? group.getString(6215) : "";

                Double price = group.isSetField(MDEntryPx.FIELD) ? group.getDouble(MDEntryPx.FIELD) : 0.0;
                Double size = group.isSetField(MDEntrySize.FIELD) ? group.getDouble(MDEntrySize.FIELD) : 0.0;
                Double forwardPoints = 0.0;
                if (group.isSetField(5675)) {
                    try {
                        forwardPoints = Double.parseDouble(group.getString(5675));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid forwardPoints value '{}'", group.getString(5675));
                        forwardPoints = 0.0;
                    }
                }

                String pipStr = group.isSetField(5678) ? group.getString(5678) : "";

                java.sql.Date settlDate = null;
                if (group.isSetField(SettlDate.FIELD)) {
                    settlDate = parseFixDate(group.getString(SettlDate.FIELD));
                }

                String spotVDate = group.isSetField(6314) ? group.getString(6314) : "";

                java.sql.Timestamp time = group.isSetField(MDEntryTime.FIELD)
                        ? parseFixTime(group.getString(MDEntryTime.FIELD))
                        : java.sql.Timestamp.from(Instant.now());

                java.sql.Timestamp rcvTime = java.sql.Timestamp.from(Instant.now());
                java.sql.Date entryDate = new java.sql.Date(System.currentTimeMillis());

                boolean quoteCond = group.isSetField(QuoteCondition.FIELD)
                        && group.getString(QuoteCondition.FIELD).charAt(0) == 'A';

                Object[] row = new Object[]{
                        time,
                        rcvTime,
                        reqID,
                        symbol,
                        symbolSfx,
                        1,
                        side,
                        price,
                        size,
                        entryDate,
                        quoteCond,
                        settlDate,
                        forwardPoints,
                        pipStr,
                        tenorValue,
                        spotVDate,
                        origin
                };

                rows.add(row);
            }

            Object[][] batch = rows.toArray(new Object[0][]);
            log.info("Inserting to KDB: symbol={} rows=\n{}", symbol, Arrays.deepToString(batch));
            kdbConnection.insertBatch(batch);
            log.info("Published {} rows for symbol {}", rows.size(), symbol);

        } catch (Exception e) {
            log.error("Unexpected error processing MarketDataSnapshotFullRefresh: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onLogout(SessionID sessionId) {
        log.info("Logout Session: {}", sessionId);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            if (MsgType.LOGON.equals(msgType)) {
                message.setInt(6300, 1);  // Set custom tag 6300=1
                log.info("Added tag 6300=1 to Logon message for session: {}", sessionId);
            }
        } catch (FieldNotFound e) {
            log.warn("MsgType not found in toAdmin for session: {}", sessionId, e);
        }
    }
    @Override
    public void fromAdmin(Message message, SessionID sessionId) {
        log.info("Admin Message received: {}", message);
    }

    @Override
    public void toApp(Message message, SessionID sessionId) {
        log.info("Application Message sent: {}", message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionId)
            throws UnsupportedMessageType, IncorrectTagValue, FieldNotFound {
        log.info("Application Message received: {}", message);

        String msgType = message.getHeader().getString(MsgType.FIELD);

        if (MsgType.MARKET_DATA_REQUEST_REJECT.equals(msgType)) {
            handleMarketDataRequestReject(message, sessionId);
        } else {
            crack(message, sessionId);
        }
    }

    private void handleMarketDataRequestReject(Message message, SessionID sessionId) {
        try {
            String mdReqID = message.isSetField(MDReqID.FIELD) ? message.getString(MDReqID.FIELD) : "UNKNOWN";
            String reason = message.isSetField(Text.FIELD) ? message.getString(Text.FIELD) : "No reason provided";

            log.error("Market Data Request Rejected. MDReqID: {}, Reason: {}", mdReqID, reason);
        } catch (FieldNotFound e) {
            log.error("Error handling Market Data Request Reject: {}", e.getMessage(), e);
        }
    }

    public static Instant getCurrentTime() {
        return Instant.now();
    }

    private java.sql.Date parseFixDate(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.length() != 8) return null;
        try {
            int year = Integer.parseInt(yyyymmdd.substring(0, 4));
            int month = Integer.parseInt(yyyymmdd.substring(4, 6));
            int day = Integer.parseInt(yyyymmdd.substring(6, 8));
            return java.sql.Date.valueOf(LocalDate.of(year, month, day));
        } catch (Exception e) {
            log.warn("Failed to parse FIX date string '{}'", yyyymmdd, e);
            return null;
        }
    }

    private java.sql.Timestamp parseFixTime(String hhmmss) {
        if (hhmmss == null || !hhmmss.contains(":")) return java.sql.Timestamp.from(Instant.now());
        try {
            LocalTime time = LocalTime.parse(hhmmss);
            LocalDate today = LocalDate.now();
            return java.sql.Timestamp.valueOf(LocalDateTime.of(today, time));
        } catch (Exception e) {
            log.warn("Failed to parse FIX time '{}'", hhmmss, e);
            return java.sql.Timestamp.from(Instant.now());
        }
    }
}