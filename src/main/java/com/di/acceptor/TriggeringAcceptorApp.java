
package com.di.acceptor;

import com.di.MainApplication;
import lombok.extern.slf4j.Slf4j;
import quickfix.*;

@Slf4j
public class TriggeringAcceptorApp extends MessageCracker implements Application {

    @Override
    public void onLogon(SessionID sessionID) {
        log.info("Inbound logon received on acceptor session: {}", sessionID);

        new Thread(() -> {
            try {
                String[] args = {"src/main/resources/di-kx.cfg"};
                MainApplication.main(args);
            } catch (Exception e) {
                log.error("Failed to trigger initiator app", e);
            }
        }).start();
    }

    @Override public void onCreate(SessionID sessionId) {}
    @Override public void onLogout(SessionID sessionId) {}
    @Override public void toAdmin(Message message, SessionID sessionId) {}
    @Override public void fromAdmin(Message message, SessionID sessionId) {}
    @Override public void toApp(Message message, SessionID sessionId) {}
    @Override public void fromApp(Message message, SessionID sessionId)
            throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {}
}
