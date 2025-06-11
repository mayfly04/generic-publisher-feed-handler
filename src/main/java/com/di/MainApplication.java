package com.di;

import com.di.fix.FIXApplication;
import com.di.helper.PropertiesHelper;
import lombok.extern.slf4j.Slf4j;
import quickfix.*;

import java.io.FileInputStream;
import java.util.Properties;

@Slf4j
public class MainApplication {
	public static Properties fixSessionIds = PropertiesHelper.loadProperties("src/main/resources/fix.properties");

	public static void main(String[] args) {

		System.out.println("Starting QuickFix Acceptor !! ");
		if (args.length != 1) {
			log.error("Please provide config file path as an argument.");
			System.out.println("Please provide config file path as an argument.");
			return;
		}

		String fileName = args[0];
		FIXApplication application = null;
		try {
			application = (FIXApplication) Class.forName(fixSessionIds.getProperty("fix_application_class")).getDeclaredConstructor().newInstance();
			log.info("Application class loaded {}", application.getClass().toString());
		} catch (Exception e) {
			log.error("Error Loading Fix Application Class {}", e.getMessage());
		}

		try {
			SessionSettings settings = new SessionSettings(new FileInputStream(fileName));
			MessageStoreFactory storeFactory = new FileStoreFactory(settings);
			LogFactory logFactory = new SLF4JLogFactory(settings);
			MessageFactory messageFactory = new DefaultMessageFactory();
			Acceptor acceptor = new SocketAcceptor(application, storeFactory, settings, logFactory, messageFactory);
			acceptor.start();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
