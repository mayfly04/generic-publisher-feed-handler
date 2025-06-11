
package com.di;

import com.di.acceptor.TriggeringAcceptorApp;
import lombok.extern.slf4j.Slf4j;
import quickfix.*;

import java.io.FileInputStream;

@Slf4j
public class MainBridgeApp {
//    public static void main(String[] args) throws Exception {
//        System.out.println("Starting QuickFix Acceptor !! ");
//        if (args.length != 1) {
//            log.error("Please provide config file path as an argument.");
//            System.out.println("Please provide config file path as an argument.");
//            return;
//        }
//        String fileName = args[0];
//        SessionSettings settings = new SessionSettings(new FileInputStream(fileName));
//        Application acceptorApp = new TriggeringAcceptorApp();
//        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
//        LogFactory logFactory = new SLF4JLogFactory(settings);
//        MessageFactory messageFactory = new DefaultMessageFactory();
//
//        Acceptor acceptor = new SocketAcceptor(acceptorApp, storeFactory, settings, logFactory, messageFactory);
//        acceptor.start();
//
//        log.info("Triggering Acceptor is up. Waiting for inbound logons...");
//    }
}
