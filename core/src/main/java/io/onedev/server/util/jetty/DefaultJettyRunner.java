package io.onedev.server.util.jetty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.DispatcherType;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;

import com.google.inject.servlet.GuiceFilter;

import io.onedev.launcher.bootstrap.Bootstrap;
import io.onedev.utils.ExceptionUtils;

@Singleton
public class DefaultJettyRunner implements JettyRunner, Provider<ServletContextHandler> {

	private static final int MAX_CONTENT_SIZE = 5000000;
	
	private Server jettyServer;
	
	private ServletContextHandler servletContextHandler;
	
	private final Provider<Set<ServerConfigurator>> serverConfiguratorsProvider;
	
	private final Provider<Set<ServletConfigurator>> servletConfiguratorsProvider;
	
	@Override
	public ServletContextHandler get() {
		return servletContextHandler;
	}

	/*
	 * Inject providers here to avoid circurlar dependencies when dependency graph gets complicated
	 */
	@Inject
	public DefaultJettyRunner(
			Provider<Set<ServerConfigurator>> serverConfiguratorsProvider, 
			Provider<Set<ServletConfigurator>> servletConfiguratorsProvider) {
		this.serverConfiguratorsProvider = serverConfiguratorsProvider;
		this.servletConfiguratorsProvider = servletConfiguratorsProvider;
	}
	
	@Override
	public void start() {
		jettyServer = new Server();

        servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        servletContextHandler.setMaxFormContentSize(MAX_CONTENT_SIZE);

        servletContextHandler.setClassLoader(DefaultJettyRunner.class.getClassLoader());
        
        servletContextHandler.setErrorHandler(new ErrorPageErrorHandler());
        servletContextHandler.addFilter(DisableTraceFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        
        servletContextHandler.getSessionHandler().getSessionManager().setSessionIdPathParameterName(null);
        
        /*
         * By default contributions is in reverse dependency order. We reverse the order so that 
         * servlet and filter contributions in dependency plugins comes first. 
         */
        List<ServletConfigurator> servletConfigurators = new ArrayList<>(servletConfiguratorsProvider.get());
        Collections.reverse(servletConfigurators);
        for (ServletConfigurator configurator: servletConfigurators) {
        	configurator.configure(servletContextHandler);
        }

        /*
         *  Add Guice filter as last filter in order to make sure that filters and servlets
         *  configured in Guice web module can be filtered correctly by filters added to 
         *  Jetty context directly.  
         */
        servletContextHandler.addFilter(GuiceFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));

        GzipHandler gzipHandler = new GzipHandler();
		gzipHandler.setIncludedMimeTypes("text/css", "application/javascript", "text/javascript");
		gzipHandler.setHandler(servletContextHandler);

        jettyServer.setHandler(gzipHandler);

        for (ServerConfigurator configurator: serverConfiguratorsProvider.get()) 
        	configurator.configure(jettyServer);
        
        if (Bootstrap.command == null) {
			try {
				jettyServer.start();
			} catch (Exception e) {
				throw ExceptionUtils.unchecked(e);
			}
		}
	}

	@Override
	public void stop() {
		if (jettyServer != null && jettyServer.isStarted()) {
			try {
				jettyServer.stop();
			} catch (Exception e) {
				throw ExceptionUtils.unchecked(e);
			}
		}
	}

}
